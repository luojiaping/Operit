package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2
import com.ai.assistance.operit.data.theme.packages.ThemePresentationTargetV2
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageBackgroundSurface
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageRenderMode
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageStyleConfig

/** Uses only active-package presentation values to decorate Bubble message component skins. */
@Composable
internal fun ThemeBubbleMessageSurfaceV2(
    component: ThemeComponentIdV2,
    skin: ResolvedThemeComponentSkinV2,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val runtime = LocalThemePackageUiRuntimeV2.current
    val targets = component.bubbleTargets()
    val imageUri = runtime.imageUriPresentation(targets.imageUri)
    val layout = runtime.imageLayoutPresentation(targets.imageLayout)
    val fontFamily = runtime.bubbleFontFamily(component)
    val shape = skin.frame.toComposeShape()
    val decoratedModifier =
        modifier
            .liquidGlass(
                enabled = requireNotNull(runtime.booleanPresentation(targets.liquidGlass)),
                shape = skin.frame.glassShape(),
                containerColor = skin.container,
            ).waterGlass(
                enabled = requireNotNull(runtime.booleanPresentation(targets.waterGlass)),
                shape = shape,
                containerColor = skin.container,
            )
    if (imageUri == null) {
        ThemeBubbleTextPresentationV2(fontFamily) {
            ThemeComponentSurfaceV2(skin = skin, modifier = decoratedModifier, content = content)
        }
        return
    }
    BubbleImageBackgroundSurface(
        imageStyle =
            BubbleImageStyleConfig(
                imageUri = imageUri,
                cropLeftRatio = layout?.cropLeft ?: 0f,
                cropTopRatio = layout?.cropTop ?: 0f,
                cropRightRatio = 1f - (layout?.cropRight ?: 1f),
                cropBottomRatio = 1f - (layout?.cropBottom ?: 1f),
                repeatXStartRatio = layout?.repeatStart ?: 0f,
                repeatXEndRatio = layout?.repeatEnd ?: 1f,
                repeatYStartRatio = layout?.repeatYStart ?: 0f,
                repeatYEndRatio = layout?.repeatYEnd ?: 1f,
                imageScale = layout?.scale ?: 1f,
                renderMode =
                    when (requireNotNull(runtime.optionPresentation(ThemePresentationTargetV2.BUBBLE_IMAGE_RENDER_MODE))) {
                        "tiled_nine_slice" -> BubbleImageRenderMode.TILED_NINE_SLICE
                        "nine_patch" -> BubbleImageRenderMode.NINE_PATCH
                        else -> error("Theme bubble image render mode is invalid.")
                    },
            ),
        shape = shape,
        modifier = decoratedModifier,
        contentPadding = PaddingValues(0.dp),
    ) {
        ThemeBubbleTextPresentationV2(fontFamily) {
            ThemeComponentSurfaceV2(
                skin = skin.copy(container = Color.Transparent),
                modifier = Modifier,
                content = content,
            )
        }
    }
}

@Composable
private fun ThemeBubbleTextPresentationV2(
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    content: @Composable () -> Unit,
) {
    if (fontFamily == null) {
        content()
    } else {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily)) {
            content()
        }
    }
}

private data class ThemeBubblePresentationTargetsV2(
    val imageUri: ThemePresentationTargetV2,
    val imageLayout: ThemePresentationTargetV2,
    val liquidGlass: ThemePresentationTargetV2,
    val waterGlass: ThemePresentationTargetV2,
)

private fun ThemeComponentIdV2.bubbleTargets(): ThemeBubblePresentationTargetsV2 =
    when (this) {
        ThemeComponentCatalogV2.MESSAGE_USER ->
            ThemeBubblePresentationTargetsV2(
                imageUri = ThemePresentationTargetV2.BUBBLE_USER_IMAGE_URI,
                imageLayout = ThemePresentationTargetV2.BUBBLE_USER_IMAGE_LAYOUT,
                liquidGlass = ThemePresentationTargetV2.BUBBLE_USER_LIQUID_GLASS,
                waterGlass = ThemePresentationTargetV2.BUBBLE_USER_WATER_GLASS,
            )

        ThemeComponentCatalogV2.MESSAGE_ASSISTANT ->
            ThemeBubblePresentationTargetsV2(
                imageUri = ThemePresentationTargetV2.BUBBLE_ASSISTANT_IMAGE_URI,
                imageLayout = ThemePresentationTargetV2.BUBBLE_ASSISTANT_IMAGE_LAYOUT,
                liquidGlass = ThemePresentationTargetV2.BUBBLE_ASSISTANT_LIQUID_GLASS,
                waterGlass = ThemePresentationTargetV2.BUBBLE_ASSISTANT_WATER_GLASS,
            )

        else -> error("Theme Bubble presentation requires a message component.")
    }

internal fun ResolvedThemeComponentFrameV2.glassShape(): CornerBasedShape =
    when (this) {
        is ResolvedThemeComponentFrameV2.RoundRect -> RoundedCornerShape(cornerRadiusDp.dp)
        else -> RoundedCornerShape(0.dp)
    }
