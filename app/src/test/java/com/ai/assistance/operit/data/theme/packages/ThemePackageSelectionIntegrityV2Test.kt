package com.ai.assistance.operit.data.theme.packages

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemePackageSelectionIntegrityV2Test {
    @Test
    fun schemaFourSelectionDoesNotReadPreviousSelectionRecords() {
        val preferences =
            mutablePreferencesOf(
                stringPreferencesKey("theme_instance_v2_json") to
                    """
                    {"reference":{"coordinate":{"packageId":"operit.default","version":"2.1.0","archiveSha256":"${"a".repeat(64)}"}},"parameterValues":{"background_image":{"type":"string","value":"content://theme/background"}}}
                    """.trimIndent(),
            )

        assertEquals("theme_instance_v4_json", THEME_INSTANCE_V2_KEY.name)
        assertThrows(IllegalStateException::class.java) {
            preferences.decodeThemeInstanceV2()
        }
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

    private fun coordinate(packageId: String, digestSeed: String): ThemePackageCoordinateV2 =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2(packageId),
            version = ThemePackageVersionV2("3.0.0"),
            archiveSha256 = ThemeArchiveSha256V2(digestSeed.repeat(64)),
        )
}
