package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.theme.packages.ThemeParameterControlV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterDefinitionV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterEffectV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterSectionV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterTypeV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterValueV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterVisibilityV2
import com.ai.assistance.operit.ui.theme.NativeThemeOffscreenHost
import com.ai.assistance.operit.ui.theme.themePackageRuntimeForAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePackagesContentAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun defaultThemeOptionsRemainVisibleWhenTheBackgroundIsUnset() {
        val accent =
            ThemeParameterDefinitionV2(
                id = "accent_color",
                type = ThemeParameterTypeV2.COLOR,
                defaultValue = ThemeParameterValueV2.ColorValue(0xFF6750A4),
                label = localized("Accent color"),
                control = ThemeParameterControlV2.ColorPalette(presetArgb = listOf(0xFF6750A4)),
                effects = listOf(ThemeParameterEffectV2.AccentPalette),
                visibility = ThemeParameterVisibilityV2.USER,
                section = ThemeParameterSectionV2.APPEARANCE,
            )
        val background =
            ThemeParameterDefinitionV2(
                id = "background_image",
                type = ThemeParameterTypeV2.IMAGE_URI,
                label = localized("Background image"),
                control = ThemeParameterControlV2.ImagePicker(),
                effects =
                    listOf(
                        ThemeParameterEffectV2.StageImage(
                            surfaceIds = listOf("app.shell", "chat.main"),
                        ),
                    ),
                visibility = ThemeParameterVisibilityV2.USER,
                section = ThemeParameterSectionV2.APPEARANCE,
            )

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = GlobalPresentationSnapshot.default(),
                packageRuntime = themePackageRuntimeForAndroidTest(),
            ) {
                ThemePackagesContent(
                    selectedTitle = "Operit Default",
                    selectedSubtitle = "operit.default · v3.0.0",
                    installed = emptyList(),
                    presentation = GlobalPresentationSnapshot.default(),
                    definitions = listOf(accent, background),
                    resolvedValues =
                        mapOf(
                            accent.id to ThemeParameterValueV2.ColorValue(0xFF6750A4),
                        ),
                    overriddenIds = emptySet(),
                    busy = false,
                    onOpenThemePicker = {},
                    onRefresh = {},
                    onImport = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onChatStyleChange = {},
                    onInputStyleChange = {},
                    onParameterChange = { _, _ -> },
                    onParameterClear = {},
                    onOpenResourcePicker = {},
                    onOpenColorDialog = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Accent color").assertExists()
        composeTestRule.onNodeWithText("Background image").assertExists()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_packages_pick_image)).assertExists()
    }

    private fun localized(value: String) =
        com.ai.assistance.operit.data.theme.packages.ThemePackageLocalizedTextV2(
            values = mapOf("*" to value),
        )
}
