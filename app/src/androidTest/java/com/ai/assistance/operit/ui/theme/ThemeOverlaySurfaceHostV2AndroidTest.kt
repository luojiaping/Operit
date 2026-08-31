package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
