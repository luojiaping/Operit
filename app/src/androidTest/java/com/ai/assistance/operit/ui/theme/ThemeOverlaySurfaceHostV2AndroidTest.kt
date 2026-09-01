package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceCatalogV2
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeOverlaySurfaceHostV2AndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun overlayHostAppliesDialogSkinAndPreservesChildClickSemantics() {
        var clickCount = 0

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = GlobalPresentationSnapshot.default(),
                packageRuntime = themePackageRuntimeForAndroidTest(),
            ) {
                ThemeOverlaySurfaceHostV2(
                    surface = ThemeSurfaceCatalogV2.OVERLAY_DIALOG,
                    modifier = Modifier.size(64.dp).testTag("overlay-host"),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(16.dp)
                                .background(LocalContentColor.current)
                                .testTag("overlay-child")
                                .clickable { clickCount += 1 },
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("overlay-child")
            .assertHasClickAction()
            .performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(1, clickCount) }

        val overlayImage = composeTestRule.onNodeWithTag("overlay-host").captureToImage()
        assertEquals(Color(0xFF3E2D56).toArgb(), pixelAtDp(overlayImage, 32f, 32f))
        assertEquals(Color(0xFF00E5FF).toArgb(), pixelAtDp(overlayImage, 32f, 1f))

        val childImage = composeTestRule.onNodeWithTag("overlay-child").captureToImage()
        assertEquals(Color(0xFFFFF0C7).toArgb(), pixelAtDp(childImage, 8f, 8f))
    }

    @Test
    fun overlayHostAppliesMenuAndSnackbarSkins() {
        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = GlobalPresentationSnapshot.default(),
                packageRuntime = themePackageRuntimeForAndroidTest(),
            ) {
                Column {
                    ThemeOverlaySurfaceHostV2(
                        surface = ThemeSurfaceCatalogV2.OVERLAY_MENU,
                        modifier = Modifier.size(48.dp).testTag("menu-host"),
                        applyContentPadding = false,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .background(LocalContentColor.current)
                                    .testTag("menu-content"),
                        )
                    }
                    ThemeOverlaySurfaceHostV2(
                        surface = ThemeSurfaceCatalogV2.OVERLAY_SNACKBAR,
                        modifier = Modifier.size(48.dp).testTag("snackbar-host"),
                        applyContentPadding = false,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .background(LocalContentColor.current)
                                    .testTag("snackbar-content"),
                        )
                    }
                }
            }
        }

        val expectedContainer = Color(0xFF102030).toArgb()
        val expectedContent = Color(0xFFE5F6FF).toArgb()
        assertEquals(expectedContainer, pixelAtDp(composeTestRule.onNodeWithTag("menu-host").captureToImage(), 24f, 24f))
        assertEquals(expectedContent, pixelAtDp(composeTestRule.onNodeWithTag("menu-content").captureToImage(), 8f, 8f))
        assertEquals(expectedContainer, pixelAtDp(composeTestRule.onNodeWithTag("snackbar-host").captureToImage(), 24f, 24f))
        assertEquals(expectedContent, pixelAtDp(composeTestRule.onNodeWithTag("snackbar-content").captureToImage(), 8f, 8f))
    }

    @Test
    fun dropdownMenuUsesTheMenuSkinForItsCompletePopupContainer() {
        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = GlobalPresentationSnapshot.default(),
                packageRuntime = themePackageRuntimeForAndroidTest(),
            ) {
                ThemeDropdownMenuV2(
                    expanded = true,
                    onDismissRequest = {},
                    modifier = Modifier.testTag("themed-menu"),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .background(LocalContentColor.current)
                                .testTag("themed-menu-content"),
                    )
                }
            }
        }

        val menuImage = composeTestRule.onNodeWithTag("themed-menu").captureToImage()
        assertEquals(Color(0xFF102030).toArgb(), pixelAtDp(menuImage, 12f, 1f))
        assertEquals(
            Color(0xFFE5F6FF).toArgb(),
            pixelAtDp(composeTestRule.onNodeWithTag("themed-menu-content").captureToImage(), 12f, 12f),
        )
    }

    private fun pixelAtDp(
        image: ImageBitmap,
        xDp: Float,
        yDp: Float,
    ): Int {
        val x = (xDp * composeTestRule.density.density).toInt().coerceIn(0, image.width - 1)
        val y = (yDp * composeTestRule.density.density).toInt().coerceIn(0, image.height - 1)
        return image.toPixelMap()[x, y].toArgb()
    }
}
