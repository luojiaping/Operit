import type {
  ThemeParameterDefinition,
  ThemeParameterEffect,
  ThemeParameterSection
} from '../shared/theme/manifest';

export const THEME_TARGET_IDS = [
  'all',
  'app.shell',
  'chat.screen',
  'background',
  'typography',
  'chrome.toolbar',
  'chrome.drawer',
  'chat.header',
  'overlay.history',
  'overlay.character',
  'conversation.thread',
  'conversation.user-bubble',
  'conversation.assistant-bubble',
  'conversation.tools',
  'conversation.markdown',
  'conversation.code',
  'composer.agent',
  'composer.classic',
  'overlay.dialog'
] as const;

export type ThemeTargetId = (typeof THEME_TARGET_IDS)[number];

export interface ThemeTargetDefinition {
  id: ThemeTargetId;
  label: string;
  group: 'global' | 'chrome' | 'conversation' | 'composer' | 'overlay';
  description: string;
}

export const THEME_TARGETS: readonly ThemeTargetDefinition[] = [
  {
    id: 'all',
    label: '全部参数',
    group: 'global',
    description: '显示主题包中的全部用户级和作者级参数'
  },
  {
    id: 'app.shell',
    label: '应用外壳',
    group: 'global',
    description: '页面根背景、字体和全局色彩'
  },
  {
    id: 'chat.screen',
    label: '聊天页面',
    group: 'global',
    description: '聊天页面的整体外观与排版'
  },
  {
    id: 'background',
    label: '背景媒体',
    group: 'global',
    description: '纯色、图片、视频、透明度和模糊'
  },
  {
    id: 'typography',
    label: '字体排版',
    group: 'global',
    description: '字体来源、字号缩放和消息排版'
  },
  {
    id: 'chrome.toolbar',
    label: '应用顶栏',
    group: 'chrome',
    description: '全局顶栏、导航按钮和右侧操作'
  },
  {
    id: 'chrome.drawer',
    label: '导航抽屉',
    group: 'chrome',
    description: '抽屉背景、快捷卡、导航项和底部入口'
  },
  {
    id: 'chat.header',
    label: '聊天 Header',
    group: 'chrome',
    description: '角色、历史、浮窗和上下文用量'
  },
  {
    id: 'overlay.history',
    label: '历史面板',
    group: 'overlay',
    description: '会话列表、搜索、分组和编辑面板'
  },
  {
    id: 'overlay.character',
    label: '角色面板',
    group: 'overlay',
    description: '角色卡选择、头像和排序菜单'
  },
  {
    id: 'conversation.thread',
    label: '消息列表',
    group: 'conversation',
    description: '消息排列、头像、宽布局和通用对话参数'
  },
  {
    id: 'conversation.user-bubble',
    label: '用户消息',
    group: 'conversation',
    description: '用户气泡颜色、字体、间距、圆角和素材'
  },
  {
    id: 'conversation.assistant-bubble',
    label: 'AI 消息',
    group: 'conversation',
    description: 'AI 气泡颜色、字体、间距、圆角和素材'
  },
  {
    id: 'conversation.tools',
    label: '工具消息',
    group: 'conversation',
    description: '思考、工具调用、结果卡和代码块'
  },
  {
    id: 'conversation.markdown',
    label: 'Markdown 文本',
    group: 'conversation',
    description: '正文、链接、列表、表格和引用'
  },
  {
    id: 'conversation.code',
    label: '代码块',
    group: 'conversation',
    description: '代码背景、工具栏、行号和等宽字体'
  },
  {
    id: 'composer.agent',
    label: 'Agent 输入栏',
    group: 'composer',
    description: '模型选择、输入框、附件和发送操作'
  },
  {
    id: 'composer.classic',
    label: 'Classic 输入栏',
    group: 'composer',
    description: '经典输入框、设置栏、附件和发送操作'
  },
  {
    id: 'overlay.dialog',
    label: '弹窗与浮层',
    group: 'overlay',
    description: '附件、全屏输入、详情和确认弹窗'
  }
] as const;

const TARGET_ID_SET = new Set<string>(THEME_TARGET_IDS);

export function isThemeTargetId(value: string | undefined): value is ThemeTargetId {
  return value !== undefined && TARGET_ID_SET.has(value);
}

export function themeTargetDefinition(target: ThemeTargetId): ThemeTargetDefinition {
  const definition = THEME_TARGETS.find((item) => item.id === target);
  if (definition == null) {
    throw new Error(`未知预览目标: ${target}`);
  }
  return definition;
}

const COMPONENT_IDS_BY_TARGET: Partial<Record<ThemeTargetId, readonly string[]>> = {
  'app.shell': ['page', 'section', 'status', 'snackbar'],
  'chat.screen': ['page', 'section', 'status'],
  background: ['page'],
  typography: ['page', 'message_user', 'message_assistant', 'input'],
  'chrome.toolbar': ['app_bar', 'button', 'icon_button'],
  'chrome.drawer': ['navigation', 'list_item', 'button'],
  'chat.header': ['icon_button', 'status'],
  'overlay.history': ['sheet', 'list_item', 'menu', 'dialog'],
  'overlay.character': ['sheet', 'list_item', 'menu'],
  'conversation.thread': ['message_user', 'message_assistant', 'status'],
  'conversation.user-bubble': ['message_user'],
  'conversation.assistant-bubble': ['message_assistant'],
  'conversation.tools': ['status', 'section', 'dialog', 'menu'],
  'conversation.markdown': ['message_assistant', 'section'],
  'conversation.code': ['message_assistant', 'section'],
  'composer.agent': ['composer', 'input', 'button', 'icon_button', 'status'],
  'composer.classic': ['composer', 'input', 'button', 'icon_button', 'status'],
  'overlay.dialog': ['dialog', 'sheet', 'menu', 'status']
};

export function componentIdsForThemeTarget(
  target: ThemeTargetId,
  availableIds: readonly string[]
): string[] {
  if (target === 'all') {
    return [...availableIds];
  }
  const targetIds = COMPONENT_IDS_BY_TARGET[target] ?? [];
  return availableIds.filter((componentId) => targetIds.includes(componentId));
}

function effectTargetNames(effect: ThemeParameterEffect): string[] {
  if (effect.type === 'presentation') {
    return effect.targets;
  }
  if (effect.type === 'stage_image') {
    return ['BACKGROUND_STAGE_IMAGE'];
  }
  return [effect.type.toUpperCase()];
}

function parameterSearchText(parameter: ThemeParameterDefinition): string {
  return [
    parameter.id,
    ...parameter.effects.flatMap(effectTargetNames),
    parameter.type,
    parameter.section ?? ''
  ]
    .join(' ')
    .toLowerCase();
}

function hasAny(text: string, values: readonly string[]): boolean {
  return values.some((value) => text.includes(value));
}

function sectionIs(parameter: ThemeParameterDefinition, section: ThemeParameterSection): boolean {
  return parameter.section === section;
}

/** 将 schema 参数投影到预览中可点击的聊天区域。 */
export function parameterBelongsToTarget(
  parameter: ThemeParameterDefinition,
  target: ThemeTargetId
): boolean {
  if (target === 'all') {
    return true;
  }

  const text = parameterSearchText(parameter);

  switch (target) {
    case 'app.shell':
    case 'chat.screen':
      return (
        sectionIs(parameter, 'APPEARANCE') &&
        !hasAny(text, ['background', 'stage_image', 'font', 'typography'])
      );
    case 'background':
      return hasAny(text, ['background', 'stage_image']);
    case 'typography':
      return hasAny(text, ['font', 'typography', 'letter_spacing']);
    case 'chrome.toolbar':
      return hasAny(text, ['toolbar', 'status_bar', 'app_bar', 'app_chrome']);
    case 'chrome.drawer':
      return hasAny(text, ['navigation', 'drawer']);
    case 'chat.header':
      return hasAny(text, ['chat_header', 'history_icon', 'pip_icon', 'header']);
    case 'overlay.history':
      return hasAny(text, ['history']);
    case 'overlay.character':
      return hasAny(text, ['character', 'avatar']);
    case 'conversation.thread':
      return (
        sectionIs(parameter, 'CONVERSATION') &&
        !hasAny(text, ['user_bubble', 'assistant_bubble', 'cursor_user', 'tool', 'think'])
      );
    case 'conversation.user-bubble':
      return hasAny(text, ['user_bubble', 'cursor_user', 'user_font', 'user_image']);
    case 'conversation.assistant-bubble':
      return hasAny(text, ['assistant_bubble', 'assistant_font', 'assistant_image']);
    case 'conversation.tools':
      return hasAny(text, ['tool', 'think', 'status', 'collapse', 'structured', 'code']);
    case 'conversation.markdown':
      return hasAny(text, ['markdown', 'typography', 'text', 'font']);
    case 'conversation.code':
      return hasAny(text, ['code', 'monospace']);
    case 'composer.agent':
    case 'composer.classic':
      return sectionIs(parameter, 'COMPOSER') || hasAny(text, ['composer', 'input']);
    case 'overlay.dialog':
      return hasAny(text, ['dialog', 'overlay', 'modal', 'attachment']);
  }
}

export function themeTargetGroupLabel(group: ThemeTargetDefinition['group']): string {
  switch (group) {
    case 'global':
      return '页面';
    case 'chrome':
      return '应用栏';
    case 'conversation':
      return '对话';
    case 'composer':
      return '输入栏';
    case 'overlay':
      return '浮层';
  }
}
