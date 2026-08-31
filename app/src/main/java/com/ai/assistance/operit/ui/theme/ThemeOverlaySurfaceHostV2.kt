package com.ai.assistance.operit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceIdV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceImplementationKindV2

/**
 * Applies package-owned overlay paint without taking ownership of the caller's window, dismissal,
 * focus, gesture, or accessibility behavior.
 */
@Composable
internal fun ThemeOverlaySurfaceHostV2(
    surface: ThemeSurfaceIdV2,
    modifier: Modifier = Modifier,
    applyContentPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val implementation = LocalThemePackageUiRuntimeV2.current.surfaceImplementationFor(surface)
    check(implementation.kind == ThemeSurfaceImplementationKindV2.TEMPLATE) {
        "Overlay surface ${surface.value} must use a template implementation."
    }

    ThemeComponentSurfaceV2(
        component = surface.overlayComponent(),
        modifier = modifier,
        applyContentPadding = applyContentPadding,
        content = content,
    )
}

internal fun ThemeSurfaceIdV2.overlayComponent(): ThemeComponentIdV2 =
    when (this) {
        ThemeSurfaceCatalogV2.OVERLAY_DIALOG -> ThemeComponentCatalogV2.DIALOG
        ThemeSurfaceCatalogV2.OVERLAY_SHEET -> ThemeComponentCatalogV2.SHEET
        ThemeSurfaceCatalogV2.OVERLAY_TOAST -> ThemeComponentCatalogV2.SNACKBAR
        else -> error("Theme surface $value is not supported by the overlay primitive host.")
    }
