// 主题工作室编辑会话：包状态 + 参数值 reducer。
// 未设值 = 包默认（导出时烘焙见 07）；控件只产生合法 ThemeParameterValue。

import type {
  ThemePackageLocalizedText,
  ThemeComponentFrame,
  ThemeComponentSkin,
  ThemeComponentStateSkin,
  ThemeParameterCondition,
  ThemeParameterDefinition,
  ThemeParameterValue,
} from './manifest';
import type { StudioPackage } from './packageLoader';

export type ParameterValueState = ThemeParameterValue | null;

export interface StudioEditorState {
  manifest: StudioPackage['manifest'];
  values: Map<string, ParameterValueState>;
  componentSkins: Map<string, ThemeComponentSkin>;
}

export type StudioEditorAction =
  | { kind: 'reset' }
  | { kind: 'setValue'; parameterId: string; value: ParameterValueState }
  | { kind: 'resetValue'; parameterId: string }
  | { kind: 'setComponentSkin'; componentId: string; skin: ThemeComponentSkin }
  | { kind: 'resetComponentSkin'; componentId: string }
  | { kind: 'resetComponentSkins' }
  | { kind: 'resetAll' };

export function createEditorState(studioPackage: StudioPackage): StudioEditorState {
  const values = new Map<string, ParameterValueState>();
  for (const parameter of studioPackage.manifest.parameters) {
    values.set(parameter.id, parameter.defaultValue);
  }
  return {
    componentSkins: cloneComponentSkins(studioPackage.manifest.presentation.componentSkins),
    manifest: studioPackage.manifest,
    values
  };
}

export function editorReducer(
  state: StudioEditorState,
  action: StudioEditorAction
): StudioEditorState {
  switch (action.kind) {
    case 'reset':
      return state;
    case 'setValue': {
      const next = new Map(state.values);
      next.set(action.parameterId, action.value);
      return { ...state, values: next };
    }
    case 'resetValue': {
      const definition = state.manifest.parameters.find(
        (parameter) => parameter.id === action.parameterId
      );
      if (definition == null) {
        return state;
      }
      const next = new Map(state.values);
      next.set(action.parameterId, definition.defaultValue);
      return { ...state, values: next };
    }
    case 'setComponentSkin': {
      const next = new Map(state.componentSkins);
      next.set(action.componentId, cloneComponentSkin(action.skin));
      return { ...state, componentSkins: next };
    }
    case 'resetComponentSkin': {
      const source = state.manifest.presentation.componentSkins[action.componentId];
      if (source == null) {
        return state;
      }
      const next = new Map(state.componentSkins);
      next.set(action.componentId, cloneComponentSkin(source));
      return { ...state, componentSkins: next };
    }
    case 'resetComponentSkins':
      return {
        ...state,
        componentSkins: cloneComponentSkins(state.manifest.presentation.componentSkins)
      };
    case 'resetAll': {
      const values = new Map<string, ParameterValueState>();
      for (const parameter of state.manifest.parameters) {
        values.set(parameter.id, parameter.defaultValue);
      }
      return {
        ...state,
        componentSkins: cloneComponentSkins(state.manifest.presentation.componentSkins),
        values
      };
    }
  }
}

export function effectiveValue(
  state: StudioEditorState,
  definition: ThemeParameterDefinition
): ParameterValueState {
  return state.values.get(definition.id) ?? null;
}

export function isOverridden(
  state: StudioEditorState,
  definition: ThemeParameterDefinition
): boolean {
  const current = state.values.get(definition.id);
  if (current === undefined) {
    return false;
  }
  if (current === null) {
    return definition.defaultValue !== null;
  }
  return !parameterValueEquals(current, definition.defaultValue);
}

export function parameterValueEquals(
  left: ThemeParameterValue,
  right: ThemeParameterValue | null
): boolean {
  if (right === null) {
    return false;
  }
  if (left.type !== right.type) {
    return false;
  }
  switch (left.type) {
    case 'color':
      return left.argb === (right as typeof left).argb;
    case 'color_pair':
      return (
        left.lightArgb === (right as typeof left).lightArgb &&
        left.darkArgb === (right as typeof left).darkArgb
      );
    case 'boolean':
      return left.value === (right as typeof left).value;
    case 'option':
      return left.value === (right as typeof left).value;
    case 'float':
      return left.value === (right as typeof left).value;
    case 'image_uri':
    case 'video_uri':
    case 'font_uri':
      return left.uri === (right as typeof left).uri;
    case 'image_layout': {
      const other = right as typeof left;
      return (
        left.cropLeft === other.cropLeft &&
        left.cropTop === other.cropTop &&
        left.cropRight === other.cropRight &&
        left.cropBottom === other.cropBottom &&
        left.repeatStart === other.repeatStart &&
        left.repeatEnd === other.repeatEnd &&
        left.repeatYStart === other.repeatYStart &&
        left.repeatYEnd === other.repeatYEnd &&
        left.scale === other.scale
      );
    }
    case 'insets': {
      const other = right as typeof left;
      return (
        left.startDp === other.startDp &&
        left.topDp === other.topDp &&
        left.endDp === other.endDp &&
        left.bottomDp === other.bottomDp
      );
    }
    case 'corner_radius':
      return left.valueDp === (right as typeof left).valueDp;
  }
}

/** visibleWhen 求值：条件全部满足才在用户面板出现 */
export function isVisible(
  state: StudioEditorState,
  definition: ThemeParameterDefinition
): boolean {
  return definition.visibleWhen.every((condition) => conditionSatisfied(state, condition));
}

function conditionSatisfied(
  state: StudioEditorState,
  condition: ThemeParameterCondition
): boolean {
  const value = state.values.get(condition.parameterId);
  if (value === undefined) {
    return false;
  }
  switch (condition.type) {
    case 'boolean_equals':
      return value?.type === 'boolean' && value.value === condition.expected;
    case 'option_equals':
      return value?.type === 'option' && value.value === condition.expected;
    case 'resource_present':
      return (
        (value?.type === 'image_uri' ||
          value?.type === 'video_uri' ||
          value?.type === 'font_uri') &&
        value.uri.length > 0
      );
  }
}

export function resolveLocalizedText(
  text: ThemePackageLocalizedText,
  locale = 'zh'
): string {
  return text[locale] ?? text['*'];
}

/** 编辑摘要：列出与包默认不同的项（导出前 diff 用） */
export function describeOverrides(state: StudioEditorState): ThemeParameterDefinition[] {
  return state.manifest.parameters.filter((parameter) => isOverridden(state, parameter));
}

function cloneComponentSkins(
  skins: Record<string, ThemeComponentSkin>
): Map<string, ThemeComponentSkin> {
  return new Map(
    Object.entries(skins).map<[string, ThemeComponentSkin]>(([componentId, skin]) => [
      componentId,
      cloneComponentSkin(skin)
    ])
  );
}

function cloneComponentSkin(skin: ThemeComponentSkin): ThemeComponentSkin {
  return {
    normal: cloneComponentStateSkin(skin.normal),
    disabled: skin.disabled == null ? skin.disabled : cloneComponentStateSkin(skin.disabled),
    selected: skin.selected == null ? skin.selected : cloneComponentStateSkin(skin.selected),
    focused: skin.focused == null ? skin.focused : cloneComponentStateSkin(skin.focused),
    error: skin.error == null ? skin.error : cloneComponentStateSkin(skin.error)
  };
}

function cloneComponentStateSkin(skin: ThemeComponentStateSkin): ThemeComponentStateSkin {
  return {
    ...skin,
    contentPadding: { ...skin.contentPadding },
    frame: cloneComponentFrame(skin.frame)
  };
}

function cloneComponentFrame(frame: ThemeComponentFrame): ThemeComponentFrame {
  switch (frame.kind) {
    case 'none':
      return { kind: 'none' };
    case 'round_rect':
      return { ...frame, border: frame.border == null ? frame.border : { ...frame.border } };
    case 'cut_corners':
      return {
        ...frame,
        accent: frame.accent == null ? frame.accent : { ...frame.accent },
        border: frame.border == null ? frame.border : { ...frame.border }
      };
    case 'hud_notched':
      return {
        ...frame,
        accent: frame.accent == null ? frame.accent : { ...frame.accent },
        border: frame.border == null ? frame.border : { ...frame.border }
      };
    case 'corner_brackets':
      return {
        ...frame,
        accent: frame.accent == null ? frame.accent : { ...frame.accent },
        border: frame.border == null ? frame.border : { ...frame.border }
      };
    case 'segmented_rail':
      return {
        ...frame,
        accent: frame.accent == null ? frame.accent : { ...frame.accent },
        border: frame.border == null ? frame.border : { ...frame.border }
      };
  }
}
