package com.ai.assistance.operit.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeOutlinedTextFieldV2Test {
    @Test
    fun inputSkinStatePrioritizesDisabledThenErrorThenFocus() {
        assertEquals(
            ThemeComponentStateV2.DISABLED,
            resolveThemeOutlinedTextFieldStateV2(
                enabled = false,
                isError = true,
                isFocused = true,
            ),
        )
        assertEquals(
            ThemeComponentStateV2.ERROR,
            resolveThemeOutlinedTextFieldStateV2(
                enabled = true,
                isError = true,
                isFocused = true,
            ),
        )
        assertEquals(
            ThemeComponentStateV2.FOCUSED,
            resolveThemeOutlinedTextFieldStateV2(
                enabled = true,
                isError = false,
                isFocused = true,
            ),
        )
        assertEquals(
            ThemeComponentStateV2.NORMAL,
            resolveThemeOutlinedTextFieldStateV2(
                enabled = true,
                isError = false,
                isFocused = false,
            ),
        )
    }
}
