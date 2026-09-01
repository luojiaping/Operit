package com.ai.assistance.operit.data.theme.packages

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThemePackageSelectionIntegrityV2Test {
    @Test
    fun schemaOneStringImageParameterIsClearedBeforeStrictDecoding() {
        val preferences =
            mutablePreferencesOf(
                THEME_INSTANCE_V2_KEY to
                    """
                    {"reference":{"coordinate":{"packageId":"operit.default","version":"2.1.0","archiveSha256":"${"a".repeat(64)}"}},"parameterValues":{"background_image":{"type":"string","value":"content://theme/background"}}}
                    """.trimIndent(),
            )

        migrateThemePackageSelectionSchemaV2(version = 1, preferences = preferences)

        assertEquals(ThemeInstanceV2.defaultBundled(), preferences.decodeThemeInstanceV2())
    }

    @Test
    fun unavailableExternalSelectionResetsToBundledDefaultAndDropsParameters() {
        val selection =
            ThemeInstanceV2(
                reference = ThemePackageReferenceV2(coordinate("operit.cyber_grid", "a")),
                parameterValues =
                    mapOf(
                        "background_image" to
                            ThemeParameterValueV2.ImageUriValue("content://theme/background"),
                    ),
            )

        val repaired = selection.repairUnavailableSelection(setOf(ThemePackageDefaultV2.coordinate))

        assertEquals(ThemeInstanceV2.defaultBundled(), repaired)
    }

    @Test
    fun availableExternalSelectionRemainsUnchanged() {
        val selection =
            ThemeInstanceV2(
                reference = ThemePackageReferenceV2(coordinate("operit.cyber_grid", "b")),
                variantId = ThemeVariantIdV2("night"),
            )

        val repaired = selection.repairUnavailableSelection(setOf(selection.reference.coordinate))

        assertSame(selection, repaired)
    }

    @Test
    fun selectingTheActiveCoordinateDoesNotReplaceItsParameterValues() {
        val coordinate = coordinate("operit.default", "d")
        val current =
            ThemeInstanceV2(
                reference = ThemePackageReferenceV2(coordinate),
                parameterValues = mapOf("accent_color" to ThemeParameterValueV2.ColorValue(0xFF00687A)),
            )

        assertEquals(false, requiresThemeActivation(current, coordinate))
    }

    @Test
    fun staleBundledCoordinateResetsToCurrentBundledRelease() {
        val staleSelection =
            ThemeInstanceV2(
                reference =
                    ThemePackageReferenceV2(
                        ThemePackageCoordinateV2(
                            packageId = ThemePackageIdV2(ThemePackageDefaultV2.PACKAGE_ID),
                            version = ThemePackageVersionV2("2.0.0"),
                            archiveSha256 = ThemeArchiveSha256V2("c".repeat(64)),
                        ),
                    ),
            )

        val repaired =
            staleSelection.repairUnavailableSelection(setOf(ThemePackageDefaultV2.coordinate))

        assertEquals(ThemePackageDefaultV2.coordinate, repaired.reference.coordinate)
    }

    private fun coordinate(packageId: String, digestSeed: String): ThemePackageCoordinateV2 =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2(packageId),
            version = ThemePackageVersionV2("2.2.0"),
            archiveSha256 = ThemeArchiveSha256V2(digestSeed.repeat(64)),
        )
}
