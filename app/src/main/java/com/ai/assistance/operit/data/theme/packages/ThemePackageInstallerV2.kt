package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ThemePackageInstallExceptionV2(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Installs only fully linked V2 packages into immutable content-addressed directories. */
internal class ThemePackageInstallerV2 private constructor(
    private val context: Context,
) {
    private val mutationMutex = Mutex()
    private val installedRoot: File
        get() = File(context.filesDir, INSTALLED_ROOT)

    private val stagingRoot: File
        get() = File(context.cacheDir, STAGING_ROOT)

    suspend fun import(
        archive: File,
        expectedSha256: String? = null,
    ): ThemePackageCoordinateV2 =
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                val staged = stage(archive)
                try {
                    val validated = ThemePackageArchiveValidatorV2.validate(staged, expectedSha256)
                    validated.manifest.basis?.let { basis ->
                        if (catalog().installations.none { installation -> installation.coordinate == basis }) {
                            throw ThemePackageInstallExceptionV2(
                                "Theme package basis is not installed: ${basis.packageId.value}@${basis.version.value}",
                            )
                        }
                    }
                    val coordinate = ThemePackagePublicationV2.publish(staged, validated, installedRoot)
                    try {
                        val installation = requireNotNull(find(coordinate)) {
                            "Published theme package cannot be found: ${coordinate.packageId.value}."
                        }
                        ThemePackageRuntimeLinkerV2.link(installation, catalog())
                        ThemeRuntimeRepositoryV2.refresh(context)
                        coordinate
                    } catch (error: Throwable) {
                        ThemePackagePublicationV2.uninstall(installedRoot, coordinate)
                        throw ThemePackageInstallExceptionV2(
                            "Theme package does not form a complete linked schema 3 presentation: ${error.message}",
                            error,
                        )
                    }
                } finally {
                    staged.delete()
                }
            }
        }

    suspend fun uninstall(coordinate: ThemePackageCoordinateV2): Boolean =
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                val target = ThemePackagePublicationV2.installationDir(installedRoot, coordinate)
                if (!target.exists()) return@withContext false
                check(!ThemePackageDefaultV2.isDefault(coordinate)) {
                    "Bundled default theme package cannot be uninstalled: ${coordinate.packageId.value}"
                }
                val active =
                    ThemePackageSelectionRepositoryV2.getInstance(context)
                        .selectionFlow
                        .first()
                        .reference.coordinate
                check(active != coordinate) {
                    "The active theme package cannot be uninstalled: ${coordinate.packageId.value}"
                }
                val dependents =
                    catalog().installations.filter { installation -> installation.manifest.basis == coordinate }
                check(dependents.isEmpty()) {
                    "Theme package is required by installed packages: " +
                        dependents.joinToString { installation -> installation.coordinate.packageId.value }
                }
                val uninstalled = ThemePackagePublicationV2.uninstall(installedRoot, coordinate)
                ThemeRuntimeRepositoryV2.refresh(context)
                uninstalled
            }
        }

    suspend fun activate(coordinate: ThemePackageCoordinateV2) {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                check(ThemeRuntimeRepositoryV2.isLinked(coordinate)) {
                    "Theme package is unavailable for activation: ${coordinate.packageId.value}"
                }
                val selectionRepository = ThemePackageSelectionRepositoryV2.getInstance(context)
                if (!requiresThemeActivation(selectionRepository.currentSelection(), coordinate)) {
                    return@withContext
                }
                selectionRepository.replaceSelection(
                    ThemeInstanceV2(reference = ThemePackageReferenceV2(coordinate)),
                )
                selectionRepository.reconcileThemeImageGrants()
            }
        }
    }

    suspend fun replaceActiveImageParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
        uri: Uri,
    ) {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                check(ThemeRuntimeRepositoryV2.isLinked(expectedCoordinate)) {
                    "Theme package is unavailable for image parameter update: ${expectedCoordinate.packageId.value}"
                }
                val selectionRepository = ThemePackageSelectionRepositoryV2.getInstance(context)
                check(selectionRepository.currentSelection().reference.coordinate == expectedCoordinate) {
                    "Theme selection changed before image parameter $parameterId was written."
                }
                try {
                    val alreadyPersisted =
                        context.contentResolver.persistedUriPermissions.any { permission ->
                            permission.uri == uri && permission.isReadPermission
                        }
                    val themeOwnsGrant =
                        if (alreadyPersisted) {
                            selectionRepository.hasThemeImageGrantOwnership(uri.toString())
                        } else {
                            selectionRepository.markPendingImageGrant(uri.toString())
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                            true
                        }
                    selectionRepository.replaceImageParameter(
                        expectedCoordinate = expectedCoordinate,
                        parameterId = parameterId,
                        value = ThemeParameterValueV2.ImageUriValue(uri.toString()),
                        trackUriOwnership = themeOwnsGrant,
                    )
                } finally {
                    selectionRepository.reconcileThemeImageGrants()
                }
            }
        }
    }

    suspend fun clearActiveImageParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
    ) {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                val selectionRepository = ThemePackageSelectionRepositoryV2.getInstance(context)
                selectionRepository.clearImageParameter(
                    expectedCoordinate = expectedCoordinate,
                    parameterId = parameterId,
                )
                selectionRepository.reconcileThemeImageGrants()
            }
        }
    }

    fun catalog(): PublishedThemeCatalogV2 = ThemePackagePublicationV2.catalog(installedRoot)

    fun find(coordinate: ThemePackageCoordinateV2): PublishedThemeInstallationV2? =
        catalog().installations.firstOrNull { installation -> installation.coordinate == coordinate }

    fun clearUnpublishedSchema2Installations() {
        File(context.filesDir, LEGACY_V1_INSTALLED_ROOT).deleteRecursively()
        clearUnpublishedSchema2ThemeInstallations(installedRoot)
    }

    private fun stage(archive: File): File {
        stagingRoot.mkdirs()
        val staged = File(stagingRoot, "${UUID.randomUUID()}.$THEME_PACKAGE_EXTENSION_V2")
        if (!archive.copyTo(staged, overwrite = true).isFile) {
            throw ThemePackageInstallExceptionV2("Unable to stage theme archive for validation.")
        }
        return staged
    }

    companion object {
        private const val INSTALLED_ROOT = "theme-packages/v2/installed"
        private const val STAGING_ROOT = "theme-packages/v2/staging"
        private const val LEGACY_V1_INSTALLED_ROOT = "theme-packages/installed"

        @Volatile
        private var instance: ThemePackageInstallerV2? = null

        fun getInstance(context: Context): ThemePackageInstallerV2 =
            instance ?: synchronized(this) {
                instance ?: ThemePackageInstallerV2(context.applicationContext).also { created ->
                    instance = created
                }
            }

        fun isThemePackageFileName(name: String): Boolean =
            name.lowercase(Locale.US).endsWith(".$THEME_PACKAGE_EXTENSION_V2")
    }
}

internal fun clearUnpublishedSchema2ThemeInstallations(installedRoot: File) {
    if (!installedRoot.isDirectory) return
    installedRoot.walkBottomUp()
        .filter { file -> file.name == THEME_PACKAGE_MANIFEST_ENTRY_V2 && file.isFile }
        .forEach { manifestFile ->
            val schemaVersion =
                try {
                    Json.parseToJsonElement(manifestFile.readText(Charsets.UTF_8))
                        .jsonObject["schemaVersion"]
                        ?.jsonPrimitive
                        ?.content
                        ?.toIntOrNull()
                } catch (_: Throwable) {
                    null
                }
            if (schemaVersion == 2) {
                requireNotNull(manifestFile.parentFile).deleteRecursively()
            }
        }
}

internal fun requiresThemeActivation(
    current: ThemeInstanceV2,
    target: ThemePackageCoordinateV2,
): Boolean = current.reference.coordinate != target
