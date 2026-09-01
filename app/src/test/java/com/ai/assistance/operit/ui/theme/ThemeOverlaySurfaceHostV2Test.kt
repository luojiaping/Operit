package com.ai.assistance.operit.ui.theme

import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceCatalogV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemeOverlaySurfaceHostV2Test {
    @Test
    fun supportedOverlaySurfacesUseTheirMatchingComponentSkins() {
        assertEquals(
            ThemeComponentCatalogV2.DIALOG,
            ThemeSurfaceCatalogV2.OVERLAY_DIALOG.overlayComponent(),
        )
        assertEquals(
            ThemeComponentCatalogV2.SHEET,
            ThemeSurfaceCatalogV2.OVERLAY_SHEET.overlayComponent(),
        )
        assertEquals(
            ThemeComponentCatalogV2.MENU,
            ThemeSurfaceCatalogV2.OVERLAY_MENU.overlayComponent(),
        )
        assertEquals(
            ThemeComponentCatalogV2.SNACKBAR,
            ThemeSurfaceCatalogV2.OVERLAY_SNACKBAR.overlayComponent(),
        )
        assertEquals(
            ThemeComponentCatalogV2.SNACKBAR,
            ThemeSurfaceCatalogV2.OVERLAY_TOAST.overlayComponent(),
        )
    }

    @Test
    fun nonOverlaySurfaceCannotUseOverlayHost() {
        assertThrows(IllegalStateException::class.java) {
            ThemeSurfaceCatalogV2.STATE_ERROR.overlayComponent()
        }
    }
}
