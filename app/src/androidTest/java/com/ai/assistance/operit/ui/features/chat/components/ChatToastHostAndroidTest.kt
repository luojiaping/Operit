package com.ai.assistance.operit.ui.features.chat.components

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatToastEvent
import com.ai.assistance.operit.ui.theme.NativeThemeOffscreenHost
import com.ai.assistance.operit.ui.theme.themePackageRuntimeForAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatToastHostAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dismissControlRetainsItsClickActionAndEventIdentity() {
        var dismissedId: Long? = null
        val event = ChatToastEvent(id = 42L, message = "Theme package updated")

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = GlobalPresentationSnapshot.default(),
                packageRuntime = themePackageRuntimeForAndroidTest(),
            ) {
                ChatToastHostContent(
                    event = event,
                    onDismiss = { dismissedId = it },
                    modifier = Modifier,
                    maxWidth = 720.dp,
                    maxHeight = 240.dp,
                    autoDismissDelayMillis = { 60_000L },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(closeLabel())
            .assertHasClickAction()
            .performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(event.id, dismissedId) }
    }

    private fun closeLabel(): String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.close)
}
