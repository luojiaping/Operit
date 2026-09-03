import { describe, expect, it } from 'vitest';
import {
  buildChatFontFaceCss,
  buildChatThemeStyle,
  resolveThemeFontFamily
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
    background: { stage: null, media: null },
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
      user_padding_top: 12,
      user_padding_bottom: 12,
      assistant_padding_left: 12,
      assistant_padding_right: 12,
      assistant_padding_top: 12,
      assistant_padding_bottom: 12,
      user_font: { type: 'system', system_font_name: 'default', custom_font_asset_url: null, scale: 1 },
      assistant_font: { type: 'system', system_font_name: 'default', custom_font_asset_url: null, scale: 1 },
      user_image: {
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
      },
      assistant_image: {
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
      }
    },
    avatars: { shape: 'circle', corner_radius: 8, user_avatar_url: null, assistant_avatar_url: null },
    ...overrides
  };
}

describe('buildChatThemeStyle 派生对齐 app', () => {
  it('暗色快照输出可读文本色（luminance 0.58 阈值）', () => {
    const style = buildChatThemeStyle(baseTheme());
    expect(style['--chat-on-primary']).toBe('#08111d');
    expect(style['--chat-on-secondary']).toBe('#08111d');
  });

  it('浅色高亮 primary 输出浅色文本（浅色面）', () => {
    const style = buildChatThemeStyle(
      baseTheme({ theme_mode: 'light' })
    );
    expect(style['--chat-on-primary']).toBe('#08111d');
  });

  it('无背景资产时 stage/media 图像变量均为 none', () => {
    const style = buildChatThemeStyle(baseTheme());
    expect(style['--chat-stage-background-image']).toBe('none');
    expect(style['--chat-media-background-image']).toBe('none');
    expect(style['--chat-background-tint']).toBe('transparent');
  });

  it('图片媒体背景输出 image + blur + 不透明度链（A1）', () => {
    const theme = baseTheme({
      background: {
        stage: null,
        media: {
          type: 'image',
          asset_url: 'https://example.com/bg.png',
          opacity: 0.7,
          blur_enabled: true,
          blur_radius_dp: 6,
          muted: true,
          loop: true
        }
      }
    });
    const style = buildChatThemeStyle(theme);
    expect(style['--chat-media-background-image']).toBe('url(https://example.com/bg.png)');
    expect(style['--chat-media-background-opacity']).toBe('0.7');
    expect(style['--chat-media-background-blur']).toBe('6px');
  });

  it('视频媒体背景仍以 image 变量关闭、由 video 元素承载', () => {
    const theme = baseTheme({
      background: {
        stage: null,
        media: {
          type: 'video',
          asset_url: 'https://example.com/bg.mp4',
          opacity: 0.5,
          blur_enabled: false,
          blur_radius_dp: 0,
          muted: false,
          loop: false
        }
      }
    });
    const style = buildChatThemeStyle(theme);
    expect(style['--chat-media-background-image']).toBe('none');
  });

  it('stage 背景按 fit 映射 background-size', () => {
    const theme = baseTheme({
      background: {
        stage: { asset_url: 'https://example.com/stage.png', fit: 'crop', opacity: 0.4 },
        media: null
      }
    });
    const style = buildChatThemeStyle(theme);
    expect(style['--chat-stage-background-size']).toBe('cover');
    expect(style['--chat-stage-background-opacity']).toBe('0.4');
  });

  it('暗色 agent 输入卡底色随 surface 链派生', () => {
    const style = buildChatThemeStyle(baseTheme());
    expect(String(style['--chat-composer-bg'])).toBeDefined();
  });

  it('气泡圆角/头像圆角映射', () => {
    const style = buildChatThemeStyle(baseTheme());
    expect(style['--chat-user-radius']).toBe('20px');
    expect(style['--chat-avatar-radius']).toBe('999px');
  });

  it('气泡字体输出独立 font-face 家族（A10）', () => {
    const theme = baseTheme();
    theme.bubble.user_font = {
      type: 'file',
      system_font_name: null,
      custom_font_asset_url: 'https://example.com/user.otf',
      scale: 1
    };
    const css = buildChatFontFaceCss(theme);
    expect(css).toContain('OperitThemeUserBubbleFont');
    expect(resolveThemeFontFamily(theme.bubble.user_font, 'OperitThemeUserBubbleFont')).toContain(
      'OperitThemeUserBubbleFont'
    );
  });

  it('气泡字体无自定义资产时使用全局字体族', () => {
    const style = buildChatThemeStyle(baseTheme());
    expect(String(style['--chat-font-family'])).toContain('PingFang SC');
  });

  it('系统字体名称映射到 CSS 字体族', () => {
    const theme = baseTheme();
    theme.font = {
      type: 'system',
      system_font_name: 'monospace',
      custom_font_asset_url: null,
      scale: 1
    };
    expect(resolveThemeFontFamily(theme.font)).toContain('ui-monospace');
  });

  it('自定义字体资产走 font-face 家族名', () => {
    const theme = baseTheme();
    theme.font = {
      type: 'file',
      system_font_name: null,
      custom_font_asset_url: 'https://example.com/f.otf',
      scale: 1
    };
    expect(resolveThemeFontFamily(theme.font)).toContain('OperitThemeFont');
  });
});
