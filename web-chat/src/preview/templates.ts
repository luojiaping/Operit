import type { InputSlotName } from '../ui/features/chat/composedsl/composeDslTypes';

// 低代码模板：表单 → 受限 main.js + Compose DSL screen，
// 生成物结构与 examples/input_slot_demo 对齐，可直接被 app PackageManager 导入

export interface TemplateField {
  key: string;
  label: string;
  type: 'text' | 'multiline' | 'number' | 'boolean';
  placeholder?: string;
}

export interface SlotTemplate {
  id: string;
  name: string;
  description: string;
  slots: InputSlotName[];
  fields: TemplateField[];
  buildState(values: Record<string, string | number | boolean>): Record<string, unknown>;
  buildScreenSource(): string;
}

function quote(value: string) {
  return JSON.stringify(value);
}

function textValue(values: Record<string, string | number | boolean>, key: string, fallback: string) {
  const value = values[key];
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

function numberValue(values: Record<string, string | number | boolean>, key: string, fallback: number) {
  const value = values[key];
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  return fallback;
}

function boolValue(values: Record<string, string | number | boolean>, key: string) {
  return values[key] === true;
}

export const SLOT_TEMPLATES: SlotTemplate[] = [
  {
    id: 'status-card',
    name: '状态卡',
    description: '输入区上方的标题 + 副标题卡片，可选进度条',
    slots: ['above_input'],
    fields: [
      { key: 'title', label: '标题', type: 'text', placeholder: '今日任务' },
      { key: 'subtitle', label: '副标题', type: 'text', placeholder: '保持专注' },
      { key: 'accent', label: '主色（hex）', type: 'text', placeholder: '#8ca9ff' },
      { key: 'showProgress', label: '显示进度条', type: 'boolean' },
      { key: 'progress', label: '进度（0-100）', type: 'number' }
    ],
    buildState(values) {
      return {
        title: textValue(values, 'title', '今日任务'),
        subtitle: textValue(values, 'subtitle', '保持专注'),
        accent: textValue(values, 'accent', '#8ca9ff'),
        showProgress: boolValue(values, 'showProgress'),
        progress: Math.max(0, Math.min(100, numberValue(values, 'progress', 50))) / 100
      };
    },
    buildScreenSource() {
      return `function SlotScreen(ctx) {
  var UI = ctx.UI;
  var s = {
    title: ctx.useState('title', '今日任务')[0],
    subtitle: ctx.useState('subtitle', '保持专注')[0],
    accent: ctx.useState('accent', '#8ca9ff')[0],
    showProgress: ctx.useState('showProgress', false)[0],
    progress: ctx.useState('progress', 0.5)[0]
  };
  var children = [
    UI.Text({ text: s.title, style: "titleMedium", color: s.accent }),
    UI.Text({ text: s.subtitle, style: "bodySmall", color: "#9ca8bb" })
  ];
  if (s.showProgress) {
    children.push(UI.LinearProgressIndicator({ progress: s.progress }));
  }
  return UI.Card(
    { fillMaxWidth: true, containerColor: "rgba(127, 150, 220, 0.14)", elevation: 0 },
    UI.Column({ fillMaxWidth: true, padding: 10, spacing: 4 }, children)
  );
}
exports.default = SlotScreen;`;
    }
  },
  {
    id: 'quick-actions',
    name: '快捷按钮排',
    description: '一排可点击按钮，放在工具栏右侧或输入区上方；按钮动作需导入后自行接入工具',
    slots: ['input_toolbar_right', 'above_input'],
    fields: [
      {
        key: 'labels',
        label: '按钮文案（逗号分隔，最多 4 个）',
        type: 'text',
        placeholder: '翻译,总结,继续,停止'
      }
    ],
    buildState(values) {
      const raw = textValue(values, 'labels', '翻译,总结');
      const labels = raw
        .split(/[,，]/)
        .map((label) => label.trim())
        .filter((label) => label.length > 0)
        .slice(0, 4);
      return { labels: labels.length > 0 ? labels : ['按钮'] };
    },
    buildScreenSource() {
      return `function SlotScreen(ctx) {
  var UI = ctx.UI;
  var labels = ctx.useState('labels', ['按钮'])[0];
  var buttons = [];
  for (var i = 0; i < labels.length; i++) {
    buttons.push(UI.Button({
      key: 'btn-' + i,
      text: labels[i],
      onClick: function () {
        // TODO: 导入 .toolpkg 后在此接入你的工具调用
      }
    }));
  }
  return UI.Row({ spacing: 6 }, buttons);
}
exports.default = SlotScreen;`;
    }
  },
  {
    id: 'note-board',
    name: '记事板',
    description: '输入框抽屉位置的便签列表',
    slots: ['input_drawer'],
    fields: [
      { key: 'title', label: '标题', type: 'text', placeholder: '便签' },
      {
        key: 'lines',
        label: '内容（每行一条）',
        type: 'multiline',
        placeholder: '复查 PR\n回复 issue\n买咖啡'
      }
    ],
    buildState(values) {
      const raw = textValue(values, 'lines', '第一件事\n第二件事');
      const lines = raw
        .split(/\n/)
        .map((line) => line.trim())
        .filter((line) => line.length > 0)
        .slice(0, 8);
      return {
        title: textValue(values, 'title', '便签'),
        lines: lines.length > 0 ? lines : ['空便签']
      };
    },
    buildScreenSource() {
      return `function SlotScreen(ctx) {
  var UI = ctx.UI;
  var title = ctx.useState('title', '便签')[0];
  var lines = ctx.useState('lines', ['空便签'])[0];
  var items = [UI.Text({ text: title, style: "labelLarge", color: "#ffd28a" })];
  for (var i = 0; i < lines.length; i++) {
    items.push(UI.Text({ key: 'line-' + i, text: "- " + lines[i], style: "bodySmall", color: "#dce4f4" }));
  }
  return UI.Card(
    { fillMaxWidth: true, containerColor: "rgba(255, 210, 138, 0.10)", elevation: 0 },
    UI.Column({ fillMaxWidth: true, padding: 10, spacing: 2 }, items)
  );
}
exports.default = SlotScreen;`;
    }
  }
];

export interface GeneratedToolpkgSources {
  mainSource: string;
  screenSource: string;
  screenPath: string;
  manifestJson: string;
}

// 生成与 examples/input_slot_demo 同构的包内容
export function buildToolpkgSources(options: {
  template: SlotTemplate;
  slot: InputSlotName;
  pluginId: string;
  displayName: string;
  state: Record<string, unknown>;
}): GeneratedToolpkgSources {
  const { template, slot, pluginId, displayName, state } = options;
  const screenPath = 'dist/ui/preview/index.ui.js';
  const mainSource = [
    'exports.registerToolPkg = function () {',
    '  ToolPkg.registerInputSlotPlugin({',
    `    id: ${quote(pluginId)},`,
    `    slot: ${quote(slot)},`,
    '    function: function (event) {',
    '      return {',
    '        handled: true,',
    '        composeDsl: {',
    `          screen: ${quote(screenPath)},`,
    `          state: ${JSON.stringify(state)}`,
    '        }',
    '      };',
    '    }',
    '  });',
    '  return true;',
    '};'
  ].join('\n');
  const manifest = {
    schema_version: 1,
    toolpkg_id: `com.operit.preview.${pluginId}`,
    version: '0.1.0',
    main: 'dist/main.js',
    display_name: { zh: displayName, en: displayName },
    description: {
      zh: `由 Preview Studio 模板「${template.name}」生成`,
      en: `Generated by Preview Studio template "${template.name}"`
    },
    enabled_by_default: false,
    subpackages: []
  };
  return {
    mainSource,
    screenSource: template.buildScreenSource(),
    screenPath,
    manifestJson: JSON.stringify(manifest, null, 2)
  };
}
