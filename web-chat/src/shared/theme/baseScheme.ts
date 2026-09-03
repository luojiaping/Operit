import type { WebThemePalette } from '../../ui/features/chat/util/chatTypes';

// Material3 语义色基线：schema 4 palette（13 键）之外的语义/错误/次容器色。
// Web 侧预览与 compose DSL runtime 在快照覆盖不到时以此为基线构成 colorScheme；
// 真机由 app 的 36 role 表经 token 解析得到完整 scheme，此处仅服务 web 预览面。

export interface M3SemanticScheme {
  tertiary_color: string;
  error_color: string;
  error_container_color: string;
  on_error_container_color: string;
  secondary_container_color: string;
  on_primary_color: string;
  on_secondary_color: string;
}

export const M3_BASELINE: Record<'dark' | 'light', M3SemanticScheme> = {
  dark: {
    tertiary_color: '#efb8c8',
    error_color: '#f2b8b5',
    error_container_color: '#8c1d18',
    on_error_container_color: '#f9dedc',
    secondary_container_color: '#4a4458',
    on_primary_color: '#08101b',
    on_secondary_color: '#08101b'
  },
  light: {
    tertiary_color: '#7d5260',
    error_color: '#b3261e',
    error_container_color: '#f9dedc',
    on_error_container_color: '#410e0b',
    secondary_container_color: '#cdeee9',
    on_primary_color: '#ffffff',
    on_secondary_color: '#ffffff'
  }
};

export function resolveM3SemanticScheme(
  palette: WebThemePalette | null | undefined,
  isLight: boolean
): M3SemanticScheme {
  const base = M3_BASELINE[isLight ? 'light' : 'dark'];
  return {
    tertiary_color: palette?.tertiary_color ?? base.tertiary_color,
    error_color: palette?.error_color ?? base.error_color,
    error_container_color: palette?.error_container_color ?? base.error_container_color,
    on_error_container_color: palette?.on_error_container_color ?? base.on_error_container_color,
    secondary_container_color:
      palette?.secondary_container_color ?? base.secondary_container_color,
    on_primary_color: palette?.on_primary_color ?? base.on_primary_color,
    on_secondary_color: palette?.on_secondary_color ?? base.on_secondary_color
  };
}

export type SemanticColorScheme = M3SemanticScheme & {
  on_primary_container: string;
  on_surface: string;
  on_surface_variant: string;
};

export function buildSemanticColorScheme(
  palette: WebThemePalette | null | undefined,
  isLight: boolean
): SemanticColorScheme | null {
  if (palette == null) {
    return null;
  }
  const m3 = resolveM3SemanticScheme(palette, isLight);
  return {
    ...m3,
    on_primary_container: palette.on_primary_container_color,
    on_surface: palette.on_surface_color,
    on_surface_variant: palette.on_surface_variant_color
  };
}
