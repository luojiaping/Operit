import { describe, expect, it } from 'vitest';
import {
  decodeThemePackageManifest,
  ThemeManifestError
} from '../src/shared/theme/manifest';
import {
  validateThemeArchiveShape,
  validateThemePackageManifest
} from '../src/shared/theme/validation';

function baseManifestJson(): Record<string, unknown> {
  return {
    schemaVersion: 4,
    packageId: 'operit.sample',
    version: '1.0.0',
    displayName: { values: { '*': '示例主题' } },
    author: { values: { '*': 'studio' } },
    description: { values: { '*': '解析样例' } },
    attribution: { text: { values: { '*': 'MIT' } }, sourceUrl: 'https://example.com' },
    basis: {
      packageId: 'operit.default',
      version: '3.0.0',
      archiveSha256: 'a'.repeat(64)
    },
    variants: [{ id: 'night', label: { values: { '*': '夜间' } } }],
    parameters: [
      {
        id: 'accent_color',
        type: 'COLOR',
        defaultValue: { type: 'color', argb: 0xff00687a },
        label: { values: { '*': '主色' } },
        description: null,
        control: { type: 'color_palette', presetArgb: [0xff00687a], allowCustom: true },
        effects: [{ type: 'accent_palette' }],
        visibility: 'USER',
        section: 'APPEARANCE',
        order: 1,
        visibleWhen: []
      },
      {
        id: 'background_image',
        type: 'IMAGE_URI',
        defaultValue: null,
        label: { values: { '*': '背景图' } },
        description: null,
        control: { type: 'image_picker', mimeTypes: ['image/png'] },
        effects: [
          {
            type: 'stage_image',
            surfaceIds: ['app.shell'],
            fit: 'CROP',
            opacity: 0.22
          }
        ],
        visibility: 'USER',
        section: 'APPEARANCE',
        order: 2,
        visibleWhen: []
      },
      {
        id: 'frame_scale',
        type: 'FLOAT',
        defaultValue: { type: 'float', value: 1 },
        label: { values: { '*': '框架缩放' } },
        description: null,
        control: { type: 'author_value' },
        effects: [{ type: 'component_frame_scale', componentIds: ['composer'] }],
        visibility: 'AUTHOR',
        section: null,
        order: 0,
        visibleWhen: []
      }
    ],
    assets: [
      {
        key: 'background_photo',
        path: 'assets/image/background_photo.png',
        kind: 'BITMAP',
        sha256: 'b'.repeat(64),
        byteSize: 1024
      }
    ],
    scenes: [{ sceneId: 'app.shell' }],
    surfaces: [{ surfaceId: 'app.shell', kind: 'SCENE', sceneId: 'app.shell' }],
    presentation: {
      material: null,
      componentSkins: {},
      behavior: {
        background: {
          enabled: false,
          mediaType: 'NONE',
          opacity: 0.22,
          blurEnabled: false,
          blurRadiusDp: 0,
          videoMuted: true,
          videoLoop: true
        },
        typography: { useCustomFont: false, family: 'DEFAULT', scale: 1 },
        conversation: {
          cursorUserBubbleFollowTheme: true,
          cursorUserBubbleLiquidGlass: false,
          cursorUserBubbleWaterGlass: false,
          cursorUserBubbleColorArgb: null,
          bubbleShowAvatar: true,
          bubbleWideLayout: false,
          bubbleUserLiquidGlass: false,
          bubbleUserWaterGlass: false,
          bubbleAssistantLiquidGlass: false,
          bubbleAssistantWaterGlass: false,
          bubbleImageRenderMode: 'TILED_NINE_SLICE',
          bubbleUserRoundedCorners: true,
          bubbleAssistantRoundedCorners: true,
          bubbleUserColorArgb: null,
          bubbleAssistantColorArgb: null,
          bubbleUserTextColorArgb: null,
          bubbleAssistantTextColorArgb: null,
          bubbleUserUseCustomFont: false,
          bubbleUserFontFamily: 'DEFAULT',
          bubbleAssistantUseCustomFont: false,
          bubbleAssistantFontFamily: 'DEFAULT',
          avatarShape: 'CIRCLE',
          avatarCornerRadiusDp: 0
        },
        composer: { transparent: false, floating: false, liquidGlass: false, waterGlass: false },
        chrome: {
          statusBarHidden: false,
          statusBarTransparent: false,
          statusBarColorArgb: null,
          toolbarTransparent: false,
          toolbarColorArgb: null,
          navigationWaterGlass: false,
          navigationButtonLiquidGlass: false,
          navigationBackgroundColorArgb: null,
          navigationAccentColorArgb: null,
          chatHeaderTransparent: false,
          chatHeaderOverlayMode: 'NONE',
          appBarContentColorMode: 'AUTO',
          chatHeaderHistoryIconColorArgb: null,
          chatHeaderPipIconColorArgb: null
        }
      }
    },
    tokens: null
  };
}

describe('decodeThemePackageManifest strict 解码', () => {
  it('合法 manifest 解码出全量字段', () => {
    const manifest = decodeThemePackageManifest(JSON.stringify(baseManifestJson()));
    expect(manifest.schemaVersion).toBe(4);
    expect(manifest.packageId).toBe('operit.sample');
    expect(manifest.parameters).toHaveLength(3);
    expect(manifest.parameters[0].type).toBe('COLOR');
    expect(manifest.presentation.behavior.conversation.bubbleShowAvatar).toBe(true);
    expect(manifest.assets[0].sha256).toBe('b'.repeat(64));
  });

  it('未知顶层字段被拒绝', () => {
    const json = baseManifestJson();
    json.extraKey = true;
    expect(() => decodeThemePackageManifest(JSON.stringify(json))).toThrow(ThemeManifestError);
  });

  it('缺少 behavior 被拒绝', () => {
    const json = baseManifestJson();
    delete (json.presentation as Record<string, unknown>).behavior;
    expect(() => decodeThemePackageManifest(JSON.stringify(json))).toThrow(/behavior/);
  });

  it('behavior 缺字段或多余字段被拒绝', () => {
    const json = baseManifestJson();
    const background = (
      json.presentation as {
        behavior: { background: Record<string, unknown> };
      }
    ).behavior.background;
    background.typedExtra = 1;
    expect(() => decodeThemePackageManifest(JSON.stringify(json))).toThrow(/未知字段/);
  });

  it('URI 值必须 content://', () => {
    const json = baseManifestJson();
    const parameters = json.parameters as Record<string, unknown>[];
    parameters[1].defaultValue = { type: 'image_uri', uri: 'https://example.com/a.png' };
    expect(() => decodeThemePackageManifest(JSON.stringify(json))).toThrow(/content:\/\//);
  });

  it('packageId 非法被拒绝', () => {
    const json = baseManifestJson();
    json.packageId = 'Sample';
    expect(() => decodeThemePackageManifest(JSON.stringify(json))).not.toThrow();
    expect(validateThemePackageManifest(decodeThemePackageManifest(JSON.stringify(json))).ok).toBe(
      false
    );
  });
});

describe('validateThemePackageManifest 语义校验', () => {
  it('合法包通过', () => {
    const manifest = decodeThemePackageManifest(JSON.stringify(baseManifestJson()));
    const result = validateThemePackageManifest(manifest);
    expect(result.ok).toBe(true);
  });

  it('USER 参数缺 section 被拒绝', () => {
    const json = baseManifestJson();
    (json.parameters as Record<string, unknown>[])[0].section = null;
    const result = validateThemePackageManifest(
      decodeThemePackageManifest(JSON.stringify(json))
    );
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.message.includes('section'))).toBe(true);
  });

  it('USER 非资源参数无默认值被拒绝', () => {
    const json = baseManifestJson();
    (json.parameters as Record<string, unknown>[])[0].defaultValue = null;
    const result = validateThemePackageManifest(
      decodeThemePackageManifest(JSON.stringify(json))
    );
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.message.includes('默认值'))).toBe(true);
  });

  it('选项条件依赖非 OPTION 参数被拒绝', () => {
    const json = baseManifestJson();
    const parameters = json.parameters as Record<string, unknown>[];
    parameters[0].visibleWhen = [
      { type: 'option_equals', parameterId: 'background_image', expected: 'none' }
    ];
    const result = validateThemePackageManifest(
      decodeThemePackageManifest(JSON.stringify(json))
    );
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.message.includes('选项条件'))).toBe(true);
  });

  it('资源条件依赖非 URI 参数被拒绝', () => {
    const json = baseManifestJson();
    (json.parameters as Record<string, unknown>[])[0].visibleWhen = [
      { type: 'resource_present', parameterId: 'accent_color' }
    ];
    const result = validateThemePackageManifest(
      decodeThemePackageManifest(JSON.stringify(json))
    );
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.message.includes('资源条件'))).toBe(true);
  });

  it('presentation 目标类型与参数类型不一致被拒绝', () => {
    const json = baseManifestJson();
    const parameters = json.parameters as Record<string, unknown>[];
    parameters[0].effects = [
      { type: 'presentation', targets: ['BACKGROUND_USE_IMAGE'] }
    ];
    const result = validateThemePackageManifest(
      decodeThemePackageManifest(JSON.stringify(json))
    );
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.message.includes('而非'))).toBe(true);
  });

  it('SCENE surface 引用缺失场景被拒绝', () => {
    const json = baseManifestJson();
    json.scenes = [];
    const result = validateThemePackageManifest(
      decodeThemePackageManifest(JSON.stringify(json))
    );
    expect(result.ok).toBe(false);
    expect(result.issues.some((issue) => issue.message.includes('缺失场景'))).toBe(true);
  });
});

describe('validateThemeArchiveShape 预算校验', () => {
  const okArchive = {
    zipComment: 'Operit Theme Package',
    hasRootManifestEntry: true,
    entryCount: 3,
    archiveBytes: 1000,
    uncompressedBytes: 900,
    singleEntryMaxBytes: 500,
    compressionRatio: 2
  };

  it('合法 archive 通过', () => {
    expect(validateThemeArchiveShape(okArchive).ok).toBe(true);
  });

  it('ZIP comment 错误被拒绝', () => {
    expect(validateThemeArchiveShape({ ...okArchive, zipComment: 'wrong' }).ok).toBe(false);
  });

  it('manifiest 非根条目被拒绝', () => {
    expect(
      validateThemeArchiveShape({ ...okArchive, hasRootManifestEntry: false }).ok
    ).toBe(false);
  });

  it('条目数超预算被拒绝', () => {
    expect(validateThemeArchiveShape({ ...okArchive, entryCount: 513 }).ok).toBe(false);
  });
});
