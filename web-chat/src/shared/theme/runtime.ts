// 完整主题运行时派生：manifest + 参数值 → WebThemeSnapshot。
// 常量与 app ThemePackageUiRuntimeV2.accentPaletteTokens / toColorScheme 一致；
// behavior 为基底，USER/AUTHOR 参数值按 effect 类型投影覆盖。

import type {
  WebThemeComponentSkin,
  WebThemeInsets,
  WebThemeSnapshot
} from '../../ui/features/chat/util/chatTypes';
import type {
  ThemeComponentFrame,
  ThemeComponentSkin,
  ThemePackageManifest,
  ThemeInsets,
  ThemeParameterValue,
  ThemePresentationTarget
} from './manifest';
import type { ParameterValueState } from './editorState';
import type { AssetLibrary } from './assets/library';

export type ThemeMode = 'dark' | 'light';

export interface TokenColor {
  lightArgb: number;
  darkArgb: number;
}

export interface ThemeRuntimeResult {
  snapshot: WebThemeSnapshot;
  tokenPool: Map<string, TokenColor>;
}

// --- 颜色工具：对齐 Android ColorUtils（HSL 空间） ---

export function hslToArgb(hue: number, saturation: number, lightness: number): number {
  const h = ((hue % 360) + 360) % 360;
  const s = Math.min(1, Math.max(0, saturation));
  const l = Math.min(1, Math.max(0, lightness));
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = l - c / 2;
  let r = 0;
  let g = 0;
  let b = 0;
  if (h < 60) {
    r = c; g = x; b = 0;
  } else if (h < 120) {
    r = x; g = c; b = 0;
  } else if (h < 180) {
    r = 0; g = c; b = x;
  } else if (h < 240) {
    r = 0; g = x; b = c;
  } else if (h < 300) {
    r = x; g = 0; b = c;
  } else {
    r = c; g = 0; b = x;
  }
  const to8 = (v: number) => Math.round((v + m) * 255);
  const argb = (to8(r) << 16) | (to8(g) << 8) | to8(b);
  return (0xff000000 | (argb >>> 0)) >>> 0;
}

export function colorToHslArgb(argb: number): [number, number, number] {
  const r = ((argb >>> 16) & 0xff) / 255;
  const g = ((argb >>> 8) & 0xff) / 255;
  const b = (argb & 0xff) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  const d = max - min;
  if (d === 0) {
    return [0, 0, l];
  }
  const s = d / (1 - Math.abs(2 * l - 1));
  let h: number;
  if (max === r) {
    h = ((g - b) / d) % 6;
  } else if (max === g) {
    h = (b - r) / d + 2;
  } else {
    h = (r - g) / d + 4;
  }
  return [h * 60, s, l];
}

function luminanceArgb(argb: number): number {
  const red = ((argb >>> 16) & 0xff) / 255;
  const green = ((argb >>> 8) & 0xff) / 255;
  const blue = (argb & 0xff) / 255;
  const toLinear = (v: number) =>
    v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  return 0.2126 * toLinear(red) + 0.7152 * toLinear(green) + 0.0722 * toLinear(blue);
}

export { luminanceArgb };

// --- accent palette：app accentPaletteTokens 的镜像 ---

function accentPaletteTokens(
  schemeRoles: Record<string, string>,
  seedArgb: number
): Map<string, TokenColor> {
  const [seedHue, seedSat, seedLight] = colorToHslArgb(seedArgb);
  const primarySaturation = Math.min(0.88, Math.max(0.42, seedSat));
  const secondarySaturation = Math.min(0.52, Math.max(0.24, primarySaturation * 0.56));
  const tertiarySaturation = Math.min(0.68, Math.max(0.34, primarySaturation * 0.72));

  const tone = (
    hueOffset: number,
    saturation: number,
    light: number,
    dark: number
  ): TokenColor => ({
    lightArgb: hslToArgb(seedHue + hueOffset, saturation, light),
    darkArgb: hslToArgb(seedHue + hueOffset, saturation, dark)
  });

  const LIGHT_ON = 0xffffffff;
  const DARK_ON = 0xff101114;
  const contrast = (light: number, dark: number): TokenColor => ({ lightArgb: light, darkArgb: dark });
  const readableOn = (color: number): number =>
    luminanceArgb(color) >= 0.45 ? DARK_ON : LIGHT_ON;

  const get = (role: string): string => {
    const tokenId = schemeRoles[role];
    if (tokenId == null || tokenId.length === 0) {
      throw new Error(`Material scheme 缺少角色 ${role}`);
    }
    return tokenId;
  };
  const tokens = new Map<string, TokenColor>();
  const assign = (role: string, value: TokenColor): void => {
    tokens.set(get(role), value);
  };

  const primaryColor: TokenColor = { lightArgb: seedArgb, darkArgb: seedArgb };
  assign('primary', primaryColor);
  assign('onPrimary', contrast(readableOn(seedArgb), readableOn(seedArgb)));
  assign('primaryContainer', tone(0, primarySaturation, 0.9, 0.3));
  assign('onPrimaryContainer', contrast(DARK_ON, LIGHT_ON));
  assign('inversePrimary', contrast(primaryColor.darkArgb, primaryColor.lightArgb));
  assign('secondary', tone(18, secondarySaturation, 0.4, 0.8));
  assign('onSecondary', contrast(LIGHT_ON, DARK_ON));
  assign('secondaryContainer', tone(18, secondarySaturation, 0.9, 0.3));
  assign('onSecondaryContainer', contrast(DARK_ON, LIGHT_ON));
  assign('tertiary', tone(58, tertiarySaturation, 0.4, 0.8));
  assign('onTertiary', contrast(LIGHT_ON, DARK_ON));
  assign('tertiaryContainer', tone(58, tertiarySaturation, 0.9, 0.3));
  assign('onTertiaryContainer', contrast(DARK_ON, LIGHT_ON));
  assign('surfaceTint', primaryColor);
  assign('outline', tone(0, Math.max(0.18, primarySaturation * 0.42), 0.48, 0.62));
  assign('outlineVariant', tone(0, Math.max(0.14, primarySaturation * 0.34), 0.8, 0.34));

  return tokens;
}

// --- runtime ---

function materialTokenIds(manifest: ThemePackageManifest): Record<string, string> {
  const material = manifest.presentation.material;
  if (material == null) {
    return {};
  }
  return material.colors as Record<string, string>;
}

function buildTokenPool(manifest: ThemePackageManifest): Map<string, TokenColor> {
  const pool = new Map<string, TokenColor>();
  const tokens = manifest.tokens ?? {};
  for (const [tokenId, token] of Object.entries(tokens)) {
    pool.set(tokenId, { lightArgb: token.lightArgb, darkArgb: token.darkArgb });
  }
  return pool;
}

export interface StageImageData {
  uri: string;
  fit: 'fill' | 'fit' | 'crop';
  opacity: number;
}

export interface RuntimeEffectsResult {
  presentation: Map<ThemePresentationTarget, ThemeParameterValue>;
  stageImage: StageImageData | null;
  shapeScale: number;
  componentFrameScale: Map<string, number>;
  componentContentInsets: Map<string, ThemeInsets>;
}

function applyParameterEffects(
  manifest: ThemePackageManifest,
  values: Map<string, ParameterValueState>,
  pool: Map<string, TokenColor>
): RuntimeEffectsResult {
  const presentation = new Map<ThemePresentationTarget, ThemeParameterValue>();
  const schemeRoles = materialTokenIds(manifest);
  let stageImage: StageImageData | null = null;
  let shapeScale = 1;
  const componentFrameScale = new Map<string, number>();
  const componentContentInsets = new Map<string, ThemeInsets>();

  for (const definition of manifest.parameters) {
    const value = values.get(definition.id) ?? definition.defaultValue;
    if (value == null) {
      continue;
    }
    for (const effect of definition.effects) {
      switch (effect.type) {
        case 'accent_palette': {
          if (value.type !== 'color') {
            break;
          }
          for (const [role, token] of accentPaletteTokens(schemeRoles, value.argb)) {
            pool.set(role, token);
          }
          break;
        }
        case 'token_color':
          if (value.type !== 'color') {
            break;
          }
          for (const tokenId of effect.tokenIds) {
            pool.set(tokenId, { lightArgb: value.argb, darkArgb: value.argb });
          }
          break;
        case 'token_color_pair':
          if (value.type !== 'color_pair') {
            break;
          }
          for (const tokenId of effect.tokenIds) {
            pool.set(tokenId, {
              lightArgb: value.lightArgb,
              darkArgb: value.darkArgb
            });
          }
          break;
        case 'stage_image':
          if (value.type !== 'image_uri') {
            break;
          }
          stageImage = {
            uri: value.uri,
            fit: effect.fit.toLowerCase() as StageImageData['fit'],
            opacity: effect.opacity
          };
          break;
        case 'typography_scale':
          if (value.type === 'float') {
            presentation.set('TYPOGRAPHY_SCALE', value);
          }
          break;
        case 'shape_scale':
          if (value.type === 'float') {
            shapeScale = value.value;
          }
          break;
        case 'component_frame_scale':
          if (value.type === 'float') {
            for (const componentId of effect.componentIds) {
              componentFrameScale.set(componentId, value.value);
            }
          }
          break;
        case 'component_content_insets':
          if (value.type === 'insets') {
            for (const componentId of effect.componentIds) {
              componentContentInsets.set(componentId, {
                startDp: value.startDp,
                topDp: value.topDp,
                endDp: value.endDp,
                bottomDp: value.bottomDp
              });
            }
          }
          break;
        case 'presentation':
          for (const target of effect.targets) {
            presentation.set(target, value);
          }
          break;
        default:
          break;
      }
    }
  }
  return {
    componentContentInsets,
    componentFrameScale,
    presentation,
    shapeScale,
    stageImage
  };
}

function behaviorPresentationValues(manifest: ThemePackageManifest): Map<ThemePresentationTarget, ThemeParameterValue> {
  const map = new Map<ThemePresentationTarget, ThemeParameterValue>();
  const b = manifest.presentation.behavior;
  const put = (target: ThemePresentationTarget, value: ThemeParameterValue) => map.set(target, value);
  const boolean = (v: boolean): ThemeParameterValue => ({ type: 'boolean', value: v });
  const float = (v: number): ThemeParameterValue => ({ type: 'float', value: v });
  const option = (v: string): ThemeParameterValue => ({ type: 'option', value: v });
  const color = (v: number | null): ThemeParameterValue | null => v == null ? null : ({ type: 'color', argb: v });

  put('TYPOGRAPHY_USE_CUSTOM_FONT', boolean(b.typography.useCustomFont));
  put('TYPOGRAPHY_FAMILY', option(b.typography.family.toLowerCase()));
  put('TYPOGRAPHY_SCALE', float(b.typography.scale));
  put('BACKGROUND_USE_IMAGE', boolean(b.background.enabled));
  put('BACKGROUND_MEDIA_TYPE', option(b.background.mediaType.toLowerCase()));
  put('BACKGROUND_OPACITY', float(b.background.opacity));
  put('BACKGROUND_BLUR_ENABLED', boolean(b.background.blurEnabled));
  put('BACKGROUND_BLUR_RADIUS', float(b.background.blurRadiusDp));
  put('BACKGROUND_VIDEO_MUTED', boolean(b.background.videoMuted));
  put('BACKGROUND_VIDEO_LOOP', boolean(b.background.videoLoop));
  put('CURSOR_USER_BUBBLE_FOLLOW_THEME', boolean(b.conversation.cursorUserBubbleFollowTheme));
  put('CURSOR_USER_BUBBLE_LIQUID_GLASS', boolean(b.conversation.cursorUserBubbleLiquidGlass));
  put('CURSOR_USER_BUBBLE_WATER_GLASS', boolean(b.conversation.cursorUserBubbleWaterGlass));
  const cursorColor = color(b.conversation.cursorUserBubbleColorArgb);
  if (cursorColor != null) put('CURSOR_USER_BUBBLE_COLOR', cursorColor);
  put('BUBBLE_SHOW_AVATAR', boolean(b.conversation.bubbleShowAvatar));
  put('BUBBLE_WIDE_LAYOUT', boolean(b.conversation.bubbleWideLayout));
  put('BUBBLE_USER_LIQUID_GLASS', boolean(b.conversation.bubbleUserLiquidGlass));
  put('BUBBLE_USER_WATER_GLASS', boolean(b.conversation.bubbleUserWaterGlass));
  put('BUBBLE_ASSISTANT_LIQUID_GLASS', boolean(b.conversation.bubbleAssistantLiquidGlass));
  put('BUBBLE_ASSISTANT_WATER_GLASS', boolean(b.conversation.bubbleAssistantWaterGlass));
  put('BUBBLE_IMAGE_RENDER_MODE', option(b.conversation.bubbleImageRenderMode.toLowerCase()));
  put('BUBBLE_USER_ROUNDED_CORNERS', boolean(b.conversation.bubbleUserRoundedCorners));
  put('BUBBLE_ASSISTANT_ROUNDED_CORNERS', boolean(b.conversation.bubbleAssistantRoundedCorners));
  const userColor = color(b.conversation.bubbleUserColorArgb);
  if (userColor != null) put('BUBBLE_USER_COLOR', userColor);
  const assistantColor = color(b.conversation.bubbleAssistantColorArgb);
  if (assistantColor != null) put('BUBBLE_ASSISTANT_COLOR', assistantColor);
  const userTextColor = color(b.conversation.bubbleUserTextColorArgb);
  if (userTextColor != null) put('BUBBLE_USER_TEXT_COLOR', userTextColor);
  const assistantTextColor = color(b.conversation.bubbleAssistantTextColorArgb);
  if (assistantTextColor != null) put('BUBBLE_ASSISTANT_TEXT_COLOR', assistantTextColor);
  put('BUBBLE_USER_USE_CUSTOM_FONT', boolean(b.conversation.bubbleUserUseCustomFont));
  put('BUBBLE_USER_FONT_FAMILY', option(b.conversation.bubbleUserFontFamily.toLowerCase()));
  put('BUBBLE_ASSISTANT_USE_CUSTOM_FONT', boolean(b.conversation.bubbleAssistantUseCustomFont));
  put('BUBBLE_ASSISTANT_FONT_FAMILY', option(b.conversation.bubbleAssistantFontFamily.toLowerCase()));
  put('AVATAR_SHAPE', option(b.conversation.avatarShape.toLowerCase()));
  put('AVATAR_CORNER_RADIUS', float(b.conversation.avatarCornerRadiusDp));
  put('COMPOSER_TRANSPARENT', boolean(b.composer.transparent));
  put('COMPOSER_FLOATING', boolean(b.composer.floating));
  put('COMPOSER_LIQUID_GLASS', boolean(b.composer.liquidGlass));
  put('COMPOSER_WATER_GLASS', boolean(b.composer.waterGlass));
  put('CHROME_STATUS_BAR_HIDDEN', boolean(b.chrome.statusBarHidden));
  put('CHROME_STATUS_BAR_TRANSPARENT', boolean(b.chrome.statusBarTransparent));
  const statusBarColor = color(b.chrome.statusBarColorArgb);
  if (statusBarColor != null) put('CHROME_STATUS_BAR_COLOR', statusBarColor);
  put('CHROME_TOOLBAR_TRANSPARENT', boolean(b.chrome.toolbarTransparent));
  const toolbarColor = color(b.chrome.toolbarColorArgb);
  if (toolbarColor != null) put('CHROME_TOOLBAR_COLOR', toolbarColor);
  put('CHROME_NAVIGATION_WATER_GLASS', boolean(b.chrome.navigationWaterGlass));
  put('CHROME_NAVIGATION_BUTTON_LIQUID_GLASS', boolean(b.chrome.navigationButtonLiquidGlass));
  const navBg = color(b.chrome.navigationBackgroundColorArgb);
  if (navBg != null) put('CHROME_NAVIGATION_BACKGROUND_COLOR', navBg);
  const navAccent = color(b.chrome.navigationAccentColorArgb);
  if (navAccent != null) put('CHROME_NAVIGATION_ACCENT_COLOR', navAccent);
  put('CHROME_CHAT_HEADER_TRANSPARENT', boolean(b.chrome.chatHeaderTransparent));
  put('CHROME_CHAT_HEADER_OVERLAY_MODE', option(b.chrome.chatHeaderOverlayMode.toLowerCase()));
  put('CHROME_APP_BAR_CONTENT_COLOR_MODE', option(b.chrome.appBarContentColorMode.toLowerCase()));
  const historyColor = color(b.chrome.chatHeaderHistoryIconColorArgb);
  if (historyColor != null) put('CHROME_CHAT_HEADER_HISTORY_ICON_COLOR', historyColor);
  const pipColor = color(b.chrome.chatHeaderPipIconColorArgb);
  if (pipColor != null) put('CHROME_CHAT_HEADER_PIP_ICON_COLOR', pipColor);
  void float;
  return map;
}

function paletteFromTokens(
  manifest: ThemePackageManifest,
  pool: Map<string, TokenColor>,
  isDark: boolean
): WebThemeSnapshot['palette'] {
  const schemeRoles = materialTokenIds(manifest);
  const roleColor = (role: string): string => {
    const tokenId = schemeRoles[role];
    if (tokenId == null || tokenId.length === 0) {
      throw new Error(`Material scheme 缺少角色 ${role}`);
    }
    const token = pool.get(tokenId);
    if (token == null) {
      throw new Error(`token 缺失: ${tokenId}（角色 ${role}）`);
    }
    const argb = isDark ? token.darkArgb : token.lightArgb;
    const hex = ((argb & 0xffffffff) >>> 0).toString(16).padStart(8, '0');
    return `#${hex.slice(2)}`;
  };
  const palette: WebThemeSnapshot['palette'] = {
    background_color: roleColor('background'),
    surface_color: roleColor('surface'),
    surface_variant_color: roleColor('surfaceVariant'),
    surface_container_color: roleColor('surfaceContainer'),
    surface_container_high_color: roleColor('surfaceContainerHigh'),
    primary_color: roleColor('primary'),
    secondary_color: roleColor('secondary'),
    primary_container_color: roleColor('primaryContainer'),
    on_primary_container_color: roleColor('onPrimaryContainer'),
    on_surface_color: roleColor('onSurface'),
    on_surface_variant_color: roleColor('onSurfaceVariant'),
    outline_color: roleColor('outline'),
    outline_variant_color: roleColor('outlineVariant')
  };
  return palette;
}

function resolveTokenHex(pool: Map<string, TokenColor>, tokenId: string, isDark: boolean): string {
  if (tokenId === 'm3.transparent') {
    return 'transparent';
  }
  const token = pool.get(tokenId);
  if (token == null) {
    throw new Error(`组件皮肤 token 缺失: ${tokenId}`);
  }
  const argb = isDark ? token.darkArgb : token.lightArgb;
  const hex = ((argb & 0xffffffff) >>> 0).toString(16).padStart(8, '0');
  return `#${hex.slice(2)}`;
}

function frameCornerRadius(frame: ThemeComponentFrame): number {
  switch (frame.kind) {
    case 'none':
      return 0;
    case 'round_rect':
      return frame.cornerRadiusDp;
    case 'cut_corners':
    case 'hud_notched':
      return frame.cutSizeDp;
    case 'corner_brackets':
    case 'segmented_rail':
      return frame.cornerCutDp;
  }
}

function resolveComponentSkin(
  skin: ThemeComponentSkin,
  pool: Map<string, TokenColor>,
  isDark: boolean
): WebThemeComponentSkin {
  const normal = skin.normal;
  return {
    container_color: resolveTokenHex(pool, normal.containerToken, isDark),
    content_color: resolveTokenHex(pool, normal.contentToken, isDark),
    corner_radius_dp: frameCornerRadius(normal.frame),
    elevation_dp: normal.elevationDp,
    content_padding: {
      start_dp: normal.contentPadding.startDp,
      top_dp: normal.contentPadding.topDp,
      end_dp: normal.contentPadding.endDp,
      bottom_dp: normal.contentPadding.bottomDp
    }
  };
}

function resolveComponentSkins(
  skins: Record<string, ThemeComponentSkin>,
  pool: Map<string, TokenColor>,
  isDark: boolean
): Record<string, WebThemeComponentSkin> {
  return Object.fromEntries(
    Object.entries(skins).map(([componentId, skin]) => [
      componentId,
      resolveComponentSkin(skin, pool, isDark)
    ])
  );
}

export function resolveThemeRuntime(
  manifest: ThemePackageManifest,
  values: Map<string, ParameterValueState>,
  themeMode: ThemeMode,
  componentSkins?: Map<string, ThemeComponentSkin>
): ThemeRuntimeResult {
  const pool = buildTokenPool(manifest);
  const {
    componentContentInsets,
    componentFrameScale,
    presentation,
    shapeScale,
    stageImage
  } = applyParameterEffects(manifest, values, pool);
  const behavior = behaviorPresentationValues(manifest);
  const componentSkinSource =
    componentSkins == null
      ? manifest.presentation.componentSkins
      : Object.fromEntries(componentSkins);
  // 参数值覆盖 behavior 基底
  for (const [target, value] of presentation) {
    behavior.set(target, value);
  }

  const isDark = themeMode === 'dark';
  const resolvedComponentSkins = resolveComponentSkins(componentSkinSource, pool, isDark);
  const color = (target: ThemePresentationTarget): string | null => {
    const value = behavior.get(target);
    if (value?.type !== 'color') {
      return null;
    }
    const hex = ((value.argb & 0xffffffff) >>> 0).toString(16).padStart(8, '0');
    return `#${hex.slice(2)}`;
  };
  const bool = (target: ThemePresentationTarget): boolean => {
    const value = behavior.get(target);
    return value?.type === 'boolean' ? value.value : false;
  };
  const number = (target: ThemePresentationTarget): number | null => {
    const value = behavior.get(target);
    return value?.type === 'float' ? value.value : null;
  };
  const option = (target: ThemePresentationTarget): string => {
    const value = behavior.get(target);
    return value?.type === 'option' ? value.value : 'default';
  };
  const uri = (target: ThemePresentationTarget): string | null => {
    const value = behavior.get(target);
    return value?.type === 'image_uri' || value?.type === 'video_uri' || value?.type === 'font_uri'
      ? value.uri
      : null;
  };
  const imageLayout = (target: ThemePresentationTarget) => {
    const value = behavior.get(target);
    return value?.type === 'image_layout' ? value : null;
  };
  const font = (
    useCustomTarget: ThemePresentationTarget,
    familyTarget: ThemePresentationTarget,
    uriTarget: ThemePresentationTarget,
    scaleTarget: ThemePresentationTarget
  ) => ({
    type: bool(useCustomTarget) ? 'custom' : 'system',
    system_font_name: option(familyTarget),
    custom_font_asset_url: uri(uriTarget),
    scale: number(scaleTarget) ?? 1
  });
  const bubbleImage = (
    uriTarget: ThemePresentationTarget,
    layoutTarget: ThemePresentationTarget
  ) => {
    const layout = imageLayout(layoutTarget);
    return {
      enabled: uri(uriTarget) !== null,
      asset_url: uri(uriTarget),
      render_mode: option('BUBBLE_IMAGE_RENDER_MODE'),
      crop_left: layout?.cropLeft ?? 0,
      crop_top: layout?.cropTop ?? 0,
      crop_right: layout?.cropRight ?? 1,
      crop_bottom: layout?.cropBottom ?? 1,
      repeat_start: layout?.repeatStart ?? 0,
      repeat_end: layout?.repeatEnd ?? 1,
      repeat_y_start: layout?.repeatYStart ?? 0,
      repeat_y_end: layout?.repeatYEnd ?? 1,
      scale: layout?.scale ?? 1
    };
  };

  const avatarShape = option('AVATAR_SHAPE');
  const mediaInput = option('BACKGROUND_MEDIA_TYPE');
  const backgroundImageValue = behavior.get('BACKGROUND_IMAGE_URI');
  const backgroundVideoValue = behavior.get('BACKGROUND_VIDEO_URI');
  const background: WebThemeSnapshot['background'] = { stage: null, media: null };

  if (stageImage != null) {
    background.stage = {
      asset_url: stageImage.uri,
      fit: stageImage.fit,
      opacity: stageImage.opacity
    };
  }

  if (bool('BACKGROUND_USE_IMAGE')) {
    if (mediaInput === 'image' && backgroundImageValue?.type === 'image_uri') {
      const blurEnabled = bool('BACKGROUND_BLUR_ENABLED');
      background.media = {
        type: 'image',
        asset_url: backgroundImageValue.uri,
        opacity: number('BACKGROUND_OPACITY') ?? 0.22,
        blur_enabled: blurEnabled,
        blur_radius_dp: number('BACKGROUND_BLUR_RADIUS') ?? 0,
        muted: bool('BACKGROUND_VIDEO_MUTED'),
        loop: bool('BACKGROUND_VIDEO_LOOP')
      };
    } else if (mediaInput === 'video' && backgroundVideoValue?.type === 'video_uri') {
      background.media = {
        type: 'video',
        asset_url: backgroundVideoValue.uri,
        opacity: number('BACKGROUND_OPACITY') ?? 0.22,
        blur_enabled: false,
        blur_radius_dp: 0,
        muted: bool('BACKGROUND_VIDEO_MUTED'),
        loop: bool('BACKGROUND_VIDEO_LOOP')
      };
    }
  }

  const fontFamily = option('TYPOGRAPHY_FAMILY');
  const userInsets = componentContentInsets.get('message_user');
  const assistantInsets = componentContentInsets.get('message_assistant');
  const userSkin = resolvedComponentSkins.message_user;
  const assistantSkin = resolvedComponentSkins.message_assistant;
  const toWebInsets = (insets: ThemeInsets): WebThemeInsets => ({
    start_dp: insets.startDp,
    top_dp: insets.topDp,
    end_dp: insets.endDp,
    bottom_dp: insets.bottomDp
  });
  const userPadding = userInsets == null ? userSkin?.content_padding : toWebInsets(userInsets);
  const assistantPadding =
    assistantInsets == null ? assistantSkin?.content_padding : toWebInsets(assistantInsets);
  const snapshot: WebThemeSnapshot = {
    source: 'studio',
    source_id: null,
    theme_mode: themeMode,
    use_system_theme: false,
    use_custom_colors: true,
    palette: paletteFromTokens(manifest, pool, isDark),
    background,
    header: {
      transparent: bool('CHROME_CHAT_HEADER_TRANSPARENT'),
      overlay: option('CHROME_CHAT_HEADER_OVERLAY_MODE') === 'overlay'
    },
    input: {
      style: 'agent',
      transparent: bool('COMPOSER_TRANSPARENT'),
      floating: bool('COMPOSER_FLOATING'),
      liquid_glass: bool('COMPOSER_LIQUID_GLASS'),
      water_glass: bool('COMPOSER_WATER_GLASS')
    },
    font: {
      type: bool('TYPOGRAPHY_USE_CUSTOM_FONT') ? 'custom' : 'system',
      system_font_name: fontFamily,
      custom_font_asset_url: uri('TYPOGRAPHY_FONT_URI'),
      scale: number('TYPOGRAPHY_SCALE') ?? 1
    },
    chrome: {
      status_bar_hidden: bool('CHROME_STATUS_BAR_HIDDEN'),
      status_bar_transparent: bool('CHROME_STATUS_BAR_TRANSPARENT'),
      status_bar_color: color('CHROME_STATUS_BAR_COLOR'),
      toolbar_transparent: bool('CHROME_TOOLBAR_TRANSPARENT'),
      toolbar_color: color('CHROME_TOOLBAR_COLOR') ?? resolvedComponentSkins.app_bar?.container_color,
      navigation_water_glass: bool('CHROME_NAVIGATION_WATER_GLASS'),
      navigation_button_liquid_glass: bool('CHROME_NAVIGATION_BUTTON_LIQUID_GLASS'),
      navigation_background_color:
        color('CHROME_NAVIGATION_BACKGROUND_COLOR') ?? resolvedComponentSkins.navigation?.container_color,
      navigation_accent_color: color('CHROME_NAVIGATION_ACCENT_COLOR'),
      app_bar_content_color_mode: option('CHROME_APP_BAR_CONTENT_COLOR_MODE'),
      chat_header_history_icon_color: color('CHROME_CHAT_HEADER_HISTORY_ICON_COLOR'),
      chat_header_pip_icon_color: color('CHROME_CHAT_HEADER_PIP_ICON_COLOR')
    },
    geometry: {
      shape_scale: shapeScale,
      component_frame_scale: Object.fromEntries(componentFrameScale),
      component_content_insets: {
        ...(userPadding == null ? {} : { message_user: userPadding }),
        ...(assistantPadding == null ? {} : { message_assistant: assistantPadding })
      }
    },
    component_skins: resolvedComponentSkins,
    chat_style: 'bubble',
    show_thinking_process: true,
    show_status_tags: true,
    show_input_processing_status: true,
    display: {
      show_user_name: true,
      show_role_name: true,
      show_model_name: false,
      show_model_provider: false,
      show_message_token_stats: false,
      show_message_timing_stats: false,
      show_message_timestamp: false,
      tool_collapse_mode: 'all',
      global_user_name: null
    },
    bubble: {
      show_avatar: bool('BUBBLE_SHOW_AVATAR'),
      wide_layout: bool('BUBBLE_WIDE_LAYOUT'),
      cursor_user_follow_theme: bool('CURSOR_USER_BUBBLE_FOLLOW_THEME'),
      cursor_user_color: color('CURSOR_USER_BUBBLE_COLOR'),
      user_bubble_color: color('BUBBLE_USER_COLOR') ?? userSkin?.container_color ?? null,
      assistant_bubble_color:
        color('BUBBLE_ASSISTANT_COLOR') ?? assistantSkin?.container_color ?? null,
      user_text_color: color('BUBBLE_USER_TEXT_COLOR') ?? userSkin?.content_color ?? null,
      assistant_text_color:
        color('BUBBLE_ASSISTANT_TEXT_COLOR') ?? assistantSkin?.content_color ?? null,
      cursor_user_liquid_glass: bool('CURSOR_USER_BUBBLE_LIQUID_GLASS'),
      cursor_user_water_glass: bool('CURSOR_USER_BUBBLE_WATER_GLASS'),
      user_liquid_glass: bool('BUBBLE_USER_LIQUID_GLASS'),
      user_water_glass: bool('BUBBLE_USER_WATER_GLASS'),
      assistant_liquid_glass: bool('BUBBLE_ASSISTANT_LIQUID_GLASS'),
      assistant_water_glass: bool('BUBBLE_ASSISTANT_WATER_GLASS'),
      user_rounded: bool('BUBBLE_USER_ROUNDED_CORNERS'),
      assistant_rounded: bool('BUBBLE_ASSISTANT_ROUNDED_CORNERS'),
      user_padding_left: userPadding?.start_dp ?? 12,
      user_padding_right: userPadding?.end_dp ?? 12,
      user_padding_top: userPadding?.top_dp ?? 12,
      user_padding_bottom: userPadding?.bottom_dp ?? 12,
      assistant_padding_left: assistantPadding?.start_dp ?? 12,
      assistant_padding_right: assistantPadding?.end_dp ?? 12,
      assistant_padding_top: assistantPadding?.top_dp ?? 12,
      assistant_padding_bottom: assistantPadding?.bottom_dp ?? 12,
      user_font: font(
        'BUBBLE_USER_USE_CUSTOM_FONT',
        'BUBBLE_USER_FONT_FAMILY',
        'BUBBLE_USER_FONT_URI',
        'TYPOGRAPHY_SCALE'
      ),
      assistant_font: font(
        'BUBBLE_ASSISTANT_USE_CUSTOM_FONT',
        'BUBBLE_ASSISTANT_FONT_FAMILY',
        'BUBBLE_ASSISTANT_FONT_URI',
        'TYPOGRAPHY_SCALE'
      ),
      user_image: bubbleImage('BUBBLE_USER_IMAGE_URI', 'BUBBLE_USER_IMAGE_LAYOUT'),
      assistant_image: bubbleImage('BUBBLE_ASSISTANT_IMAGE_URI', 'BUBBLE_ASSISTANT_IMAGE_LAYOUT')
    },
    avatars: {
      shape: avatarShape === 'rounded' ? 'rounded' : avatarShape === 'square' ? 'square' : 'circle',
      corner_radius: number('AVATAR_CORNER_RADIUS') ?? 12,
      user_avatar_url: null,
      assistant_avatar_url: null
    }
  };

  return { snapshot, tokenPool: pool };
}

/** 编辑态 → 快照：解析素材引用后用完整 runtime 派生（06 主路径） */
export async function resolveEditorRuntimeTheme(
  manifest: ThemePackageManifest,
  values: Map<string, ParameterValueState>,
  themeMode: ThemeMode,
  assetLibrary: AssetLibrary,
  componentSkins?: Map<string, ThemeComponentSkin>
): Promise<WebThemeSnapshot> {
  const resolved = new Map<string, ParameterValueState>(values);
  for (const [id, value] of values) {
    if (value == null) {
      continue;
    }
    if (value.type === 'image_uri' || value.type === 'video_uri' || value.type === 'font_uri') {
      const url = await assetLibrary.resolveUrl(value.uri);
      if (url != null) {
        resolved.set(id, { ...value, uri: url });
      }
    }
  }
  return resolveThemeRuntime(manifest, resolved, themeMode, componentSkins).snapshot;
}
