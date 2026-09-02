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

internal class ThemePackageInstallExceptionV2(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Installs only fully linked schema-4 packages into immutable content-addressed directories. */
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
                            "Theme package does not form a complete linked schema 4 presentation: ${error.message}",
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
                selectionRepository.reconcileThemeResourceGrants()
            }
        }
    }

    suspend fun replaceActiveResourceParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
        uri: Uri,
        value: ThemeParameterValueV2,
    ) {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                require(value.resourceUri() == uri.toString()) {
                    "Theme resource parameter $parameterId must match the granted URI."
                }
                check(ThemeRuntimeRepositoryV2.isLinked(expectedCoordinate)) {
                    "Theme package is unavailable for resource parameter update: ${expectedCoordinate.packageId.value}"
                }
                val runtime = ThemeRuntimeRepositoryV2.require(expectedCoordinate)
                val definition = requireNotNull(runtime.parameterDefinitions[parameterId]) {
                    "Theme package does not declare resource parameter $parameterId."
                }
                require(runtime.parameterOwners[parameterId] == expectedCoordinate) {
                    "Theme package cannot update inherited resource parameter $parameterId."
                }
                require(definition.type.isUri() && value.matches(definition.type)) {
                    "Theme parameter $parameterId does not accept the selected resource type."
                }
                val selectionRepository = ThemePackageSelectionRepositoryV2.getInstance(context)
                check(selectionRepository.currentSelection().reference.coordinate == expectedCoordinate) {
                    "Theme selection changed before resource parameter $parameterId was written."
                }
                try {
                    val alreadyPersisted =
                        context.contentResolver.persistedUriPermissions.any { permission ->
                            permission.uri == uri && permission.isReadPermission
                        }
                    val themeOwnsGrant =
                        if (alreadyPersisted) {
                            selectionRepository.hasThemeResourceGrantOwnership(uri.toString())
                        } else {
                            selectionRepository.markPendingResourceGrant(uri.toString())
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                            true
                        }
                    selectionRepository.replaceResourceParameter(
                        expectedCoordinate = expectedCoordinate,
                        parameterId = parameterId,
                        value = value,
                        trackUriOwnership = themeOwnsGrant,
                    )
                } finally {
                    selectionRepository.reconcileThemeResourceGrants()
                }
            }
        }
    }

    suspend fun clearActiveResourceParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
    ) {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                val runtime = ThemeRuntimeRepositoryV2.require(expectedCoordinate)
                val definition = requireNotNull(runtime.parameterDefinitions[parameterId]) {
                    "Theme package does not declare resource parameter $parameterId."
                }
                require(runtime.parameterOwners[parameterId] == expectedCoordinate && definition.type.isUri()) {
                    "Theme package cannot reset resource parameter $parameterId."
                }
                val selectionRepository = ThemePackageSelectionRepositoryV2.getInstance(context)
                selectionRepository.clearResourceParameter(
                    expectedCoordinate = expectedCoordinate,
                    parameterId = parameterId,
                )
                selectionRepository.reconcileThemeResourceGrants()
            }
        }
    }

    fun catalog(): PublishedThemeCatalogV2 = ThemePackagePublicationV2.catalog(installedRoot)

    fun find(coordinate: ThemePackageCoordinateV2): PublishedThemeInstallationV2? =
        catalog().installations.firstOrNull { installation -> installation.coordinate == coordinate }

    private fun stage(archive: File): File {
        stagingRoot.mkdirs()
        val staged = File(stagingRoot, "${UUID.randomUUID()}.$THEME_PACKAGE_EXTENSION_V2")
        if (!archive.copyTo(staged, overwrite = true).isFile) {
            throw ThemePackageInstallExceptionV2("Unable to stage theme archive for validation.")
        }
        return staged
    }

    companion object {
        private const val INSTALLED_ROOT = "theme-packages/v4/installed"
        private const val STAGING_ROOT = "theme-packages/v4/staging"

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

internal fun requiresThemeActivation(
    current: ThemeInstanceV2,
    target: ThemePackageCoordinateV2,
): Boolean = current.reference.coordinate != target

private fun ThemeParameterValueV2.resourceUri(): String =
    when (this) {
        is ThemeParameterValueV2.ImageUriValue -> uri
        is ThemeParameterValueV2.VideoUriValue -> uri
        is ThemeParameterValueV2.FontUriValue -> uri
        else -> error("Theme resource parameter value must contain a content URI.")
    }
