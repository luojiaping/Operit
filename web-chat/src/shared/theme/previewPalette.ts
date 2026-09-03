import type { WebThemeSnapshot } from '../../ui/features/chat/util/chatTypes';

// 预览面板与主题设置页选色的“自定义色派生”。
// 容器色/对比文本按近似规则（浅色提亮 0.7、暗色提亮 0.2 / 压暗 0.3），
// 供 mock/iframe 预览使用；真机最终值由 app `ThemePackageUiRuntimeV2` 链接后下发。

type PreviewPalettePick = Pick<
  WebThemeSnapshot['palette'],
  | 'primary_color'
  | 'secondary_color'
  | 'primary_container_color'
  | 'on_primary_container_color'
>;

export function deriveCustomPalette(
  primary: string,
  secondary: string,
  isLight: boolean
): PreviewPalettePick {
  if (isLight) {
    const primaryContainer = lightenColor(primary, 0.7);
    return {
      primary_color: primary,
      secondary_color: secondary,
      primary_container_color: primaryContainer,
      on_primary_container_color: contrastingText(primaryContainer)
    };
  }
  return {
    primary_color: lightenColor(primary, 0.2),
    secondary_color: lightenColor(secondary, 0.2),
    primary_container_color: darkenColor(primary, 0.3),
    on_primary_container_color: '#ffffff'
  };
}

function contrastingText(color: string): string {
  const rgb = parseRgb(color);
  if (rgb == null) {
    return '#ffffff';
  }
  const luminance = (0.299 * rgb.red + 0.587 * rgb.green + 0.114 * rgb.blue) / 255;
  return luminance > 0.58 ? '#08111d' : '#f7fbff';
}

function lightenColor(color: string, factor: number): string {
  const rgb = parseRgb(color);
  if (rgb == null) {
    return color;
  }
  return rgbToHex({
    red: Math.round(rgb.red + (255 - rgb.red) * factor),
    green: Math.round(rgb.green + (255 - rgb.green) * factor),
    blue: Math.round(rgb.blue + (255 - rgb.blue) * factor)
  });
}

function darkenColor(color: string, factor: number): string {
  const rgb = parseRgb(color);
  if (rgb == null) {
    return color;
  }
  return rgbToHex({
    red: Math.round(rgb.red * (1 - factor)),
    green: Math.round(rgb.green * (1 - factor)),
    blue: Math.round(rgb.blue * (1 - factor))
  });
}

function parseRgb(color: string): { red: number; green: number; blue: number } | null {
  if (!color.startsWith('#')) {
    return null;
  }
  const hex = color.slice(1);
  if (hex.length === 3) {
    const expanded = hex
      .split('')
      .map((char) => char + char)
      .join('');
    return hexToRgb(expanded);
  }
  if (hex.length === 6) {
    return hexToRgb(hex);
  }
  return null;
}

function hexToRgb(hex: string): { red: number; green: number; blue: number } | null {
  const red = Number.parseInt(hex.slice(0, 2), 16);
  const green = Number.parseInt(hex.slice(2, 4), 16);
  const blue = Number.parseInt(hex.slice(4, 6), 16);
  if (Number.isNaN(red) || Number.isNaN(green) || Number.isNaN(blue)) {
    return null;
  }
  return { red, green, blue };
}

function rgbToHex(rgb: { red: number; green: number; blue: number }): string {
  const channel = (value: number) => value.toString(16).padStart(2, '0');
  return `#${channel(clampChannel(rgb.red))}${channel(clampChannel(rgb.green))}${channel(clampChannel(rgb.blue))}`;
}

function clampChannel(value: number): number {
  return Math.min(255, Math.max(0, value));
}
