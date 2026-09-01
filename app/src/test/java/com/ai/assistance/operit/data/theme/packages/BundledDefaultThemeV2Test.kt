package com.ai.assistance.operit.data.theme.packages

import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.ui.theme.createThemePackageUiRuntimeV2
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * 内置默认主题必须与外置仓库 Release 字节一致，并使用与导入包完全相同的
 * schema 3 校验器。这是“默认主题不是特殊分支”的静态门槛。
 */
class BundledDefaultThemeV2Test {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun bundledDefaultArchivePassesTheSameValidatorAsImportedPackages() {
        val archive = File("src/main/assets/theme-packages/operit-default-v2.otheme")
        assertTrue("Bundled default package is missing: ${archive.absolutePath}", archive.isFile)

        val validated =
            ThemePackageArchiveValidatorV2.validate(
                archive,
                expectedSha256 = ThemePackageDefaultV2.ARCHIVE_SHA256,
            )

        assertEquals(ThemePackageDefaultV2.PACKAGE_ID, validated.manifest.packageId)
        assertEquals(ThemePackageDefaultV2.VERSION, validated.manifest.version)
        assertEquals(THEME_PACKAGE_SCHEMA_VERSION, validated.manifest.schemaVersion)
        assertEquals(
            setOf("accent_color", "background_image"),
            validated.manifest.parameters.map { parameter -> parameter.id }.toSet(),
        )
        assertEquals(
            ThemePackageDefaultV2.coordinate,
            validated.manifest.coordinateFor(validated.archiveSha256),
        )
        ZipFile(archive).use { zip ->
            assertEquals(THEME_PACKAGE_ZIP_COMMENT_V2, zip.comment)
        }
    }

    @Test
    fun bundledDefaultArchiveLinksAndProjectsItsDeclaredParameters() {
        val archive = File("src/main/assets/theme-packages/operit-default-v2.otheme")
        val validated = ThemePackageArchiveValidatorV2.validate(archive)
        val root = tmp.newFolder("installed")
        val coordinate = ThemePackagePublicationV2.publish(archive, validated, root)
        val catalog = ThemePackagePublicationV2.catalog(root)
        val installation = requireNotNull(catalog.installations.singleOrNull())
        val linked = ThemePackageRuntimeLinkerV2.link(installation, catalog)

        assertEquals(coordinate, linked.coordinate)
        assertEquals(coordinate, linked.parameterOwners.getValue("accent_color"))
        assertEquals(coordinate, linked.parameterOwners.getValue("background_image"))

        val parameters =
            ThemePackageRuntimeLinkerV2.resolveParameters(
                ThemeInstanceV2(
                    reference = ThemePackageReferenceV2(coordinate),
                    parameterValues =
                        mapOf(
                            "accent_color" to ThemeParameterValueV2.ColorValue(0xFF00687A),
                            "background_image" to
                                ThemeParameterValueV2.ImageUriValue("content://theme/background"),
                        ),
                ),
                linked,
            )
        val runtime =
            createThemePackageUiRuntimeV2(
                linked = linked,
                parameters = parameters,
                darkTheme = false,
                userFontScale = 1f,
            )

        assertEquals(0xFF00687A.toInt(), runtime.colorScheme.primary.toArgb())
        assertEquals(
            "content://theme/background",
            runtime.stageImage(ThemeSurfaceCatalogV2.APP_SHELL)?.uri,
        )
        assertEquals(
            "content://theme/background",
            runtime.stageImage(ThemeSurfaceCatalogV2.CHAT_MAIN)?.uri,
        )
    }
}
