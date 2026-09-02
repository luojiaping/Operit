package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemePresentationTargetV2

/** Applies the active package's Composer behavior without introducing a separate settings state. */
@Composable
internal fun ThemeComposerSurfaceV2(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val runtime = LocalThemePackageUiRuntimeV2.current
    val skin = runtime.componentSkin(ThemeComponentCatalogV2.COMPOSER)
    val floating = requireNotNull(runtime.booleanPresentation(ThemePresentationTargetV2.COMPOSER_FLOATING))
    val decoratedModifier =
        modifier
            .then(if (floating) Modifier.padding(horizontal = 12.dp, vertical = 8.dp) else Modifier)
            .liquidGlass(
                enabled = requireNotNull(runtime.booleanPresentation(ThemePresentationTargetV2.COMPOSER_LIQUID_GLASS)),
                shape = skin.frame.glassShape(),
                containerColor = skin.container,
            ).waterGlass(
                enabled = requireNotNull(runtime.booleanPresentation(ThemePresentationTargetV2.COMPOSER_WATER_GLASS)),
                shape = skin.frame.toComposeShape(),
                containerColor = skin.container,
            )
    ThemeComponentSurfaceV2(skin = skin, modifier = decoratedModifier, content = content)
}
