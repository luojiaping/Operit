// .otheme 导出：编辑态 → 严格序列化 manifest + 素材清单 + deterministic zip。
// 烘焙规则：非 URI 参数值写 defaultValue；URI 参数不烘焙（schema 4 无包默认），
// 其绑定素材写入 assets[] 供场景/未来使用，导出侧给出提示清单。

import {
  THEME_PACKAGE_EXTENSION,
  THEME_PACKAGE_MANIFEST_ENTRY,
  THEME_PACKAGE_SCHEMA_VERSION,
  THEME_PACKAGE_ZIP_COMMENT
} from './manifest';
import type {
  ThemePackageAssetEntry,
  ThemePackageAttribution,
  ThemePackageCoordinate,
  ThemePackageLocalizedText,
  ThemePackageManifest,
  ThemePackageVariant
} from './manifest';
import type { StudioEditorState } from './editorState';
import type { AssetRecord } from './assets/library';
import { createStoredZip } from '../../preview/zipWriter';
import { sha256Hex, loadThemePackageFromBytes } from './packageLoader';

export interface ThemeExportInput {
  manifest: ThemePackageManifest;
  state: StudioEditorState;
  /** 素材库中当前绑定素材（用于 assets[] 声明） */
  boundAssets: AssetRecord[];
}

export interface ThemeExportPlan {
  manifest: ThemePackageManifest;
  /** URI 参数未烘焙提示清单 */
  unbakedUriParameters: string[];
}

export interface ThemeExportResult {
  blob: Blob;
  sha256: string;
  manifestJsonBytes: number;
  assetCount: number;
}

export function buildExportManifest(input: ThemeExportInput): ThemeExportPlan {
  const { manifest, state, boundAssets } = input;
  const parameters = manifest.parameters.map((parameter) => {
    const value = state.values.get(parameter.id);
    let defaultValue = parameter.defaultValue;
    if (value != null && !isUriValue(value)) {
      defaultValue = value;
    }
    return { ...parameter, defaultValue };
  });

  const unbakedUriParameters = manifest.parameters
    .filter((parameter) => {
      const value = state.values.get(parameter.id);
      return value != null && isUriValue(value);
    })
    .map((parameter) => parameter.id);

  const assets = manifest.assets.map((asset) => ({ ...asset }));
  const existingKeys = new Set(assets.map((asset) => asset.key));
  for (const record of boundAssets) {
    if (!existingKeys.has(record.key)) {
      assets.push({
        key: record.key,
        path: record.path,
        kind: record.kind,
        sha256: record.sha256,
        byteSize: record.byteSize
      });
    }
  }

  return {
    manifest: {
      ...manifest,
      schemaVersion: THEME_PACKAGE_SCHEMA_VERSION,
      parameters,
      assets,
      presentation: {
        ...manifest.presentation,
        componentSkins: Object.fromEntries(state.componentSkins)
      }
    },
    unbakedUriParameters
  };
}

export function isUriValue(value: { type: string }): boolean {
  return value.type === 'image_uri' || value.type === 'video_uri' || value.type === 'font_uri';
}

function localizedText(text: ThemePackageLocalizedText): Record<string, unknown> {
  return { values: text };
}

function optional<T>(value: T | null | undefined): Record<string, unknown> | T | undefined {
  return value == null ? undefined : value;
}

function coordinate(value: ThemePackageCoordinate | null): Record<string, unknown> | undefined {
  if (value == null) {
    return undefined;
  }
  return {
    packageId: value.packageId,
    version: value.version,
    archiveSha256: value.archiveSha256
  };
}

function attribution(value: ThemePackageAttribution | null): Record<string, unknown> | undefined {
  if (value == null) {
    return undefined;
  }
  return { text: localizedText(value.text), sourceUrl: value.sourceUrl };
}

function variants(values: ThemePackageVariant[]): Record<string, unknown>[] {
  return values.map((variant) => ({
    id: variant.id,
    label: localizedText(variant.label)
  }));
}

function parameters(list: ThemePackageManifest['parameters']): Record<string, unknown>[] {
  return list.map((parameter) => ({
    id: parameter.id,
    type: parameter.type,
    ...(parameter.defaultValue == null ? {} : { defaultValue: encodeValue(parameter.defaultValue) }),
    label: localizedText(parameter.label),
    ...(parameter.description == null ? {} : { description: localizedText(parameter.description) }),
    control: encodeControl(parameter.control),
    effects: parameter.effects.map((effect) => encodeEffect(effect)),
    visibility: parameter.visibility,
    ...(parameter.section == null ? {} : { section: parameter.section }),
    order: parameter.order,
    ...(parameter.visibleWhen.length === 0 ? {} : { visibleWhen: parameter.visibleWhen.map((condition) => encodeCondition(condition)) })
  }));
}

function encodeValue(value: NonNullable<ThemePackageManifest['parameters'][number]['defaultValue']>): Record<string, unknown> {
  switch (value.type) {
    case 'color':
      return { type: 'color', argb: value.argb };
    case 'color_pair':
      return { type: 'color_pair', lightArgb: value.lightArgb, darkArgb: value.darkArgb };
    case 'boolean':
      return { type: 'boolean', value: value.value };
    case 'option':
      return { type: 'option', value: value.value };
    case 'float':
      return { type: 'float', value: value.value };
    case 'image_uri':
      return { type: 'image_uri', uri: value.uri };
    case 'video_uri':
      return { type: 'video_uri', uri: value.uri };
    case 'font_uri':
      return { type: 'font_uri', uri: value.uri };
    case 'image_layout':
      return {
        type: 'image_layout',
        cropLeft: value.cropLeft,
        cropTop: value.cropTop,
        cropRight: value.cropRight,
        cropBottom: value.cropBottom,
        repeatStart: value.repeatStart,
        repeatEnd: value.repeatEnd,
        repeatYStart: value.repeatYStart,
        repeatYEnd: value.repeatYEnd,
        scale: value.scale
      };
    case 'insets':
      return { type: 'insets', startDp: value.startDp, topDp: value.topDp, endDp: value.endDp, bottomDp: value.bottomDp };
    case 'corner_radius':
      return { type: 'corner_radius', valueDp: value.valueDp };
  }
}

function encodeControl(control: ThemePackageManifest['parameters'][number]['control']): Record<string, unknown> {
  switch (control.type) {
    case 'color_palette':
      return { type: 'color_palette', presetArgb: control.presetArgb, allowCustom: control.allowCustom };
    case 'color_pair_palette':
      return { type: 'color_pair_palette', lightPresetArgb: control.lightPresetArgb, darkPresetArgb: control.darkPresetArgb, allowCustom: control.allowCustom };
    case 'toggle':
      return { type: 'toggle' };
    case 'choice':
      return { type: 'choice', options: control.options.map((option) => ({ id: option.id, label: localizedText(option.label) })) };
    case 'slider':
      return { type: 'slider', minimum: control.minimum, maximum: control.maximum, step: control.step };
    case 'image_picker':
      return { type: 'image_picker', mimeTypes: control.mimeTypes };
    case 'video_picker':
      return { type: 'video_picker', mimeTypes: control.mimeTypes };
    case 'font_picker':
      return { type: 'font_picker', mimeTypes: control.mimeTypes };
    case 'author_value':
      return { type: 'author_value' };
  }
}

function encodeEffect(effect: ThemePackageManifest['parameters'][number]['effects'][number]): Record<string, unknown> {
  switch (effect.type) {
    case 'accent_palette':
      return { type: 'accent_palette' };
    case 'token_color':
      return { type: 'token_color', tokenIds: effect.tokenIds };
    case 'token_color_pair':
      return { type: 'token_color_pair', tokenIds: effect.tokenIds };
    case 'stage_image':
      return { type: 'stage_image', surfaceIds: effect.surfaceIds, fit: effect.fit, opacity: effect.opacity };
    case 'typography_scale':
      return { type: 'typography_scale' };
    case 'shape_scale':
      return { type: 'shape_scale' };
    case 'component_frame_scale':
      return { type: 'component_frame_scale', componentIds: effect.componentIds };
    case 'component_content_insets':
      return { type: 'component_content_insets', componentIds: effect.componentIds };
    case 'presentation':
      return { type: 'presentation', targets: effect.targets };
  }
}

function encodeCondition(condition: ThemePackageManifest['parameters'][number]['visibleWhen'][number]): Record<string, unknown> {
  switch (condition.type) {
    case 'boolean_equals':
      return { type: 'boolean_equals', parameterId: condition.parameterId, expected: condition.expected };
    case 'option_equals':
      return { type: 'option_equals', parameterId: condition.parameterId, expected: condition.expected };
    case 'resource_present':
      return { type: 'resource_present', parameterId: condition.parameterId };
  }
}

function assets(list: ThemePackageAssetEntry[]): Record<string, unknown>[] {
  return list.map((asset) => ({
    key: asset.key,
    path: asset.path,
    kind: asset.kind,
    sha256: asset.sha256,
    byteSize: asset.byteSize
  }));
}

function presentation(manifest: ThemePackageManifest): Record<string, unknown> {
  const material = manifest.presentation.material;
  return {
    ...(material == null
      ? {}
      : {
          material: {
            colors: material.colors,
            typography: {
              family: material.typography.family,
              displayScale: material.typography.displayScale,
              titleScale: material.typography.titleScale,
              bodyScale: material.typography.bodyScale,
              labelScale: material.typography.labelScale,
              letterSpacingEm: material.typography.letterSpacingEm
            },
            shapes: {
              extraSmallDp: material.shapes.extraSmallDp,
              smallDp: material.shapes.smallDp,
              mediumDp: material.shapes.mediumDp,
              largeDp: material.shapes.largeDp,
              extraLargeDp: material.shapes.extraLargeDp
            }
          }
        }),
    ...(Object.keys(manifest.presentation.componentSkins).length === 0
      ? {}
      : { componentSkins: manifest.presentation.componentSkins }),
    behavior: encodeBehavior(manifest.presentation.behavior)
  };
}

function encodeBehavior(behavior: ThemePackageManifest['presentation']['behavior']): Record<string, unknown> {
  const b = behavior;
  return {
    background: {
      enabled: b.background.enabled,
      mediaType: b.background.mediaType,
      opacity: b.background.opacity,
      blurEnabled: b.background.blurEnabled,
      blurRadiusDp: b.background.blurRadiusDp,
      videoMuted: b.background.videoMuted,
      videoLoop: b.background.videoLoop
    },
    typography: {
      useCustomFont: b.typography.useCustomFont,
      family: b.typography.family,
      scale: b.typography.scale
    },
    conversation: {
      cursorUserBubbleFollowTheme: b.conversation.cursorUserBubbleFollowTheme,
      cursorUserBubbleLiquidGlass: b.conversation.cursorUserBubbleLiquidGlass,
      cursorUserBubbleWaterGlass: b.conversation.cursorUserBubbleWaterGlass,
      cursorUserBubbleColorArgb: b.conversation.cursorUserBubbleColorArgb,
      bubbleShowAvatar: b.conversation.bubbleShowAvatar,
      bubbleWideLayout: b.conversation.bubbleWideLayout,
      bubbleUserLiquidGlass: b.conversation.bubbleUserLiquidGlass,
      bubbleUserWaterGlass: b.conversation.bubbleUserWaterGlass,
      bubbleAssistantLiquidGlass: b.conversation.bubbleAssistantLiquidGlass,
      bubbleAssistantWaterGlass: b.conversation.bubbleAssistantWaterGlass,
      bubbleImageRenderMode: b.conversation.bubbleImageRenderMode,
      bubbleUserRoundedCorners: b.conversation.bubbleUserRoundedCorners,
      bubbleAssistantRoundedCorners: b.conversation.bubbleAssistantRoundedCorners,
      bubbleUserColorArgb: b.conversation.bubbleUserColorArgb,
      bubbleAssistantColorArgb: b.conversation.bubbleAssistantColorArgb,
      bubbleUserTextColorArgb: b.conversation.bubbleUserTextColorArgb,
      bubbleAssistantTextColorArgb: b.conversation.bubbleAssistantTextColorArgb,
      bubbleUserUseCustomFont: b.conversation.bubbleUserUseCustomFont,
      bubbleUserFontFamily: b.conversation.bubbleUserFontFamily,
      bubbleAssistantUseCustomFont: b.conversation.bubbleAssistantUseCustomFont,
      bubbleAssistantFontFamily: b.conversation.bubbleAssistantFontFamily,
      avatarShape: b.conversation.avatarShape,
      avatarCornerRadiusDp: b.conversation.avatarCornerRadiusDp
    },
    composer: {
      transparent: b.composer.transparent,
      floating: b.composer.floating,
      liquidGlass: b.composer.liquidGlass,
      waterGlass: b.composer.waterGlass
    },
    chrome: {
      statusBarHidden: b.chrome.statusBarHidden,
      statusBarTransparent: b.chrome.statusBarTransparent,
      statusBarColorArgb: b.chrome.statusBarColorArgb,
      toolbarTransparent: b.chrome.toolbarTransparent,
      toolbarColorArgb: b.chrome.toolbarColorArgb,
      navigationWaterGlass: b.chrome.navigationWaterGlass,
      navigationButtonLiquidGlass: b.chrome.navigationButtonLiquidGlass,
      navigationBackgroundColorArgb: b.chrome.navigationBackgroundColorArgb,
      navigationAccentColorArgb: b.chrome.navigationAccentColorArgb,
      chatHeaderTransparent: b.chrome.chatHeaderTransparent,
      chatHeaderOverlayMode: b.chrome.chatHeaderOverlayMode,
      appBarContentColorMode: b.chrome.appBarContentColorMode,
      chatHeaderHistoryIconColorArgb: b.chrome.chatHeaderHistoryIconColorArgb,
      chatHeaderPipIconColorArgb: b.chrome.chatHeaderPipIconColorArgb
    }
  };
}

function tokens(manifest: ThemePackageManifest): Record<string, unknown> | undefined {
  if (manifest.tokens == null) {
    return undefined;
  }
  return { tokens: manifest.tokens };
}

export function encodeThemePackageManifestDoc(manifest: ThemePackageManifest): string {
  const doc: Record<string, unknown> = {
    schemaVersion: manifest.schemaVersion,
    packageId: manifest.packageId,
    version: manifest.version,
    displayName: localizedText(manifest.displayName),
    author: optional(manifest.author == null ? undefined : localizedText(manifest.author)),
    description: optional(manifest.description == null ? undefined : localizedText(manifest.description)),
    attribution: attribution(manifest.attribution),
    basis: coordinate(manifest.basis),
    variants: variants(manifest.variants),
    parameters: parameters(manifest.parameters),
    assets: assets(manifest.assets),
    scenes: manifest.scenes,
    surfaces: manifest.surfaces,
    presentation: presentation(manifest),
    tokens: tokens(manifest)
  };
  return JSON.stringify(doc);
}

export async function exportThemePackage(
  plan: ThemeExportPlan,
  boundAssets: AssetRecord[]
): Promise<ThemeExportResult> {
  const manifestJson = encodeThemePackageManifestDoc(plan.manifest);
  const assetEntries: { name: string; bytes: Uint8Array }[] = [];
  for (const record of boundAssets) {
    assetEntries.push({
      name: record.path,
      bytes: new Uint8Array(await record.blob.arrayBuffer())
    });
  }
  const entries = [
    { name: THEME_PACKAGE_MANIFEST_ENTRY, bytes: new TextEncoder().encode(manifestJson) },
    ...assetEntries
  ];
  entries.sort((a, b) => a.name.localeCompare(b.name));
  const blob = createStoredZip(entries, THEME_PACKAGE_ZIP_COMMENT);
  const sha = await sha256Hex(new Uint8Array(await blob.arrayBuffer()));
  return {
    blob,
    sha256: sha,
    manifestJsonBytes: entries[0].bytes.byteLength,
    assetCount: boundAssets.length
  };
}

export function exportFileName(packageId: string, version: string): string {
  return `${packageId}-${version}${THEME_PACKAGE_EXTENSION}`;
}

export async function smokeValidateExportedArchive(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  const loaded = await loadThemePackageFromBytes(bytes);
  return loaded.archiveSha256;
}
