import type {
  WebBootstrapResponse,
  WebCharacterSelectorResponse,
  WebChatMessage,
  WebChatSummary,
  WebInputSettingsState,
  WebMemorySelectorState,
  WebModelSelectorState,
  WebThemeSnapshot
} from '../chatTypes';

// 1x1 像素级的占位图：数据 URI 保证 mock 模式离线可用
const PLACEHOLDER_IMAGE =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="480" height="270">' +
      '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">' +
      '<stop offset="0" stop-color="#2b3a67"/><stop offset="1" stop-color="#764ba2"/>' +
      '</linearGradient></defs><rect width="480" height="270" fill="url(#g)"/>' +
      '<circle cx="360" cy="80" r="42" fill="rgba(255,255,255,0.18)"/>' +
      '<text x="24" y="240" font-family="sans-serif" font-size="20" fill="rgba(255,255,255,0.75)">preview asset</text>' +
      '</svg>'
  );

const BUBBLE_TILE_IMAGE =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96">' +
      '<rect width="96" height="96" fill="#31518f"/>' +
      '<rect x="8" y="8" width="18" height="18" rx="6" fill="#e8eefc"/>' +
      '<rect x="70" y="8" width="18" height="18" rx="6" fill="#e8eefc"/>' +
      '<rect x="8" y="70" width="18" height="18" rx="6" fill="#e8eefc"/>' +
      '<rect x="70" y="70" width="18" height="18" rx="6" fill="#e8eefc"/>' +
      '<rect x="40" y="40" width="16" height="16" rx="4" fill="#9db8f0"/>' +
      '</svg>'
  );

const DISABLED_IMAGE: WebThemeSnapshot['bubble']['user_image'] = {
  enabled: false,
  asset_url: null,
  render_mode: 'tiled_nine_slice',
  crop_left: 0,
  crop_top: 0,
  crop_right: 1,
  crop_bottom: 1,
  repeat_start: 0,
  repeat_end: 1,
  repeat_y_start: 0,
  repeat_y_end: 1,
  scale: 1
};

function palette(overrides: Partial<WebThemeSnapshot['palette']>): WebThemeSnapshot['palette'] {
  return {
    background_color: '#0f1520',
    surface_color: '#1b202b',
    surface_variant_color: '#303542',
    surface_container_color: '#1f2532',
    surface_container_high_color: '#262d3b',
    primary_color: '#7b9bff',
    secondary_color: '#70d3c2',
    primary_container_color: '#c7d3f4',
    on_primary_container_color: '#0f1a31',
    on_surface_color: '#f2f6ff',
    on_surface_variant_color: '#9ca8bb',
    outline_color: '#8f909a',
    outline_variant_color: '#45464f',
    ...overrides
  };
}

// 默认暗色快照：与 app WebChatHttpBridge 下发 DTO 同构（13 键 palette）
export const MOCK_THEME_DARK: WebThemeSnapshot = {
  source: 'global',
  source_id: null,
  theme_mode: 'dark',
  use_system_theme: false,
  use_custom_colors: false,
  palette: palette({
    primary_container_color: '#c7d3f4',
    on_primary_container_color: '#0f1a31'
  }),
  background: { stage: null, media: null },
  header: { transparent: false, overlay: false },
  input: {
    style: 'agent',
    transparent: false,
    floating: false,
    liquid_glass: false,
    water_glass: false
  },
  font: {
    type: 'system',
    system_font_name: 'default',
    custom_font_asset_url: null,
    scale: 1
  },
  chat_style: 'bubble',
  show_thinking_process: true,
  show_status_tags: true,
  show_input_processing_status: true,
  display: {
    show_user_name: true,
    show_role_name: true,
    show_model_name: false,
    show_model_provider: false,
    show_message_token_stats: true,
    show_message_timing_stats: false,
    show_message_timestamp: true,
    tool_collapse_mode: 'all',
    global_user_name: '开发者'
  },
  bubble: {
    show_avatar: true,
    wide_layout: false,
    cursor_user_follow_theme: true,
    cursor_user_color: null,
    user_bubble_color: null,
    assistant_bubble_color: null,
    user_text_color: null,
    assistant_text_color: null,
    cursor_user_liquid_glass: false,
    cursor_user_water_glass: false,
    user_liquid_glass: false,
    user_water_glass: false,
    assistant_liquid_glass: false,
    assistant_water_glass: false,
    user_rounded: true,
    assistant_rounded: true,
    user_padding_left: 12,
    user_padding_right: 12,
    user_padding_top: 12,
    user_padding_bottom: 12,
    assistant_padding_left: 12,
    assistant_padding_right: 12,
    assistant_padding_top: 12,
    assistant_padding_bottom: 12,
    user_font: { type: 'system', system_font_name: 'default', custom_font_asset_url: null, scale: 1 },
    assistant_font: { type: 'system', system_font_name: 'default', custom_font_asset_url: null, scale: 1 },
    user_image: DISABLED_IMAGE,
    assistant_image: DISABLED_IMAGE
  },
  avatars: {
    shape: 'circle',
    corner_radius: 8,
    user_avatar_url: null,
    assistant_avatar_url: null
  }
};

export const MOCK_THEME_LIGHT: WebThemeSnapshot = {
  ...MOCK_THEME_DARK,
  theme_mode: 'light',
  palette: palette({
    background_color: '#faf8ff',
    surface_color: '#ffffff',
    surface_variant_color: '#ece8f1',
    surface_container_color: '#f1edf6',
    surface_container_high_color: '#e7e2ef',
    primary_color: '#4c5fd5',
    secondary_color: '#2e9d8f',
    primary_container_color: '#dde2fb',
    on_primary_container_color: '#131a3a',
    on_surface_color: '#1a1c22',
    on_surface_variant_color: '#43454e',
    outline_color: '#7a7a85',
    outline_variant_color: '#c8c4cf'
  })
};

// 带背景图与模糊、气泡九宫格、独立气泡字体的展示快照
export const MOCK_THEME_SCENIC: WebThemeSnapshot = {
  ...MOCK_THEME_DARK,
  source: 'character_card',
  source_id: 'card-atmosphere',
  background: {
    stage: null,
    media: {
      type: 'image',
      asset_url: PLACEHOLDER_IMAGE,
      opacity: 0.9,
      blur_enabled: true,
      blur_radius_dp: 6,
      muted: true,
      loop: true
    }
  },
  header: { transparent: true, overlay: false },
  input: {
    style: 'agent',
    transparent: true,
    floating: true,
    liquid_glass: true,
    water_glass: false
  },
  font: {
    type: 'system',
    system_font_name: 'default',
    custom_font_asset_url: null,
    scale: 1.05
  },
  bubble: {
    ...MOCK_THEME_DARK.bubble,
    user_image: {
      enabled: true,
      asset_url: BUBBLE_TILE_IMAGE,
      render_mode: 'tiled_nine_slice',
      crop_left: 0.12,
      crop_top: 0.12,
      crop_right: 0.88,
      crop_bottom: 0.88,
      repeat_start: 0.35,
      repeat_end: 0.65,
      repeat_y_start: 0.35,
      repeat_y_end: 0.65,
      scale: 1
    },
    user_font: {
      type: 'system',
      system_font_name: 'KaiTi',
      custom_font_asset_url: null,
      scale: 1
    }
  }
};

export const MOCK_BOOTSTRAP: WebBootstrapResponse = {
  version_name: 'preview-studio-mock',
  current_chat_id: 'chat-main',
  default_chat_style: 'bubble',
  default_input_style: 'agent',
  show_thinking_process: true,
  show_status_tags: true,
  show_input_processing_status: true,
  capabilities: {
    attachments: true,
    per_chat_theme: true,
    structured_render: true,
    streaming: true,
    rename_chat: true,
    delete_chat: true
  }
};

export const MOCK_CHARACTER_SELECTOR: WebCharacterSelectorResponse = {
  active_prompt: {
    type: 'character_card',
    id: 'card-default',
    name: '默认助手',
    avatar_url: null
  },
  cards: [
    {
      id: 'card-default',
      name: '默认助手',
      description: '通用编程与写作助手',
      avatar_url: null,
      created_at: 1710000000000,
      updated_at: 1710000000000
    },
    {
      id: 'card-atmosphere',
      name: '氛围模式',
      description: '演示背景图与气泡主题',
      avatar_url: null,
      created_at: 1710000000000,
      updated_at: 1710000000000
    }
  ],
  groups: [
    {
      id: 'group-work',
      name: '工作',
      description: '工作相关角色',
      member_count: 1,
      avatar_url: null,
      created_at: 1710000000000,
      updated_at: 1710000000000
    }
  ]
};

export const MOCK_MODEL_SELECTOR: WebModelSelectorState = {
  current_config_id: 'cfg-main',
  current_config_name: '主力配置',
  current_model_index: 0,
  current_model_name: 'operit-preview-model',
  current_provider_type: 'OpenAI',
  locked_by_character_card: false,
  locked_character_card_id: null,
  locked_character_card_name: null,
  thinking_quality_mapping: {
    mode: 'levels',
    parameter_label: '思考强度',
    options: [
      { id: 'off', label: '关闭' },
      { id: 'low', label: '低' },
      { id: 'medium', label: '中' },
      { id: 'high', label: '高' }
    ],
    reasoning_required: false
  },
  configs: [
    {
      id: 'cfg-main',
      name: '主力配置',
      model_name: 'operit-preview-model',
      models: ['operit-preview-model', 'operit-preview-model-pro'],
      selected: true,
      selected_model_index: 0
    },
    {
      id: 'cfg-fast',
      name: '快速配置',
      model_name: 'operit-fast',
      models: ['operit-fast'],
      selected: false,
      selected_model_index: 0
    }
  ]
};

export const MOCK_INPUT_SETTINGS: WebInputSettingsState = {
  enable_thinking_mode: true,
  thinking_option_id: 'medium',
  enable_memory_auto_update: true,
  enable_auto_read: false,
  enable_max_context_mode: false,
  enable_tools: true,
  disable_stream_output: false,
  disable_user_preference_description: false,
  permission_level: 'ALLOW',
  current_window_tokens: 6180,
  base_context_length_k: 32,
  max_context_length_k: 128,
  active_context_length_k: 64,
  max_window_tokens: 65536
};

export const MOCK_MEMORY_SELECTOR: WebMemorySelectorState = {
  current_profile_id: 'mem-default',
  profiles: [
    { id: 'mem-default', name: '默认记忆' },
    { id: 'mem-project', name: '项目记忆' }
  ]
};

export const MOCK_CHATS: WebChatSummary[] = [
  {
    id: 'chat-main',
    title: '预览：结构化渲染与主题',
    updated_at: Date.now() - 1000 * 60 * 3,
    group: null,
    character_card_name: '默认助手',
    character_group_id: null,
    character_group_name: null,
    binding_avatar_url: null,
    parent_chat_id: null,
    active_streaming: false,
    locked: false
  },
  {
    id: 'chat-light',
    title: '预览：浅色主题',
    updated_at: Date.now() - 1000 * 60 * 60 * 2,
    group: null,
    character_card_name: '默认助手',
    character_group_id: null,
    character_group_name: null,
    binding_avatar_url: null,
    parent_chat_id: null,
    active_streaming: false,
    locked: false
  },
  {
    id: 'chat-scenic',
    title: '预览：背景与气泡图片',
    updated_at: Date.now() - 1000 * 60 * 60 * 26,
    group: '收藏',
    character_card_name: '氛围模式',
    character_group_id: null,
    character_group_name: null,
    binding_avatar_url: null,
    parent_chat_id: null,
    active_streaming: false,
    locked: true
  }
];

const FILE_DIFF_SAMPLE = [
  '<file-diff path="app/src/main.js" details="2 处修改">',
  '<![CDATA[',
  '@@ -12,7 +12,9 @@',
  ' function loadConfig(path) {',
  '-  const raw = fs.readFileSync(path);',
  '+  const raw = fs.readFileSync(path, "utf8");',
  '+  const parsed = JSON.parse(raw);',
  '+  validateSchema(parsed);',
  '   return parsed;',
  ' }',
  ']]>',
  '</file-diff>'
].join('\n');

const BASE_TIME = Date.now() - 1000 * 60 * 5;

export const MOCK_MESSAGES_MAIN: WebChatMessage[] = [
  {
    id: 'msg-1',
    sender: 'user',
    content_raw: '帮我看下这个项目的主题系统能不能在浏览器里预览？',
    timestamp: BASE_TIME,
    input_tokens: 24,
    cached_input_tokens: 0,
    output_tokens: 0,
    attachments: [],
    wait_duration_ms: 420,
    output_duration_ms: 0,
    completed_at: BASE_TIME
  },
  {
    id: 'msg-2',
    sender: 'assistant',
    content_raw: '',
    timestamp: BASE_TIME + 1000,
    role_name: '默认助手',
    provider: 'OpenAI',
    model_name: 'operit-preview-model',
    input_tokens: 26,
    cached_input_tokens: 12,
    output_tokens: 180,
    wait_duration_ms: 640,
    output_duration_ms: 2100,
    completed_at: BASE_TIME + 4000,
    variant_count: 2,
    selected_variant_index: 0,
    content_blocks: [
      {
        kind: 'group',
        group_type: 'think_tools',
        children: [
          {
            kind: 'xml',
            tag_name: 'think',
            raw_tag_name: 'think',
            attrs: {},
            content:
              '用户想在浏览器预览主题。需要梳理：主题快照结构、CSS 变量管线、背景与气泡能力。先给结论再给步骤。',
            closed: true
          },
          {
            kind: 'xml',
            tag_name: 'tool',
            raw_tag_name: 'tool',
            attrs: { name: 'ReadFile', path: 'web-chat/src/ui/features/chat/util/chatTheme.ts' },
            content: '读取 chatTheme.ts 共 300 行',
            closed: true
          },
          {
            kind: 'xml',
            tag_name: 'tool_result',
            raw_tag_name: 'tool_result',
            attrs: { name: 'ReadFile', status: 'success' },
            content:
              'buildChatThemeStyle 把快照展开为 CSS 变量；buildChatFontFaceCss 注入自定义字体。',
            closed: true
          }
        ]
      },
      {
        kind: 'xml',
        tag_name: 'tool_result',
        raw_tag_name: 'tool_result',
        attrs: { name: 'EditFile', status: 'success' },
        content: FILE_DIFF_SAMPLE,
        closed: true
      },
      {
        kind: 'xml',
        tag_name: 'status',
        raw_tag_name: 'status',
        attrs: { type: 'completion' },
        content: '主题预览方案已就绪',
        closed: true
      },
      {
        kind: 'text',
        content: [
          '可以。整体分三步：\n\n',
          '1. 抽出 `SimulatorShell` 复用 web-chat 组件\n',
          '2. mock 层喂 `WebThemeSnapshot` 驱动 CSS 变量\n',
          '3. 插槽插件在浏览器执行 screen 脚本实时渲染\n\n',
          '```ts\nconst style = buildChatThemeStyle(snapshot);\nroot.setAttribute(\'style\', style);\n```\n\n',
          '这样手机端与浏览器共享同一套主题语义。'
        ].join('')
      }
    ],
    attachments: []
  },
  {
    id: 'msg-3',
    sender: 'user',
    content_raw: '很好，另外把浅色模式也展示一下。',
    timestamp: BASE_TIME + 8000,
    input_tokens: 18,
    cached_input_tokens: 0,
    output_tokens: 0,
    wait_duration_ms: 210,
    output_duration_ms: 0,
    completed_at: BASE_TIME + 8000,
    attachments: [
      {
        id: 'attach-note',
        file_name: '主题清单.md',
        mime_type: 'text/markdown',
        file_size: 1284,
        content: null,
        asset_url: null
      }
    ]
  },
  {
    id: 'msg-4',
    sender: 'assistant',
    content_raw: '',
    timestamp: BASE_TIME + 9000,
    role_name: '默认助手',
    input_tokens: 30,
    cached_input_tokens: 0,
    output_tokens: 92,
    wait_duration_ms: 380,
    output_duration_ms: 960,
    completed_at: BASE_TIME + 11000,
    content_blocks: [
      {
        kind: 'xml',
        tag_name: 'search',
        raw_tag_name: 'search',
        attrs: { query: 'light theme palette' },
        content: '',
        closed: true
      },
      {
        kind: 'text',
        content: '浅色主题在「浅色主题」会话里，切过去即可看到 palette 差异。'
      }
    ],
    attachments: []
  }
];

export const MOCK_MESSAGES_LIGHT: WebChatMessage[] = [
  {
    id: 'msg-light-1',
    sender: 'user',
    content_raw: '这是浅色主题的演示会话。',
    timestamp: Date.now() - 1000 * 60 * 60 * 2,
    input_tokens: 12,
    cached_input_tokens: 0,
    output_tokens: 0,
    attachments: [],
    wait_duration_ms: 180,
    output_duration_ms: 0,
    completed_at: Date.now() - 1000 * 60 * 60 * 2
  },
  {
    id: 'msg-light-2',
    sender: 'assistant',
    content_raw: '',
    timestamp: Date.now() - 1000 * 60 * 60 * 2 + 2000,
    role_name: '默认助手',
    input_tokens: 14,
    cached_input_tokens: 0,
    output_tokens: 46,
    wait_duration_ms: 260,
    output_duration_ms: 700,
    completed_at: Date.now() - 1000 * 60 * 60 * 2 + 3000,
    content_blocks: [
      {
        kind: 'text',
        content: '浅色 palette 下注意 on-primary 与代码块的对比度处理。'
      }
    ],
    attachments: []
  }
];

export const MOCK_MESSAGES_SCENIC: WebChatMessage[] = [
  {
    id: 'msg-scenic-1',
    sender: 'user',
    content_raw: '展示一下背景图、透明输入栏和九宫格气泡。',
    timestamp: Date.now() - 1000 * 60 * 60 * 26,
    input_tokens: 16,
    cached_input_tokens: 0,
    output_tokens: 0,
    attachments: [],
    wait_duration_ms: 300,
    output_duration_ms: 0,
    completed_at: Date.now() - 1000 * 60 * 60 * 26
  },
  {
    id: 'msg-scenic-2',
    sender: 'assistant',
    content_raw: '',
    timestamp: Date.now() - 1000 * 60 * 60 * 26 + 1500,
    role_name: '氛围模式',
    input_tokens: 18,
    cached_input_tokens: 0,
    output_tokens: 52,
    wait_duration_ms: 240,
    output_duration_ms: 800,
    completed_at: Date.now() - 1000 * 60 * 60 * 26 + 2500,
    content_blocks: [
      {
        kind: 'text',
        content: '用户气泡使用九宫格贴图，输入栏为透明 + 液态玻璃，背景带 6px 模糊。'
      }
    ],
    attachments: []
  }
];

export const MOCK_THEMES: Record<string, WebThemeSnapshot> = {
  'chat-main': MOCK_THEME_DARK,
  'chat-light': MOCK_THEME_LIGHT,
  'chat-scenic': MOCK_THEME_SCENIC
};

export const MOCK_MESSAGES: Record<string, WebChatMessage[]> = {
  'chat-main': MOCK_MESSAGES_MAIN,
  'chat-light': MOCK_MESSAGES_LIGHT,
  'chat-scenic': MOCK_MESSAGES_SCENIC
};

export const MOCK_STREAM_REPLY = [
  '<think>收到消息，组织回复结构：先确认，再给细节。</think>',
  '收到。这条回复由 MockTransport 的 SSE 流逐块推送，',
  '用来验证 `assistant_delta` 增量合并与流式渲染。\n\n',
  '- 事件顺序与真机一致：start → user_message → assistant_delta… → assistant_done\n',
  '- 每个分块约 40ms，观察滚动跟随与光标效果\n\n',
  '```kotlin\nstreamMessage(...) // 客户端解析 event/data 块\n```'
].join('');

export { PLACEHOLDER_IMAGE };
