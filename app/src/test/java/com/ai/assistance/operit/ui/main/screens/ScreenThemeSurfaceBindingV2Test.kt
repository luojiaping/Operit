package com.ai.assistance.operit.ui.main.screens

import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceCatalogV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenThemeSurfaceBindingV2Test {
    @Test
    fun everyNativeRouteHasOneRegisteredDailySurface() {
        val bindings = ScreenRouteRegistry.nativeRouteSurfaceBindings()

        assertTrue(bindings.isNotEmpty())
        assertTrue(bindings.values.all { surface -> surface in ThemeSurfaceCatalogV2.requiredDailySurfaces })
        assertEquals(ScreenRouteRegistry.nativeRouteCount(), bindings.size)
    }

    @Test
    fun representativeNativeRoutesUseTheirDedicatedThemeSurfaces() {
        assertEquals(
            ThemeSurfaceCatalogV2.CHAT_MAIN,
            ScreenRouteRegistry.themeSurfaceOf(Screen.AiChat),
        )
        assertEquals(
            ThemeSurfaceCatalogV2.SETTINGS_INDEX,
            ScreenRouteRegistry.themeSurfaceOf(Screen.Settings),
        )
        assertEquals(
            ThemeSurfaceCatalogV2.MARKET_HOME,
            ScreenRouteRegistry.themeSurfaceOf(Screen.Market()),
        )
        assertEquals(
            ThemeSurfaceCatalogV2.PLUGIN_HOST_SHELL,
            ScreenRouteRegistry.themeSurfaceOf(
                Screen.ToolPkgComposeDsl(
                    containerPackageName = "test.package",
                    uiModuleId = "main",
                    title = "Test",
                ),
            ),
        )
    }
}
