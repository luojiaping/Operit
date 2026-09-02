package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneDefinitionV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageFitV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val MEMBER_ID_PATTERN_V2 = Regex("^[a-z][a-z0-9_]*$")
private val SHA256_PATTERN_V2 = Regex("^[0-9a-f]{64}$")
private val THEME_SYSTEM_FONT_OPTION_IDS = setOf("default", "sans_serif", "serif", "monospace", "cursive")

internal const val THEME_PACKAGE_SCHEMA_VERSION = 4
internal const val THEME_PACKAGE_MANIFEST_ENTRY_V2 = "operit-theme.json"
internal const val THEME_PACKAGE_EXTENSION_V2 = "otheme"
internal const val THEME_PACKAGE_ZIP_COMMENT_V2 = "Operit Theme Package"

/** Locale-keyed text; the key '*' is the required default locale entry. */
@Serializable
internal data class ThemePackageLocalizedTextV2(
    val values: Map<String, String>,
) {
    init {
        require(values.isNotEmpty()) { "Localized text must declare at least one entry." }
        require(values.containsKey("*")) { "Localized text must declare the default '*' entry." }
        require(values.values.all { it.isNotEmpty() }) { "Localized text entries must not be empty." }
    }

    fun resolve(locale: String): String = values[locale] ?: values.getValue("*")
}

@Serializable
internal enum class ThemeAssetKindV2 {
    BITMAP,
    NINE_SLICE,
    FONT,
    PATH,
}

@Serializable
internal data class ThemePackageAssetEntryV2(
    val key: String,
    val path: String,
    val kind: ThemeAssetKindV2,
    val sha256: String,
    val byteSize: Long,
) {
    init {
        require(MEMBER_ID_PATTERN_V2.matches(key)) { "Theme asset key must be a member ID: $key" }
        require(
            path.isNotBlank() &&
                !path.startsWith('/') &&
                !path.contains('\\') &&
                !path.contains(':') &&
                path.split('/').none { segment -> segment == ".." },
        ) {
            "Theme asset path must be a portable relative archive path: $path"
        }
        require(SHA256_PATTERN_V2.matches(sha256)) { "Theme asset digest must be lowercase sha-256." }
        require(byteSize > 0) { "Theme asset byte size must be positive." }
    }
}

@Serializable
internal data class ThemePackageVariantV2(
    val id: String,
    val label: ThemePackageLocalizedTextV2,
) {
    init {
        require(MEMBER_ID_PATTERN_V2.matches(id)) { "Theme variant ID must be a member ID: $id" }
    }
}

@Serializable
internal data class ThemePackageAttributionV2(
    val text: ThemePackageLocalizedTextV2,
    val sourceUrl: String,
) {
    init {
        require(sourceUrl.startsWith("https://")) {
            "Theme package attribution source URL must use HTTPS."
        }
    }
}

@Serializable
internal enum class ThemeParameterTypeV2 {
    COLOR,
    COLOR_PAIR,
    BOOLEAN,
    OPTION,
    FLOAT,
    IMAGE_URI,
    VIDEO_URI,
    FONT_URI,
    IMAGE_LAYOUT,
    INSETS,
    CORNER_RADIUS,
}

@Serializable
internal enum class ThemeParameterVisibilityV2 {
    USER,
    AUTHOR,
}

@Serializable
internal enum class ThemeParameterSectionV2 {
    APPEARANCE,
    CONVERSATION,
    COMPOSER,
    APP_CHROME,
}

@Serializable
internal data class ThemeParameterChoiceV2(
    val id: String,
    val label: ThemePackageLocalizedTextV2,
) {
    init {
        ThemeParameterIdV2(id)
    }
}

/** Declares the compact native control used to edit one package-owned value. */
@Serializable
internal sealed interface ThemeParameterControlV2 {
    @Serializable
    @SerialName("color_palette")
    data class ColorPalette(
        val presetArgb: List<Long> = emptyList(),
        val allowCustom: Boolean = true,
    ) : ThemeParameterControlV2 {
        init {
            require(presetArgb.all { argb -> argb in 0..0xFFFFFFFFL }) {
                "Theme color palette values must be ARGB within 0..0xffffffff."
            }
            require(presetArgb.all { argb -> argb ushr 24 == 0xFFL }) {
                "Theme color palette values must be opaque."
            }
            require(presetArgb.distinct().size == presetArgb.size) {
                "Theme color palette values must be unique."
            }
            require(presetArgb.isNotEmpty() || allowCustom) {
                "Theme color palette must provide a preset or allow custom colors."
            }
        }
    }

    @Serializable
    @SerialName("color_pair_palette")
    data class ColorPairPalette(
        val lightPresetArgb: List<Long> = emptyList(),
        val darkPresetArgb: List<Long> = emptyList(),
        val allowCustom: Boolean = true,
    ) : ThemeParameterControlV2 {
        init {
            val colors = lightPresetArgb + darkPresetArgb
            require(colors.all { argb -> argb in 0..0xFFFFFFFFL }) {
                "Theme color pair palette values must be ARGB within 0..0xffffffff."
            }
            require(lightPresetArgb.distinct().size == lightPresetArgb.size) {
                "Theme light color pair palette entries must be unique."
            }
            require(darkPresetArgb.distinct().size == darkPresetArgb.size) {
                "Theme dark color pair palette entries must be unique."
            }
            require(colors.isNotEmpty() || allowCustom) {
                "Theme color pair palette must provide a preset or allow custom colors."
            }
        }
    }

    @Serializable
    @SerialName("toggle")
    data object Toggle : ThemeParameterControlV2

    @Serializable
    @SerialName("choice")
    data class Choice(
        val options: List<ThemeParameterChoiceV2>,
    ) : ThemeParameterControlV2 {
        init {
            require(options.isNotEmpty()) { "Theme choice control must declare options." }
            require(options.map { option -> option.id }.distinct().size == options.size) {
                "Theme choice options must have unique IDs."
            }
        }
    }

    @Serializable
    @SerialName("slider")
    data class Slider(
        val minimum: Float,
        val maximum: Float,
        val step: Float,
    ) : ThemeParameterControlV2 {
        init {
            require(minimum.isFinite() && maximum.isFinite() && step.isFinite()) {
                "Theme slider bounds must be finite."
            }
            require(minimum < maximum) { "Theme slider minimum must be lower than maximum." }
            require(step > 0f && step <= maximum - minimum) {
                "Theme slider step must fit within its range."
            }
        }
    }

    @Serializable
    @SerialName("image_picker")
    data class ImagePicker(
        val mimeTypes: List<String> = DEFAULT_IMAGE_MIME_TYPES,
    ) : ThemeParameterControlV2 {
        init {
            require(mimeTypes.isNotEmpty()) { "Theme image picker must declare MIME types." }
            require(mimeTypes.all(ALLOWED_IMAGE_MIME_TYPES::contains)) {
                "Theme image picker declares an unsupported MIME type."
            }
            require(mimeTypes.distinct().size == mimeTypes.size) {
                "Theme image picker MIME types must be unique."
            }
        }

        companion object {
            val DEFAULT_IMAGE_MIME_TYPES = listOf("image/jpeg", "image/png", "image/webp")
            private val ALLOWED_IMAGE_MIME_TYPES = DEFAULT_IMAGE_MIME_TYPES.toSet()
        }
    }

    @Serializable
    @SerialName("video_picker")
    data class VideoPicker(
        val mimeTypes: List<String> = DEFAULT_VIDEO_MIME_TYPES,
    ) : ThemeParameterControlV2 {
        init {
            require(mimeTypes.isNotEmpty()) { "Theme video picker must declare MIME types." }
            require(mimeTypes.all(ALLOWED_VIDEO_MIME_TYPES::contains)) {
                "Theme video picker declares an unsupported MIME type."
            }
            require(mimeTypes.distinct().size == mimeTypes.size) {
                "Theme video picker MIME types must be unique."
            }
        }

        companion object {
            val DEFAULT_VIDEO_MIME_TYPES = listOf("video/mp4", "video/webm")
            private val ALLOWED_VIDEO_MIME_TYPES = DEFAULT_VIDEO_MIME_TYPES.toSet()
        }
    }

    @Serializable
    @SerialName("font_picker")
    data class FontPicker(
        val mimeTypes: List<String> = DEFAULT_FONT_MIME_TYPES,
    ) : ThemeParameterControlV2 {
        init {
            require(mimeTypes.isNotEmpty()) { "Theme font picker must declare MIME types." }
            require(mimeTypes.all(ALLOWED_FONT_MIME_TYPES::contains)) {
                "Theme font picker declares an unsupported MIME type."
            }
            require(mimeTypes.distinct().size == mimeTypes.size) {
                "Theme font picker MIME types must be unique."
            }
        }

        companion object {
            val DEFAULT_FONT_MIME_TYPES = listOf("font/ttf", "font/otf")
            private val ALLOWED_FONT_MIME_TYPES = DEFAULT_FONT_MIME_TYPES.toSet()
        }
    }

    /** Author values remain part of the package contract but never render in application settings. */
    @Serializable
    @SerialName("author_value")
    data object AuthorValue : ThemeParameterControlV2
}

@Serializable
internal sealed interface ThemeParameterConditionV2 {
    val parameterId: String

    @Serializable
    @SerialName("boolean_equals")
    data class BooleanEquals(
        override val parameterId: String,
        val expected: Boolean,
    ) : ThemeParameterConditionV2 {
        init {
            ThemeParameterIdV2(parameterId)
        }
    }

    @Serializable
    @SerialName("option_equals")
    data class OptionEquals(
        override val parameterId: String,
        val expected: String,
    ) : ThemeParameterConditionV2 {
        init {
            ThemeParameterIdV2(parameterId)
            ThemeParameterIdV2(expected)
        }
    }

    @Serializable
    @SerialName("resource_present")
    data class ResourcePresent(
        override val parameterId: String,
    ) : ThemeParameterConditionV2 {
        init {
            ThemeParameterIdV2(parameterId)
        }
    }
}

/** Targets cover the full development baseline without exposing an untyped visual-property map. */
@Serializable
internal enum class ThemePresentationTargetV2(
    val valueType: ThemeParameterTypeV2,
    val optionIds: Set<String> = emptySet(),
) {
    TYPOGRAPHY_USE_CUSTOM_FONT(ThemeParameterTypeV2.BOOLEAN),
    TYPOGRAPHY_FAMILY(ThemeParameterTypeV2.OPTION, THEME_SYSTEM_FONT_OPTION_IDS),
    TYPOGRAPHY_FONT_URI(ThemeParameterTypeV2.FONT_URI),
    TYPOGRAPHY_SCALE(ThemeParameterTypeV2.FLOAT),
    BACKGROUND_USE_IMAGE(ThemeParameterTypeV2.BOOLEAN),
    BACKGROUND_MEDIA_TYPE(ThemeParameterTypeV2.OPTION, setOf("none", "image", "video")),
    BACKGROUND_IMAGE_URI(ThemeParameterTypeV2.IMAGE_URI),
    BACKGROUND_VIDEO_URI(ThemeParameterTypeV2.VIDEO_URI),
    BACKGROUND_OPACITY(ThemeParameterTypeV2.FLOAT),
    BACKGROUND_BLUR_ENABLED(ThemeParameterTypeV2.BOOLEAN),
    BACKGROUND_BLUR_RADIUS(ThemeParameterTypeV2.FLOAT),
    BACKGROUND_VIDEO_MUTED(ThemeParameterTypeV2.BOOLEAN),
    BACKGROUND_VIDEO_LOOP(ThemeParameterTypeV2.BOOLEAN),
    CURSOR_USER_BUBBLE_FOLLOW_THEME(ThemeParameterTypeV2.BOOLEAN),
    CURSOR_USER_BUBBLE_LIQUID_GLASS(ThemeParameterTypeV2.BOOLEAN),
    CURSOR_USER_BUBBLE_WATER_GLASS(ThemeParameterTypeV2.BOOLEAN),
    CURSOR_USER_BUBBLE_COLOR(ThemeParameterTypeV2.COLOR),
    BUBBLE_SHOW_AVATAR(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_WIDE_LAYOUT(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_USER_LIQUID_GLASS(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_USER_WATER_GLASS(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_ASSISTANT_LIQUID_GLASS(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_ASSISTANT_WATER_GLASS(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_IMAGE_RENDER_MODE(ThemeParameterTypeV2.OPTION, setOf("tiled_nine_slice", "nine_patch")),
    BUBBLE_USER_ROUNDED_CORNERS(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_ASSISTANT_ROUNDED_CORNERS(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_USER_COLOR(ThemeParameterTypeV2.COLOR),
    BUBBLE_ASSISTANT_COLOR(ThemeParameterTypeV2.COLOR),
    BUBBLE_USER_TEXT_COLOR(ThemeParameterTypeV2.COLOR),
    BUBBLE_ASSISTANT_TEXT_COLOR(ThemeParameterTypeV2.COLOR),
    BUBBLE_USER_USE_CUSTOM_FONT(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_USER_FONT_FAMILY(ThemeParameterTypeV2.OPTION, THEME_SYSTEM_FONT_OPTION_IDS),
    BUBBLE_USER_FONT_URI(ThemeParameterTypeV2.FONT_URI),
    BUBBLE_ASSISTANT_USE_CUSTOM_FONT(ThemeParameterTypeV2.BOOLEAN),
    BUBBLE_ASSISTANT_FONT_FAMILY(ThemeParameterTypeV2.OPTION, THEME_SYSTEM_FONT_OPTION_IDS),
    BUBBLE_ASSISTANT_FONT_URI(ThemeParameterTypeV2.FONT_URI),
    BUBBLE_USER_IMAGE_URI(ThemeParameterTypeV2.IMAGE_URI),
    BUBBLE_ASSISTANT_IMAGE_URI(ThemeParameterTypeV2.IMAGE_URI),
    BUBBLE_USER_IMAGE_LAYOUT(ThemeParameterTypeV2.IMAGE_LAYOUT),
    BUBBLE_ASSISTANT_IMAGE_LAYOUT(ThemeParameterTypeV2.IMAGE_LAYOUT),
    BUBBLE_USER_CONTENT_INSETS(ThemeParameterTypeV2.INSETS),
    BUBBLE_ASSISTANT_CONTENT_INSETS(ThemeParameterTypeV2.INSETS),
    AVATAR_SHAPE(ThemeParameterTypeV2.OPTION, setOf("circle", "square", "rounded")),
    AVATAR_CORNER_RADIUS(ThemeParameterTypeV2.CORNER_RADIUS),
    COMPOSER_TRANSPARENT(ThemeParameterTypeV2.BOOLEAN),
    COMPOSER_FLOATING(ThemeParameterTypeV2.BOOLEAN),
    COMPOSER_LIQUID_GLASS(ThemeParameterTypeV2.BOOLEAN),
    COMPOSER_WATER_GLASS(ThemeParameterTypeV2.BOOLEAN),
    CHROME_STATUS_BAR_HIDDEN(ThemeParameterTypeV2.BOOLEAN),
    CHROME_STATUS_BAR_TRANSPARENT(ThemeParameterTypeV2.BOOLEAN),
    CHROME_STATUS_BAR_COLOR(ThemeParameterTypeV2.COLOR),
    CHROME_TOOLBAR_TRANSPARENT(ThemeParameterTypeV2.BOOLEAN),
    CHROME_TOOLBAR_COLOR(ThemeParameterTypeV2.COLOR),
    CHROME_NAVIGATION_WATER_GLASS(ThemeParameterTypeV2.BOOLEAN),
    CHROME_NAVIGATION_BUTTON_LIQUID_GLASS(ThemeParameterTypeV2.BOOLEAN),
    CHROME_NAVIGATION_BACKGROUND_COLOR(ThemeParameterTypeV2.COLOR),
    CHROME_NAVIGATION_ACCENT_COLOR(ThemeParameterTypeV2.COLOR),
    CHROME_CHAT_HEADER_TRANSPARENT(ThemeParameterTypeV2.BOOLEAN),
    CHROME_CHAT_HEADER_OVERLAY_MODE(ThemeParameterTypeV2.OPTION, setOf("none", "overlay")),
    CHROME_APP_BAR_CONTENT_COLOR_MODE(ThemeParameterTypeV2.OPTION, setOf("auto", "light", "dark")),
    CHROME_CHAT_HEADER_HISTORY_ICON_COLOR(ThemeParameterTypeV2.COLOR),
    CHROME_CHAT_HEADER_PIP_ICON_COLOR(ThemeParameterTypeV2.COLOR),
}

/** A parameter may only affect an explicit target declared by the active theme package. */
@Serializable
internal sealed interface ThemeParameterEffectV2 {
    @Serializable
    @SerialName("accent_palette")
    data object AccentPalette : ThemeParameterEffectV2

    @Serializable
    @SerialName("token_color")
    data class TokenColor(
        val tokenIds: List<String>,
    ) : ThemeParameterEffectV2 {
        init {
            require(tokenIds.isNotEmpty()) { "Theme token color effect must target a token." }
            require(tokenIds.distinct().size == tokenIds.size) {
                "Theme token color effect targets must be unique."
            }
            tokenIds.forEach(::ThemeSceneTokenIdV1)
        }
    }

    @Serializable
    @SerialName("token_color_pair")
    data class TokenColorPair(
        val tokenIds: List<String>,
    ) : ThemeParameterEffectV2 {
        init {
            require(tokenIds.isNotEmpty()) { "Theme token color pair effect must target a token." }
            require(tokenIds.distinct().size == tokenIds.size) {
                "Theme token color pair effect targets must be unique."
            }
            tokenIds.forEach(::ThemeSceneTokenIdV1)
        }
    }

    @Serializable
    @SerialName("stage_image")
    data class StageImage(
        val surfaceIds: List<String>,
        val fit: ThemeSceneImageFitV1 = ThemeSceneImageFitV1.CROP,
        val opacity: Float = 0.22f,
    ) : ThemeParameterEffectV2 {
        init {
            require(surfaceIds.isNotEmpty()) { "Theme stage image effect must target a surface." }
            require(surfaceIds.distinct().size == surfaceIds.size) {
                "Theme stage image effect targets must be unique."
            }
            surfaceIds.forEach(::ThemeSurfaceIdV2)
            require(opacity in 0f..1f) { "Theme stage image opacity must be within [0, 1]." }
        }
    }

    @Serializable
    @SerialName("typography_scale")
    data object TypographyScale : ThemeParameterEffectV2

    @Serializable
    @SerialName("shape_scale")
    data object ShapeScale : ThemeParameterEffectV2

    @Serializable
    @SerialName("component_frame_scale")
    data class ComponentFrameScale(
        val componentIds: List<String>,
    ) : ThemeParameterEffectV2 {
        init {
            require(componentIds.isNotEmpty()) { "Theme component frame scale must target a component." }
            require(componentIds.distinct().size == componentIds.size) {
                "Theme component frame scale targets must be unique."
            }
            componentIds.forEach(::ThemeComponentIdV2)
        }
    }

    @Serializable
    @SerialName("component_content_insets")
    data class ComponentContentInsets(
        val componentIds: List<String>,
    ) : ThemeParameterEffectV2 {
        init {
            require(componentIds.isNotEmpty()) { "Theme component inset effect must target a component." }
            require(componentIds.distinct().size == componentIds.size) {
                "Theme component inset effect targets must be unique."
            }
            componentIds.forEach(::ThemeComponentIdV2)
        }
    }

    @Serializable
    @SerialName("presentation")
    data class Presentation(
        val targets: List<ThemePresentationTargetV2>,
    ) : ThemeParameterEffectV2 {
        init {
            require(targets.isNotEmpty()) { "Theme presentation effect must target a presentation value." }
            require(targets.distinct().size == targets.size) {
                "Theme presentation effect targets must be unique."
            }
        }
    }
}

@Serializable
internal data class ThemeParameterDefinitionV2(
    val id: String,
    val type: ThemeParameterTypeV2,
    val defaultValue: ThemeParameterValueV2? = null,
    val label: ThemePackageLocalizedTextV2,
    val description: ThemePackageLocalizedTextV2? = null,
    val control: ThemeParameterControlV2,
    val effects: List<ThemeParameterEffectV2>,
    val visibility: ThemeParameterVisibilityV2 = ThemeParameterVisibilityV2.AUTHOR,
    val section: ThemeParameterSectionV2? = null,
    val order: Int = 0,
    val visibleWhen: List<ThemeParameterConditionV2> = emptyList(),
) {
    init {
        ThemeParameterIdV2(id)
        require(defaultValue == null || defaultValue.matches(type)) {
            "Theme parameter $id declares type $type with a mismatched default value."
        }
        require(control.matches(type)) {
            "Theme parameter $id declares control $control for incompatible type $type."
        }
        require(effects.isNotEmpty()) { "Theme parameter $id must declare at least one visual effect." }
        require(effects.all { effect -> effect.matches(type) }) {
            "Theme parameter $id declares an effect incompatible with type $type."
        }
        effects.filterIsInstance<ThemeParameterEffectV2.Presentation>().forEach { effect ->
            effect.targets.forEach { target ->
                require(target.valueType == type) {
                    "Theme parameter $id cannot apply $type to presentation target $target."
                }
                if (type == ThemeParameterTypeV2.OPTION) {
                    val choice = control as? ThemeParameterControlV2.Choice
                    require(choice != null && choice.options.all { option -> option.id in target.optionIds }) {
                        "Theme parameter $id declares an option outside presentation target $target."
                    }
                }
            }
        }
        if (type.isUri()) {
            require(defaultValue == null) { "Theme URI parameter $id must not declare a package default." }
        }
        val palette = control as? ThemeParameterControlV2.ColorPalette
        if (palette != null) {
            val colorDefault = defaultValue as? ThemeParameterValueV2.ColorValue
            require(colorDefault != null) {
                "Theme color palette parameter $id must declare a color default."
            }
            require(colorDefault.argb ushr 24 == 0xFFL) {
                "Theme color palette parameter $id must use an opaque default color."
            }
        }
        val choices = control as? ThemeParameterControlV2.Choice
        if (choices != null) {
            val optionDefault = defaultValue as? ThemeParameterValueV2.OptionValue
            require(optionDefault != null && choices.options.any { option -> option.id == optionDefault.value }) {
                "Theme choice parameter $id must declare one of its options as the default."
            }
        }
        if (visibility == ThemeParameterVisibilityV2.USER) {
            require(section != null) { "User-visible theme parameter $id must declare a section." }
            require(control !is ThemeParameterControlV2.AuthorValue) {
                "User-visible theme parameter $id cannot use an author-only control."
            }
            require(control.supportsUserSettingsSurface()) {
                "User-visible theme parameter $id declares a control outside the compact settings surface."
            }
            require(defaultValue != null || type.isUri()) {
                "User-visible non-resource theme parameter $id must declare a default value."
            }
        } else {
            require(section == null) { "Author-only theme parameter $id cannot declare a settings section." }
            require(control is ThemeParameterControlV2.AuthorValue) {
                "Author-only theme parameter $id must use the author-only control."
            }
        }
    }
}

@Serializable
internal enum class ThemeSystemFontFamilyV2 {
    DEFAULT,
    SANS_SERIF,
    SERIF,
    MONOSPACE,
    CURSIVE,
}

@Serializable
internal enum class ThemeBackgroundMediaTypeV2 {
    NONE,
    IMAGE,
    VIDEO,
}

@Serializable
internal enum class ThemeBubbleImageRenderModeV2 {
    TILED_NINE_SLICE,
    NINE_PATCH,
}

@Serializable
internal enum class ThemeAvatarShapeV2 {
    CIRCLE,
    SQUARE,
    ROUNDED,
}

@Serializable
internal enum class ThemeChatHeaderOverlayModeV2 {
    NONE,
    OVERLAY,
}

@Serializable
internal enum class ThemeChromeContentColorModeV2 {
    AUTO,
    LIGHT,
    DARK,
}

/** Static package behavior and explicit defaults for values that are not component-skin geometry. */
@Serializable
internal data class ThemePackagePresentationBehaviorV2(
    val background: ThemeBackgroundPresentationV2 = ThemeBackgroundPresentationV2(),
    val typography: ThemeTypographyPresentationV2 = ThemeTypographyPresentationV2(),
    val conversation: ThemeConversationPresentationV2 = ThemeConversationPresentationV2(),
    val composer: ThemeComposerPresentationV2 = ThemeComposerPresentationV2(),
    val chrome: ThemeChromePresentationV2 = ThemeChromePresentationV2(),
)

@Serializable
internal data class ThemeBackgroundPresentationV2(
    val enabled: Boolean = false,
    val mediaType: ThemeBackgroundMediaTypeV2 = ThemeBackgroundMediaTypeV2.NONE,
    val opacity: Float = 0.22f,
    val blurEnabled: Boolean = false,
    val blurRadiusDp: Float = 0f,
    val videoMuted: Boolean = true,
    val videoLoop: Boolean = true,
) {
    init {
        require(opacity in 0f..1f) { "Theme background opacity must be within [0, 1]." }
        require(blurRadiusDp in 0f..96f) { "Theme background blur radius must be within [0, 96] dp." }
        require(!enabled || mediaType != ThemeBackgroundMediaTypeV2.NONE) {
            "An enabled theme background must declare image or video media."
        }
    }
}

@Serializable
internal data class ThemeTypographyPresentationV2(
    val useCustomFont: Boolean = false,
    val family: ThemeSystemFontFamilyV2 = ThemeSystemFontFamilyV2.DEFAULT,
    val scale: Float = 1f,
) {
    init {
        require(scale in 0.5f..2f) { "Theme typography presentation scale must be within [0.5, 2]." }
    }
}

@Serializable
internal data class ThemeConversationPresentationV2(
    val cursorUserBubbleFollowTheme: Boolean = true,
    val cursorUserBubbleLiquidGlass: Boolean = false,
    val cursorUserBubbleWaterGlass: Boolean = false,
    val cursorUserBubbleColorArgb: Long? = null,
    val bubbleShowAvatar: Boolean = true,
    val bubbleWideLayout: Boolean = false,
    val bubbleUserLiquidGlass: Boolean = false,
    val bubbleUserWaterGlass: Boolean = false,
    val bubbleAssistantLiquidGlass: Boolean = false,
    val bubbleAssistantWaterGlass: Boolean = false,
    val bubbleImageRenderMode: ThemeBubbleImageRenderModeV2 = ThemeBubbleImageRenderModeV2.TILED_NINE_SLICE,
    val bubbleUserRoundedCorners: Boolean = true,
    val bubbleAssistantRoundedCorners: Boolean = true,
    val bubbleUserColorArgb: Long? = null,
    val bubbleAssistantColorArgb: Long? = null,
    val bubbleUserTextColorArgb: Long? = null,
    val bubbleAssistantTextColorArgb: Long? = null,
    val bubbleUserUseCustomFont: Boolean = false,
    val bubbleUserFontFamily: ThemeSystemFontFamilyV2 = ThemeSystemFontFamilyV2.DEFAULT,
    val bubbleAssistantUseCustomFont: Boolean = false,
    val bubbleAssistantFontFamily: ThemeSystemFontFamilyV2 = ThemeSystemFontFamilyV2.DEFAULT,
    val avatarShape: ThemeAvatarShapeV2 = ThemeAvatarShapeV2.CIRCLE,
    val avatarCornerRadiusDp: Float = 0f,
) {
    init {
        listOfNotNull(
            cursorUserBubbleColorArgb,
            bubbleUserColorArgb,
            bubbleAssistantColorArgb,
            bubbleUserTextColorArgb,
            bubbleAssistantTextColorArgb,
        ).forEach { argb -> require(argb in 0..0xFFFFFFFFL) { "Theme conversation color must be ARGB." } }
        require(avatarCornerRadiusDp in 0f..96f) {
            "Theme avatar corner radius must be within [0, 96] dp."
        }
    }
}

@Serializable
internal data class ThemeComposerPresentationV2(
    val transparent: Boolean = false,
    val floating: Boolean = false,
    val liquidGlass: Boolean = false,
    val waterGlass: Boolean = false,
)

@Serializable
internal data class ThemeChromePresentationV2(
    val statusBarHidden: Boolean = false,
    val statusBarTransparent: Boolean = false,
    val statusBarColorArgb: Long? = null,
    val toolbarTransparent: Boolean = false,
    val toolbarColorArgb: Long? = null,
    val navigationWaterGlass: Boolean = false,
    val navigationButtonLiquidGlass: Boolean = false,
    val navigationBackgroundColorArgb: Long? = null,
    val navigationAccentColorArgb: Long? = null,
    val chatHeaderTransparent: Boolean = false,
    val chatHeaderOverlayMode: ThemeChatHeaderOverlayModeV2 = ThemeChatHeaderOverlayModeV2.NONE,
    val appBarContentColorMode: ThemeChromeContentColorModeV2 = ThemeChromeContentColorModeV2.AUTO,
    val chatHeaderHistoryIconColorArgb: Long? = null,
    val chatHeaderPipIconColorArgb: Long? = null,
) {
    init {
        listOfNotNull(
            statusBarColorArgb,
            toolbarColorArgb,
            navigationBackgroundColorArgb,
            navigationAccentColorArgb,
            chatHeaderHistoryIconColorArgb,
            chatHeaderPipIconColorArgb,
        ).forEach { argb -> require(argb in 0..0xFFFFFFFFL) { "Theme chrome color must be ARGB." } }
    }
}

@Serializable
internal data class ThemeTypographyV2(
    val family: ThemeSystemFontFamilyV2 = ThemeSystemFontFamilyV2.DEFAULT,
    val displayScale: Float = 1f,
    val titleScale: Float = 1f,
    val bodyScale: Float = 1f,
    val labelScale: Float = 1f,
    val letterSpacingEm: Float = 0f,
) {
    init {
        listOf(displayScale, titleScale, bodyScale, labelScale).forEach { scale ->
            require(scale in 0.5f..2f) { "Theme typography scales must be within [0.5, 2.0]." }
        }
        require(letterSpacingEm in -0.08f..0.2f) {
            "Theme typography letter spacing must be within [-0.08, 0.2]."
        }
    }
}

@Serializable
internal data class ThemeShapesV2(
    val extraSmallDp: Float,
    val smallDp: Float,
    val mediumDp: Float,
    val largeDp: Float,
    val extraLargeDp: Float,
) {
    init {
        listOf(extraSmallDp, smallDp, mediumDp, largeDp, extraLargeDp).forEach { radius ->
            require(radius in 0f..96f) { "Theme shape radii must be within [0, 96] dp." }
        }
    }
}

/** Full token-backed Material projection. Themes own every role used by native Material widgets. */
@Serializable
internal data class ThemeMaterialColorSchemeV2(
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val inversePrimary: String,
    val secondary: String,
    val onSecondary: String,
    val secondaryContainer: String,
    val onSecondaryContainer: String,
    val tertiary: String,
    val onTertiary: String,
    val tertiaryContainer: String,
    val onTertiaryContainer: String,
    val background: String,
    val onBackground: String,
    val surface: String,
    val onSurface: String,
    val surfaceVariant: String,
    val onSurfaceVariant: String,
    val surfaceTint: String,
    val inverseSurface: String,
    val inverseOnSurface: String,
    val error: String,
    val onError: String,
    val errorContainer: String,
    val onErrorContainer: String,
    val outline: String,
    val outlineVariant: String,
    val scrim: String,
    val surfaceBright: String,
    val surfaceDim: String,
    val surfaceContainerLowest: String,
    val surfaceContainerLow: String,
    val surfaceContainer: String,
    val surfaceContainerHigh: String,
    val surfaceContainerHighest: String,
) {
    fun tokenIds(): Set<String> =
        setOf(
            primary,
            onPrimary,
            primaryContainer,
            onPrimaryContainer,
            inversePrimary,
            secondary,
            onSecondary,
            secondaryContainer,
            onSecondaryContainer,
            tertiary,
            onTertiary,
            tertiaryContainer,
            onTertiaryContainer,
            background,
            onBackground,
            surface,
            onSurface,
            surfaceVariant,
            onSurfaceVariant,
            surfaceTint,
            inverseSurface,
            inverseOnSurface,
            error,
            onError,
            errorContainer,
            onErrorContainer,
            outline,
            outlineVariant,
            scrim,
            surfaceBright,
            surfaceDim,
            surfaceContainerLowest,
            surfaceContainerLow,
            surfaceContainer,
            surfaceContainerHigh,
            surfaceContainerHighest,
        ).also { ids -> ids.forEach(::ThemeSceneTokenIdV1) }

    companion object {
        /** 单 token 填充全部角色；仅用于测试与工具，真实主题必须给出完整角色表。 */
        fun uniform(token: String): ThemeMaterialColorSchemeV2 =
            ThemeMaterialColorSchemeV2(
                primary = token,
                onPrimary = token,
                primaryContainer = token,
                onPrimaryContainer = token,
                inversePrimary = token,
                secondary = token,
                onSecondary = token,
                secondaryContainer = token,
                onSecondaryContainer = token,
                tertiary = token,
                onTertiary = token,
                tertiaryContainer = token,
                onTertiaryContainer = token,
                background = token,
                onBackground = token,
                surface = token,
                onSurface = token,
                surfaceVariant = token,
                onSurfaceVariant = token,
                surfaceTint = token,
                inverseSurface = token,
                inverseOnSurface = token,
                error = token,
                onError = token,
                errorContainer = token,
                onErrorContainer = token,
                outline = token,
                outlineVariant = token,
                scrim = token,
                surfaceBright = token,
                surfaceDim = token,
                surfaceContainerLowest = token,
                surfaceContainerLow = token,
                surfaceContainer = token,
                surfaceContainerHigh = token,
                surfaceContainerHighest = token,
            )
    }
}

/** Semantic roles adjusted by a user-selected accent palette; surfaces remain package-owned. */
internal fun ThemeMaterialColorSchemeV2.accentTokenIds(): List<String> =
    listOf(
        primary,
        onPrimary,
        primaryContainer,
        onPrimaryContainer,
        inversePrimary,
        secondary,
        onSecondary,
        secondaryContainer,
        onSecondaryContainer,
        tertiary,
        onTertiary,
        tertiaryContainer,
        onTertiaryContainer,
        surfaceTint,
        outline,
        outlineVariant,
    )

@Serializable
internal data class ThemeMaterialProjectionV2(
    val colors: ThemeMaterialColorSchemeV2,
    val typography: ThemeTypographyV2,
    val shapes: ThemeShapesV2,
)

@Serializable
internal data class ThemeComponentInsetsV2(
    val startDp: Float = 0f,
    val topDp: Float = 0f,
    val endDp: Float = 0f,
    val bottomDp: Float = 0f,
) {
    init {
        listOf(startDp, topDp, endDp, bottomDp).forEach { value ->
            require(value in 0f..96f) { "Theme component insets must be within [0, 96] dp." }
        }
    }
}

@Serializable
internal data class ThemeComponentFrameStrokeV2(
    val token: String,
    val widthDp: Float,
) {
    init {
        ThemeSceneTokenIdV1(token)
        require(widthDp in 0.25f..16f) { "Theme component frame stroke width must be within [0.25, 16] dp." }
    }
}

/** Explicit geometry owned by a component skin; no implicit outline or corner treatment remains. */
@Serializable
internal sealed interface ThemeComponentFrameSpecV2 {
    @Serializable
    @SerialName("none")
    data object None : ThemeComponentFrameSpecV2

    @Serializable
    @SerialName("round_rect")
    data class RoundRect(
        val cornerRadiusDp: Float,
        val border: ThemeComponentFrameStrokeV2? = null,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cornerRadiusDp in 0f..96f) { "Round-rect frame radius must be within [0, 96] dp." }
        }
    }

    @Serializable
    @SerialName("cut_corners")
    data class CutCorners(
        val cutSizeDp: Float,
        val border: ThemeComponentFrameStrokeV2,
        val accent: ThemeComponentFrameStrokeV2? = null,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cutSizeDp in 0.5f..48f) { "Cut-corner frame size must be within [0.5, 48] dp." }
        }
    }

    @Serializable
    @SerialName("hud_notched")
    data class HudNotched(
        val cutSizeDp: Float,
        val notchWidthFraction: Float,
        val notchDepthDp: Float,
        val border: ThemeComponentFrameStrokeV2,
        val accent: ThemeComponentFrameStrokeV2? = null,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cutSizeDp in 0.5f..48f) { "HUD frame cut size must be within [0.5, 48] dp." }
            require(notchWidthFraction in 0.1f..0.7f) {
                "HUD frame notch width fraction must be within [0.1, 0.7]."
            }
            require(notchDepthDp in 0.5f..48f) { "HUD frame notch depth must be within [0.5, 48] dp." }
        }
    }

    @Serializable
    @SerialName("corner_brackets")
    data class CornerBrackets(
        val cornerCutDp: Float,
        val bracketLengthDp: Float,
        val border: ThemeComponentFrameStrokeV2,
        val accent: ThemeComponentFrameStrokeV2? = null,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cornerCutDp in 0f..48f) { "Bracket frame corner cut must be within [0, 48] dp." }
            require(bracketLengthDp in 4f..96f) {
                "Bracket frame length must be within [4, 96] dp."
            }
        }
    }

    @Serializable
    @SerialName("segmented_rail")
    data class SegmentedRail(
        val cornerCutDp: Float,
        val railInsetDp: Float,
        val segmentLengthDp: Float,
        val border: ThemeComponentFrameStrokeV2,
        val accent: ThemeComponentFrameStrokeV2,
    ) : ThemeComponentFrameSpecV2 {
        init {
            require(cornerCutDp in 0f..48f) { "Rail frame corner cut must be within [0, 48] dp." }
            require(railInsetDp in 0f..48f) { "Rail frame inset must be within [0, 48] dp." }
            require(segmentLengthDp in 4f..160f) {
                "Rail frame segment length must be within [4, 160] dp."
            }
        }
    }
}

internal fun ThemeComponentFrameSpecV2.strokes(): List<ThemeComponentFrameStrokeV2> =
    when (this) {
        ThemeComponentFrameSpecV2.None -> emptyList()
        is ThemeComponentFrameSpecV2.RoundRect -> listOfNotNull(border)
        is ThemeComponentFrameSpecV2.CutCorners -> listOfNotNull(border, accent)
        is ThemeComponentFrameSpecV2.HudNotched -> listOfNotNull(border, accent)
        is ThemeComponentFrameSpecV2.CornerBrackets -> listOfNotNull(border, accent)
        is ThemeComponentFrameSpecV2.SegmentedRail -> listOf(border, accent)
    }

@Serializable
internal data class ThemeComponentStateSkinV2(
    val containerToken: String,
    val contentToken: String,
    val frame: ThemeComponentFrameSpecV2,
    val elevationDp: Float = 0f,
    val contentPadding: ThemeComponentInsetsV2 = ThemeComponentInsetsV2(),
) {
    init {
        ThemeSceneTokenIdV1(containerToken)
        ThemeSceneTokenIdV1(contentToken)
        require(elevationDp in 0f..48f) { "Theme component elevation must be within [0, 48] dp." }
    }
}

@Serializable
internal data class ThemeComponentSkinV2(
    val normal: ThemeComponentStateSkinV2,
    val disabled: ThemeComponentStateSkinV2? = null,
    val selected: ThemeComponentStateSkinV2? = null,
    val focused: ThemeComponentStateSkinV2? = null,
    val error: ThemeComponentStateSkinV2? = null,
)

@Serializable
internal enum class ThemeSurfaceImplementationKindV2 {
    SCENE,
    TEMPLATE,
    HOST_SHELL,
}

@Serializable
internal data class ThemeSurfaceImplementationV2(
    val surfaceId: String,
    val kind: ThemeSurfaceImplementationKindV2,
    val sceneId: String? = null,
) {
    init {
        ThemeSurfaceIdV2(surfaceId)
        when (kind) {
            ThemeSurfaceImplementationKindV2.SCENE -> {
                require(!sceneId.isNullOrBlank()) { "A scene surface must declare its scene ID." }
                ThemeSceneIdV1(requireNotNull(sceneId))
            }

            ThemeSurfaceImplementationKindV2.TEMPLATE,
            ThemeSurfaceImplementationKindV2.HOST_SHELL,
            -> require(sceneId == null) { "Only scene surfaces may declare a scene ID." }
        }
    }
}

/** A child package may explicitly override only the presentation it owns; its base supplies the rest. */
@Serializable
internal data class ThemePackagePresentationPatchV2(
    val material: ThemeMaterialProjectionV2? = null,
    val componentSkins: Map<String, ThemeComponentSkinV2> = emptyMap(),
    val behavior: ThemePackagePresentationBehaviorV2,
) {
    init {
        componentSkins.keys.forEach(::ThemeComponentIdV2)
    }
}

/** Root document of one V2 `.otheme` archive, parsed strictly with unknown keys rejected. */
@Serializable
internal data class ThemePackageManifestV2(
    val schemaVersion: Int,
    val packageId: String,
    val version: String,
    val displayName: ThemePackageLocalizedTextV2,
    val author: ThemePackageLocalizedTextV2? = null,
    val description: ThemePackageLocalizedTextV2? = null,
    val attribution: ThemePackageAttributionV2? = null,
    val basis: ThemePackageCoordinateV2? = null,
    val variants: List<ThemePackageVariantV2> = emptyList(),
    val parameters: List<ThemeParameterDefinitionV2> = emptyList(),
    val assets: List<ThemePackageAssetEntryV2> = emptyList(),
    val tokens: ThemeSceneTokenSetV1 = ThemeSceneTokenSetV1(),
    val scenes: List<ThemeSceneDefinitionV1> = emptyList(),
    val surfaces: List<ThemeSurfaceImplementationV2> = emptyList(),
    val presentation: ThemePackagePresentationPatchV2,
) {
    init {
        require(schemaVersion == THEME_PACKAGE_SCHEMA_VERSION) {
            "Theme package schema version must be $THEME_PACKAGE_SCHEMA_VERSION."
        }
        ThemePackageIdV2(packageId)
        ThemePackageVersionV2(version)
        require(assets.map { it.key }.distinct().size == assets.size) { "Theme asset keys must be unique." }
        require(assets.map { it.path }.distinct().size == assets.size) { "Theme asset paths must be unique." }
        require(parameters.map { it.id }.distinct().size == parameters.size) { "Theme parameter IDs must be unique." }
        require(variants.map { it.id }.distinct().size == variants.size) { "Theme variant IDs must be unique." }
        require(scenes.map { it.sceneId }.distinct().size == scenes.size) { "Theme scene IDs must be unique." }
        require(surfaces.map { it.surfaceId }.distinct().size == surfaces.size) {
            "Theme surface implementations must be unique."
        }
        basis?.let { base ->
            require(base.packageId.value != packageId) { "Theme package cannot use itself as its basis." }
        }
        surfaces.filter { it.kind == ThemeSurfaceImplementationKindV2.SCENE }.forEach { surface ->
            require(scenes.any { scene -> scene.sceneId.value == surface.sceneId }) {
                "Theme surface ${surface.surfaceId} references a missing scene ${surface.sceneId}."
            }
        }
        validateParameterConditions(parameters)
    }

    fun coordinateFor(archiveSha256: ThemeArchiveSha256V2): ThemePackageCoordinateV2 =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2(packageId),
            version = ThemePackageVersionV2(version),
            archiveSha256 = archiveSha256,
        )

    fun parameterDefinition(id: String): ThemeParameterDefinitionV2? =
        parameters.firstOrNull { it.id == id }
}

private fun validateParameterConditions(parameters: List<ThemeParameterDefinitionV2>) {
    val definitions = parameters.associateBy { definition -> definition.id }
    parameters.forEach { definition ->
        definition.visibleWhen.forEach { condition ->
            val dependency = definitions[condition.parameterId]
                ?: throw IllegalArgumentException(
                    "Theme parameter ${definition.id} depends on unknown parameter ${condition.parameterId}.",
                )
            when (condition) {
                is ThemeParameterConditionV2.BooleanEquals ->
                    require(dependency.type == ThemeParameterTypeV2.BOOLEAN) {
                        "Theme parameter ${definition.id} has a boolean condition on non-boolean ${dependency.id}."
                    }

                is ThemeParameterConditionV2.OptionEquals -> {
                    require(dependency.type == ThemeParameterTypeV2.OPTION) {
                        "Theme parameter ${definition.id} has an option condition on non-option ${dependency.id}."
                    }
                    val choice = dependency.control as? ThemeParameterControlV2.Choice
                    require(choice?.options?.any { option -> option.id == condition.expected } == true) {
                        "Theme parameter ${definition.id} depends on unknown option ${condition.expected}."
                    }
                }

                is ThemeParameterConditionV2.ResourcePresent ->
                    require(dependency.type.isUri()) {
                        "Theme parameter ${definition.id} has a resource condition on non-resource ${dependency.id}."
                    }
            }
        }
    }
}

internal fun ThemeParameterValueV2.matches(type: ThemeParameterTypeV2): Boolean =
    when (type) {
        ThemeParameterTypeV2.COLOR -> this is ThemeParameterValueV2.ColorValue
        ThemeParameterTypeV2.COLOR_PAIR -> this is ThemeParameterValueV2.ColorPairValue
        ThemeParameterTypeV2.BOOLEAN -> this is ThemeParameterValueV2.BooleanValue
        ThemeParameterTypeV2.OPTION -> this is ThemeParameterValueV2.OptionValue
        ThemeParameterTypeV2.FLOAT -> this is ThemeParameterValueV2.FloatValue
        ThemeParameterTypeV2.IMAGE_URI -> this is ThemeParameterValueV2.ImageUriValue
        ThemeParameterTypeV2.VIDEO_URI -> this is ThemeParameterValueV2.VideoUriValue
        ThemeParameterTypeV2.FONT_URI -> this is ThemeParameterValueV2.FontUriValue
        ThemeParameterTypeV2.IMAGE_LAYOUT -> this is ThemeParameterValueV2.ImageLayoutValue
        ThemeParameterTypeV2.INSETS -> this is ThemeParameterValueV2.InsetsValue
        ThemeParameterTypeV2.CORNER_RADIUS -> this is ThemeParameterValueV2.CornerRadiusValue
    }

internal fun ThemeParameterTypeV2.isUri(): Boolean =
    this == ThemeParameterTypeV2.IMAGE_URI ||
        this == ThemeParameterTypeV2.VIDEO_URI ||
        this == ThemeParameterTypeV2.FONT_URI

internal fun ThemeParameterControlV2.matches(type: ThemeParameterTypeV2): Boolean =
    when (this) {
        is ThemeParameterControlV2.ColorPalette -> type == ThemeParameterTypeV2.COLOR
        is ThemeParameterControlV2.ColorPairPalette -> type == ThemeParameterTypeV2.COLOR_PAIR
        ThemeParameterControlV2.Toggle -> type == ThemeParameterTypeV2.BOOLEAN
        is ThemeParameterControlV2.Choice -> type == ThemeParameterTypeV2.OPTION
        is ThemeParameterControlV2.Slider -> type == ThemeParameterTypeV2.FLOAT
        is ThemeParameterControlV2.ImagePicker -> type == ThemeParameterTypeV2.IMAGE_URI
        is ThemeParameterControlV2.VideoPicker -> type == ThemeParameterTypeV2.VIDEO_URI
        is ThemeParameterControlV2.FontPicker -> type == ThemeParameterTypeV2.FONT_URI
        ThemeParameterControlV2.AuthorValue -> true
    }

internal fun ThemeParameterControlV2.supportsUserSettingsSurface(): Boolean =
    this is ThemeParameterControlV2.ColorPalette ||
        this is ThemeParameterControlV2.Toggle ||
        this is ThemeParameterControlV2.Choice ||
        this is ThemeParameterControlV2.Slider ||
        this is ThemeParameterControlV2.ImagePicker ||
        this is ThemeParameterControlV2.VideoPicker ||
        this is ThemeParameterControlV2.FontPicker

internal fun ThemeParameterEffectV2.matches(type: ThemeParameterTypeV2): Boolean =
    when (this) {
        ThemeParameterEffectV2.AccentPalette,
        is ThemeParameterEffectV2.TokenColor -> type == ThemeParameterTypeV2.COLOR

        is ThemeParameterEffectV2.TokenColorPair -> type == ThemeParameterTypeV2.COLOR_PAIR
        is ThemeParameterEffectV2.StageImage -> type == ThemeParameterTypeV2.IMAGE_URI
        ThemeParameterEffectV2.TypographyScale,
        ThemeParameterEffectV2.ShapeScale,
        is ThemeParameterEffectV2.ComponentFrameScale -> type == ThemeParameterTypeV2.FLOAT

        is ThemeParameterEffectV2.ComponentContentInsets -> type == ThemeParameterTypeV2.INSETS
        is ThemeParameterEffectV2.Presentation -> targets.all { target -> target.valueType == type }
    }
