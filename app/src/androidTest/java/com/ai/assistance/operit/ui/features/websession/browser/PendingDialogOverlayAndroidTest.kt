package com.ai.assistance.operit.ui.features.websession.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionPendingDialogState
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.ui.theme.NativeThemeOffscreenHost
import com.ai.assistance.operit.ui.theme.themePackageRuntimeForAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingDialogOverlayAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun promptDialogPreservesInputAndConfirmationCallbacks() {
        val initialValue = "initial value"
        var promptValue by mutableStateOf(initialValue)
        var confirmedValue: String? = null

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = GlobalPresentationSnapshot.default(),
                packageRuntime = themePackageRuntimeForAndroidTest(),
            ) {
                PendingDialogOverlay(
                    dialog =
                        WebSessionPendingDialogState(
                            type = "prompt",
                            message = "Enter a value",
                        ),
                    promptValue = promptValue,
                    onPromptValueChange = { promptValue = it },
                    onConfirm = { confirmedValue = promptValue },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText(initialValue).performTextReplacement("updated value")
        composeTestRule.onNodeWithText(okLabel()).performClick()
        composeTestRule.runOnIdle {
            assertEquals("updated value", promptValue)
            assertEquals("updated value", confirmedValue)
        }
    }

    private fun okLabel(): String =
        ApplicationProvider.getApplicationContext<Context>().getString(android.R.string.ok)
}
