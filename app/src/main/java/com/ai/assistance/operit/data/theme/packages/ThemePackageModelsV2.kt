package com.ai.assistance.operit.data.theme.packages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val PACKAGE_ID_PATTERN_V2 = Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+$")
private val MEMBER_ID_PATTERN_V2 = Regex("^[a-z][a-z0-9_]*$")
private val SEMVER_PATTERN_V2 =
    Regex(
        "^\\d+\\.\\d+\\.\\d+" +
            "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
    )
private val SHA256_PATTERN_V2 = Regex("^[0-9a-f]{64}$")

@Serializable
@JvmInline
internal value class ThemePackageIdV2(val value: String) {
    init {
        require(PACKAGE_ID_PATTERN_V2.matches(value)) { "Invalid theme package ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemePackageVersionV2(val value: String) {
    init {
        require(SEMVER_PATTERN_V2.matches(value)) { "Invalid theme package version: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeArchiveSha256V2(val value: String) {
    init {
        require(SHA256_PATTERN_V2.matches(value)) { "Invalid theme archive digest: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeVariantIdV2(val value: String) {
    init {
        require(MEMBER_ID_PATTERN_V2.matches(value)) { "Invalid theme variant ID: $value" }
    }
}

@Serializable
@JvmInline
internal value class ThemeParameterIdV2(val value: String) {
    init {
        require(MEMBER_ID_PATTERN_V2.matches(value)) { "Invalid theme parameter ID: $value" }
    }
}

/** Identifies one immutable installed V2 package by its exact release archive. */
@Serializable
internal data class ThemePackageCoordinateV2(
    val packageId: ThemePackageIdV2,
    val version: ThemePackageVersionV2,
    val archiveSha256: ThemeArchiveSha256V2,
)

@Serializable
internal data class ThemePackageReferenceV2(
    val coordinate: ThemePackageCoordinateV2,
)

@Serializable
internal sealed interface ThemeParameterValueV2 {
    @Serializable
    @SerialName("color")
    data class ColorValue(val argb: Long) : ThemeParameterValueV2 {
        init {
            require(argb in 0..0xFFFFFFFFL) { "Color value must be ARGB within 0..0xffffffff." }
        }
    }

    @Serializable
    @SerialName("color_pair")
    data class ColorPairValue(
        val lightArgb: Long,
        val darkArgb: Long,
    ) : ThemeParameterValueV2 {
        init {
            require(lightArgb in 0..0xFFFFFFFFL) { "Theme light color must be ARGB within 0..0xffffffff." }
            require(darkArgb in 0..0xFFFFFFFFL) { "Theme dark color must be ARGB within 0..0xffffffff." }
        }
    }

    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : ThemeParameterValueV2

    @Serializable
    @SerialName("option")
    data class OptionValue(val value: String) : ThemeParameterValueV2 {
        init {
            require(MEMBER_ID_PATTERN_V2.matches(value)) { "Theme option value must be a member ID: $value" }
        }
    }

    @Serializable
    @SerialName("float")
    data class FloatValue(val value: Float) : ThemeParameterValueV2 {
        init {
            require(value.isFinite()) { "Theme numeric value must be finite." }
        }
    }

    @Serializable
    @SerialName("image_uri")
    data class ImageUriValue(val uri: String) : ThemeParameterValueV2 {
        init {
            require(uri.startsWith("content://")) { "Theme image URIs must use content://." }
        }
    }

    @Serializable
    @SerialName("video_uri")
    data class VideoUriValue(val uri: String) : ThemeParameterValueV2 {
        init {
            require(uri.startsWith("content://")) { "Theme video URIs must use content://." }
        }
    }

    @Serializable
    @SerialName("font_uri")
    data class FontUriValue(val uri: String) : ThemeParameterValueV2 {
        init {
            require(uri.startsWith("content://")) { "Theme font URIs must use content://." }
        }
    }

    @Serializable
    @SerialName("image_layout")
    data class ImageLayoutValue(
        val cropLeft: Float = 0f,
        val cropTop: Float = 0f,
        val cropRight: Float = 1f,
        val cropBottom: Float = 1f,
        val repeatStart: Float = 0f,
        val repeatEnd: Float = 1f,
        val repeatYStart: Float = 0f,
        val repeatYEnd: Float = 1f,
        val scale: Float = 1f,
    ) : ThemeParameterValueV2 {
        init {
            require(cropLeft in 0f..1f && cropTop in 0f..1f) {
                "Theme image crop start must be within [0, 1]."
            }
            require(cropRight in cropLeft..1f && cropBottom in cropTop..1f) {
                "Theme image crop end must follow its start within [0, 1]."
            }
            require(repeatStart in 0f..1f && repeatEnd in repeatStart..1f) {
                "Theme image horizontal repeat range must be within [0, 1]."
            }
            require(repeatYStart in 0f..1f && repeatYEnd in repeatYStart..1f) {
                "Theme image vertical repeat range must be within [0, 1]."
            }
            require(scale in 0.1f..8f) { "Theme image scale must be within [0.1, 8]." }
        }
    }

    @Serializable
    @SerialName("insets")
    data class InsetsValue(
        val startDp: Float,
        val topDp: Float,
        val endDp: Float,
        val bottomDp: Float,
    ) : ThemeParameterValueV2 {
        init {
            listOf(startDp, topDp, endDp, bottomDp).forEach { value ->
                require(value in 0f..96f) { "Theme parameter inset must be within [0, 96] dp." }
            }
        }
    }

    @Serializable
    @SerialName("corner_radius")
    data class CornerRadiusValue(val valueDp: Float) : ThemeParameterValueV2 {
        init {
            require(valueDp in 0f..96f) { "Theme corner radius must be within [0, 96] dp." }
        }
    }
}

/** Application-level selection of one globally linked V2 theme package. */
@Serializable
internal data class ThemeInstanceV2(
    val reference: ThemePackageReferenceV2,
    val variantId: ThemeVariantIdV2? = null,
    val parameterValues: Map<String, ThemeParameterValueV2> = emptyMap(),
) {
    init {
        require(parameterValues.keys.all(MEMBER_ID_PATTERN_V2::matches)) {
            "Theme instance parameter keys must be valid parameter IDs: ${parameterValues.keys}"
        }
    }

    companion object {
        fun defaultBundled(): ThemeInstanceV2 =
            ThemeInstanceV2(reference = ThemePackageReferenceV2(ThemePackageDefaultV2.coordinate))
    }
}
