package com.ai.assistance.operit.ui.theme

import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceImplementationKindV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemeSurfaceHostV2Test {
    @Test
    fun templatesAndExternalShellsUseDifferentV2ComponentHosts() {
        assertEquals(
            ThemeComponentCatalogV2.PAGE,
            ThemeSurfaceImplementationKindV2.TEMPLATE.hostComponent(),
        )
        assertEquals(
            ThemeComponentCatalogV2.SECTION,
            ThemeSurfaceImplementationKindV2.HOST_SHELL.hostComponent(),
        )
    }

    @Test
    fun sceneCannotUseTheGenericSurfaceHost() {
        assertThrows(IllegalStateException::class.java) {
            ThemeSurfaceImplementationKindV2.SCENE.hostComponent()
        }
    }
}
