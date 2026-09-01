import { useEffect, useRef, useState } from 'react';
import type { WebThemeSnapshot } from '../ui/features/chat/util/chatTypes';
import { deriveCustomPalette } from '../ui/features/chat/util/chatTheme';

// 主题工作室：左侧调参，右侧手机壳实时渲染。
// 基线 palette 只保留必填字段，用户改色后派生项（容器色、对比文本、
// 语义色）全部交给 chatTheme 内对齐 app 的派生管线实时计算

type ThemeMode = 'dark' | 'light';

interface ThemeDraft {
  mode: ThemeMode;
  primary: string;
  secondary: string;
  backgroundKind: 'color' | 'image';
  backgroundColor: string;
  backgroundImageUrl: string | null;
  backgroundOpacity: number;
  useBlur: boolean;
  blurRadius: number;
  userBubbleColor: string | null;
  assistantBubbleColor: string | null;
  bubbleRounded: boolean;
  showAvatar: boolean;
  fontScale: number;
  chatStyle: 'bubble' | 'cursor';
  inputStyle: 'agent' | 'classic';
  headerTransparent: boolean;
  inputTransparent: boolean;
  inputLiquidGlass: boolean;
}

const CLEAN_BASE: Record<ThemeMode, WebThemeSnapshot> = {
  dark: {
    source: 'global',
    source_id: null,
    theme_mode: 'dark',
    use_system_theme: false,
    use_custom_colors: true,
    palette: {
      background_color: '#0f1520',
      surface_color: '#1b202b',
      surface_variant_color: '#303542',
      surface_container_color: '#1f2532',
      surface_container_high_color: '#262d3b',
      primary_color: '#7b9bff',
      secondary_color: '#70d3c2',
      on_surface_color: '#f2f6ff',
      on_surface_variant_color: '#9ca8bb',
      outline_color: '#8f909a',
      outline_variant_color: '#45464f'
    },
    background: { type: 'image', asset_url: null, opacity: 0.3, use_blur: false, blur_radius: 10 },
    header: { transparent: false, overlay: false },
    input: { style: 'agent', transparent: false, floating: false, liquid_glass: false, water_glass: false },
    font: { type: 'system', system_font_name: 'default', custom_font_asset_url: null, scale: 1 },
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
      assistant_padding_left: 12,
      assistant_padding_right: 12,
      user_image: { enabled: false, asset_url: null, render_mode: 'tiled_nine_slice' },
      assistant_image: { enabled: false, asset_url: null, render_mode: 'tiled_nine_slice' }
    },
    avatars: { shape: 'circle', corner_radius: 8, user_avatar_url: null, assistant_avatar_url: null }
  },
  light: {
    source: 'global',
    source_id: null,
    theme_mode: 'light',
    use_system_theme: false,
    use_custom_colors: true,
    palette: {
      background_color: '#faf8ff',
      surface_color: '#ffffff',
      surface_variant_color: '#ece8f1',
      surface_container_color: '#f1edf6',
      surface_container_high_color: '#e7e2ef',
      primary_color: '#4c5fd5',
      secondary_color: '#2e9d8f',
      on_surface_color: '#1a1c22',
      on_surface_variant_color: '#43454e',
      outline_color: '#7a7a85',
      outline_variant_color: '#c8c4cf'
    },
    background: { type: 'image', asset_url: null, opacity: 0.3, use_blur: false, blur_radius: 10 },
    header: { transparent: false, overlay: false },
    input: { style: 'agent', transparent: false, floating: false, liquid_glass: false, water_glass: false },
    font: { type: 'system', system_font_name: 'default', custom_font_asset_url: null, scale: 1 },
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
      assistant_padding_left: 12,
      assistant_padding_right: 12,
      user_image: { enabled: false, asset_url: null, render_mode: 'tiled_nine_slice' },
      assistant_image: { enabled: false, asset_url: null, render_mode: 'tiled_nine_slice' }
    },
    avatars: { shape: 'circle', corner_radius: 8, user_avatar_url: null, assistant_avatar_url: null }
  }
};

const DEFAULT_DRAFT: ThemeDraft = {
  mode: 'dark',
  primary: '#7b9bff',
  secondary: '#70d3c2',
  backgroundKind: 'color',
  backgroundColor: '#0f1520',
  backgroundImageUrl: null,
  backgroundOpacity: 0.85,
  useBlur: true,
  blurRadius: 10,
  userBubbleColor: null,
  assistantBubbleColor: null,
  bubbleRounded: true,
  showAvatar: true,
  fontScale: 1,
  chatStyle: 'bubble',
  inputStyle: 'agent',
  headerTransparent: false,
  inputTransparent: false,
  inputLiquidGlass: false
};

function buildTheme(draft: ThemeDraft): WebThemeSnapshot {
  const base = CLEAN_BASE[draft.mode];
  const isLight = draft.mode === 'light';
  // 用户自定义色走 app 同款派生（暗色提亮 0.2、容器与对比文本同步），
  // 快照携带的即 app 解析后的最终色
  const customPalette = deriveCustomPalette(draft.primary, draft.secondary, isLight);
  const palette: WebThemeSnapshot['palette'] = {
    ...base.palette,
    ...customPalette
  };
  if (draft.backgroundKind === 'color') {
    palette.background_color = draft.backgroundColor;
  }
  return {
    ...base,
    theme_mode: draft.mode,
    palette,
    background: {
      type: 'image',
      asset_url: draft.backgroundKind === 'image' ? draft.backgroundImageUrl : null,
      opacity:
        draft.backgroundKind === 'image' ? draft.backgroundOpacity : 0,
      use_blur: draft.backgroundKind === 'image' && draft.useBlur,
      blur_radius: draft.blurRadius,
      muted: true,
      loop: true
    },
    header: {
      transparent: draft.headerTransparent,
      overlay: false,
      history_icon_color: null,
      pip_icon_color: null
    },
    input: {
      style: draft.inputStyle,
      transparent: draft.inputTransparent,
      floating: draft.inputTransparent,
      liquid_glass: draft.inputLiquidGlass,
      water_glass: false
    },
    font: { ...base.font, scale: draft.fontScale },
    chat_style: draft.chatStyle,
    bubble: {
      ...base.bubble,
      show_avatar: draft.showAvatar,
      user_rounded: draft.bubbleRounded,
      assistant_rounded: draft.bubbleRounded,
      user_bubble_color: draft.userBubbleColor,
      assistant_bubble_color: draft.assistantBubbleColor
    }
  };
}

function ColorField({
  label,
  value,
  onChange
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="studio-field is-color">
      <span>{label}</span>
      <span className="studio-color-row">
        <input
          onChange={(event) => onChange(event.target.value)}
          type="color"
          value={value}
        />
        <code>{value}</code>
      </span>
    </label>
  );
}

function ToggleField({
  label,
  value,
  onChange
}: {
  label: string;
  value: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="studio-field is-toggle">
      <span>{label}</span>
      <input
        checked={value}
        onChange={(event) => onChange(event.target.checked)}
        type="checkbox"
      />
    </label>
  );
}

function SliderField({
  label,
  max,
  min,
  step,
  value,
  onChange
}: {
  label: string;
  max: number;
  min: number;
  step: number;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="studio-field is-slider">
      <span>
        {label} <code>{value}</code>
      </span>
      <input
        max={max}
        min={min}
        onChange={(event) => onChange(Number(event.target.value))}
        step={step}
        type="range"
        value={value}
      />
    </label>
  );
}

export function ThemeStudioPanel({
  onApplyTheme
}: {
  onApplyTheme: (theme: WebThemeSnapshot) => void;
}) {
  const [draft, setDraft] = useState<ThemeDraft>(DEFAULT_DRAFT);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  // 面板合成完整快照后交给手机视口（iframe）应用，
  // 派生在 iframe 内的 chatTheme 管线完成，mock 本地即时无需节流
  useEffect(() => {
    onApplyTheme(buildTheme(draft));
  }, [draft, onApplyTheme]);

  function updateDraft(patch: Partial<ThemeDraft>) {
    setDraft((current) => ({ ...current, ...patch }));
  }

  function onBackgroundImagePicked(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      updateDraft({
        backgroundKind: 'image',
        backgroundImageUrl: typeof reader.result === 'string' ? reader.result : null
      });
    };
    reader.readAsDataURL(file);
  }

  return (
    <div className="studio-panel">
      <div className="studio-group">
        <h4>基础</h4>
        <div className="studio-segment">
          {(['dark', 'light'] as const).map((mode) => (
            <button
              className={draft.mode === mode ? 'is-active' : ''}
              key={mode}
              onClick={() => {
                const base = CLEAN_BASE[mode];
                updateDraft({
                  mode,
                  primary: base.palette.primary_color,
                  secondary: base.palette.secondary_color,
                  backgroundColor: base.palette.background_color
                });
              }}
              type="button"
            >
              {mode === 'dark' ? '深色' : '浅色'}
            </button>
          ))}
        </div>
        <ColorField
          label="主色"
          onChange={(primary) => updateDraft({ primary })}
          value={draft.primary}
        />
        <ColorField
          label="辅色"
          onChange={(secondary) => updateDraft({ secondary })}
          value={draft.secondary}
        />
      </div>

      <div className="studio-group">
        <h4>背景</h4>
        <div className="studio-segment">
          {(
            [
              ['color', '纯色'],
              ['image', '图片']
            ] as const
          ).map(([kind, label]) => (
            <button
              className={draft.backgroundKind === kind ? 'is-active' : ''}
              key={kind}
              onClick={() => updateDraft({ backgroundKind: kind })}
              type="button"
            >
              {label}
            </button>
          ))}
        </div>
        {draft.backgroundKind === 'color' ? (
          <ColorField
            label="背景颜色"
            onChange={(backgroundColor) => updateDraft({ backgroundColor })}
            value={draft.backgroundColor}
          />
        ) : (
          <>
            <div className="studio-file-row">
              <button
                className="studio-file-button"
                onClick={() => fileInputRef.current?.click()}
                type="button"
              >
                选择本地图片
              </button>
              <span className="studio-file-hint">图片只在浏览器本地使用</span>
              <input
                accept="image/*"
                hidden
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) {
                    onBackgroundImagePicked(file);
                  }
                  event.target.value = '';
                }}
                ref={fileInputRef}
                type="file"
              />
            </div>
            {draft.backgroundImageUrl ? (
              <img
                alt="背景预览"
                className="studio-background-thumb"
                src={draft.backgroundImageUrl}
              />
            ) : null}
            <SliderField
              label="背景不透明度"
              max={1}
              min={0.05}
              onChange={(backgroundOpacity) => updateDraft({ backgroundOpacity })}
              step={0.05}
              value={draft.backgroundOpacity}
            />
            <ToggleField
              label="背景模糊"
              onChange={(useBlur) => updateDraft({ useBlur })}
              value={draft.useBlur}
            />
            {draft.useBlur ? (
              <SliderField
                label="模糊半径"
                max={30}
                min={0}
                onChange={(blurRadius) => updateDraft({ blurRadius })}
                step={1}
                value={draft.blurRadius}
              />
            ) : null}
          </>
        )}
      </div>

      <div className="studio-group">
        <h4>气泡</h4>
        <ToggleField
          label="圆角气泡"
          onChange={(bubbleRounded) => updateDraft({ bubbleRounded })}
          value={draft.bubbleRounded}
        />
        <ToggleField
          label="显示头像"
          onChange={(showAvatar) => updateDraft({ showAvatar })}
          value={draft.showAvatar}
        />
        <ToggleField
          label="用户气泡用主色"
          onChange={(useTheme) =>
            updateDraft({ userBubbleColor: useTheme ? null : '#2e3b52' })
          }
          value={draft.userBubbleColor === null}
        />
      </div>

      <div className="studio-group">
        <h4>界面风格</h4>
        <div className="studio-segment">
          {(
            [
              ['bubble', '气泡消息'],
              ['cursor', '光标消息']
            ] as const
          ).map(([style, label]) => (
            <button
              className={draft.chatStyle === style ? 'is-active' : ''}
              key={style}
              onClick={() => updateDraft({ chatStyle: style })}
              type="button"
            >
              {label}
            </button>
          ))}
        </div>
        <div className="studio-segment">
          {(
            [
              ['agent', 'Agent 输入栏'],
              ['classic', 'Classic 输入栏']
            ] as const
          ).map(([style, label]) => (
            <button
              className={draft.inputStyle === style ? 'is-active' : ''}
              key={style}
              onClick={() => updateDraft({ inputStyle: style })}
              type="button"
            >
              {label}
            </button>
          ))}
        </div>
        <ToggleField
          label="透明顶栏"
          onChange={(headerTransparent) => updateDraft({ headerTransparent })}
          value={draft.headerTransparent}
        />
        <ToggleField
          label="透明输入栏"
          onChange={(inputTransparent) => updateDraft({ inputTransparent })}
          value={draft.inputTransparent}
        />
        {draft.inputTransparent ? (
          <ToggleField
            label="输入栏液态玻璃"
            onChange={(inputLiquidGlass) => updateDraft({ inputLiquidGlass })}
            value={draft.inputLiquidGlass}
          />
        ) : null}
      </div>

      <div className="studio-group">
        <h4>字体</h4>
        <SliderField
          label="字号缩放"
          max={1.6}
          min={0.8}
          onChange={(fontScale) => updateDraft({ fontScale })}
          step={0.05}
          value={draft.fontScale}
        />
      </div>

      <button
        className="studio-reset"
        onClick={() => setDraft(DEFAULT_DRAFT)}
        type="button"
      >
        重置为主题默认
      </button>
      <p className="studio-note">
        颜色派生（容器色、对比文本、语义色）与 app 的
        ThemeColorSchemeResolver 同一套规则，预览即所得。
      </p>
    </div>
  );
}
