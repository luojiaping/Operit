package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.ui.theme.NativeThemeOffscreenHost
import com.ai.assistance.operit.ui.theme.themePackageRuntimeForAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchDialogAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchDialogPreservesQueryAndConfirmCallback() {
        var query by mutableStateOf("")
        var searchCount = 0

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = GlobalPresentationSnapshot.default(),
                packageRuntime = themePackageRuntimeForAndroidTest(),
            ) {
                SearchDialog(
                    showDialog = true,
                    searchQuery = query,
                    onQueryChange = { query = it },
                    isCaseSensitive = false,
                    onCaseSensitiveChange = {},
                    useWildcard = false,
                    onWildcardChange = {},
                    onSearch = { searchCount += 1 },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("*.md")
        composeTestRule.onNodeWithText(searchLabel()).performClick()
        composeTestRule.runOnIdle {
            assertEquals("*.md", query)
            assertEquals(1, searchCount)
        }
    }

    private fun searchLabel(): String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.search)
}
