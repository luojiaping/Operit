package com.ai.assistance.operit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeThemeDetachedHostAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun detachedHostProvidesPresentationResolvedThemeAndScaledTypography() {
        val fontScale = 1.25f
        val presentation =
            GlobalPresentationSnapshot(
                themeMode = GlobalThemeMode.LIGHT,
                fontScale = fontScale,
            )
        val packageRuntime = themePackageRuntimeForAndroidTest(userFontScale = fontScale)
        var providedPresentation: GlobalPresentationSnapshot? = null
        var providedPackageRuntime: ThemePackageUiRuntimeV2? = null
        var providedBodyLargeSize = Typography().bodyLarge.fontSize

        composeTestRule.setContent {
            NativeThemeOffscreenHost(
                presentation = presentation,
                packageRuntime = packageRuntime,
            ) {
                val localPresentation = LocalGlobalPresentation.current
                val localPackageRuntime = LocalThemePackageUiRuntimeV2.current
                val materialBodyLargeSize = MaterialTheme.typography.bodyLarge.fontSize
                SideEffect {
                    providedPresentation = localPresentation
                    providedPackageRuntime = localPackageRuntime
                    providedBodyLargeSize = materialBodyLargeSize
                }
            }
        }

        composeTestRule.runOnIdle {
            assertSame(presentation, providedPresentation)
            assertSame(packageRuntime, providedPackageRuntime)
            assertEquals(packageRuntime.typography.bodyLarge.fontSize, providedBodyLargeSize)
        }
    }
}
