import type { CSSProperties } from 'react';
import type { WebBubbleImageTheme, WebFontTheme, WebThemeSnapshot } from './chatTypes';

type ThemeStyle = CSSProperties & Record<string, string | number>;

// 默认值对齐 app ThemePreferenceSnapshot.defaultVisual 与 M3 baseline scheme。
// 快照字段缺失时用这里的显式默认值渲染，与真机行为一致。
const M3_BASELINE = {
  tertiaryDark: '#efb8c8',
  tertiaryLight: '#7d5260',
  errorDark: '#f2b8b5',
  errorLight: '#b3261e',
  errorContainerDark: '#8c1d18',
  errorContainerLight: '#f9dedc',
  onErrorContainerDark: '#f9dedc',
  onErrorContainerLight: '#410e0b'
} as const;

const BACKGROUND_BLUR_RADIUS_DEFAULT = 10;
const BUBBLE_IMAGE_DEFAULTS = {
  crop_left: 0,
  crop_top: 0,
  crop_right: 0,
  crop_bottom: 0,
  repeat_start: 0.35,
  repeat_end: 0.65,
  repeat_y_start: 0.35,
  repeat_y_end: 0.65,
  image_scale: 1
} as const;

function fallbackColor(value: string | null | undefined, fallback: string) {
  return value ?? fallback;
}

function clampUnit(value: number | undefined) {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return 0;
  }
  return Math.max(0, Math.min(1, value));
}

// 气泡级字体解析与全局字体走同一规则；"default" 是 app 的系统字体默认值，
// 直接拼进 CSS 字体栈无效，跳过（A12 对齐 Type.kt 的默认分支）
function resolveFontFamilyFromFont(font: WebFontTheme | undefined, customFontFaceName: string) {
  if (!font) {
    return null;
  }
  if (font.custom_font_asset_url) {
    return `"${customFontFaceName}", "PingFang SC", "Microsoft YaHei", sans-serif`;
  }
  const systemName = font.system_font_name?.trim();
  if (systemName && systemName !== 'default') {
    return `"${systemName}", "PingFang SC", "Microsoft YaHei", sans-serif`;
  }
  return null;
}

function resolveFontFamily(theme: WebThemeSnapshot) {
  return (
    resolveFontFamilyFromFont(theme.font, 'OperitThemeFont') ??
    '"PingFang SC", "Microsoft YaHei", sans-serif'
  );
}

function clampOpacity(value: number | undefined) {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return 0.2;
  }
  return Math.max(0, Math.min(1, value));
}

function toRgba(color: string | null | undefined, alpha?: number) {
  if (!color) {
    return null;
  }

  const normalized = color.trim();
  if (normalized.startsWith('#')) {
    const hex = normalized.slice(1);
    const size = hex.length === 3 ? 1 : 2;
    if (hex.length !== 3 && hex.length !== 6) {
      return normalized;
    }

    const read = (index: number) => {
      const chunk = hex.slice(index * size, index * size + size);
      const expanded = size === 1 ? chunk + chunk : chunk;
      return Number.parseInt(expanded, 16);
    };

    const red = read(0);
    const green = read(1);
    const blue = read(2);
    const resolvedAlpha = alpha ?? 1;
    return `rgba(${red}, ${green}, ${blue}, ${resolvedAlpha.toFixed(3)})`;
  }

  const rgbaMatch = normalized.match(
    /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i
  );
  if (!rgbaMatch) {
    return normalized;
  }

  const red = Number.parseFloat(rgbaMatch[1]);
  const green = Number.parseFloat(rgbaMatch[2]);
  const blue = Number.parseFloat(rgbaMatch[3]);
  const baseAlpha = rgbaMatch[4] ? Number.parseFloat(rgbaMatch[4]) : 1;
  const resolvedAlpha = alpha === undefined ? baseAlpha : alpha;
  return `rgba(${red}, ${green}, ${blue}, ${resolvedAlpha.toFixed(3)})`;
}

type Rgb = { red: number; green: number; blue: number };

function parseRgb(color: string | null | undefined) {
  if (!color) {
    return null;
  }

  const normalized = color.trim();
  if (normalized.startsWith('#')) {
    const hex = normalized.slice(1);
    const size = hex.length === 3 ? 1 : 2;
    if (hex.length !== 3 && hex.length !== 6) {
      return null;
    }

    const read = (index: number) => {
      const chunk = hex.slice(index * size, index * size + size);
      const expanded = size === 1 ? chunk + chunk : chunk;
      return Number.parseInt(expanded, 16);
    };

    return {
      red: read(0),
      green: read(1),
      blue: read(2)
    };
  }

  const rgbaMatch = normalized.match(
    /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i
  );
  if (!rgbaMatch) {
    return null;
  }

  return {
    red: Number.parseFloat(rgbaMatch[1]),
    green: Number.parseFloat(rgbaMatch[2]),
    blue: Number.parseFloat(rgbaMatch[3])
  };
}

function rgbToHex(rgb: Rgb) {
  const part = (value: number) =>
    Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0');
  return `#${part(rgb.red)}${part(rgb.green)}${part(rgb.blue)}`;
}

// 对齐 ThemeColorSchemeResolver.kt：luminance 阈值 0.5（不是旧 web 的 0.58），
// 支持 onColorMode 强制黑白
function contrastingText(
  color: string | null | undefined,
  options: { onColorMode?: 'auto' | 'light' | 'dark' } = {}
) {
  const { onColorMode } = options;
  if (onColorMode === 'light') {
    return '#ffffff';
  }
  if (onColorMode === 'dark') {
    return '#000000';
  }
  const rgb = parseRgb(color);
  if (!rgb) {
    return '#ffffff';
  }
  const luminance = (0.299 * rgb.red + 0.587 * rgb.green + 0.114 * rgb.blue) / 255;
  return luminance > 0.5 ? '#000000' : '#ffffff';
}

// 对齐 ThemeColorSchemeResolver.kt 的 lighten/darken 公式
function lightenColor(color: string, factor: number) {
  const rgb = parseRgb(color);
  if (!rgb) {
    return color;
  }
  return rgbToHex({
    red: rgb.red + (255 - rgb.red) * factor,
    green: rgb.green + (255 - rgb.green) * factor,
    blue: rgb.blue + (255 - rgb.blue) * factor
  });
}

function darkenColor(color: string, factor: number) {
  const rgb = parseRgb(color);
  if (!rgb) {
    return color;
  }
  return rgbToHex({
    red: rgb.red * (1 - factor),
    green: rgb.green * (1 - factor),
    blue: rgb.blue * (1 - factor)
  });
}

// palette 背景亮度决定明暗：快照 palette 是 app 解析后的最终色板，
// 以它为准可避免 use_system_theme 下 theme_mode 与实际 scheme 反向（A19）
function isPaletteLight(theme: WebThemeSnapshot) {
  const rgb = parseRgb(theme.palette.background_color);
  if (!rgb) {
    return theme.theme_mode === 'light';
  }
  const luminance = (0.299 * rgb.red + 0.587 * rgb.green + 0.114 * rgb.blue) / 255;
  return luminance > 0.5;
}

// 气泡九宫格转 border-image：crop 是相对图片的四边裁剪线（0-1），
// tiled_nine_slice 中间平铺对应 repeat，nine_patch 中间拉伸对应 stretch（A4/A5）。
// enabled 与 asset_url 的前置判断由调用方负责
export function buildBubbleImageBorderStyle(image: WebBubbleImageTheme) {
  const cropLeft = clampUnit(image.crop_left ?? BUBBLE_IMAGE_DEFAULTS.crop_left);
  const cropTop = clampUnit(image.crop_top ?? BUBBLE_IMAGE_DEFAULTS.crop_top);
  const cropRight = clampUnit(image.crop_right ?? BUBBLE_IMAGE_DEFAULTS.crop_right);
  const cropBottom = clampUnit(image.crop_bottom ?? BUBBLE_IMAGE_DEFAULTS.crop_bottom);
  const left = Math.round(cropLeft * 100);
  const top = Math.round(cropTop * 100);
  const right = Math.round(cropRight * 100);
  const bottom = Math.round(cropBottom * 100);
  const repeat = image.render_mode === 'nine_patch' ? 'stretch' : 'repeat';
  return {
    borderImageSource: `url(${image.asset_url})`,
    borderImageSlice: `${left} ${100 - right} ${100 - bottom} ${top}%`,
    borderImageRepeat: repeat,
    borderImageOutset: '0'
  } as CSSProperties;
}

export function buildChatThemeStyle(theme: WebThemeSnapshot | null): ThemeStyle {
  if (!theme) {
    return {};
  }

  const isLight = isPaletteLight(theme);
  const onColorMode = theme.on_color_mode ?? 'auto';
  const palette = theme.palette;
  const hasBackgroundAsset = Boolean(theme.background.asset_url);
  const primary = theme.primary_color || palette.primary_color || '#8ca9ff';
  const secondary = theme.secondary_color || palette.secondary_color || '#67d4c8';
  const backgroundColor = palette.background_color || (isLight ? '#faf8ff' : '#101520');
  const surfaceColor = palette.surface_color || (isLight ? '#ffffff' : '#1b202b');
  const surfaceVariantColor =
    palette.surface_variant_color || (isLight ? '#ece8f1' : '#45464f');
  const surfaceContainerColor =
    palette.surface_container_color || surfaceColor;
  const surfaceContainerHighColor =
    palette.surface_container_high_color || surfaceContainerColor;
  const surfaceContainerHighestColor =
    palette.surface_container_highest_color || surfaceContainerHighColor;
  const outlineColor = palette.outline_color || (isLight ? '#7a7a85' : '#8f909a');
  const outlineVariantColor =
    palette.outline_variant_color || (isLight ? '#c8c4cf' : '#45464f');
  const primaryContainerColor =
    palette.primary_container_color || (toRgba(primary, isLight ? 0.24 : 0.28) ?? primary);
  const onPrimaryContainerColor =
    palette.on_primary_container_color ||
    contrastingText(primaryContainerColor, { onColorMode: isLight ? 'auto' : 'light' });
  // onPrimary/onSecondary 优先快照；缺失时对齐 app 派生（A16）
  const onPrimaryColor =
    palette.on_primary_color || contrastingText(primary, { onColorMode });
  const onSecondaryColor =
    palette.on_secondary_color || contrastingText(secondary, { onColorMode });
  // tertiary/error 缺失时用 M3 baseline（app 非自定义色时的 scheme 值，A15）
  const tertiaryColor =
    palette.tertiary_color || (isLight ? M3_BASELINE.tertiaryLight : M3_BASELINE.tertiaryDark);
  const errorColor =
    palette.error_color || (isLight ? M3_BASELINE.errorLight : M3_BASELINE.errorDark);
  const errorContainerColor =
    palette.error_container_color ||
    (isLight ? M3_BASELINE.errorContainerLight : M3_BASELINE.errorContainerDark);
  const onErrorContainerColor = isLight
    ? M3_BASELINE.onErrorContainerLight
    : M3_BASELINE.onErrorContainerDark;
  // secondaryContainer 对齐 app 自定义色派生：亮 lighten 0.7 / 暗 darken 0.3
  const secondaryContainerColor =
    palette.secondary_container_color ||
    (isLight ? lightenColor(secondary, 0.7) : darkenColor(secondary, 0.3));
  const onSecondaryContainerColor = contrastingText(secondaryContainerColor, {
    onColorMode: isLight ? 'auto' : 'light'
  });
  const cardSoft = toRgba(surfaceVariantColor, 0.3) ?? surfaceVariantColor;
  const cardStrong = toRgba(surfaceVariantColor, 0.5) ?? surfaceVariantColor;
  const cardSubtle = toRgba(surfaceVariantColor, 0.2) ?? surfaceVariantColor;
  // B21：侧栏底色对齐 app 的 surface 0.95，不再是 surfaceContainer
  const historyPanelBackground =
    (hasBackgroundAsset ? toRgba(surfaceColor, 0.95) : null) ?? surfaceColor;
  const panelSurface =
    (hasBackgroundAsset ? toRgba(surfaceColor, 0.85) : null) ?? surfaceColor;
  const panelSurfaceStrong =
    (hasBackgroundAsset ? toRgba(surfaceColor, 0.92) : null) ?? surfaceColor;
  const transparentPanelSurface = toRgba(surfaceColor, 0.72) ?? surfaceColor;
  const transparentPanelSurfaceStrong = toRgba(surfaceColor, 0.9) ?? surfaceColor;
  const shadowColor = isLight ? 'rgba(44, 58, 90, 0.14)' : 'rgba(0, 0, 0, 0.24)';
  const assistantBubble = fallbackColor(
    theme.bubble.assistant_bubble_color,
    surfaceColor
  );
  const userBubble = fallbackColor(
    theme.chat_style === 'cursor' && !theme.bubble.cursor_user_follow_theme
      ? theme.bubble.cursor_user_color
      : theme.bubble.user_bubble_color,
    primaryContainerColor
  );
  const backgroundTint =
    hasBackgroundAsset && !theme.header.transparent
      ? toRgba(backgroundColor, isLight ? 0.04 : 0.08) ?? 'transparent'
      : 'transparent';
  // A1：背景模糊对齐 app 的 Modifier.blur(radius.dp)，默认 10
  const backgroundBlurRadius = theme.background.use_blur
    ? Math.max(0, theme.background.blur_radius ?? BACKGROUND_BLUR_RADIUS_DEFAULT)
    : 0;
  // B5：agent 输入卡底色对齐 app——暗色 lerp(surface, onSurface, 0.08)、
  // 亮色 surface、有背景图 surface 0.85、transparent 为真透明
  const onSurfaceColor = palette.on_surface_color || (isLight ? '#000000' : '#ffffff');
  const agentCardBase = isLight
    ? surfaceColor
    : `color-mix(in srgb, ${surfaceColor} 92%, ${onSurfaceColor})`;
  const agentCardSurface = theme.input.transparent
    ? 'transparent'
    : hasBackgroundAsset
      ? (toRgba(agentCardBase, 0.85) ?? agentCardBase)
      : agentCardBase;
  // B6：顶部质感——暗色 inset 高光近似 topEdgeHighlight，亮色外扩散阴影近似 outerDiffuseShadow
  const agentCardShadow = isLight
    ? `0 0 0 1px ${toRgba(onSurfaceColor, 0.05) ?? 'transparent'}, 0 -6px 18px ${shadowColor}`
    : `inset 0 1px 0 ${toRgba(onSurfaceColor, 0.04) ?? 'transparent'}, inset 0 0 14px ${
        toRgba(onSurfaceColor, 0.03) ?? 'transparent'
      }, 0 -10px 22px ${shadowColor}`;

  return {
    '--chat-primary': primary,
    '--chat-secondary': secondary,
    '--chat-tertiary': tertiaryColor,
    '--chat-error': errorColor,
    '--chat-error-container': errorContainerColor,
    '--chat-on-error-container': onErrorContainerColor,
    '--chat-secondary-container': secondaryContainerColor,
    '--chat-on-secondary-container': onSecondaryContainerColor,
    '--chat-primary-container': primaryContainerColor,
    '--chat-on-primary-container': onPrimaryContainerColor,
    '--chat-on-primary': onPrimaryColor,
    '--chat-on-secondary': onSecondaryColor,
    '--chat-root-background': backgroundColor,
    '--chat-background-image': theme.background.asset_url ? `url(${theme.background.asset_url})` : 'none',
    '--chat-background-opacity': String(theme.background.asset_url ? backgroundOpacity(theme) : 0),
    '--chat-background-blur': `${backgroundBlurRadius}px`,
    '--chat-background-tint': backgroundTint,
    '--chat-surface-color': surfaceColor,
    '--chat-surface-variant-color': surfaceVariantColor,
    '--chat-surface-container-color': surfaceContainerColor,
    '--chat-surface-container-high-color': surfaceContainerHighColor,
    '--chat-surface-container-highest-color': surfaceContainerHighestColor,
    '--chat-surface-strong': surfaceColor,
    '--chat-surface-soft': toRgba(surfaceContainerHighColor, 0.94) ?? surfaceContainerHighColor,
    '--chat-surface-faint': cardSubtle,
    '--chat-card-soft': cardSoft,
    '--chat-card-strong': cardStrong,
    '--chat-history-panel-bg': historyPanelBackground,
    '--chat-history-item-bg': cardSoft,
    '--chat-history-item-active-bg': toRgba(primaryContainerColor, 0.3) ?? primaryContainerColor,
    '--chat-border': toRgba(outlineVariantColor, 0.68) ?? outlineVariantColor,
    '--chat-field-border': toRgba(outlineColor, 0.72) ?? outlineColor,
    '--chat-field-border-strong': outlineColor,
    '--chat-shadow': shadowColor,
    '--chat-text-main': onSurfaceColor,
    '--chat-text-soft': palette.on_surface_variant_color || toRgba(onSurfaceColor, 0.7) || onSurfaceColor,
    '--chat-text-muted': toRgba(onSurfaceColor, 0.7) ?? onSurfaceColor,
    '--chat-user-bubble': userBubble,
    '--chat-assistant-bubble': assistantBubble,
    '--chat-user-text': fallbackColor(
      theme.bubble.user_text_color,
      onPrimaryContainerColor
    ),
    '--chat-assistant-text': fallbackColor(
      theme.bubble.assistant_text_color,
      onSurfaceColor
    ),
    '--chat-font-scale': String(theme.font.scale || 1),
    '--chat-font-family': resolveFontFamily(theme),
    '--chat-user-font-family':
      resolveFontFamilyFromFont(theme.bubble.user_font, 'OperitThemeFontBubbleUser') ??
      resolveFontFamily(theme),
    '--chat-assistant-font-family':
      resolveFontFamilyFromFont(theme.bubble.assistant_font, 'OperitThemeFontBubbleAssistant') ??
      resolveFontFamily(theme),
    '--chat-user-radius': theme.bubble.user_rounded ? '20px' : '8px',
    '--chat-assistant-radius': theme.bubble.assistant_rounded ? '20px' : '8px',
    '--chat-avatar-radius':
      theme.avatars.shape === 'square'
        ? `${theme.avatars.corner_radius ?? 10}px`
        : '999px',
    '--chat-user-padding-left': `${theme.bubble.user_padding_left || 12}px`,
    '--chat-user-padding-right': `${theme.bubble.user_padding_right || 12}px`,
    '--chat-assistant-padding-left': `${theme.bubble.assistant_padding_left || 12}px`,
    '--chat-assistant-padding-right': `${theme.bubble.assistant_padding_right || 12}px`,
    '--chat-header-bg': theme.header.transparent
      ? 'transparent'
      : toRgba(surfaceVariantColor, 0.2) ?? surfaceVariantColor,
    '--chat-header-icon-active-bg': toRgba(primary, 0.15) ?? primary,
    '--chat-header-icon-muted': toRgba(onSurfaceColor, 0.7) ?? onSurfaceColor,
    // A13：header 历史与 pip 图标可配色，缺省走 muted
    '--chat-header-history-icon': theme.header.history_icon_color
      ? theme.header.history_icon_color
      : toRgba(onSurfaceColor, 0.7) ?? onSurfaceColor,
    '--chat-header-pip-icon': theme.header.pip_icon_color
      ? theme.header.pip_icon_color
      : toRgba(onSurfaceColor, 0.7) ?? onSurfaceColor,
    '--chat-header-track': toRgba(surfaceVariantColor, 0.3) ?? surfaceVariantColor,
    // A15/B18：token 环阈值色对齐 app——>90% error、>75% tertiary
    '--chat-context-warn': tertiaryColor,
    '--chat-context-danger': errorColor,
    // B1：classic 进度条阶段色对齐 app
    '--chat-progress-connecting': tertiaryColor,
    '--chat-progress-tool': secondary,
    '--chat-progress-processing': primary,
    '--chat-progress-result': toRgba(tertiaryColor, 0.8) ?? tertiaryColor,
    '--chat-button-filled-bg': primaryContainerColor,
    '--chat-button-filled-fg': onPrimaryContainerColor,
    '--chat-composer-bg': theme.input.transparent ? 'transparent' : panelSurface,
    '--chat-composer-bg-transparent': transparentPanelSurface,
    '--chat-composer-bg-strong': transparentPanelSurfaceStrong,
    '--chat-agent-card-bg': agentCardSurface,
    '--chat-agent-card-shadow': agentCardShadow,
    '--chat-queue-bg': theme.input.transparent ? transparentPanelSurface : panelSurface,
    '--chat-queue-item-bg':
      theme.input.transparent ? transparentPanelSurfaceStrong : panelSurfaceStrong,
    '--chat-field-bg': surfaceColor,
    '--chat-scroll-button-bg': toRgba(surfaceContainerHighColor, 0.62) ?? surfaceContainerHighColor,
    '--chat-panel-bg': panelSurfaceStrong,
    '--chat-code-block-bg': toRgba(surfaceContainerHighestColor, isLight ? 0.9 : 0.85) ?? surfaceContainerHighestColor,
    '--chat-code-toolbar-bg': toRgba(surfaceContainerHighColor, 0.95) ?? surfaceContainerHighColor,
    '--chat-code-block-fg': toRgba(onSurfaceColor, 0.92) ?? onSurfaceColor,
    // A18：diff 语义色对齐 app DialogComponents
    '--chat-diff-add-bg': primaryContainerColor,
    '--chat-diff-add-fg': onPrimaryContainerColor,
    '--chat-diff-del-bg': errorContainerColor,
    '--chat-diff-del-fg': onErrorContainerColor,
    '--chat-diff-hunk-bg': secondaryContainerColor,
    '--chat-diff-hunk-fg': onSecondaryContainerColor,
    '--chat-scrim': toRgba(onSurfaceColor, isLight ? 0.14 : 0.38) ?? onSurfaceColor,
    '--chat-dialog-scrim':
      toRgba(onSurfaceColor, isLight ? 0.22 : 0.5) ?? onSurfaceColor,
    '--chat-action-primary-bg': primary,
    '--chat-action-primary-fg': onPrimaryColor,
    '--chat-action-queued-bg': secondary,
    '--chat-action-queued-fg': onSecondaryColor,
    // A15：取消按钮走 error，不再写死粉色系
    '--chat-action-danger-bg': errorColor,
    '--chat-action-danger-fg': contrastingText(errorColor)
  };
}

function backgroundOpacity(theme: WebThemeSnapshot) {
  return clampOpacity(theme.background.opacity);
}

export function buildChatFontFaceCss(theme: WebThemeSnapshot | null) {
  const globalFace = theme?.font.custom_font_asset_url
    ? `@font-face {
    font-family: "OperitThemeFont";
    src: url("${theme.font.custom_font_asset_url}");
  }`
    : '';
  // A10：气泡级自定义字体各生成一个独立 font-face
  const userFace = theme?.bubble.user_font?.custom_font_asset_url
    ? `@font-face {
    font-family: "OperitThemeFontBubbleUser";
    src: url("${theme.bubble.user_font.custom_font_asset_url}");
  }`
    : '';
  const assistantFace = theme?.bubble.assistant_font?.custom_font_asset_url
    ? `@font-face {
    font-family: "OperitThemeFontBubbleAssistant";
    src: url("${theme.bubble.assistant_font.custom_font_asset_url}");
  }`
    : '';
  return [globalFace, userFace, assistantFace].filter(Boolean).join('\n');
}
