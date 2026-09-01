package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeOutlinedTextFieldV2AndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun focusedInputUsesFocusedSkinAndRetainsEditableSemantics() {
        var value by mutableStateOf("MMMM")

        composeTestRule.setContent {
            TestThemeHost {
                ThemeOutlinedTextFieldV2(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.width(280.dp).testTag("input-surface"),
                    textFieldModifier = Modifier.testTag("input-field"),
                    textStyle = TextStyle(fontSize = 28.sp),
                    singleLine = true,
                )
            }
        }

        assertEquals(Color(0xFF102030).toArgb(), surfacePixelArgb())
        assertEquals(Color(0xFF617589).toArgb(), framePixelArgb())
        assertContainsColor(Color(0xFFE5F6FF))

        composeTestRule.onNodeWithTag("input-field").performTouchInput { click() }
        composeTestRule.waitForIdle()

        assertEquals(Color(0xFF16435A).toArgb(), surfacePixelArgb())
        assertEquals(Color(0xFF00E5FF).toArgb(), framePixelArgb())
        assertContainsColor(Color(0xFFE5F6FF))

        composeTestRule.onNodeWithTag("input-field").performTextReplacement("theme query")
        composeTestRule.runOnIdle { assertEquals("theme query", value) }
    }

    @Test
    fun disabledInputUsesDisabledSkinAndRemainsDisabledSemantically() {
        composeTestRule.setContent {
            TestThemeHost {
                ThemeOutlinedTextFieldV2(
                    value = "disabled",
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier.width(280.dp).testTag("input-surface"),
                    textFieldModifier = Modifier.testTag("input-field"),
                )
            }
        }

        composeTestRule.onNodeWithTag("input-field").assertIsNotEnabled()
        assertEquals(Color(0xFF24313B).toArgb(), surfacePixelArgb())
    }

    @Test
    fun errorInputUsesErrorSkinFrame() {
        composeTestRule.setContent {
            TestThemeHost {
                ThemeOutlinedTextFieldV2(
                    value = "error",
                    onValueChange = {},
                    isError = true,
                    modifier = Modifier.width(280.dp).testTag("input-surface"),
                    textFieldModifier = Modifier.testTag("input-field"),
                )
            }
        }

        assertEquals(Color(0xFF5D1C2B).toArgb(), surfacePixelArgb())
        assertEquals(Color(0xFFFF4D6D).toArgb(), framePixelArgb())
    }

    @Composable
    private fun TestThemeHost(content: @Composable () -> Unit) {
        NativeThemeOffscreenHost(
            presentation = GlobalPresentationSnapshot.default(),
            packageRuntime = themePackageRuntimeForAndroidTest(),
            content = content,
        )
    }

    private fun surfacePixelArgb(): Int {
        val image = composeTestRule.onNodeWithTag("input-surface").captureToImage()
        return pixelAtDp(image, 240f, 28f)
    }

    private fun framePixelArgb(): Int {
        val image = composeTestRule.onNodeWithTag("input-surface").captureToImage()
        return pixelAtDp(image, 140f, 1f)
    }

    private fun assertContainsColor(expected: Color) {
        val image = composeTestRule.onNodeWithTag("input-surface").captureToImage()
        val pixels = image.toPixelMap()
        val containsExpectedColor =
            (0 until image.height).any { y ->
                (0 until image.width).any { x -> pixels[x, y].toArgb() == expected.toArgb() }
            }
        assertTrue(containsExpectedColor)
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
