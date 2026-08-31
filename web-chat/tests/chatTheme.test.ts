import { describe, expect, it } from 'vitest';
import {
  buildBubbleImageBorderStyle,
  buildChatThemeStyle
} from '../src/ui/features/chat/util/chatTheme';
import type { WebThemeSnapshot } from '../src/ui/features/chat/util/chatTypes';

function baseTheme(overrides: Partial<WebThemeSnapshot> = {}): WebThemeSnapshot {
  return {
    source: 'test',
    source_id: null,
    theme_mode: 'dark',
    use_system_theme: false,
    use_custom_colors: false,
    palette: {
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
      outline_variant_color: '#45464f'
    },
    background: { type: 'image', asset_url: null, opacity: 0.3 },
    header: { transparent: false, overlay: false },
    input: {
      style: 'agent',
      transparent: false,
      floating: false,
      liquid_glass: false,
      water_glass: false
    },
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
      show_message_timing_stats: true,
      show_message_timestamp: true,
      tool_collapse_mode: 'all',
      global_user_name: 'tester'
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
    avatars: { shape: 'circle', corner_radius: 8, user_avatar_url: null, assistant_avatar_url: null },
    ...overrides
  };
}

describe('buildChatThemeStyle 派生对齐 app', () => {
  it('暗色快照输出 M3 baseline 的 tertiary 与 error', () => {
    const style = buildChatThemeStyle(baseTheme());
    expect(style['--chat-tertiary']).toBe('#efb8c8');
    expect(style['--chat-error']).toBe('#f2b8b5');
    expect(style['--chat-error-container']).toBe('#8c1d18');
  });

  it('浅色快照输出 M3 baseline 浅色语义色', () => {
    const style = buildChatThemeStyle(
      baseTheme({
        theme_mode: 'light',
        palette: {
          ...baseTheme().palette,
          background_color: '#faf8ff',
          surface_color: '#ffffff'
        }
      })
    );
    expect(style['--chat-tertiary']).toBe('#7d5260');
    expect(style['--chat-error']).toBe('#b3261e');
  });

  it('快照语义色优先于 baseline 派生', () => {
    const theme = baseTheme();
    theme.palette.tertiary_color = '#123456';
    const style = buildChatThemeStyle(theme);
    expect(style['--chat-tertiary']).toBe('#123456');
  });

  it('onColorMode=dark 时对比文本强制黑（对齐 app 阈值 0.5 而非旧 web 的 0.58）', () => {
    const theme = baseTheme({ on_color_mode: 'dark' });
    theme.palette.on_primary_color = undefined;
    const style = buildChatThemeStyle(theme);
    expect(style['--chat-on-primary']).toBe('#000000');
  });

  it('快照提供 on_primary 时直接使用不再自算', () => {
    const theme = baseTheme();
    theme.palette.on_primary_color = '#deadbe';
    const style = buildChatThemeStyle(theme);
    expect(style['--chat-on-primary']).toBe('#deadbe');
  });

  it('背景模糊关闭时 blur 为 0，开启时用快照半径（A1）', () => {
    const off = buildChatThemeStyle(baseTheme());
    expect(off['--chat-background-blur']).toBe('0px');
    const theme = baseTheme();
    theme.background.use_blur = true;
    theme.background.blur_radius = 6;
    expect(buildChatThemeStyle(theme)['--chat-background-blur']).toBe('6px');
  });

  it('暗色 agent 输入卡底色混合 onSurface 8%（B5）', () => {
    const style = buildChatThemeStyle(baseTheme());
    expect(String(style['--chat-agent-card-bg'])).toContain('color-mix(in srgb');
  });

  it('气泡九宫格参数转 border-image（A4/A5）', () => {
    const theme = baseTheme();
    theme.bubble.user_image = {
      enabled: true,
      asset_url: 'https://example.com/tile.png',
      render_mode: 'tiled_nine_slice',
      crop_left: 0.1,
      crop_top: 0.2,
      crop_right: 0.3,
      crop_bottom: 0.4
    };
    const style = buildBubbleImageBorderStyle(theme.bubble.user_image);
    expect(style.borderImageSource).toBe('url(https://example.com/tile.png)');
    expect(style.borderImageSlice).toBe('10 70 60 20%');
    expect(style.borderImageRepeat).toBe('repeat');
  });

  it('nine_patch 模式映射为 stretch', () => {
    const theme = baseTheme();
    theme.bubble.user_image = {
      enabled: true,
      asset_url: 'https://example.com/tile.png',
      render_mode: 'nine_patch'
    };
    expect(buildBubbleImageBorderStyle(theme.bubble.user_image).borderImageRepeat).toBe('stretch');
  });

  it('气泡级字体输出独立变量，缺省回落全局（A10）', () => {
    const theme = baseTheme();
    theme.bubble.user_font = {
      type: 'system',
      system_font_name: 'KaiTi',
      custom_font_asset_url: null,
      scale: 1
    };
    const style = buildChatThemeStyle(theme);
    expect(String(style['--chat-user-font-family'])).toContain('KaiTi');
    expect(String(style['--chat-assistant-font-family'])).not.toContain('KaiTi');
  });

  it('use_system_theme 分叉时明暗由 palette 背景亮度决定（A19）', () => {
    const theme = baseTheme({ theme_mode: 'dark', use_system_theme: true });
    // 系统亮色解析后的亮背景 palette + theme_mode=dark 的组合
    theme.palette.background_color = '#faf8ff';
    theme.palette.surface_color = '#ffffff';
    const style = buildChatThemeStyle(theme);
    expect(style['--chat-shadow']).toBe('rgba(44, 58, 90, 0.14)');
  });
});
