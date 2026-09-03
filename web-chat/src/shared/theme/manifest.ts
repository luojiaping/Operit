import type { WebThemeSnapshot } from '../../ui/features/chat/util/chatTypes';

type JsonRecord = Record<string, unknown>;

// schema 4 主题包契约的 Web 侧镜像。
// 与 app ThemePackageManifestV2 / ThemePackageModelsV2 逐字段对齐，
// strict 解码：未知键拒绝、显式默认值语义（字段缺失=默认值，不输出 null）。

export const THEME_PACKAGE_SCHEMA_VERSION = 4;
export const THEME_PACKAGE_EXTENSION = '.otheme';
export const THEME_PACKAGE_MANIFEST_ENTRY = 'operit-theme.json';
export const THEME_PACKAGE_ZIP_COMMENT = 'Operit Theme Package';

export const MEMBER_ID_PATTERN = /^[a-z][a-z0-9_]*$/;
export const PACKAGE_ID_PATTERN = /^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/;
export const SHA256_PATTERN = /^[0-9a-f]{64}$/;
export const SEMVER_PATTERN = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/;

export type ThemeAssetKind = 'BITMAP' | 'NINE_SLICE' | 'FONT' | 'PATH';
export type ThemeParameterType =
  | 'COLOR'
  | 'COLOR_PAIR'
  | 'BOOLEAN'
  | 'OPTION'
  | 'FLOAT'
  | 'IMAGE_URI'
  | 'VIDEO_URI'
  | 'FONT_URI'
  | 'IMAGE_LAYOUT'
  | 'INSETS'
  | 'CORNER_RADIUS';
export type ThemeParameterVisibility = 'USER' | 'AUTHOR';
export type ThemeParameterSection = 'APPEARANCE' | 'CONVERSATION' | 'COMPOSER' | 'APP_CHROME';
export type ThemeSceneImageFit = 'FILL' | 'FIT' | 'CROP';
export type ThemeSurfaceKind = 'SCENE' | 'TEMPLATE' | 'HOST_SHELL';

export interface ThemePackageLocalizedText {
  [locale: string]: string;
}

export interface ThemePackageCoordinate {
  packageId: string;
  version: string;
  archiveSha256: string;
}

export interface ThemePackageVariant {
  id: string;
  label: ThemePackageLocalizedText;
}

export interface ThemePackageAssetEntry {
  key: string;
  path: string;
  kind: ThemeAssetKind;
  sha256: string;
  byteSize: number;
}

export interface ThemePackageAttribution {
  text: ThemePackageLocalizedText;
  sourceUrl: string;
}

export type ThemeParameterValue =
  | { type: 'color'; argb: number }
  | { type: 'color_pair'; lightArgb: number; darkArgb: number }
  | { type: 'boolean'; value: boolean }
  | { type: 'option'; value: string }
  | { type: 'float'; value: number }
  | { type: 'image_uri'; uri: string }
  | { type: 'video_uri'; uri: string }
  | { type: 'font_uri'; uri: string }
  | {
      type: 'image_layout';
      cropLeft: number;
      cropTop: number;
      cropRight: number;
      cropBottom: number;
      repeatStart: number;
      repeatEnd: number;
      repeatYStart: number;
      repeatYEnd: number;
      scale: number;
    }
  | { type: 'insets'; startDp: number; topDp: number; endDp: number; bottomDp: number }
  | { type: 'corner_radius'; valueDp: number };

export type ThemeParameterControl =
  | {
      type: 'color_palette';
      presetArgb: number[];
      allowCustom: boolean;
    }
  | {
      type: 'color_pair_palette';
      lightPresetArgb: number[];
      darkPresetArgb: number[];
      allowCustom: boolean;
    }
  | { type: 'toggle' }
  | { type: 'choice'; options: { id: string; label: ThemePackageLocalizedText }[] }
  | { type: 'slider'; minimum: number; maximum: number; step: number }
  | { type: 'image_picker'; mimeTypes: string[] }
  | { type: 'video_picker'; mimeTypes: string[] }
  | { type: 'font_picker'; mimeTypes: string[] }
  | { type: 'author_value' };

export type ThemeParameterCondition =
  | { type: 'boolean_equals'; parameterId: string; expected: boolean }
  | { type: 'option_equals'; parameterId: string; expected: string }
  | { type: 'resource_present'; parameterId: string };

export type ThemeParameterEffect =
  | { type: 'accent_palette' }
  | { type: 'token_color'; tokenIds: string[] }
  | { type: 'token_color_pair'; tokenIds: string[] }
  | { type: 'stage_image'; surfaceIds: string[]; fit: ThemeSceneImageFit; opacity: number }
  | { type: 'typography_scale' }
  | { type: 'shape_scale' }
  | { type: 'component_frame_scale'; componentIds: string[] }
  | { type: 'component_content_insets'; componentIds: string[] }
  | { type: 'presentation'; targets: ThemePresentationTarget[] };

export type ThemePresentationTarget =
  | 'TYPOGRAPHY_USE_CUSTOM_FONT'
  | 'TYPOGRAPHY_FAMILY'
  | 'TYPOGRAPHY_FONT_URI'
  | 'TYPOGRAPHY_SCALE'
  | 'BACKGROUND_USE_IMAGE'
  | 'BACKGROUND_MEDIA_TYPE'
  | 'BACKGROUND_IMAGE_URI'
  | 'BACKGROUND_VIDEO_URI'
  | 'BACKGROUND_OPACITY'
  | 'BACKGROUND_BLUR_ENABLED'
  | 'BACKGROUND_BLUR_RADIUS'
  | 'BACKGROUND_VIDEO_MUTED'
  | 'BACKGROUND_VIDEO_LOOP'
  | 'CURSOR_USER_BUBBLE_FOLLOW_THEME'
  | 'CURSOR_USER_BUBBLE_LIQUID_GLASS'
  | 'CURSOR_USER_BUBBLE_WATER_GLASS'
  | 'CURSOR_USER_BUBBLE_COLOR'
  | 'BUBBLE_SHOW_AVATAR'
  | 'BUBBLE_WIDE_LAYOUT'
  | 'BUBBLE_USER_LIQUID_GLASS'
  | 'BUBBLE_USER_WATER_GLASS'
  | 'BUBBLE_ASSISTANT_LIQUID_GLASS'
  | 'BUBBLE_ASSISTANT_WATER_GLASS'
  | 'BUBBLE_IMAGE_RENDER_MODE'
  | 'BUBBLE_USER_ROUNDED_CORNERS'
  | 'BUBBLE_ASSISTANT_ROUNDED_CORNERS'
  | 'BUBBLE_USER_COLOR'
  | 'BUBBLE_ASSISTANT_COLOR'
  | 'BUBBLE_USER_TEXT_COLOR'
  | 'BUBBLE_ASSISTANT_TEXT_COLOR'
  | 'BUBBLE_USER_USE_CUSTOM_FONT'
  | 'BUBBLE_USER_FONT_FAMILY'
  | 'BUBBLE_USER_FONT_URI'
  | 'BUBBLE_ASSISTANT_USE_CUSTOM_FONT'
  | 'BUBBLE_ASSISTANT_FONT_FAMILY'
  | 'BUBBLE_ASSISTANT_FONT_URI'
  | 'BUBBLE_USER_IMAGE_URI'
  | 'BUBBLE_ASSISTANT_IMAGE_URI'
  | 'BUBBLE_USER_IMAGE_LAYOUT'
  | 'BUBBLE_ASSISTANT_IMAGE_LAYOUT'
  | 'BUBBLE_USER_CONTENT_INSETS'
  | 'BUBBLE_ASSISTANT_CONTENT_INSETS'
  | 'AVATAR_SHAPE'
  | 'AVATAR_CORNER_RADIUS'
  | 'COMPOSER_TRANSPARENT'
  | 'COMPOSER_FLOATING'
  | 'COMPOSER_LIQUID_GLASS'
  | 'COMPOSER_WATER_GLASS'
  | 'CHROME_STATUS_BAR_HIDDEN'
  | 'CHROME_STATUS_BAR_TRANSPARENT'
  | 'CHROME_STATUS_BAR_COLOR'
  | 'CHROME_TOOLBAR_TRANSPARENT'
  | 'CHROME_TOOLBAR_COLOR'
  | 'CHROME_NAVIGATION_WATER_GLASS'
  | 'CHROME_NAVIGATION_BUTTON_LIQUID_GLASS'
  | 'CHROME_NAVIGATION_BACKGROUND_COLOR'
  | 'CHROME_NAVIGATION_ACCENT_COLOR'
  | 'CHROME_CHAT_HEADER_TRANSPARENT'
  | 'CHROME_CHAT_HEADER_OVERLAY_MODE'
  | 'CHROME_APP_BAR_CONTENT_COLOR_MODE'
  | 'CHROME_CHAT_HEADER_HISTORY_ICON_COLOR'
  | 'CHROME_CHAT_HEADER_PIP_ICON_COLOR';

export interface ThemeParameterDefinition {
  id: string;
  type: ThemeParameterType;
  defaultValue: ThemeParameterValue | null;
  label: ThemePackageLocalizedText;
  description: ThemePackageLocalizedText | null;
  control: ThemeParameterControl;
  effects: ThemeParameterEffect[];
  visibility: ThemeParameterVisibility;
  section: ThemeParameterSection | null;
  order: number;
  visibleWhen: ThemeParameterCondition[];
}

export interface ThemeMaterialProjection {
  colors: ThemeMaterialColorScheme;
  typography: ThemeTypography;
  shapes: ThemeShapes;
}

export interface ThemeMaterialColorScheme {
  [role: string]: string;
}

export interface ThemeTypography {
  family: 'DEFAULT' | 'SANS_SERIF' | 'SERIF' | 'MONOSPACE' | 'CURSIVE';
  displayScale: number;
  titleScale: number;
  bodyScale: number;
  labelScale: number;
  letterSpacingEm: number;
}

export interface ThemeShapes {
  extraSmallDp: number;
  smallDp: number;
  mediumDp: number;
  largeDp: number;
  extraLargeDp: number;
}

export interface ThemeComponentSkin {
  normal: ThemeComponentStateSkin;
  disabled?: ThemeComponentStateSkin | null;
  selected?: ThemeComponentStateSkin | null;
  focused?: ThemeComponentStateSkin | null;
  error?: ThemeComponentStateSkin | null;
}

export interface ThemeComponentStateSkin {
  containerToken: string;
  contentToken: string;
  frame: ThemeComponentFrame;
  elevationDp: number;
  contentPadding: ThemeInsets;
}

export type ThemeComponentFrame =
  | { kind: 'none' }
  | { kind: 'round_rect'; cornerRadiusDp: number; border?: ThemeStroke | null }
  | { kind: 'cut_corners'; cutSizeDp: number; border?: ThemeStroke | null; accent?: ThemeStroke | null }
  | {
      kind: 'hud_notched';
      cutSizeDp: number;
      notchWidthFraction: number;
      notchDepthDp: number;
      border?: ThemeStroke | null;
      accent?: ThemeStroke | null;
    }
  | { kind: 'corner_brackets'; cornerCutDp: number; bracketLengthDp: number; border?: ThemeStroke | null; accent?: ThemeStroke | null }
  | {
      kind: 'segmented_rail';
      cornerCutDp: number;
      railInsetDp: number;
      segmentLengthDp: number;
      border?: ThemeStroke | null;
      accent?: ThemeStroke | null;
    };

export interface ThemeStroke {
  token: string;
  widthDp: number;
}

export interface ThemeInsets {
  startDp: number;
  topDp: number;
  endDp: number;
  bottomDp: number;
}

export interface ThemeBackgroundPresentation {
  enabled: boolean;
  mediaType: 'NONE' | 'IMAGE' | 'VIDEO';
  opacity: number;
  blurEnabled: boolean;
  blurRadiusDp: number;
  videoMuted: boolean;
  videoLoop: boolean;
}

export interface ThemeTypographyPresentation {
  useCustomFont: boolean;
  family: ThemeTypography['family'];
  scale: number;
}

export interface ThemeConversationPresentation {
  cursorUserBubbleFollowTheme: boolean;
  cursorUserBubbleLiquidGlass: boolean;
  cursorUserBubbleWaterGlass: boolean;
  cursorUserBubbleColorArgb: number | null;
  bubbleShowAvatar: boolean;
  bubbleWideLayout: boolean;
  bubbleUserLiquidGlass: boolean;
  bubbleUserWaterGlass: boolean;
  bubbleAssistantLiquidGlass: boolean;
  bubbleAssistantWaterGlass: boolean;
  bubbleImageRenderMode: 'TILED_NINE_SLICE' | 'NINE_PATCH';
  bubbleUserRoundedCorners: boolean;
  bubbleAssistantRoundedCorners: boolean;
  bubbleUserColorArgb: number | null;
  bubbleAssistantColorArgb: number | null;
  bubbleUserTextColorArgb: number | null;
  bubbleAssistantTextColorArgb: number | null;
  bubbleUserUseCustomFont: boolean;
  bubbleUserFontFamily: ThemeTypography['family'];
  bubbleAssistantUseCustomFont: boolean;
  bubbleAssistantFontFamily: ThemeTypography['family'];
  avatarShape: 'CIRCLE' | 'SQUARE' | 'ROUNDED';
  avatarCornerRadiusDp: number;
}

export interface ThemeComposerPresentation {
  transparent: boolean;
  floating: boolean;
  liquidGlass: boolean;
  waterGlass: boolean;
}

export interface ThemeChromePresentation {
  statusBarHidden: boolean;
  statusBarTransparent: boolean;
  statusBarColorArgb: number | null;
  toolbarTransparent: boolean;
  toolbarColorArgb: number | null;
  navigationWaterGlass: boolean;
  navigationButtonLiquidGlass: boolean;
  navigationBackgroundColorArgb: number | null;
  navigationAccentColorArgb: number | null;
  chatHeaderTransparent: boolean;
  chatHeaderOverlayMode: 'NONE' | 'OVERLAY';
  appBarContentColorMode: 'AUTO' | 'LIGHT' | 'DARK';
  chatHeaderHistoryIconColorArgb: number | null;
  chatHeaderPipIconColorArgb: number | null;
}

export interface ThemePackagePresentationBehavior {
  background: ThemeBackgroundPresentation;
  typography: ThemeTypographyPresentation;
  conversation: ThemeConversationPresentation;
  composer: ThemeComposerPresentation;
  chrome: ThemeChromePresentation;
}

export interface ThemePackagePresentation {
  material: ThemeMaterialProjection | null;
  componentSkins: Record<string, ThemeComponentSkin>;
  behavior: ThemePackagePresentationBehavior;
}

export interface ThemeSurfaceImplementation {
  surfaceId: string;
  kind: ThemeSurfaceKind;
  sceneId: string | null;
}

export interface ThemeSceneColorToken {
  type: 'color';
  lightArgb: number;
  darkArgb: number;
}

export interface ThemePackageManifest {
  schemaVersion: number;
  packageId: string;
  version: string;
  displayName: ThemePackageLocalizedText;
  author: ThemePackageLocalizedText | null;
  description: ThemePackageLocalizedText | null;
  attribution: ThemePackageAttribution | null;
  basis: ThemePackageCoordinate | null;
  variants: ThemePackageVariant[];
  parameters: ThemeParameterDefinition[];
  assets: ThemePackageAssetEntry[];
  scenes: unknown[];
  surfaces: ThemeSurfaceImplementation[];
  presentation: ThemePackagePresentation;
  tokens: Record<string, ThemeSceneColorToken> | null;
}

export interface ThemePackageDocument {
  manifest: ThemePackageManifest;
  assetArchiveEntries: Map<string, { path: string; kind: ThemeAssetKind }>;
  archiveSha256: string;
}

export function decodeThemePackageManifest(raw: string): ThemePackageManifest {
  const json = decodeStrictObject(JSON.parse(raw) as unknown, 'manifest');
  const manifest: ThemePackageManifest = {
    schemaVersion: decodeNumber(json, 'schemaVersion', 4),
    packageId: decodeString(json, 'packageId'),
    version: decodeString(json, 'version'),
    displayName: decodeLocalizedText(json, 'displayName'),
    author: decodeOptionalLocalizedText(json, 'author'),
    description: decodeOptionalLocalizedText(json, 'description'),
    attribution: decodeAttribution(json),
    basis: decodeOptionalBasis(json),
    variants: decodeVariants(json),
    parameters: decodeParameterDefinitions(json),
    assets: decodeAssets(json),
    scenes: decodeArray(json, 'scenes'),
    surfaces: decodeSurfaces(json),
    presentation: decodePresentation(json),
    tokens: decodeTokens(json)
  };
  assertNoUnknownKeys(json, MANIFEST_KEYS, 'manifest');
  return manifest;
}

const MANIFEST_KEYS = new Set([
  'schemaVersion',
  'packageId',
  'version',
  'displayName',
  'author',
  'description',
  'attribution',
  'basis',
  'variants',
  'parameters',
  'assets',
  'scenes',
  'surfaces',
  'presentation',
  'tokens'
]);

const PRESENTATION_KEYS = new Set(['material', 'componentSkins', 'behavior']);
const BEHAVIOR_KEYS = new Set(['background', 'typography', 'conversation', 'composer', 'chrome']);
const BACKGROUND_KEYS = new Set([
  'enabled',
  'mediaType',
  'opacity',
  'blurEnabled',
  'blurRadiusDp',
  'videoMuted',
  'videoLoop'
]);
const TYPOGRAPHY_KEYS = new Set(['useCustomFont', 'family', 'scale']);
const CONVERSATION_KEYS = new Set([
  'cursorUserBubbleFollowTheme',
  'cursorUserBubbleLiquidGlass',
  'cursorUserBubbleWaterGlass',
  'cursorUserBubbleColorArgb',
  'bubbleShowAvatar',
  'bubbleWideLayout',
  'bubbleUserLiquidGlass',
  'bubbleUserWaterGlass',
  'bubbleAssistantLiquidGlass',
  'bubbleAssistantWaterGlass',
  'bubbleImageRenderMode',
  'bubbleUserRoundedCorners',
  'bubbleAssistantRoundedCorners',
  'bubbleUserColorArgb',
  'bubbleAssistantColorArgb',
  'bubbleUserTextColorArgb',
  'bubbleAssistantTextColorArgb',
  'bubbleUserUseCustomFont',
  'bubbleUserFontFamily',
  'bubbleAssistantUseCustomFont',
  'bubbleAssistantFontFamily',
  'avatarShape',
  'avatarCornerRadiusDp'
]);
const COMPOSER_KEYS = new Set(['transparent', 'floating', 'liquidGlass', 'waterGlass']);
const CHROME_KEYS = new Set([
  'statusBarHidden',
  'statusBarTransparent',
  'statusBarColorArgb',
  'toolbarTransparent',
  'toolbarColorArgb',
  'navigationWaterGlass',
  'navigationButtonLiquidGlass',
  'navigationBackgroundColorArgb',
  'navigationAccentColorArgb',
  'chatHeaderTransparent',
  'chatHeaderOverlayMode',
  'appBarContentColorMode',
  'chatHeaderHistoryIconColorArgb',
  'chatHeaderPipIconColorArgb'
]);
const PARAMETER_KEYS = new Set([
  'id',
  'type',
  'defaultValue',
  'label',
  'description',
  'control',
  'effects',
  'visibility',
  'section',
  'order',
  'visibleWhen'
]);
const ASSET_KEYS = new Set(['key', 'path', 'kind', 'sha256', 'byteSize']);
const FIELD_KEYS = new Set(['id', 'label']);

function decodeStrictObject(value: unknown, path: string): JsonRecord {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new ThemeManifestError(`${path} 必须是 JSON 对象`);
  }
  return value as JsonRecord;
}

function decodeNumber(json: JsonRecord, key: string, expected?: number): number {
  const value = json[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new ThemeManifestError(`${key} 必须是有限数字`);
  }
  if (expected !== undefined && value !== expected) {
    throw new ThemeManifestError(`${key} 必须等于 ${expected}`);
  }
  return value;
}

function decodeString(json: JsonRecord, key: string): string {
  const value = json[key];
  if (typeof value !== 'string' || value.length === 0) {
    throw new ThemeManifestError(`${key} 必须是非空字符串`);
  }
  return value;
}

function decodeOptionalString(json: JsonRecord, key: string): string | null {
  const value = json[key];
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value !== 'string' || value.length === 0) {
    throw new ThemeManifestError(`${key} 必须是字符串`);
  }
  return value;
}

function decodeBoolean(json: JsonRecord, key: string): boolean {
  const value = json[key];
  if (typeof value !== 'boolean') {
    throw new ThemeManifestError(`${key} 必须是布尔值`);
  }
  return value;
}

function decodeArray(json: JsonRecord, key: string): unknown[] {
  const value = json[key];
  if (!Array.isArray(value)) {
    throw new ThemeManifestError(`${key} 必须是数组`);
  }
  return value;
}

function decodeLocalizedText(json: JsonRecord, key: string): ThemePackageLocalizedText {
  const wrapper = decodeStrictObject(json[key], key);
  assertNoUnknownKeys(wrapper, new Set(['values']), `${key}（本地化文本）`);
  const value = decodeStrictObject(wrapper['values'], `${key}.values`);
  if (typeof value['*'] !== 'string' || value['*'].length === 0) {
    throw new ThemeManifestError(`${key}.values 必须包含必填的 "*" 默认文案`);
  }
  for (const [locale, text] of Object.entries(value)) {
    if (typeof text !== 'string' || text.length === 0) {
      throw new ThemeManifestError(`${key}.values.${locale} 必须是非空字符串`);
    }
  }
  return value as unknown as ThemePackageLocalizedText;
}

function decodeOptionalLocalizedText(
  json: JsonRecord,
  key: string
): ThemePackageLocalizedText | null {
  const value = json[key];
  if (value === undefined || value === null) {
    return null;
  }
  return decodeLocalizedText({ [key]: value }, key);
}

function decodeAttribution(json: JsonRecord): ThemePackageAttribution | null {
  const value = json['attribution'];
  if (value === undefined || value === null) {
    return null;
  }
  const attribution = decodeStrictObject(value, 'attribution');
  assertNoUnknownKeys(attribution, new Set(['text', 'sourceUrl']), 'attribution');
  const sourceUrl = decodeString(attribution, 'sourceUrl');
  if (sourceUrl.startsWith('https://') !== true) {
    throw new ThemeManifestError('attribution.sourceUrl 必须以 https:// 开头');
  }
  return {
    text: decodeLocalizedText(attribution, 'text'),
    sourceUrl
  };
}

function decodeOptionalBasis(json: JsonRecord): ThemePackageCoordinate | null {
  const value = json['basis'];
  if (value === undefined || value === null) {
    return null;
  }
  const basis = decodeStrictObject(value, 'basis');
  assertNoUnknownKeys(basis, new Set(['packageId', 'version', 'archiveSha256']), 'basis');
  return {
    packageId: decodeString(basis, 'packageId'),
    version: decodeString(basis, 'version'),
    archiveSha256: decodeString(basis, 'archiveSha256')
  };
}

function decodeVariants(json: JsonRecord): ThemePackageVariant[] {
  if (json['variants'] === undefined) {
    return [];
  }
  return decodeArray(json, 'variants').map((entry, index) => {
    const variant = decodeStrictObject(entry, `variants[${index}]`);
    assertNoUnknownKeys(variant, FIELD_KEYS, `variants[${index}]`);
    return {
      id: decodeString(variant, 'id'),
      label: decodeLocalizedText(variant, 'label')
    };
  });
}

function decodeParameterDefinitions(json: JsonRecord): ThemeParameterDefinition[] {
  return decodeArray(json, 'parameters').map((entry, index) => {
    const definition = decodeStrictObject(entry, `parameters[${index}]`);
    assertNoUnknownKeys(definition, PARAMETER_KEYS, `parameters[${index}]`);
    return {
      id: decodeString(definition, 'id'),
      type: decodeEnum(definition, 'type', THEME_PARAMETER_TYPES, `parameters[${index}]`),
      defaultValue: decodeOptionalParameterValue(definition, index),
      label: decodeLocalizedText(definition, 'label'),
      description: decodeOptionalLocalizedText(definition, 'description'),
      control: decodeControl(definition, index),
      effects: decodeEffects(definition, index),
      visibility: decodeVisibility(definition),
      section: decodeSection(definition),
      order: definition['order'] === undefined ? 0 : decodeNumber(definition, 'order'),
      visibleWhen: decodeConditions(definition, index)
    };
  });
}

const THEME_PARAMETER_TYPES: ThemeParameterType[] = [
  'COLOR',
  'COLOR_PAIR',
  'BOOLEAN',
  'OPTION',
  'FLOAT',
  'IMAGE_URI',
  'VIDEO_URI',
  'FONT_URI',
  'IMAGE_LAYOUT',
  'INSETS',
  'CORNER_RADIUS'
];

function decodeEnum<T extends string>(
  json: JsonRecord,
  key: string,
  allowed: readonly T[],
  path: string
): T {
  const value = json[key];
  if (typeof value !== 'string' || !(allowed as readonly string[]).includes(value)) {
    throw new ThemeManifestError(`${path}.${key} 必须是 ${allowed.join('|')} 之一`);
  }
  return value as T;
}

function decodeOptionalEnum<T extends string>(
  json: JsonRecord,
  key: string,
  allowed: readonly T[],
  fallback: T | null,
  path: string
): T | null {
  const value = json[key];
  if (value === undefined || value === null) {
    return fallback;
  }
  return decodeEnum(json, key, allowed, path);
}

function decodeVisibility(json: JsonRecord): ThemeParameterVisibility {
  const value = json['visibility'];
  if (value === undefined || value === null) {
    return 'AUTHOR';
  }
  return decodeEnum(json, 'visibility', ['USER', 'AUTHOR'] as const, 'visibility');
}

function decodeSection(json: JsonRecord): ThemeParameterSection | null {
  const value = json['section'];
  if (value === undefined || value === null) {
    return null;
  }
  return decodeEnum(
    json,
    'section',
    ['APPEARANCE', 'CONVERSATION', 'COMPOSER', 'APP_CHROME'] as const,
    'section'
  );
}

function decodeOptionalParameterValue(
  definition: JsonRecord,
  index: number
): ThemeParameterValue | null {
  const value = definition['defaultValue'];
  if (value === undefined || value === null) {
    return null;
  }
  const v = decodeStrictObject(value, `parameters[${index}].defaultValue`);
  const type = decodeString(v, 'type');
  switch (type) {
    case 'color':
      return { type, argb: decodeArgb(v, 'argb', `parameters[${index}].defaultValue`) };
    case 'color_pair':
      return {
        type,
        lightArgb: decodeArgb(v, 'lightArgb', `parameters[${index}].defaultValue`),
        darkArgb: decodeArgb(v, 'darkArgb', `parameters[${index}].defaultValue`)
      };
    case 'boolean':
      return { type, value: decodeBoolean(v, 'value') };
    case 'option':
      return { type, value: decodeString(v, 'value') };
    case 'float':
      return { type, value: decodeFloatFinite(v, 'value', `parameters[${index}].defaultValue`) };
    case 'image_uri':
    case 'video_uri':
    case 'font_uri':
      return {
        type,
        uri: decodeContentUri(v, 'uri', `parameters[${index}].defaultValue`)
      };
    case 'image_layout':
      return decodeImageLayout(v, `parameters[${index}].defaultValue`);
    case 'insets':
      return {
        type,
        startDp: decodeDp(v, 'startDp', `parameters[${index}].defaultValue`),
        topDp: decodeDp(v, 'topDp', `parameters[${index}].defaultValue`),
        endDp: decodeDp(v, 'endDp', `parameters[${index}].defaultValue`),
        bottomDp: decodeDp(v, 'bottomDp', `parameters[${index}].defaultValue`)
      };
    case 'corner_radius':
      return {
        type,
        valueDp: decodeDp(v, 'valueDp', `parameters[${index}].defaultValue`)
      };
    default:
      throw new ThemeManifestError(
        `parameters[${index}].defaultValue.type 不支持: ${String(type)}`
      );
  }
}

function decodeArgb(json: JsonRecord, key: string, path: string): number {
  const value = json[key];
  if (
    typeof value !== 'number' ||
    value < 0 ||
    value > 0xffffffff ||
    !Number.isInteger(value)
  ) {
    throw new ThemeManifestError(`${path}.${key} 必须是 0..0xffffffff 的整数 ARGB`);
  }
  return value;
}

function decodeFloatFinite(json: JsonRecord, key: string, path: string): number {
  const value = json[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new ThemeManifestError(`${path}.${key} 必须是有限数字`);
  }
  return value;
}

function decodeContentUri(json: JsonRecord, key: string, path: string): string {
  const value = decodeString(json, key);
  if (!value.startsWith('content://')) {
    throw new ThemeManifestError(`${path}.${key} 必须是 content:// URI`);
  }
  return value;
}

function decodeDp(json: JsonRecord, key: string, path: string): number {
  const value = decodeFloatFinite(json, key, path);
  if (value < 0 || value > 96) {
    throw new ThemeManifestError(`${path}.${key} 必须在 [0, 96] dp 内`);
  }
  return value;
}

function decodeImageLayout(
  json: JsonRecord,
  path: string
): Extract<ThemeParameterValue, { type: 'image_layout' }> {
  const inUnit = (json: JsonRecord, key: string): number => {
    const value = decodeFloatFinite(json, key, path);
    if (value < 0 || value > 1) {
      throw new ThemeManifestError(`${path}.${key} 必须在 [0, 1] 内`);
    }
    return value;
  };
  const cropLeft = inUnit(json, 'cropLeft');
  const cropTop = inUnit(json, 'cropTop');
  const cropRight = inUnit(json, 'cropRight');
  const cropBottom = inUnit(json, 'cropBottom');
  const repeatStart = inUnit(json, 'repeatStart');
  const repeatEnd = inUnit(json, 'repeatEnd');
  const repeatYStart = inUnit(json, 'repeatYStart');
  const repeatYEnd = inUnit(json, 'repeatYEnd');
  if (cropRight < cropLeft || cropBottom < cropTop) {
    throw new ThemeManifestError(`${path} crop 结束不能小于起始`);
  }
  if (repeatEnd < repeatStart || repeatYEnd < repeatYStart) {
    throw new ThemeManifestError(`${path} repeat 结束不能小于起始`);
  }
  const scale = decodeFloatFinite(json, 'scale', path);
  if (scale < 0.1 || scale > 8) {
    throw new ThemeManifestError(`${path}.scale 必须在 [0.1, 8] 内`);
  }
  return {
    type: 'image_layout',
    cropLeft,
    cropTop,
    cropRight,
    cropBottom,
    repeatStart,
    repeatEnd,
    repeatYStart,
    repeatYEnd,
    scale
  };
}

function decodeControl(definition: JsonRecord, index: number): ThemeParameterControl {
  const control = decodeStrictObject(definition['control'], `parameters[${index}].control`);
  const type = decodeString(control, 'type');
  const path = `parameters[${index}].control`;
  switch (type) {
    case 'color_palette':
      return {
        type,
        presetArgb: decodeArgbArray(control, 'presetArgb', path),
        allowCustom: decodeOptionalBoolean(control, 'allowCustom', true)
      };
    case 'color_pair_palette':
      return {
        type,
        lightPresetArgb: decodeArgbArray(control, 'lightPresetArgb', path),
        darkPresetArgb: decodeArgbArray(control, 'darkPresetArgb', path),
        allowCustom: decodeOptionalBoolean(control, 'allowCustom', true)
      };
    case 'toggle':
      return { type };
    case 'choice':
      return {
        type,
        options: decodeArray(control, 'options').map((option, optionIndex) => {
          const o = decodeStrictObject(option, `${path}.options[${optionIndex}]`);
          assertNoUnknownKeys(o, FIELD_KEYS, `${path}.options[${optionIndex}]`);
          return { id: decodeString(o, 'id'), label: decodeLocalizedText(o, 'label') };
        })
      };
    case 'slider':
      return {
        type,
        minimum: decodeFloatFinite(control, 'minimum', path),
        maximum: decodeFloatFinite(control, 'maximum', path),
        step: decodeFloatFinite(control, 'step', path)
      };
    case 'image_picker':
      return { type, mimeTypes: decodeMimeTypes(control, path, DEFAULT_IMAGE_MIME_TYPES) };
    case 'video_picker':
      return { type, mimeTypes: decodeMimeTypes(control, path, DEFAULT_VIDEO_MIME_TYPES) };
    case 'font_picker':
      return { type, mimeTypes: decodeMimeTypes(control, path, DEFAULT_FONT_MIME_TYPES) };
    case 'author_value':
      return { type };
    default:
      throw new ThemeManifestError(`${path}.type 不支持: ${String(type)}`);
  }
}

const DEFAULT_IMAGE_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const DEFAULT_VIDEO_MIME_TYPES = ['video/mp4', 'video/webm'];
const DEFAULT_FONT_MIME_TYPES = ['font/ttf', 'font/otf'];
const ALLOWED_IMAGE_MIME_TYPES = new Set(DEFAULT_IMAGE_MIME_TYPES);
const ALLOWED_VIDEO_MIME_TYPES = new Set(DEFAULT_VIDEO_MIME_TYPES);
const ALLOWED_FONT_MIME_TYPES = new Set(DEFAULT_FONT_MIME_TYPES);
const ALLOWED_MIME_TYPES = new Map<string, Set<string>>([
  ['image_picker', ALLOWED_IMAGE_MIME_TYPES],
  ['video_picker', ALLOWED_VIDEO_MIME_TYPES],
  ['font_picker', ALLOWED_FONT_MIME_TYPES]
]);

function decodeMimeTypes(
  control: JsonRecord,
  path: string,
  defaults: string[]
): string[] {
  const value = control['mimeTypes'];
  const controlType = control['type'];
  const allowed = ALLOWED_MIME_TYPES.get(typeof controlType === 'string' ? controlType : '');
  if (allowed === undefined) {
    return defaults;
  }
  if (value === undefined) {
    return [...allowed];
  }
  const mimes = decodeArray(control, 'mimeTypes');
  for (const mime of mimes) {
    if (typeof mime !== 'string' || !allowed.has(mime)) {
      throw new ThemeManifestError(`${path}.mimeTypes 含不支持类型: ${String(mime)}`);
    }
  }
  return mimes as string[];
}

function decodeArgbArray(json: JsonRecord, key: string, path: string): number[] {
  const value = json[key];
  if (value === undefined) {
    return [];
  }
  const list = decodeArray({ [key]: value }, key);
  return list.map((entry, index) => {
    if (typeof entry !== 'number' || entry < 0 || entry > 0xffffffff) {
      throw new ThemeManifestError(`${path}.${key}[${index}] 必须是 ARGB 整数`);
    }
    return entry;
  });
}

function decodeOptionalBoolean(json: JsonRecord, key: string, fallback: boolean): boolean {
  const value = json[key];
  if (value === undefined) {
    return fallback;
  }
  if (typeof value !== 'boolean') {
    throw new ThemeManifestError(`${key} 必须是布尔值`);
  }
  return value;
}

function decodeEffects(definition: JsonRecord, index: number): ThemeParameterEffect[] {
  return decodeArray(definition, 'effects').map((entry, effectIndex) => {
    const effect = decodeStrictObject(entry, `parameters[${index}].effects[${effectIndex}]`);
    const path = `parameters[${index}].effects[${effectIndex}]`;
    const type = decodeString(effect, 'type');
    switch (type) {
      case 'accent_palette':
        return { type };
      case 'token_color':
      case 'token_color_pair':
        return { type, tokenIds: decodeStringArray(effect, 'tokenIds', path) };
      case 'stage_image':
        return {
          type,
          surfaceIds: decodeStringArray(effect, 'surfaceIds', path),
          fit: decodeEnum(effect, 'fit', ['FILL', 'FIT', 'CROP'], path),
          opacity: decodeFloatIn(0, 1, effect, 'opacity', path)
        };
      case 'typography_scale':
      case 'shape_scale':
        return { type };
      case 'component_frame_scale':
      case 'component_content_insets':
        return { type, componentIds: decodeStringArray(effect, 'componentIds', path) };
      case 'presentation':
        return {
          type,
          targets: decodeStringArray(effect, 'targets', path) as ThemePresentationTarget[]
        };
      default:
        throw new ThemeManifestError(`${path}.type 不支持: ${String(type)}`);
    }
  });
}

function decodeStringArray(json: JsonRecord, key: string, path: string): string[] {
  return decodeArray(json, key).map((entry, entryIndex) => {
    if (typeof entry !== 'string' || entry.length === 0) {
      throw new ThemeManifestError(`${path}.${key}[${entryIndex}] 必须是非空字符串`);
    }
    return entry;
  });
}

function decodeFloatIn(
  minimum: number,
  maximum: number,
  json: JsonRecord,
  key: string,
  path: string
): number {
  const value = decodeFloatFinite(json, key, path);
  if (value < minimum || value > maximum) {
    throw new ThemeManifestError(`${path}.${key} 必须在 [${minimum}, ${maximum}] 内`);
  }
  return value;
}

function decodeConditions(definition: JsonRecord, index: number): ThemeParameterCondition[] {
  const value = definition['visibleWhen'];
  if (value === undefined || value === null) {
    return [];
  }
  return decodeArray(definition, 'visibleWhen').map((entry, conditionIndex) => {
    const condition = decodeStrictObject(entry, `parameters[${index}].visibleWhen[${conditionIndex}]`);
    const path = `parameters[${index}].visibleWhen[${conditionIndex}]`;
    const type = decodeString(condition, 'type');
    switch (type) {
      case 'boolean_equals':
        return {
          type,
          parameterId: decodeString(condition, 'parameterId'),
          expected: decodeBoolean(condition, 'expected')
        };
      case 'option_equals':
        return {
          type,
          parameterId: decodeString(condition, 'parameterId'),
          expected: decodeString(condition, 'expected')
        };
      case 'resource_present':
        return { type, parameterId: decodeString(condition, 'parameterId') };
      default:
        throw new ThemeManifestError(`${path}.type 不支持: ${String(type)}`);
    }
  });
}

function decodeAssets(json: JsonRecord): ThemePackageAssetEntry[] {
  const value = json['assets'];
  if (value === undefined) {
    return [];
  }
  return decodeArray(json, 'assets').map((entry, index) => {
    const asset = decodeStrictObject(entry, `assets[${index}]`);
    assertNoUnknownKeys(asset, ASSET_KEYS, `assets[${index}]`);
    return {
      key: decodeString(asset, 'key'),
      path: decodeString(asset, 'path'),
      kind: decodeEnum(asset, 'kind', ['BITMAP', 'NINE_SLICE', 'FONT', 'PATH'], `assets[${index}]`),
      sha256: decodeString(asset, 'sha256'),
      byteSize: decodePositiveNumber(asset, 'byteSize', `assets[${index}]`)
    };
  });
}

function decodePositiveNumber(json: JsonRecord, key: string, path: string): number {
  const value = json[key];
  if (typeof value !== 'number' || !Number.isInteger(value) || value <= 0) {
    throw new ThemeManifestError(`${path}.${key} 必须是正整数`);
  }
  return value;
}

function decodeSurfaces(json: JsonRecord): ThemeSurfaceImplementation[] {
  return decodeArray(json, 'surfaces').map((entry, index) => {
    const surface = decodeStrictObject(entry, `surfaces[${index}]`);
    assertNoUnknownKeys(surface, new Set(['surfaceId', 'kind', 'sceneId']), `surfaces[${index}]`);
    return {
      surfaceId: decodeString(surface, 'surfaceId'),
      kind: decodeEnum(surface, 'kind', ['SCENE', 'TEMPLATE', 'HOST_SHELL'], `surfaces[${index}]`),
      sceneId: decodeOptionalString(surface, 'sceneId')
    };
  });
}

function decodePresentation(json: JsonRecord): ThemePackagePresentation {
  const presentation = decodeStrictObject(json['presentation'], 'presentation');
  assertNoUnknownKeys(presentation, PRESENTATION_KEYS, 'presentation');
  const materialRaw = presentation['material'];
  const behaviorRaw = presentation['behavior'];
  if (behaviorRaw === undefined || behaviorRaw === null) {
    throw new ThemeManifestError('presentation.behavior 必填');
  }
  const behavior = decodeStrictObject(behaviorRaw, 'presentation.behavior');
  assertNoUnknownKeys(behavior, BEHAVIOR_KEYS, 'presentation.behavior');
  return {
    material:
      materialRaw === undefined || materialRaw === null
        ? null
        : decodeMaterial(materialRaw),
    componentSkins:
      presentation['componentSkins'] === undefined
        ? {}
        : (decodeStrictObject(presentation['componentSkins'], 'presentation.componentSkins') as Record<string, ThemeComponentSkin>),
    behavior: decodeBehavior(behavior)
  };
}

const MATERIAL_KEYS = new Set(['colors', 'typography', 'shapes']);

function decodeMaterial(raw: unknown): ThemeMaterialProjection {
  const material = decodeStrictObject(raw, 'presentation.material');
  assertNoUnknownKeys(material, MATERIAL_KEYS, 'presentation.material');
  const colorsRaw = decodeStrictObject(material['colors'], 'presentation.material.colors');
  const colors: Record<string, string> = {};
  for (const [role, tokenId] of Object.entries(colorsRaw)) {
    if (typeof tokenId !== 'string' || tokenId.length === 0) {
      throw new ThemeManifestError(`presentation.material.colors.${role} 必须是非空 token id`);
    }
    colors[role] = tokenId;
  }
  const typographyRaw = decodeStrictObject(material['typography'], 'presentation.material.typography');
  assertNoUnknownKeys(
    typographyRaw,
    new Set(['family', 'displayScale', 'titleScale', 'bodyScale', 'labelScale', 'letterSpacingEm']),
    'presentation.material.typography'
  );
  const shapesRaw = decodeStrictObject(material['shapes'], 'presentation.material.shapes');
  assertNoUnknownKeys(
    shapesRaw,
    new Set(['extraSmallDp', 'smallDp', 'mediumDp', 'largeDp', 'extraLargeDp']),
    'presentation.material.shapes'
  );
  return {
    colors,
    typography: {
      family: decodeTypographyFamily(typographyRaw, 'family'),
      displayScale: decodeFloatFinite(typographyRaw, 'displayScale', 'presentation.material.typography'),
      titleScale: decodeFloatFinite(typographyRaw, 'titleScale', 'presentation.material.typography'),
      bodyScale: decodeFloatFinite(typographyRaw, 'bodyScale', 'presentation.material.typography'),
      labelScale: decodeFloatFinite(typographyRaw, 'labelScale', 'presentation.material.typography'),
      letterSpacingEm: decodeFloatFinite(typographyRaw, 'letterSpacingEm', 'presentation.material.typography')
    },
    shapes: {
      extraSmallDp: decodeFloatFinite(shapesRaw, 'extraSmallDp', 'presentation.material.shapes'),
      smallDp: decodeFloatFinite(shapesRaw, 'smallDp', 'presentation.material.shapes'),
      mediumDp: decodeFloatFinite(shapesRaw, 'mediumDp', 'presentation.material.shapes'),
      largeDp: decodeFloatFinite(shapesRaw, 'largeDp', 'presentation.material.shapes'),
      extraLargeDp: decodeFloatFinite(shapesRaw, 'extraLargeDp', 'presentation.material.shapes')
    }
  };
}

function decodeBehavior(json: JsonRecord): ThemePackagePresentationBehavior {
  const background = decodeStrictObject(json['background'], 'presentation.behavior.background');
  assertNoUnknownKeys(background, BACKGROUND_KEYS, 'presentation.behavior.background');
  const typography = decodeStrictObject(json['typography'], 'presentation.behavior.typography');
  assertNoUnknownKeys(typography, TYPOGRAPHY_KEYS, 'presentation.behavior.typography');
  const conversation = decodeStrictObject(
    json['conversation'],
    'presentation.behavior.conversation'
  );
  assertNoUnknownKeys(conversation, CONVERSATION_KEYS, 'presentation.behavior.conversation');
  const composer = decodeStrictObject(json['composer'], 'presentation.behavior.composer');
  assertNoUnknownKeys(composer, COMPOSER_KEYS, 'presentation.behavior.composer');
  const chrome = decodeStrictObject(json['chrome'], 'presentation.behavior.chrome');
  assertNoUnknownKeys(chrome, CHROME_KEYS, 'presentation.behavior.chrome');

  return {
    background: {
      enabled: decodeBoolean(background, 'enabled'),
      mediaType: decodeEnum(
        background,
        'mediaType',
        ['NONE', 'IMAGE', 'VIDEO'],
        'presentation.behavior.background'
      ),
      opacity: decodeFloatIn(0, 1, background, 'opacity', 'presentation.behavior.background'),
      blurEnabled: decodeBoolean(background, 'blurEnabled'),
      blurRadiusDp: decodeFloatIn(0, 96, background, 'blurRadiusDp', 'presentation.behavior.background'),
      videoMuted: decodeBoolean(background, 'videoMuted'),
      videoLoop: decodeBoolean(background, 'videoLoop')
    },
    typography: {
      useCustomFont: decodeBoolean(typography, 'useCustomFont'),
      family: decodeTypographyFamily(typography, 'family'),
      scale: decodeFloatIn(0.5, 2, typography, 'scale', 'presentation.behavior.typography')
    },
    conversation: {
      cursorUserBubbleFollowTheme: decodeBoolean(conversation, 'cursorUserBubbleFollowTheme'),
      cursorUserBubbleLiquidGlass: decodeBoolean(conversation, 'cursorUserBubbleLiquidGlass'),
      cursorUserBubbleWaterGlass: decodeBoolean(conversation, 'cursorUserBubbleWaterGlass'),
      cursorUserBubbleColorArgb: decodeOptionalArgb(conversation, 'cursorUserBubbleColorArgb'),
      bubbleShowAvatar: decodeBoolean(conversation, 'bubbleShowAvatar'),
      bubbleWideLayout: decodeBoolean(conversation, 'bubbleWideLayout'),
      bubbleUserLiquidGlass: decodeBoolean(conversation, 'bubbleUserLiquidGlass'),
      bubbleUserWaterGlass: decodeBoolean(conversation, 'bubbleUserWaterGlass'),
      bubbleAssistantLiquidGlass: decodeBoolean(conversation, 'bubbleAssistantLiquidGlass'),
      bubbleAssistantWaterGlass: decodeBoolean(conversation, 'bubbleAssistantWaterGlass'),
      bubbleImageRenderMode: decodeEnum(
        conversation,
        'bubbleImageRenderMode',
        ['TILED_NINE_SLICE', 'NINE_PATCH'],
        'presentation.behavior.conversation'
      ),
      bubbleUserRoundedCorners: decodeBoolean(conversation, 'bubbleUserRoundedCorners'),
      bubbleAssistantRoundedCorners: decodeBoolean(conversation, 'bubbleAssistantRoundedCorners'),
      bubbleUserColorArgb: decodeOptionalArgb(conversation, 'bubbleUserColorArgb'),
      bubbleAssistantColorArgb: decodeOptionalArgb(conversation, 'bubbleAssistantColorArgb'),
      bubbleUserTextColorArgb: decodeOptionalArgb(conversation, 'bubbleUserTextColorArgb'),
      bubbleAssistantTextColorArgb: decodeOptionalArgb(conversation, 'bubbleAssistantTextColorArgb'),
      bubbleUserUseCustomFont: decodeBoolean(conversation, 'bubbleUserUseCustomFont'),
      bubbleUserFontFamily: decodeTypographyFamily(conversation, 'bubbleUserFontFamily'),
      bubbleAssistantUseCustomFont: decodeBoolean(conversation, 'bubbleAssistantUseCustomFont'),
      bubbleAssistantFontFamily: decodeTypographyFamily(conversation, 'bubbleAssistantFontFamily'),
      avatarShape: decodeEnum(
        conversation,
        'avatarShape',
        ['CIRCLE', 'SQUARE', 'ROUNDED'],
        'presentation.behavior.conversation'
      ),
      avatarCornerRadiusDp: decodeFloatIn(
        0,
        96,
        conversation,
        'avatarCornerRadiusDp',
        'presentation.behavior.conversation'
      )
    },
    composer: {
      transparent: decodeBoolean(composer, 'transparent'),
      floating: decodeBoolean(composer, 'floating'),
      liquidGlass: decodeBoolean(composer, 'liquidGlass'),
      waterGlass: decodeBoolean(composer, 'waterGlass')
    },
    chrome: {
      statusBarHidden: decodeBoolean(chrome, 'statusBarHidden'),
      statusBarTransparent: decodeBoolean(chrome, 'statusBarTransparent'),
      statusBarColorArgb: decodeOptionalArgb(chrome, 'statusBarColorArgb'),
      toolbarTransparent: decodeBoolean(chrome, 'toolbarTransparent'),
      toolbarColorArgb: decodeOptionalArgb(chrome, 'toolbarColorArgb'),
      navigationWaterGlass: decodeBoolean(chrome, 'navigationWaterGlass'),
      navigationButtonLiquidGlass: decodeBoolean(chrome, 'navigationButtonLiquidGlass'),
      navigationBackgroundColorArgb: decodeOptionalArgb(chrome, 'navigationBackgroundColorArgb'),
      navigationAccentColorArgb: decodeOptionalArgb(chrome, 'navigationAccentColorArgb'),
      chatHeaderTransparent: decodeBoolean(chrome, 'chatHeaderTransparent'),
      chatHeaderOverlayMode: decodeEnum(
        chrome,
        'chatHeaderOverlayMode',
        ['NONE', 'OVERLAY'],
        'presentation.behavior.chrome'
      ),
      appBarContentColorMode: decodeEnum(
        chrome,
        'appBarContentColorMode',
        ['AUTO', 'LIGHT', 'DARK'],
        'presentation.behavior.chrome'
      ),
      chatHeaderHistoryIconColorArgb: decodeOptionalArgb(chrome, 'chatHeaderHistoryIconColorArgb'),
      chatHeaderPipIconColorArgb: decodeOptionalArgb(chrome, 'chatHeaderPipIconColorArgb')
    }
  };
}

function decodeTypographyFamily(json: JsonRecord, key: string): ThemeTypography['family'] {
  return decodeEnum(
    json,
    key,
    ['DEFAULT', 'SANS_SERIF', 'SERIF', 'MONOSPACE', 'CURSIVE'],
    key
  );
}

function decodeOptionalArgb(json: JsonRecord, key: string): number | null {
  const value = json[key];
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value !== 'number' || value < 0 || value > 0xffffffff || !Number.isInteger(value)) {
    throw new ThemeManifestError(`${key} 必须是可以缺省的 ARGB 整数`);
  }
  return value;
}

function decodeTokens(json: JsonRecord): Record<string, ThemeSceneColorToken> | null {
  const value = json['tokens'];
  if (value === undefined || value === null) {
    return null;
  }
  const wrapper = decodeStrictObject(value, 'tokens');
  assertNoUnknownKeys(wrapper, new Set(['tokens']), 'tokens');
  const tokenMap = decodeStrictObject(wrapper['tokens'], 'tokens.tokens');
  const result: Record<string, ThemeSceneColorToken> = {};
  for (const [tokenId, tokenRaw] of Object.entries(tokenMap)) {
    const token = decodeStrictObject(tokenRaw, `tokens.tokens.${tokenId}`);
    assertNoUnknownKeys(
      token,
      new Set(['type', 'lightArgb', 'darkArgb']),
      `tokens.tokens.${tokenId}`
    );
    const type = decodeString(token, 'type');
    if (type !== 'color') {
      throw new ThemeManifestError(`tokens.tokens.${tokenId}.type 仅支持 color`);
    }
    result[tokenId] = {
      type,
      lightArgb: decodeArgb(token, 'lightArgb', `tokens.tokens.${tokenId}`),
      darkArgb: decodeArgb(token, 'darkArgb', `tokens.tokens.${tokenId}`)
    };
  }
  return result;
}

function assertNoUnknownKeys(json: JsonRecord, allowed: Set<string>, path: string): void {
  for (const key of Object.keys(json)) {
    if (!allowed.has(key)) {
      throw new ThemeManifestError(`${path} 含未知字段: ${key}`);
    }
  }
}

export class ThemeManifestError extends Error {
  readonly path: string;

  constructor(message: string, path = 'manifest') {
    super(message);
    this.name = 'ThemeManifestError';
    this.path = path;
  }
}
