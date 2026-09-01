package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact schema 3 artifact bundled with Operit and used as the required base theme. */
internal object ThemePackageDefaultV2 {
    const val PACKAGE_ID = "operit.default"
    const val VERSION = "2.2.0"
    const val ARCHIVE_SHA256 = "dba4169c8e1636c5f2d85749f7770b8657d99217764563aac0302137e37ef4fd"

    private const val ASSET_PATH = "theme-packages/operit-default-v2.otheme"

    val coordinate =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2(PACKAGE_ID),
            version = ThemePackageVersionV2(VERSION),
            archiveSha256 = ThemeArchiveSha256V2(ARCHIVE_SHA256),
        )

    suspend fun ensureInstalled(context: Context) {
        withContext(Dispatchers.IO) {
            val installer = ThemePackageInstallerV2.getInstance(context)
            installer.clearUnpublishedSchema2Installations()
            if (installer.find(coordinate) != null) return@withContext

            val staged = File(context.cacheDir, "operit-default-v2.$THEME_PACKAGE_EXTENSION_V2")
            try {
                context.assets.open(ASSET_PATH).use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
                val installed = installer.import(staged, expectedSha256 = ARCHIVE_SHA256)
                check(installed == coordinate) {
                    "Bundled default schema 3 theme coordinate does not match its release lock."
                }
            } finally {
                staged.delete()
            }
        }
    }

    fun isDefault(coordinate: ThemePackageCoordinateV2): Boolean = coordinate == this.coordinate
}
