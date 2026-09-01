package com.ai.assistance.operit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.theme.packages.LinkedThemeRuntimeV2
import com.ai.assistance.operit.data.theme.packages.ResolvedThemeParametersV2
import com.ai.assistance.operit.data.theme.packages.ThemeArchiveSha256V2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentFrameSpecV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentFrameStrokeV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentStateSkinV2
import com.ai.assistance.operit.data.theme.packages.ThemeMaterialColorSchemeV2
import com.ai.assistance.operit.data.theme.packages.ThemeMaterialProjectionV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageCoordinateV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageIdV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageVersionV2
import com.ai.assistance.operit.data.theme.packages.ThemeShapesV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceImplementationKindV2
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceImplementationV2
import com.ai.assistance.operit.data.theme.packages.ThemeTypographyV2
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1

internal fun themePackageRuntimeForAndroidTest(
    userFontScale: Float = 1f,
): ThemePackageUiRuntimeV2 {
    val container = Color(0xFF102030)
    val content = Color(0xFFE5F6FF)
    val selected = Color(0xFF16435A)
    val disabled = Color(0xFF24313B)
    val error = Color(0xFF5D1C2B)
    val statusErrorContent = Color(0xFFFFF0C7)
    val statusErrorBorder = Color(0xFFFF4D6D)
    val overlayDialogContainer = Color(0xFF3E2D56)
    val overlayDialogContent = Color(0xFFFFF0C7)
    val overlayDialogBorder = Color(0xFF00E5FF)
    val inputNormalBorder = Color(0xFF617589)
    val inputFocusedBorder = Color(0xFF00E5FF)
    val inputErrorBorder = Color(0xFFFF4D6D)
    val tokens =
        ThemeSceneTokenSetV1(
            tokens =
                mapOf(
                    "test.container" to colorToken(container),
                    "test.content" to colorToken(content),
                    "test.selected" to colorToken(selected),
                    "test.disabled" to colorToken(disabled),
                    "test.error" to colorToken(error),
                    "test.status_error_content" to colorToken(statusErrorContent),
                    "test.status_error_border" to colorToken(statusErrorBorder),
                    "test.overlay_dialog_container" to colorToken(overlayDialogContainer),
                    "test.overlay_dialog_content" to colorToken(overlayDialogContent),
                    "test.overlay_dialog_border" to colorToken(overlayDialogBorder),
                    "test.input_normal_border" to colorToken(inputNormalBorder),
                    "test.input_focused_border" to colorToken(inputFocusedBorder),
                    "test.input_error_border" to colorToken(inputErrorBorder),
                ),
        )
    val coordinate =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2("test.android_theme"),
            version = ThemePackageVersionV2("1.0.0"),
            archiveSha256 = ThemeArchiveSha256V2("ab".repeat(32)),
        )
    val normal = componentState("test.container", "test.content")
    val componentSkins =
        ThemeComponentCatalogV2.requiredComponents
            .associateWith {
                ThemeComponentSkinV2(
                    normal = normal,
                    disabled = componentState("test.disabled", "test.content"),
                    selected = componentState("test.selected", "test.content"),
                    focused = componentState("test.selected", "test.content"),
                    error = componentState("test.error", "test.content"),
                )
            } +
            (
                ThemeComponentCatalogV2.DIALOG to
                    ThemeComponentSkinV2(
                        normal =
                            componentState(
                                containerToken = "test.overlay_dialog_container",
                                contentToken = "test.overlay_dialog_content",
                                frame =
                                    ThemeComponentFrameSpecV2.RoundRect(
                                        cornerRadiusDp = 0f,
                                        border =
                                            ThemeComponentFrameStrokeV2(
                                                token = "test.overlay_dialog_border",
                                                widthDp = 4f,
                                            ),
                                    ),
                            ),
                    )
            ) +
            (
                ThemeComponentCatalogV2.INPUT to
                    ThemeComponentSkinV2(
                        normal =
                            componentState(
                                containerToken = "test.container",
                                contentToken = "test.content",
                                frame =
                                    ThemeComponentFrameSpecV2.RoundRect(
                                        cornerRadiusDp = 0f,
                                        border =
                                            ThemeComponentFrameStrokeV2(
                                                token = "test.input_normal_border",
                                                widthDp = 4f,
                                            ),
                                    ),
                            ),
                        disabled =
                            componentState(
                                containerToken = "test.disabled",
                                contentToken = "test.content",
                                frame = ThemeComponentFrameSpecV2.RoundRect(cornerRadiusDp = 0f),
                            ),
                        focused =
                            componentState(
                                containerToken = "test.selected",
                                contentToken = "test.content",
                                frame =
                                    ThemeComponentFrameSpecV2.RoundRect(
                                        cornerRadiusDp = 0f,
                                        border =
                                            ThemeComponentFrameStrokeV2(
                                                token = "test.input_focused_border",
                                                widthDp = 4f,
                                            ),
                                    ),
                            ),
                        error =
                            componentState(
                                containerToken = "test.error",
                                contentToken = "test.content",
                                frame =
                                    ThemeComponentFrameSpecV2.RoundRect(
                                        cornerRadiusDp = 0f,
                                        border =
                                            ThemeComponentFrameStrokeV2(
                                                token = "test.input_error_border",
                                                widthDp = 4f,
                                            ),
                                    ),
                            ),
                    )
            ) +
            (
                ThemeComponentCatalogV2.STATUS to
                    ThemeComponentSkinV2(
                        normal = componentState("test.container", "test.content"),
                        error =
                            componentState(
                                containerToken = "test.error",
                                contentToken = "test.status_error_content",
                                frame =
                                    ThemeComponentFrameSpecV2.RoundRect(
                                        cornerRadiusDp = 0f,
                                        border =
                                            ThemeComponentFrameStrokeV2(
                                                token = "test.status_error_border",
                                                widthDp = 2f,
                                            ),
                                    ),
                            ),
                    )
            )
    val linked =
        LinkedThemeRuntimeV2(
            coordinate = coordinate,
            packageChain = listOf(coordinate),
            material =
                ThemeMaterialProjectionV2(
                    colors = ThemeMaterialColorSchemeV2.uniform("test.container"),
                    typography = ThemeTypographyV2(),
                    shapes = ThemeShapesV2(2f, 4f, 8f, 16f, 28f),
                ),
            componentSkins = componentSkins,
            surfaces =
                mapOf(
                    ThemeSurfaceCatalogV2.OVERLAY_DIALOG to
                        ThemeSurfaceImplementationV2(
                            surfaceId = ThemeSurfaceCatalogV2.OVERLAY_DIALOG.value,
                            kind = ThemeSurfaceImplementationKindV2.TEMPLATE,
                        ),
                    ThemeSurfaceCatalogV2.OVERLAY_SHEET to
                        ThemeSurfaceImplementationV2(
                            surfaceId = ThemeSurfaceCatalogV2.OVERLAY_SHEET.value,
                            kind = ThemeSurfaceImplementationKindV2.TEMPLATE,
                        ),
                    ThemeSurfaceCatalogV2.OVERLAY_MENU to
                        ThemeSurfaceImplementationV2(
                            surfaceId = ThemeSurfaceCatalogV2.OVERLAY_MENU.value,
                            kind = ThemeSurfaceImplementationKindV2.TEMPLATE,
                        ),
                    ThemeSurfaceCatalogV2.OVERLAY_SNACKBAR to
                        ThemeSurfaceImplementationV2(
                            surfaceId = ThemeSurfaceCatalogV2.OVERLAY_SNACKBAR.value,
                            kind = ThemeSurfaceImplementationKindV2.TEMPLATE,
                        ),
                    ThemeSurfaceCatalogV2.OVERLAY_TOAST to
                        ThemeSurfaceImplementationV2(
                            surfaceId = ThemeSurfaceCatalogV2.OVERLAY_TOAST.value,
                            kind = ThemeSurfaceImplementationKindV2.TEMPLATE,
                        ),
                ),
            tokens = tokens,
            scenes = emptyMap(),
            assets = emptyMap(),
            parameterDefinitions = emptyMap(),
        )
    return createThemePackageUiRuntimeV2(
        linked = linked,
        parameters = ResolvedThemeParametersV2(emptyMap()),
        darkTheme = true,
        userFontScale = userFontScale,
    )
}

private fun componentState(
    containerToken: String,
    contentToken: String,
    frame: ThemeComponentFrameSpecV2 = ThemeComponentFrameSpecV2.RoundRect(cornerRadiusDp = 0f),
): ThemeComponentStateSkinV2 =
    ThemeComponentStateSkinV2(
        containerToken = containerToken,
        contentToken = contentToken,
        frame = frame,
    )

private fun colorToken(color: Color): ThemeSceneTokenValueV1.ColorToken =
    ThemeSceneTokenValueV1.ColorToken(
        lightArgb = color.toArgb().toLong() and 0xFFFFFFFFL,
        darkArgb = color.toArgb().toLong() and 0xFFFFFFFFL,
    )
