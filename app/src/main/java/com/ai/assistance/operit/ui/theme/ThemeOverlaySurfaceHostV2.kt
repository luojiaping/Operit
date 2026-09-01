package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
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
    ThemeComponentSurfaceV2(
        skin = surface.overlaySkin(),
        modifier = modifier,
        applyContentPadding = applyContentPadding,
        content = content,
    )
}

/** Paints a Material popup with the menu skin while preserving its popup and dismissal behavior. */
@Composable
internal fun ThemeDropdownMenuV2(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = ThemeSurfaceCatalogV2.OVERLAY_MENU.overlaySkin()
    val shape = remember(skin.frame) { skin.frame.toComposeShape() }
    val framedModifier =
        modifier.drawWithCache {
            val framePlan = skin.frame.createRenderPlan(size, this)
            onDrawWithContent {
                drawContent()
                drawThemeComponentFrame(framePlan)
            }
        }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = framedModifier,
        offset = offset,
        properties = properties,
        shape = shape,
        containerColor = skin.container,
        tonalElevation = 0.dp,
        shadowElevation = skin.elevationDp.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides skin.content) {
            Column(
                modifier =
                    Modifier.padding(
                        start = skin.paddingStartDp.dp,
                        top = skin.paddingTopDp.dp,
                        end = skin.paddingEndDp.dp,
                        bottom = skin.paddingBottomDp.dp,
                    ),
                content = content,
            )
        }
    }
}

@Composable
private fun ThemeSurfaceIdV2.overlaySkin(): ResolvedThemeComponentSkinV2 {
    val runtime = LocalThemePackageUiRuntimeV2.current
    val implementation = runtime.surfaceImplementationFor(this)
    check(implementation.kind == ThemeSurfaceImplementationKindV2.TEMPLATE) {
        "Overlay surface $value must use a template implementation."
    }
    return runtime.componentSkin(overlayComponent())
}

internal fun ThemeSurfaceIdV2.overlayComponent(): ThemeComponentIdV2 =
    when (this) {
        ThemeSurfaceCatalogV2.OVERLAY_DIALOG -> ThemeComponentCatalogV2.DIALOG
        ThemeSurfaceCatalogV2.OVERLAY_SHEET -> ThemeComponentCatalogV2.SHEET
        ThemeSurfaceCatalogV2.OVERLAY_MENU -> ThemeComponentCatalogV2.MENU
        ThemeSurfaceCatalogV2.OVERLAY_SNACKBAR -> ThemeComponentCatalogV2.SNACKBAR
        ThemeSurfaceCatalogV2.OVERLAY_TOAST -> ThemeComponentCatalogV2.SNACKBAR
        else -> error("Theme surface $value is not supported by the overlay primitive host.")
    }
