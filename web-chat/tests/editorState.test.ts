import { describe, expect, it } from 'vitest';
import {
  createEditorState,
  describeOverrides,
  editorReducer,
  isVisible,
  isOverridden,
  parameterValueEquals
} from '../src/shared/theme/editorState';
import type { ThemeParameterDefinition } from '../src/shared/theme/manifest';
import type { StudioPackage } from '../src/shared/theme/packageLoader';

function parameter(patch: Partial<ThemeParameterDefinition>): ThemeParameterDefinition {
  return {
    id: 'p',
    type: 'BOOLEAN',
    defaultValue: { type: 'boolean', value: true },
    label: { '*': '参数' },
    description: null,
    control: { type: 'toggle' },
    effects: [{ type: 'presentation', targets: ['COMPOSER_TRANSPARENT'] }],
    visibility: 'USER',
    section: 'APPEARANCE',
    order: 0,
    visibleWhen: [],
    ...patch
  };
}

function studioPackage(parameters: ThemeParameterDefinition[]): StudioPackage {
  return {
    manifest: {
      schemaVersion: 4,
      packageId: 'operit.test',
      version: '1.0.0',
      displayName: { '*': '测试' },
      author: null,
      description: null,
      attribution: null,
      basis: null,
      variants: [],
      parameters,
      assets: [],
      scenes: [],
      surfaces: [],
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
    },
    assets: new Map(),
    archiveBytes: 100,
    archiveSha256: '0'.repeat(64)
  };
}

describe('editorState', () => {
  it('初始值为包默认，setValue/resetValue 生效', () => {
    const pkg = studioPackage([parameter({})]);
    let state = createEditorState(pkg);
    expect(state.values.get('p')).toEqual({ type: 'boolean', value: true });
    expect(isOverridden(state, pkg.manifest.parameters[0])).toBe(false);

    state = editorReducer(state, {
      kind: 'setValue',
      parameterId: 'p',
      value: { type: 'boolean', value: false }
    });
    expect(isOverridden(state, pkg.manifest.parameters[0])).toBe(true);

    state = editorReducer(state, { kind: 'resetValue', parameterId: 'p' });
    expect(state.values.get('p')).toEqual({ type: 'boolean', value: true });
  });

  it('resetAll 恢复全部默认', () => {
    const pkg = studioPackage([parameter({})]);
    let state = createEditorState(pkg);
    state = editorReducer(state, {
      kind: 'setValue',
      parameterId: 'p',
      value: { type: 'boolean', value: false }
    });
    state = editorReducer(state, { kind: 'resetAll' });
    expect(state.values.get('p')).toEqual({ type: 'boolean', value: true });
  });

  it('resource_present 条件在选择素材前隐藏、选择后显示', () => {
    const background = parameter({
      id: 'background_image',
      type: 'IMAGE_URI',
      defaultValue: null,
      control: { type: 'image_picker', mimeTypes: ['image/png'] },
      effects: [
        { type: 'stage_image', surfaceIds: ['app.shell'], fit: 'CROP', opacity: 0.22 }
      ]
    });
    const opacity = parameter({
      id: 'background_opacity',
      type: 'FLOAT',
      defaultValue: { type: 'float', value: 0.22 },
      control: { type: 'slider', minimum: 0, maximum: 1, step: 0.05 },
      effects: [{ type: 'presentation', targets: ['BACKGROUND_OPACITY'] }],
      visibleWhen: [{ type: 'resource_present', parameterId: 'background_image' }]
    });
    const pkg = studioPackage([background, opacity]);
    let state = createEditorState(pkg);
    expect(isVisible(state, opacity)).toBe(false);

    state = editorReducer(state, {
      kind: 'setValue',
      parameterId: 'background_image',
      value: { type: 'image_uri', uri: 'content://media/1' }
    });
    expect(isVisible(state, opacity)).toBe(true);
  });

  it('option_equals 条件随依赖值联动', () => {
    const shape = parameter({
      id: 'avatar_shape',
      type: 'OPTION',
      defaultValue: { type: 'option', value: 'circle' },
      control: {
        type: 'choice',
        options: [
          { id: 'circle', label: { '*': '圆' } },
          { id: 'rounded', label: { '*': '圆角' } }
        ]
      },
      effects: [{ type: 'presentation', targets: ['AVATAR_SHAPE'] }]
    });
    const radius = parameter({
      id: 'avatar_radius',
      type: 'FLOAT',
      defaultValue: { type: 'float', value: 12 },
      control: { type: 'slider', minimum: 0, maximum: 96, step: 1 },
      effects: [{ type: 'presentation', targets: ['AVATAR_CORNER_RADIUS'] }],
      visibleWhen: [{ type: 'option_equals', parameterId: 'avatar_shape', expected: 'rounded' }]
    });
    const pkg = studioPackage([shape, radius]);
    let state = createEditorState(pkg);
    expect(isVisible(state, radius)).toBe(false);
    state = editorReducer(state, {
      kind: 'setValue',
      parameterId: 'avatar_shape',
      value: { type: 'option', value: 'rounded' }
    });
    expect(isVisible(state, radius)).toBe(true);
  });

  it('describeOverrides 只列变更项', () => {
    const a = parameter({ id: 'a' });
    const b = parameter({ id: 'b', defaultValue: { type: 'boolean', value: false } });
    const pkg = studioPackage([a, b]);
    let state = createEditorState(pkg);
    expect(describeOverrides(state)).toHaveLength(0);
    state = editorReducer(state, {
      kind: 'setValue',
      parameterId: 'a',
      value: { type: 'boolean', value: false }
    });
    expect(describeOverrides(state).map((definition) => definition.id)).toEqual(['a']);
  });

  it('parameterValueEquals 各类型比较', () => {
    expect(
      parameterValueEquals({ type: 'color', argb: 1 }, { type: 'color', argb: 1 })
    ).toBe(true);
    expect(
      parameterValueEquals(
        { type: 'insets', startDp: 1, topDp: 2, endDp: 3, bottomDp: 4 },
        { type: 'insets', startDp: 1, topDp: 2, endDp: 3, bottomDp: 4 }
      )
    ).toBe(true);
    expect(
      parameterValueEquals(
        { type: 'image_layout', cropLeft: 0, cropTop: 0, cropRight: 1, cropBottom: 1, repeatStart: 0, repeatEnd: 1, repeatYStart: 0, repeatYEnd: 1, scale: 1 },
        { type: 'image_layout', cropLeft: 0, cropTop: 0, cropRight: 1, cropBottom: 1, repeatStart: 0, repeatEnd: 1, repeatYStart: 0, repeatYEnd: 1, scale: 2 }
      )
    ).toBe(false);
    expect(parameterValueEquals({ type: 'font_uri', uri: 'content://f' }, null)).toBe(false);
  });
});
