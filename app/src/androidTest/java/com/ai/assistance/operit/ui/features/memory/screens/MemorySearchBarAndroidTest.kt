package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.ui.theme.NativeThemeOffscreenHost
import com.ai.assistance.operit.ui.theme.themePackageRuntimeForAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemorySearchBarAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchFieldPreservesQueryAndImeSearchCallback() {
        var query by mutableStateOf("")
        var searchCount = 0

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = GlobalPresentationSnapshot.default(),
                packageRuntime = themePackageRuntimeForAndroidTest(),
            ) {
                MemorySearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { searchCount += 1 },
                    onSettingsClick = {},
                    onMenuClick = {},
                )
            }
        }

        composeTestRule
            .onNode(hasSetTextAction())
            .performTextReplacement("graph query")
            .performImeAction()
        composeTestRule.runOnIdle {
            assertEquals("graph query", query)
            assertEquals(1, searchCount)
        }
    }
}
