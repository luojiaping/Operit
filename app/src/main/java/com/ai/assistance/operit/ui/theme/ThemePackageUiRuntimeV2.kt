package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
import com.ai.assistance.operit.data.theme.packages.LinkedThemeRuntimeV2
import com.ai.assistance.operit.data.theme.packages.ResolvedThemeParametersV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentStateSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentFrameSpecV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentInsetsV2
import com.ai.assistance.operit.data.theme.packages.ThemeInstanceV2
import com.ai.assistance.operit.data.theme.packages.ThemeMaterialColorSchemeV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterEffectV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterValueV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageRuntimeLinkerV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageSelectionRepositoryV2
import com.ai.assistance.operit.data.theme.packages.ThemeRuntimeRepositoryV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceIdV2
import com.ai.assistance.operit.data.theme.packages.ThemeSystemFontFamilyV2
import com.ai.assistance.operit.data.theme.packages.ThemePresentationTargetV2
import com.ai.assistance.operit.data.theme.packages.ThemePackagePresentationBehaviorV2
import com.ai.assistance.operit.data.theme.packages.ThemeShapesV2
import com.ai.assistance.operit.data.theme.packages.ThemeTypographyV2
import com.ai.assistance.operit.data.theme.packages.accentTokenIds
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageFitV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenResolverV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1
import com.ai.assistance.operit.ui.theme.scene.render.ThemeSceneAssetRepositoryV1

internal enum class ThemeComponentStateV2 {
    NORMAL,
    DISABLED,
    SELECTED,
    FOCUSED,
    ERROR,
}

@Immutable
internal data class ResolvedThemeComponentSkinV2(
    val container: Color,
    val content: Color,
    val frame: ResolvedThemeComponentFrameV2,
    val elevationDp: Float,
    val paddingStartDp: Float,
    val paddingTopDp: Float,
    val paddingEndDp: Float,
    val paddingBottomDp: Float,
)

@Immutable
internal data class ResolvedThemeStageImageV2(
    val uri: String,
    val fit: ThemeSceneImageFitV1,
    val opacity: Float,
)

@Immutable
internal data class ResolvedThemeBackgroundMediaV2(
    val uri: String,
    val type: ThemeBackgroundMediaTypeV2,
    val opacity: Float,
    val blurEnabled: Boolean,
    val blurRadiusDp: Float,
    val videoMuted: Boolean,
    val videoLoop: Boolean,
)

internal enum class ThemeBackgroundMediaTypeV2 {
    IMAGE,
    VIDEO,
}

/** Runtime projection used by every Operit-owned Compose root. */
@Immutable
internal data class ThemePackageUiRuntimeV2(
    val linked: LinkedThemeRuntimeV2,
    val parameters: ResolvedThemeParametersV2,
    val darkTheme: Boolean,
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val tokens: ThemeSceneTokenResolverV1,
    val assets: ThemeSceneAssetRepositoryV1,
    val componentSkins: Map<com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2, ThemeComponentSkinV2> = linked.componentSkins,
    val stageImages: Map<ThemeSurfaceIdV2, ResolvedThemeStageImageV2> = emptyMap(),
    val presentationValues: Map<ThemePresentationTargetV2, ThemeParameterValueV2> = emptyMap(),
    val presentationFonts: Map<ThemePresentationTargetV2, FontFamily> = emptyMap(),
) {
    fun componentSkin(
        component: com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2,
        state: ThemeComponentStateV2 = ThemeComponentStateV2.NORMAL,
    ): ResolvedThemeComponentSkinV2 {
        val skin = requireNotNull(componentSkins[component]) {
            "Active theme has no skin for ${component.value}."
        }
        return resolveComponentState(skin, state).applyComponentPresentation(component, presentationValues)
    }

    fun bubbleMessageSkin(
        component: com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2,
        state: ThemeComponentStateV2 = ThemeComponentStateV2.NORMAL,
    ): ResolvedThemeComponentSkinV2 {
        require(
            component == com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.MESSAGE_USER ||
                component == com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.MESSAGE_ASSISTANT,
        ) { "Bubble message projection requires a message component." }
        val skin = componentSkin(component, state)
        val parameterizedSkin =
            when (component) {
            com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.MESSAGE_USER ->
                skin.withColors(
                    container = colorPresentation(ThemePresentationTargetV2.BUBBLE_USER_COLOR),
                    content = colorPresentation(ThemePresentationTargetV2.BUBBLE_USER_TEXT_COLOR),
                ).withInsets(insetsPresentation(ThemePresentationTargetV2.BUBBLE_USER_CONTENT_INSETS))

            com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.MESSAGE_ASSISTANT ->
                skin.withColors(
                    container = colorPresentation(ThemePresentationTargetV2.BUBBLE_ASSISTANT_COLOR),
                    content = colorPresentation(ThemePresentationTargetV2.BUBBLE_ASSISTANT_TEXT_COLOR),
                ).withInsets(insetsPresentation(ThemePresentationTargetV2.BUBBLE_ASSISTANT_CONTENT_INSETS))

            else -> error("Bubble message projection requires a message component.")
        }
        val roundedTarget =
            if (component == com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.MESSAGE_USER) {
                ThemePresentationTargetV2.BUBBLE_USER_ROUNDED_CORNERS
            } else {
                ThemePresentationTargetV2.BUBBLE_ASSISTANT_ROUNDED_CORNERS
            }
        return parameterizedSkin.withRoundedCorners(requireNotNull(booleanPresentation(roundedTarget)))
    }

    fun cursorUserMessageSkin(
        state: ThemeComponentStateV2 = ThemeComponentStateV2.NORMAL,
    ): ResolvedThemeComponentSkinV2 {
        val skin = componentSkin(com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.MESSAGE_USER, state)
        val followsTheme = requireNotNull(booleanPresentation(ThemePresentationTargetV2.CURSOR_USER_BUBBLE_FOLLOW_THEME))
        return if (followsTheme) skin else skin.withColors(
            container = colorPresentation(ThemePresentationTargetV2.CURSOR_USER_BUBBLE_COLOR),
        )
    }

    fun avatarShape(): Shape =
        when (optionPresentation(ThemePresentationTargetV2.AVATAR_SHAPE)) {
            null, "circle" -> CircleShape

            "rounded" ->
                RoundedCornerShape(
                    requireNotNull(cornerRadiusPresentation(ThemePresentationTargetV2.AVATAR_CORNER_RADIUS)).dp,
                )
            "square" -> RoundedCornerShape(0.dp)
            else -> error("Theme avatar shape must be circle, rounded, or square.")
        }

    fun bubbleFontFamily(
        component: com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2,
    ): FontFamily? =
        when (component) {
            com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.MESSAGE_USER ->
                resolveBubbleFontFamily(
                    enabledTarget = ThemePresentationTargetV2.BUBBLE_USER_USE_CUSTOM_FONT,
                    familyTarget = ThemePresentationTargetV2.BUBBLE_USER_FONT_FAMILY,
                    fontTarget = ThemePresentationTargetV2.BUBBLE_USER_FONT_URI,
                )

            com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.MESSAGE_ASSISTANT ->
                resolveBubbleFontFamily(
                    enabledTarget = ThemePresentationTargetV2.BUBBLE_ASSISTANT_USE_CUSTOM_FONT,
                    familyTarget = ThemePresentationTargetV2.BUBBLE_ASSISTANT_FONT_FAMILY,
                    fontTarget = ThemePresentationTargetV2.BUBBLE_ASSISTANT_FONT_URI,
                )

            else -> error("Theme Bubble font projection requires a message component.")
        }

    private fun resolveBubbleFontFamily(
        enabledTarget: ThemePresentationTargetV2,
        familyTarget: ThemePresentationTargetV2,
        fontTarget: ThemePresentationTargetV2,
    ): FontFamily? {
        if (!requireNotNull(booleanPresentation(enabledTarget))) return null
        return presentationFonts[fontTarget]
            ?: requireNotNull(optionPresentation(familyTarget)).toThemeSystemFontFamily().toComposeFontFamily()
    }

    private fun resolveComponentState(
        skin: ThemeComponentSkinV2,
        state: ThemeComponentStateV2,
    ): ResolvedThemeComponentSkinV2 {
        val selected =
            when (state) {
                ThemeComponentStateV2.NORMAL -> skin.normal
                ThemeComponentStateV2.DISABLED -> skin.disabled ?: skin.normal
                ThemeComponentStateV2.SELECTED -> skin.selected ?: skin.normal
                ThemeComponentStateV2.FOCUSED -> skin.focused ?: skin.normal
                ThemeComponentStateV2.ERROR -> skin.error ?: skin.normal
            }
        return selected.resolve(tokens, darkTheme)
    }

    fun stageImage(surface: ThemeSurfaceIdV2): ResolvedThemeStageImageV2? = stageImages[surface]

    fun presentationValue(target: ThemePresentationTargetV2): ThemeParameterValueV2? =
        presentationValues[target]

    fun booleanPresentation(target: ThemePresentationTargetV2): Boolean? =
        (presentationValues[target] as? ThemeParameterValueV2.BooleanValue)?.value

    fun colorPresentation(target: ThemePresentationTargetV2): Color? =
        (presentationValues[target] as? ThemeParameterValueV2.ColorValue)?.let { value -> Color(value.argb.toInt()) }

    fun insetsPresentation(target: ThemePresentationTargetV2): ThemeParameterValueV2.InsetsValue? =
        presentationValues[target] as? ThemeParameterValueV2.InsetsValue

    fun optionPresentation(target: ThemePresentationTargetV2): String? =
        (presentationValues[target] as? ThemeParameterValueV2.OptionValue)?.value

    fun floatPresentation(target: ThemePresentationTargetV2): Float? =
        (presentationValues[target] as? ThemeParameterValueV2.FloatValue)?.value

    fun imageUriPresentation(target: ThemePresentationTargetV2): String? =
        (presentationValues[target] as? ThemeParameterValueV2.ImageUriValue)?.uri

    fun videoUriPresentation(target: ThemePresentationTargetV2): String? =
        (presentationValues[target] as? ThemeParameterValueV2.VideoUriValue)?.uri

    fun fontUriPresentation(target: ThemePresentationTargetV2): String? =
        (presentationValues[target] as? ThemeParameterValueV2.FontUriValue)?.uri

    fun imageLayoutPresentation(target: ThemePresentationTargetV2): ThemeParameterValueV2.ImageLayoutValue? =
        presentationValues[target] as? ThemeParameterValueV2.ImageLayoutValue

    fun cornerRadiusPresentation(target: ThemePresentationTargetV2): Float? =
        (presentationValues[target] as? ThemeParameterValueV2.CornerRadiusValue)?.valueDp

    fun backgroundMedia(): ResolvedThemeBackgroundMediaV2? {
        if (booleanPresentation(ThemePresentationTargetV2.BACKGROUND_USE_IMAGE) != true) return null
        return when (optionPresentation(ThemePresentationTargetV2.BACKGROUND_MEDIA_TYPE)) {
            "image" -> {
                val uri = imageUriPresentation(ThemePresentationTargetV2.BACKGROUND_IMAGE_URI) ?: return null
                ResolvedThemeBackgroundMediaV2(
                    uri = uri,
                    type = ThemeBackgroundMediaTypeV2.IMAGE,
                    opacity = requireBackgroundOpacity(),
                    blurEnabled = booleanPresentation(ThemePresentationTargetV2.BACKGROUND_BLUR_ENABLED) == true,
                    blurRadiusDp = floatPresentation(ThemePresentationTargetV2.BACKGROUND_BLUR_RADIUS) ?: 0f,
                    videoMuted = true,
                    videoLoop = false,
                )
            }

            "video" -> {
                val uri = videoUriPresentation(ThemePresentationTargetV2.BACKGROUND_VIDEO_URI) ?: return null
                ResolvedThemeBackgroundMediaV2(
                    uri = uri,
                    type = ThemeBackgroundMediaTypeV2.VIDEO,
                    opacity = requireBackgroundOpacity(),
                    blurEnabled = false,
                    blurRadiusDp = 0f,
                    videoMuted = booleanPresentation(ThemePresentationTargetV2.BACKGROUND_VIDEO_MUTED) == true,
                    videoLoop = booleanPresentation(ThemePresentationTargetV2.BACKGROUND_VIDEO_LOOP) == true,
                )
            }

            else -> null
        }
    }

    private fun requireBackgroundOpacity(): Float =
        requireNotNull(floatPresentation(ThemePresentationTargetV2.BACKGROUND_OPACITY)) {
            "Theme background media requires BACKGROUND_OPACITY."
        }.coerceIn(0f, 1f)

}

private fun ResolvedThemeComponentSkinV2.applyComponentPresentation(
    component: com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2,
    values: Map<ThemePresentationTargetV2, ThemeParameterValueV2>,
): ResolvedThemeComponentSkinV2 =
    when (component) {
        com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.COMPOSER ->
            if ((values[ThemePresentationTargetV2.COMPOSER_TRANSPARENT] as? ThemeParameterValueV2.BooleanValue)?.value == true) {
                copy(container = Color.Transparent)
            } else {
                this
            }

        com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.APP_BAR ->
            withColors(
                container =
                    if (
                        (values[ThemePresentationTargetV2.CHROME_TOOLBAR_TRANSPARENT]
                            as? ThemeParameterValueV2.BooleanValue)
                            ?.value == true
                    ) {
                        Color.Transparent
                    } else {
                        (values[ThemePresentationTargetV2.CHROME_TOOLBAR_COLOR] as? ThemeParameterValueV2.ColorValue)
                            ?.let { value -> Color(value.argb.toInt()) }
                    },
            )

        com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2.NAVIGATION ->
            withColors(
                container =
                    (values[ThemePresentationTargetV2.CHROME_NAVIGATION_BACKGROUND_COLOR]
                        as? ThemeParameterValueV2.ColorValue)
                        ?.let { value -> Color(value.argb.toInt()) },
            ).withFrameAccent(
                (values[ThemePresentationTargetV2.CHROME_NAVIGATION_ACCENT_COLOR]
                    as? ThemeParameterValueV2.ColorValue)
                    ?.let { value -> Color(value.argb.toInt()) },
            )

        else -> this
    }

private fun ResolvedThemeComponentSkinV2.withColors(
    container: Color? = null,
    content: Color? = null,
): ResolvedThemeComponentSkinV2 =
    copy(
        container = container ?: this.container,
        content = content ?: this.content,
    )

private fun ResolvedThemeComponentSkinV2.withInsets(
    insets: ThemeParameterValueV2.InsetsValue?,
): ResolvedThemeComponentSkinV2 =
    if (insets == null) {
        this
    } else {
        copy(
            paddingStartDp = insets.startDp,
            paddingTopDp = insets.topDp,
            paddingEndDp = insets.endDp,
            paddingBottomDp = insets.bottomDp,
        )
    }

private fun ResolvedThemeComponentSkinV2.withRoundedCorners(
    enabled: Boolean,
): ResolvedThemeComponentSkinV2 =
    if (enabled) {
        this
    } else {
        copy(frame = ResolvedThemeComponentFrameV2.RoundRect(cornerRadiusDp = 0f, border = null))
    }

private fun ResolvedThemeComponentSkinV2.withFrameAccent(
    color: Color?,
): ResolvedThemeComponentSkinV2 =
    if (color == null) {
        this
    } else {
        copy(frame = frame.withAccent(color))
    }

private fun ResolvedThemeComponentFrameV2.withAccent(color: Color): ResolvedThemeComponentFrameV2 =
    when (this) {
        ResolvedThemeComponentFrameV2.None -> this
        is ResolvedThemeComponentFrameV2.RoundRect -> this
        is ResolvedThemeComponentFrameV2.CutCorners ->
            copy(
                accent =
                    ResolvedThemeComponentFrameStrokeV2(
                        color = color,
                        widthDp = accent?.widthDp ?: border.widthDp,
                    ),
            )

        is ResolvedThemeComponentFrameV2.HudNotched ->
            copy(
                accent =
                    ResolvedThemeComponentFrameStrokeV2(
                        color = color,
                        widthDp = accent?.widthDp ?: border.widthDp,
                    ),
            )

        is ResolvedThemeComponentFrameV2.CornerBrackets ->
            copy(
                accent =
                    ResolvedThemeComponentFrameStrokeV2(
                        color = color,
                        widthDp = accent?.widthDp ?: border.widthDp,
                    ),
            )

        is ResolvedThemeComponentFrameV2.SegmentedRail ->
            copy(accent = accent.copy(color = color))
    }

/**
 * 单一解析入口：主界面、悬浮窗、overlay 与离屏导出共用同一套链接结果，
 * 消除“主界面用主题包、其余表面用动态配色”的分叉。
 */
@Composable
internal fun rememberActiveThemePackageRuntimeV2(): ThemePackageUiRuntimeV2 {
    val context = LocalContext.current
    val presentation = rememberGlobalPresentation()
    val instance by remember(context) {
        ThemePackageSelectionRepositoryV2.getInstance(context).selectionFlow
    }.collectAsState(initial = ThemeInstanceV2.defaultBundled())
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        presentation.themeMode == GlobalThemeMode.DARK ||
            (presentation.themeMode == GlobalThemeMode.SYSTEM && systemDarkTheme)
    val linked = remember(instance.reference.coordinate) {
        ThemeRuntimeRepositoryV2.require(instance.reference.coordinate)
    }
    val parameters = remember(instance, linked) {
        ThemePackageRuntimeLinkerV2.resolveParameters(instance, linked)
    }
    val baseRuntime = remember(linked, parameters, darkTheme, presentation.fontScale) {
        createThemePackageUiRuntimeV2(
            linked = linked,
            parameters = parameters,
            darkTheme = darkTheme,
            userFontScale = presentation.fontScale,
        )
    }
    val presentationFonts by produceState<Map<ThemePresentationTargetV2, FontFamily>>(emptyMap(), context, baseRuntime) {
        value = resolveThemePackageFontFamiliesV2(context, baseRuntime)
    }
    return remember(linked, parameters, darkTheme, presentation.fontScale, presentationFonts) {
        createThemePackageUiRuntimeV2(
            linked = linked,
            parameters = parameters,
            darkTheme = darkTheme,
            userFontScale = presentation.fontScale,
            presentationFonts = presentationFonts,
        )
    }
}

internal fun createThemePackageUiRuntimeV2(
    linked: LinkedThemeRuntimeV2,
    parameters: ResolvedThemeParametersV2,
    darkTheme: Boolean,
    userFontScale: Float,
    presentationFonts: Map<ThemePresentationTargetV2, FontFamily> = emptyMap(),
): ThemePackageUiRuntimeV2 {
    val parameterizedPresentation = resolveThemeParameterPresentationV2(linked, parameters)
    val tokens = ThemeSceneTokenResolverV1(parameterizedPresentation.tokens)
    val material = linked.material
    return ThemePackageUiRuntimeV2(
        linked = linked,
        parameters = parameters,
        darkTheme = darkTheme,
        colorScheme = material.colors.toColorScheme(tokens, darkTheme),
        typography =
            parameterizedPresentation.typography.toTypography(
                userFontScale,
                presentationFonts[ThemePresentationTargetV2.TYPOGRAPHY_FONT_URI],
            ),
        shapes =
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(parameterizedPresentation.shapes.extraSmallDp.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(parameterizedPresentation.shapes.smallDp.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(parameterizedPresentation.shapes.mediumDp.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(parameterizedPresentation.shapes.largeDp.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(parameterizedPresentation.shapes.extraLargeDp.dp),
            ),
        tokens = tokens,
        assets = ThemeSceneAssetRepositoryV1(linked.assets),
        componentSkins = parameterizedPresentation.componentSkins,
        stageImages = parameterizedPresentation.stageImages,
        presentationValues = parameterizedPresentation.values,
        presentationFonts = presentationFonts,
    )
}

private data class ThemeParameterPresentationV2(
    val tokens: ThemeSceneTokenSetV1,
    val typography: ThemeTypographyV2,
    val shapes: ThemeShapesV2,
    val componentSkins: Map<com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2, ThemeComponentSkinV2>,
    val stageImages: Map<ThemeSurfaceIdV2, ResolvedThemeStageImageV2>,
    val values: Map<ThemePresentationTargetV2, ThemeParameterValueV2>,
)

/** Applies user-owned values to a runtime copy, never to a linked package or installed archive. */
private fun resolveThemeParameterPresentationV2(
    linked: LinkedThemeRuntimeV2,
    parameters: ResolvedThemeParametersV2,
): ThemeParameterPresentationV2 {
    val tokens = linked.tokens.tokens.toMutableMap()
    val stageImages = mutableMapOf<ThemeSurfaceIdV2, ResolvedThemeStageImageV2>()
    val presentationValues = linked.behavior.parameterValues().toMutableMap()
    var typography: ThemeTypographyV2
    var shapes = linked.material.shapes
    var componentSkins = linked.componentSkins

    val activeParameters =
        linked.parameterDefinitions.mapNotNull { (parameterId, definition) ->
            if (linked.parameterOwners[parameterId] != linked.coordinate) {
                null
            } else {
                parameters.values[parameterId]?.let { value -> parameterId to (definition to value) }
            }
        }

    activeParameters.forEach { (_, pair) ->
        val (definition, value) = pair
        definition.effects.filterIsInstance<ThemeParameterEffectV2.Presentation>().forEach { effect ->
            effect.targets.forEach { target -> presentationValues[target] = value }
        }
    }
    typography = linked.material.typography.withPresentation(presentationValues)

    activeParameters.forEach { (parameterId, pair) ->
        val (definition, value) = pair
        definition.effects.forEach { effect ->
            if (effect is ThemeParameterEffectV2.Presentation) return@forEach
            when (effect) {
                ThemeParameterEffectV2.AccentPalette -> {
                    val color = value.requireColor(parameterId, "accent palette")
                    require(color.argb ushr 24 == 0xFFL) {
                        "Theme accent palette parameter $parameterId must be opaque."
                    }
                    tokens.putAll(linked.material.colors.accentPaletteTokens(color.argb))
                }

                is ThemeParameterEffectV2.TokenColor -> {
                    val color = value.requireColor(parameterId, "token color")
                    effect.tokenIds.forEach { tokenId ->
                        tokens[tokenId] =
                            ThemeSceneTokenValueV1.ColorToken(
                                lightArgb = color.argb,
                                darkArgb = color.argb,
                            )
                    }
                }

                is ThemeParameterEffectV2.TokenColorPair -> {
                    val color = value.requireColorPair(parameterId, "token color pair")
                    effect.tokenIds.forEach { tokenId ->
                        tokens[tokenId] =
                            ThemeSceneTokenValueV1.ColorToken(
                                lightArgb = color.lightArgb,
                                darkArgb = color.darkArgb,
                            )
                    }
                }

                is ThemeParameterEffectV2.StageImage -> {
                    val uri = value.requireImageUri(parameterId)
                    val opacity =
                        (presentationValues[ThemePresentationTargetV2.BACKGROUND_OPACITY]
                            as? ThemeParameterValueV2.FloatValue)
                            ?.value
                            ?.coerceIn(0f, 1f)
                            ?: effect.opacity
                    effect.surfaceIds.forEach { surfaceId ->
                        val surface = ThemeSurfaceIdV2(surfaceId)
                        stageImages[surface] =
                            ResolvedThemeStageImageV2(
                                uri = uri,
                                fit = effect.fit,
                                opacity = opacity,
                            )
                    }
                }

                ThemeParameterEffectV2.TypographyScale -> {
                    val scale = value.requireFloat(parameterId, "typography scale")
                    typography = typography.scaledBy(scale)
                    val existingScale =
                        (presentationValues[ThemePresentationTargetV2.TYPOGRAPHY_SCALE]
                            as? ThemeParameterValueV2.FloatValue)
                            ?.value
                            ?: 1f
                    presentationValues[ThemePresentationTargetV2.TYPOGRAPHY_SCALE] =
                        ThemeParameterValueV2.FloatValue((existingScale * scale).coerceIn(0.5f, 2f))
                }

                ThemeParameterEffectV2.ShapeScale -> {
                    val scale = value.requireFloat(parameterId, "shape scale")
                    shapes = shapes.scaledBy(scale)
                }

                is ThemeParameterEffectV2.ComponentFrameScale -> {
                    val scale = value.requireFloat(parameterId, "component frame scale")
                    effect.componentIds.forEach { componentId ->
                        val key = com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2(componentId)
                        val skin = requireNotNull(componentSkins[key]) {
                            "Theme component frame parameter $parameterId targets missing $componentId."
                        }
                        componentSkins = componentSkins + (key to skin.scaleFrameGeometry(scale))
                    }
                }

                is ThemeParameterEffectV2.ComponentContentInsets -> {
                    val insets = value.requireInsets(parameterId, "component content insets")
                    effect.componentIds.forEach { componentId ->
                        val key = com.ai.assistance.operit.data.theme.packages.ThemeComponentIdV2(componentId)
                        val skin = requireNotNull(componentSkins[key]) {
                            "Theme component inset parameter $parameterId targets missing $componentId."
                        }
                        componentSkins = componentSkins + (key to skin.withContentInsets(insets))
                    }
                }

                is ThemeParameterEffectV2.Presentation -> Unit
            }
        }
    }

    return ThemeParameterPresentationV2(
        tokens = ThemeSceneTokenSetV1(tokens),
        typography = typography,
        shapes = shapes,
        componentSkins = componentSkins,
        stageImages = stageImages,
        values = presentationValues,
    )
}

private fun ThemePackagePresentationBehaviorV2.parameterValues(): Map<ThemePresentationTargetV2, ThemeParameterValueV2> =
    buildMap {
        put(ThemePresentationTargetV2.TYPOGRAPHY_USE_CUSTOM_FONT, ThemeParameterValueV2.BooleanValue(typography.useCustomFont))
        put(ThemePresentationTargetV2.TYPOGRAPHY_FAMILY, ThemeParameterValueV2.OptionValue(typography.family.name.lowercase()))
        put(ThemePresentationTargetV2.TYPOGRAPHY_SCALE, ThemeParameterValueV2.FloatValue(typography.scale))
        put(ThemePresentationTargetV2.BACKGROUND_USE_IMAGE, ThemeParameterValueV2.BooleanValue(background.enabled))
        put(ThemePresentationTargetV2.BACKGROUND_MEDIA_TYPE, ThemeParameterValueV2.OptionValue(background.mediaType.name.lowercase()))
        put(ThemePresentationTargetV2.BACKGROUND_OPACITY, ThemeParameterValueV2.FloatValue(background.opacity))
        put(ThemePresentationTargetV2.BACKGROUND_BLUR_ENABLED, ThemeParameterValueV2.BooleanValue(background.blurEnabled))
        put(ThemePresentationTargetV2.BACKGROUND_BLUR_RADIUS, ThemeParameterValueV2.FloatValue(background.blurRadiusDp))
        put(ThemePresentationTargetV2.BACKGROUND_VIDEO_MUTED, ThemeParameterValueV2.BooleanValue(background.videoMuted))
        put(ThemePresentationTargetV2.BACKGROUND_VIDEO_LOOP, ThemeParameterValueV2.BooleanValue(background.videoLoop))
        put(ThemePresentationTargetV2.CURSOR_USER_BUBBLE_FOLLOW_THEME, ThemeParameterValueV2.BooleanValue(conversation.cursorUserBubbleFollowTheme))
        put(ThemePresentationTargetV2.CURSOR_USER_BUBBLE_LIQUID_GLASS, ThemeParameterValueV2.BooleanValue(conversation.cursorUserBubbleLiquidGlass))
        put(ThemePresentationTargetV2.CURSOR_USER_BUBBLE_WATER_GLASS, ThemeParameterValueV2.BooleanValue(conversation.cursorUserBubbleWaterGlass))
        conversation.cursorUserBubbleColorArgb?.let { color ->
            put(ThemePresentationTargetV2.CURSOR_USER_BUBBLE_COLOR, ThemeParameterValueV2.ColorValue(color))
        }
        put(ThemePresentationTargetV2.BUBBLE_SHOW_AVATAR, ThemeParameterValueV2.BooleanValue(conversation.bubbleShowAvatar))
        put(ThemePresentationTargetV2.BUBBLE_WIDE_LAYOUT, ThemeParameterValueV2.BooleanValue(conversation.bubbleWideLayout))
        put(ThemePresentationTargetV2.BUBBLE_USER_LIQUID_GLASS, ThemeParameterValueV2.BooleanValue(conversation.bubbleUserLiquidGlass))
        put(ThemePresentationTargetV2.BUBBLE_USER_WATER_GLASS, ThemeParameterValueV2.BooleanValue(conversation.bubbleUserWaterGlass))
        put(ThemePresentationTargetV2.BUBBLE_ASSISTANT_LIQUID_GLASS, ThemeParameterValueV2.BooleanValue(conversation.bubbleAssistantLiquidGlass))
        put(ThemePresentationTargetV2.BUBBLE_ASSISTANT_WATER_GLASS, ThemeParameterValueV2.BooleanValue(conversation.bubbleAssistantWaterGlass))
        put(ThemePresentationTargetV2.BUBBLE_IMAGE_RENDER_MODE, ThemeParameterValueV2.OptionValue(conversation.bubbleImageRenderMode.name.lowercase()))
        put(ThemePresentationTargetV2.BUBBLE_USER_ROUNDED_CORNERS, ThemeParameterValueV2.BooleanValue(conversation.bubbleUserRoundedCorners))
        put(ThemePresentationTargetV2.BUBBLE_ASSISTANT_ROUNDED_CORNERS, ThemeParameterValueV2.BooleanValue(conversation.bubbleAssistantRoundedCorners))
        conversation.bubbleUserColorArgb?.let { color -> put(ThemePresentationTargetV2.BUBBLE_USER_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        conversation.bubbleAssistantColorArgb?.let { color -> put(ThemePresentationTargetV2.BUBBLE_ASSISTANT_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        conversation.bubbleUserTextColorArgb?.let { color -> put(ThemePresentationTargetV2.BUBBLE_USER_TEXT_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        conversation.bubbleAssistantTextColorArgb?.let { color -> put(ThemePresentationTargetV2.BUBBLE_ASSISTANT_TEXT_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        put(ThemePresentationTargetV2.BUBBLE_USER_USE_CUSTOM_FONT, ThemeParameterValueV2.BooleanValue(conversation.bubbleUserUseCustomFont))
        put(ThemePresentationTargetV2.BUBBLE_USER_FONT_FAMILY, ThemeParameterValueV2.OptionValue(conversation.bubbleUserFontFamily.name.lowercase()))
        put(ThemePresentationTargetV2.BUBBLE_ASSISTANT_USE_CUSTOM_FONT, ThemeParameterValueV2.BooleanValue(conversation.bubbleAssistantUseCustomFont))
        put(ThemePresentationTargetV2.BUBBLE_ASSISTANT_FONT_FAMILY, ThemeParameterValueV2.OptionValue(conversation.bubbleAssistantFontFamily.name.lowercase()))
        put(ThemePresentationTargetV2.AVATAR_SHAPE, ThemeParameterValueV2.OptionValue(conversation.avatarShape.name.lowercase()))
        put(ThemePresentationTargetV2.AVATAR_CORNER_RADIUS, ThemeParameterValueV2.CornerRadiusValue(conversation.avatarCornerRadiusDp))
        put(ThemePresentationTargetV2.COMPOSER_TRANSPARENT, ThemeParameterValueV2.BooleanValue(composer.transparent))
        put(ThemePresentationTargetV2.COMPOSER_FLOATING, ThemeParameterValueV2.BooleanValue(composer.floating))
        put(ThemePresentationTargetV2.COMPOSER_LIQUID_GLASS, ThemeParameterValueV2.BooleanValue(composer.liquidGlass))
        put(ThemePresentationTargetV2.COMPOSER_WATER_GLASS, ThemeParameterValueV2.BooleanValue(composer.waterGlass))
        put(ThemePresentationTargetV2.CHROME_STATUS_BAR_HIDDEN, ThemeParameterValueV2.BooleanValue(chrome.statusBarHidden))
        put(ThemePresentationTargetV2.CHROME_STATUS_BAR_TRANSPARENT, ThemeParameterValueV2.BooleanValue(chrome.statusBarTransparent))
        chrome.statusBarColorArgb?.let { color -> put(ThemePresentationTargetV2.CHROME_STATUS_BAR_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        put(ThemePresentationTargetV2.CHROME_TOOLBAR_TRANSPARENT, ThemeParameterValueV2.BooleanValue(chrome.toolbarTransparent))
        chrome.toolbarColorArgb?.let { color -> put(ThemePresentationTargetV2.CHROME_TOOLBAR_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        put(ThemePresentationTargetV2.CHROME_NAVIGATION_WATER_GLASS, ThemeParameterValueV2.BooleanValue(chrome.navigationWaterGlass))
        put(ThemePresentationTargetV2.CHROME_NAVIGATION_BUTTON_LIQUID_GLASS, ThemeParameterValueV2.BooleanValue(chrome.navigationButtonLiquidGlass))
        chrome.navigationBackgroundColorArgb?.let { color -> put(ThemePresentationTargetV2.CHROME_NAVIGATION_BACKGROUND_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        chrome.navigationAccentColorArgb?.let { color -> put(ThemePresentationTargetV2.CHROME_NAVIGATION_ACCENT_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        put(ThemePresentationTargetV2.CHROME_CHAT_HEADER_TRANSPARENT, ThemeParameterValueV2.BooleanValue(chrome.chatHeaderTransparent))
        put(ThemePresentationTargetV2.CHROME_CHAT_HEADER_OVERLAY_MODE, ThemeParameterValueV2.OptionValue(chrome.chatHeaderOverlayMode.name.lowercase()))
        put(ThemePresentationTargetV2.CHROME_APP_BAR_CONTENT_COLOR_MODE, ThemeParameterValueV2.OptionValue(chrome.appBarContentColorMode.name.lowercase()))
        chrome.chatHeaderHistoryIconColorArgb?.let { color -> put(ThemePresentationTargetV2.CHROME_CHAT_HEADER_HISTORY_ICON_COLOR, ThemeParameterValueV2.ColorValue(color)) }
        chrome.chatHeaderPipIconColorArgb?.let { color -> put(ThemePresentationTargetV2.CHROME_CHAT_HEADER_PIP_ICON_COLOR, ThemeParameterValueV2.ColorValue(color)) }
    }

private fun ThemeParameterValueV2.requireColor(
    parameterId: String,
    effect: String,
): ThemeParameterValueV2.ColorValue =
    this as? ThemeParameterValueV2.ColorValue
        ?: error("Theme parameter $parameterId must resolve to a color for $effect.")

private fun ThemeParameterValueV2.requireImageUri(parameterId: String): String =
    (this as? ThemeParameterValueV2.ImageUriValue)?.uri
        ?: error("Theme parameter $parameterId must resolve to an image URI.")

private fun ThemeParameterValueV2.requireColorPair(
    parameterId: String,
    effect: String,
): ThemeParameterValueV2.ColorPairValue =
    this as? ThemeParameterValueV2.ColorPairValue
        ?: error("Theme parameter $parameterId must resolve to a color pair for $effect.")

private fun ThemeParameterValueV2.requireFloat(
    parameterId: String,
    effect: String,
): Float =
    (this as? ThemeParameterValueV2.FloatValue)?.value
        ?: error("Theme parameter $parameterId must resolve to a number for $effect.")

private fun ThemeParameterValueV2.requireInsets(
    parameterId: String,
    effect: String,
): ThemeParameterValueV2.InsetsValue =
    this as? ThemeParameterValueV2.InsetsValue
        ?: error("Theme parameter $parameterId must resolve to insets for $effect.")

private fun ThemeTypographyV2.scaledBy(scale: Float): ThemeTypographyV2 =
    copy(
        displayScale = (displayScale * scale).coerceIn(0.5f, 2f),
        titleScale = (titleScale * scale).coerceIn(0.5f, 2f),
        bodyScale = (bodyScale * scale).coerceIn(0.5f, 2f),
        labelScale = (labelScale * scale).coerceIn(0.5f, 2f),
    )

private fun ThemeTypographyV2.withPresentation(
    values: Map<ThemePresentationTargetV2, ThemeParameterValueV2>,
): ThemeTypographyV2 {
    val useCustomFont =
        (values[ThemePresentationTargetV2.TYPOGRAPHY_USE_CUSTOM_FONT] as? ThemeParameterValueV2.BooleanValue)
            ?.value == true
    val selectedFamily =
        (values[ThemePresentationTargetV2.TYPOGRAPHY_FAMILY] as? ThemeParameterValueV2.OptionValue)
            ?.value
            ?.toThemeSystemFontFamily()
    val scale =
        (values[ThemePresentationTargetV2.TYPOGRAPHY_SCALE] as? ThemeParameterValueV2.FloatValue)
            ?.value
            ?.coerceIn(0.5f, 2f)
            ?: 1f
    return copy(
        family = if (useCustomFont) requireNotNull(selectedFamily) else family,
        displayScale = (displayScale * scale).coerceIn(0.5f, 2f),
        titleScale = (titleScale * scale).coerceIn(0.5f, 2f),
        bodyScale = (bodyScale * scale).coerceIn(0.5f, 2f),
        labelScale = (labelScale * scale).coerceIn(0.5f, 2f),
    )
}

private fun String.toThemeSystemFontFamily(): ThemeSystemFontFamilyV2 =
    ThemeSystemFontFamilyV2.entries.firstOrNull { family -> family.name.equals(this, ignoreCase = true) }
        ?: error("Theme font family must be one of ${ThemeSystemFontFamilyV2.entries.joinToString()}.")

private fun ThemeSystemFontFamilyV2.toComposeFontFamily(): FontFamily =
    when (this) {
        ThemeSystemFontFamilyV2.DEFAULT -> FontFamily.Default
        ThemeSystemFontFamilyV2.SANS_SERIF -> FontFamily.SansSerif
        ThemeSystemFontFamilyV2.SERIF -> FontFamily.Serif
        ThemeSystemFontFamilyV2.MONOSPACE -> FontFamily.Monospace
        ThemeSystemFontFamilyV2.CURSIVE -> FontFamily.Cursive
    }

private fun ThemeShapesV2.scaledBy(scale: Float): ThemeShapesV2 =
    copy(
        extraSmallDp = (extraSmallDp * scale).coerceIn(0f, 96f),
        smallDp = (smallDp * scale).coerceIn(0f, 96f),
        mediumDp = (mediumDp * scale).coerceIn(0f, 96f),
        largeDp = (largeDp * scale).coerceIn(0f, 96f),
        extraLargeDp = (extraLargeDp * scale).coerceIn(0f, 96f),
    )

private fun ThemeComponentSkinV2.scaleFrameGeometry(scale: Float): ThemeComponentSkinV2 =
    copy(
        normal = normal.scaleFrameGeometry(scale),
        disabled = disabled?.scaleFrameGeometry(scale),
        selected = selected?.scaleFrameGeometry(scale),
        focused = focused?.scaleFrameGeometry(scale),
        error = error?.scaleFrameGeometry(scale),
    )

private fun ThemeComponentStateSkinV2.scaleFrameGeometry(scale: Float): ThemeComponentStateSkinV2 =
    copy(frame = frame.scaledBy(scale))

private fun ThemeComponentFrameSpecV2.scaledBy(scale: Float): ThemeComponentFrameSpecV2 =
    when (this) {
        ThemeComponentFrameSpecV2.None -> this
        is ThemeComponentFrameSpecV2.RoundRect -> copy(cornerRadiusDp = (cornerRadiusDp * scale).coerceIn(0f, 96f))
        is ThemeComponentFrameSpecV2.CutCorners -> copy(cutSizeDp = (cutSizeDp * scale).coerceIn(0.5f, 48f))
        is ThemeComponentFrameSpecV2.HudNotched ->
            copy(
                cutSizeDp = (cutSizeDp * scale).coerceIn(0.5f, 48f),
                notchDepthDp = (notchDepthDp * scale).coerceIn(0.5f, 48f),
            )

        is ThemeComponentFrameSpecV2.CornerBrackets ->
            copy(
                cornerCutDp = (cornerCutDp * scale).coerceIn(0f, 48f),
                bracketLengthDp = (bracketLengthDp * scale).coerceIn(4f, 96f),
            )

        is ThemeComponentFrameSpecV2.SegmentedRail ->
            copy(
                cornerCutDp = (cornerCutDp * scale).coerceIn(0f, 48f),
                railInsetDp = (railInsetDp * scale).coerceIn(0f, 48f),
                segmentLengthDp = (segmentLengthDp * scale).coerceIn(4f, 160f),
            )
    }

private fun ThemeComponentSkinV2.withContentInsets(
    insets: ThemeParameterValueV2.InsetsValue,
): ThemeComponentSkinV2 {
    val resolved =
        ThemeComponentInsetsV2(
            startDp = insets.startDp,
            topDp = insets.topDp,
            endDp = insets.endDp,
            bottomDp = insets.bottomDp,
        )
    fun ThemeComponentStateSkinV2.withInsets(): ThemeComponentStateSkinV2 = copy(contentPadding = resolved)
    return copy(
        normal = normal.withInsets(),
        disabled = disabled?.withInsets(),
        selected = selected?.withInsets(),
        focused = focused?.withInsets(),
        error = error?.withInsets(),
    )
}

private fun ThemeMaterialColorSchemeV2.accentPaletteTokens(seedArgb: Long): Map<String, ThemeSceneTokenValueV1.ColorToken> {
    val seedHsl = FloatArray(3)
    ColorUtils.colorToHSL(seedArgb.toInt(), seedHsl)
    val primarySaturation = seedHsl[1].coerceIn(0.42f, 0.88f)
    val secondarySaturation = (primarySaturation * 0.56f).coerceIn(0.24f, 0.52f)
    val tertiarySaturation = (primarySaturation * 0.72f).coerceIn(0.34f, 0.68f)

    fun tone(
        hueOffset: Float,
        saturation: Float,
        light: Float,
        dark: Float,
    ): ThemeSceneTokenValueV1.ColorToken =
        ThemeSceneTokenValueV1.ColorToken(
            lightArgb = hslArgb(seedHsl[0] + hueOffset, saturation, light),
            darkArgb = hslArgb(seedHsl[0] + hueOffset, saturation, dark),
        )

    val lightOn = 0xFFFFFFFFL
    val darkOn = 0xFF101114L
    fun contrast(light: Long, dark: Long) = ThemeSceneTokenValueV1.ColorToken(light, dark)
    fun readableOn(color: Long): Long =
        if (ColorUtils.calculateLuminance(color.toInt()) >= 0.45) darkOn else lightOn

    val patch = linkedMapOf<String, ThemeSceneTokenValueV1.ColorToken>()
    fun assign(
        tokenId: String,
        value: ThemeSceneTokenValueV1.ColorToken,
    ) {
        val existing = patch[tokenId]
        require(existing == null || existing == value) {
            "Accent palette maps token $tokenId to incompatible Material roles."
        }
        patch[tokenId] = value
    }

    val primaryColor = ThemeSceneTokenValueV1.ColorToken(seedArgb, seedArgb)
    val primaryContainerColor = tone(0f, primarySaturation, 0.90f, 0.30f)
    assign(this.primary, primaryColor)
    assign(onPrimary, contrast(readableOn(seedArgb), readableOn(seedArgb)))
    assign(primaryContainer, primaryContainerColor)
    assign(onPrimaryContainer, contrast(darkOn, lightOn))
    assign(inversePrimary, contrast(primaryColor.darkArgb, primaryColor.lightArgb))

    val secondaryColor = tone(18f, secondarySaturation, 0.40f, 0.80f)
    val secondaryContainerColor = tone(18f, secondarySaturation, 0.90f, 0.30f)
    assign(this.secondary, secondaryColor)
    assign(onSecondary, contrast(lightOn, darkOn))
    assign(secondaryContainer, secondaryContainerColor)
    assign(onSecondaryContainer, contrast(darkOn, lightOn))

    val tertiaryColor = tone(58f, tertiarySaturation, 0.40f, 0.80f)
    val tertiaryContainerColor = tone(58f, tertiarySaturation, 0.90f, 0.30f)
    assign(this.tertiary, tertiaryColor)
    assign(onTertiary, contrast(lightOn, darkOn))
    assign(tertiaryContainer, tertiaryContainerColor)
    assign(onTertiaryContainer, contrast(darkOn, lightOn))

    assign(surfaceTint, primaryColor)
    assign(outline, tone(0f, (primarySaturation * 0.42f).coerceAtLeast(0.18f), 0.48f, 0.62f))
    assign(outlineVariant, tone(0f, (primarySaturation * 0.34f).coerceAtLeast(0.14f), 0.80f, 0.34f))

    val expectedTargets = accentTokenIds().toSet()
    require(patch.keys == expectedTargets) { "Accent palette must cover every declared accent role." }
    return patch
}

private fun hslArgb(
    hue: Float,
    saturation: Float,
    lightness: Float,
): Long {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    return ColorUtils.HSLToColor(floatArrayOf(normalizedHue, saturation, lightness)).toLong() and 0xFFFFFFFFL
}

private fun ThemeComponentStateSkinV2.resolve(
    tokens: ThemeSceneTokenResolverV1,
    darkTheme: Boolean,
): ResolvedThemeComponentSkinV2 =
    ResolvedThemeComponentSkinV2(
        container = tokens.color(ThemeSceneTokenIdV1(containerToken), darkTheme),
        content = tokens.color(ThemeSceneTokenIdV1(contentToken), darkTheme),
        frame = frame.resolve(tokens, darkTheme),
        elevationDp = elevationDp,
        paddingStartDp = contentPadding.startDp,
        paddingTopDp = contentPadding.topDp,
        paddingEndDp = contentPadding.endDp,
        paddingBottomDp = contentPadding.bottomDp,
    )

private fun ThemeMaterialColorSchemeV2.toColorScheme(
    tokens: ThemeSceneTokenResolverV1,
    darkTheme: Boolean,
): ColorScheme {
    fun color(tokenId: String): Color = tokens.color(ThemeSceneTokenIdV1(tokenId), darkTheme)
    val baseline = if (darkTheme) darkColorScheme() else lightColorScheme()
    return baseline.copy(
        primary = color(primary),
        onPrimary = color(onPrimary),
        primaryContainer = color(primaryContainer),
        onPrimaryContainer = color(onPrimaryContainer),
        inversePrimary = color(inversePrimary),
        secondary = color(secondary),
        onSecondary = color(onSecondary),
        secondaryContainer = color(secondaryContainer),
        onSecondaryContainer = color(onSecondaryContainer),
        tertiary = color(tertiary),
        onTertiary = color(onTertiary),
        tertiaryContainer = color(tertiaryContainer),
        onTertiaryContainer = color(onTertiaryContainer),
        background = color(background),
        onBackground = color(onBackground),
        surface = color(surface),
        onSurface = color(onSurface),
        surfaceVariant = color(surfaceVariant),
        onSurfaceVariant = color(onSurfaceVariant),
        surfaceTint = color(surfaceTint),
        inverseSurface = color(inverseSurface),
        inverseOnSurface = color(inverseOnSurface),
        error = color(error),
        onError = color(onError),
        errorContainer = color(errorContainer),
        onErrorContainer = color(onErrorContainer),
        outline = color(outline),
        outlineVariant = color(outlineVariant),
        scrim = color(scrim),
        surfaceBright = color(surfaceBright),
        surfaceDim = color(surfaceDim),
        surfaceContainerLowest = color(surfaceContainerLowest),
        surfaceContainerLow = color(surfaceContainerLow),
        surfaceContainer = color(surfaceContainer),
        surfaceContainerHigh = color(surfaceContainerHigh),
        surfaceContainerHighest = color(surfaceContainerHighest),
    )
}

private fun com.ai.assistance.operit.data.theme.packages.ThemeTypographyV2.toTypography(
    userFontScale: Float,
    fontFamily: FontFamily?,
): Typography {
    val baseline = createCustomTypography(userFontScale)
    val declaredFamily = family.toComposeFontFamily()
    val resolvedFamily = fontFamily ?: declaredFamily
    fun TextStyle.withTheme(scale: Float): TextStyle =
        copy(
            fontFamily = resolvedFamily,
            fontSize = fontSize * scale,
            lineHeight = lineHeight * scale,
            letterSpacing = letterSpacingEm.sp,
        )
    return Typography(
        displayLarge = baseline.displayLarge.withTheme(displayScale),
        displayMedium = baseline.displayMedium.withTheme(displayScale),
        displaySmall = baseline.displaySmall.withTheme(displayScale),
        headlineLarge = baseline.headlineLarge.withTheme(titleScale),
        headlineMedium = baseline.headlineMedium.withTheme(titleScale),
        headlineSmall = baseline.headlineSmall.withTheme(titleScale),
        titleLarge = baseline.titleLarge.withTheme(titleScale),
        titleMedium = baseline.titleMedium.withTheme(titleScale),
        titleSmall = baseline.titleSmall.withTheme(titleScale),
        bodyLarge = baseline.bodyLarge.withTheme(bodyScale),
        bodyMedium = baseline.bodyMedium.withTheme(bodyScale),
        bodySmall = baseline.bodySmall.withTheme(bodyScale),
        labelLarge = baseline.labelLarge.withTheme(labelScale),
        labelMedium = baseline.labelMedium.withTheme(labelScale),
        labelSmall = baseline.labelSmall.withTheme(labelScale),
    )
}
