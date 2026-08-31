package com.ai.assistance.operit.data.theme.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemeSurfaceHostPolicyV2Test {
    @Test
    fun policyClassifiesEveryRequiredSurface() {
        ThemeSurfaceCatalogV2.requiredDailySurfaces.forEach { surface ->
            ThemeSurfaceHostPolicyV2.expectedKind(surface)
        }
    }

    @Test
    fun sceneAndHostShellSurfacesUseDedicatedKinds() {
        assertEquals(
            ThemeSurfaceImplementationKindV2.SCENE,
            ThemeSurfaceHostPolicyV2.expectedKind(ThemeSurfaceCatalogV2.APP_SHELL),
        )
        assertEquals(
            ThemeSurfaceImplementationKindV2.SCENE,
            ThemeSurfaceHostPolicyV2.expectedKind(ThemeSurfaceCatalogV2.CHAT_MAIN),
        )
        assertEquals(
            ThemeSurfaceImplementationKindV2.HOST_SHELL,
            ThemeSurfaceHostPolicyV2.expectedKind(ThemeSurfaceCatalogV2.PLUGIN_HOST_SHELL),
        )
        assertEquals(
            ThemeSurfaceImplementationKindV2.TEMPLATE,
            ThemeSurfaceHostPolicyV2.expectedKind(ThemeSurfaceCatalogV2.SETTINGS_INDEX),
        )
    }

    @Test
    fun policyRejectsAnUnsupportedSurfaceKind() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemeSurfaceHostPolicyV2.requireExpectedKind(
                surface = ThemeSurfaceCatalogV2.SETTINGS_INDEX,
                actualKind = ThemeSurfaceImplementationKindV2.SCENE,
            )
        }
    }
}
