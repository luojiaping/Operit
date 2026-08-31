package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceIdV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceImplementationKindV2

/**
 * Hosts one non-scene daily surface. Package implementations decide whether this is a native page
 * template or an external-content shell; business content, semantics, and interactions remain
 * owned by the caller.
 */
@Composable
internal fun ThemeSurfaceHostV2(
    surface: ThemeSurfaceIdV2,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val implementation = LocalThemePackageUiRuntimeV2.current.surfaceImplementationFor(surface)
    val component = implementation.kind.hostComponent()

    ThemeComponentSurfaceV2(
        component = component,
        modifier = modifier.fillMaxSize(),
        applyContentPadding = false,
        clipContent = false,
        content = content,
    )
}

internal fun ThemeSurfaceImplementationKindV2.hostComponent(): ThemeComponentIdV2 =
    when (this) {
        ThemeSurfaceImplementationKindV2.TEMPLATE -> ThemeComponentCatalogV2.PAGE
        ThemeSurfaceImplementationKindV2.HOST_SHELL -> ThemeComponentCatalogV2.SECTION
        ThemeSurfaceImplementationKindV2.SCENE ->
            error("Scene surfaces must use their registered scene host.")
    }
