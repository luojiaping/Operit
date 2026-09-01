package com.ai.assistance.operit.ui.features.settings.screens

import com.ai.assistance.operit.ui.theme.ThemeComponentStateV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackagesLayoutTest {
    @Test
    fun themeChoicesStackOnNarrowOrLargeFontScreens() {
        assertTrue(usesStackedThemeChoiceLayout(screenWidthDp = 360, fontScale = 1f))
        assertTrue(usesStackedThemeChoiceLayout(screenWidthDp = 600, fontScale = 2f))
        assertFalse(usesStackedThemeChoiceLayout(screenWidthDp = 600, fontScale = 1f))
    }

    @Test
    fun unlinkedThemePickerEntriesCannotBeSelected() {
        assertEquals(
            ThemeComponentStateV2.DISABLED,
            themePickerEntryState(selected = false, linked = false),
        )
        assertEquals(
            ThemeComponentStateV2.SELECTED,
            themePickerEntryState(selected = true, linked = true),
        )
    }
}
