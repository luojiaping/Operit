import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  colorToHslArgb,
  hslToArgb,
  luminanceArgb,
  resolveThemeRuntime
} from '../src/shared/theme/runtime';
import { loadThemePackageFromBytes } from '../src/shared/theme/packageLoader';

describe('runtime 颜色常量', () => {
  it('hslToArgb 对纯色与灰阶正确', () => {
    // 纯红 HSL(0, 1.0, 0.5) → #ff0000
    expect(hslToArgb(0, 1, 0.5)).toBe(0xffff0000);
    // 白
    expect(hslToArgb(0, 1, 1)).toBe(0xffffffff);
    // 黑
    expect(hslToArgb(0, 1, 0)).toBe(0xff000000);
  });

  it('colorToHslArgb 与 hslToArgb 往返一致', () => {
    const argb = 0xff4271cd;
    const [h, s, l] = colorToHslArgb(argb);
    expect(hslToArgb(h, s, l)).toBe(argb);
  });

  it('luminance 阈值判定', () => {
    expect(luminanceArgb(0xffffffff) >= 0.45).toBe(true);
    expect(luminanceArgb(0xff000000) >= 0.45).toBe(false);
  });
});

describe('resolveThemeRuntime', () => {
  it('default 3.0.0 全量解析：accpet palette 由种子派生且 13 键齐全', async () => {
    const bytes = readFileSync(resolve(__dirname, '../public/templates/operit-default-3.0.0.otheme'));
    const { manifest } = await loadThemePackageFromBytes(new Uint8Array(bytes));
    const values = new Map<string, null>();
    for (const parameter of manifest.parameters) {
      values.set(parameter.id, parameter.defaultValue);
    }
    const { snapshot } = resolveThemeRuntime(manifest, values, 'dark');
    expect(snapshot.palette.primary_color).toMatch(/^#[0-9a-f]{6}$/);
    expect(snapshot.palette.background_color).toMatch(/^#[0-9a-f]{6}$/);
    expect(snapshot.palette.primary_container_color).toMatch(/^#[0-9a-f]{6}$/);
    expect(snapshot.palette.on_primary_container_color).toMatch(/^#[0-9a-f]{6}$/);
    expect(snapshot.palette.surface_color).toMatch(/^#[0-9a-f]{6}$/);
    expect(snapshot.palette.outline_variant_color).toMatch(/^#[0-9a-f]{6}$/);
    expect(snapshot.bubble.show_avatar).toBe(true);
    expect(snapshot.avatars.shape).toBe('circle');
  });

  it('accent_color 修改后 primary 变化且 derived onPrimaryContainer 同步', async () => {
    const bytes = readFileSync(resolve(__dirname, '../public/templates/operit-default-3.0.0.otheme'));
    const { manifest } = await loadThemePackageFromBytes(new Uint8Array(bytes));
    const values = new Map<string, null>();
    for (const parameter of manifest.parameters) {
      values.set(parameter.id, parameter.defaultValue);
    }
    const baseline = resolveThemeRuntime(manifest, values, 'dark').snapshot;

    const values2 = new Map(values);
    values2.set('accent_color', { type: 'color', argb: 0xff22aa22 });
    const changed = resolveThemeRuntime(manifest, values2, 'dark').snapshot;

    expect(changed.palette.primary_color).not.toBe(baseline.palette.primary_color);
    expect(changed.palette.primary_container_color).not.toBe(
      baseline.palette.primary_container_color
    );
  });

  it('背景图参数存在时输出 stage 背景；无参数为 null', async () => {
    const bytes = readFileSync(resolve(__dirname, '../public/templates/operit-default-3.0.0.otheme'));
    const { manifest } = await loadThemePackageFromBytes(new Uint8Array(bytes));
    const values = new Map<string, null>();
    for (const parameter of manifest.parameters) {
      values.set(parameter.id, parameter.defaultValue);
    }
    const empty = resolveThemeRuntime(manifest, values, 'dark').snapshot;
    expect(empty.background.stage).toBeNull();

    const values2 = new Map(values);
    values2.set('background_image', {
      type: 'image_uri',
      uri: 'asset://bg'
    });
    const withImage = resolveThemeRuntime(manifest, values2, 'dark').snapshot;
    expect(withImage.background.stage?.fit).toBe('crop');
    expect(withImage.background.stage?.asset_url).toBe('asset://bg');
    expect(withImage.background.stage?.opacity).toBeCloseTo(0.22);
  });
});
