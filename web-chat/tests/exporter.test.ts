import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { loadThemePackageFromBytes } from '../src/shared/theme/packageLoader';
import { createEditorState, editorReducer } from '../src/shared/theme/editorState';
import {
  buildExportManifest,
  exportThemePackage,
  exportFileName,
  smokeValidateExportedArchive
} from '../src/shared/theme/exporter';
import { decodeThemePackageManifest, THEME_PACKAGE_MANIFEST_ENTRY } from '../src/shared/theme/manifest';
import { readThemeZip } from '../src/shared/theme/zipReader';

async function defaultManifest() {
  const bytes = readFileSync(resolve(__dirname, '../public/templates/operit-default-3.0.0.otheme'));
  const { manifest } = await loadThemePackageFromBytes(new Uint8Array(bytes));
  return manifest;
}

describe('buildExportManifest 烘焙', () => {
  it('非 URI 编辑值烘入 defaultValue（accent_color）', async () => {
    const manifest = await defaultManifest();
    const state = createEditorState({ manifest, assets: new Map(), archiveBytes: 1, archiveSha256: '0'.repeat(64) });
    const next = editorReducer(state, {
      kind: 'setValue',
      parameterId: 'accent_color',
      value: { type: 'color', argb: 0xff123456 }
    });
    const plan = buildExportManifest({ manifest, state: next, boundAssets: [] });
    const accent = plan.manifest.parameters.find((parameter) => parameter.id === 'accent_color');
    expect(accent?.defaultValue).toEqual({ type: 'color', argb: 0xff123456 });
  });

  it('URI 参数值不烘焙并出现在提示清单', async () => {
    const manifest = await defaultManifest();
    const state = createEditorState({ manifest, assets: new Map(), archiveBytes: 1, archiveSha256: '0'.repeat(64) });
    const next = editorReducer(state, {
      kind: 'setValue',
      parameterId: 'background_image',
      value: { type: 'image_uri', uri: 'asset://bg' }
    });
    const plan = buildExportManifest({ manifest, state: next, boundAssets: [] });
    const bg = plan.manifest.parameters.find((parameter) => parameter.id === 'background_image');
    expect(bg?.defaultValue).toBeNull();
    expect(plan.unbakedUriParameters).toContain('background_image');
  });

  it('绑定素材写入 assets[] 且去重', async () => {
    const manifest = await defaultManifest();
    const record = {
      key: 'bg',
      path: 'assets/image/bg.png',
      kind: 'BITMAP' as const,
      mimeType: 'image/png',
      sha256: 'a'.repeat(64),
      byteSize: 8,
      blob: new Blob(['x'], { type: 'image/png' })
    };
    const plan = buildExportManifest({ manifest, state: createEditorState({ manifest, assets: new Map(), archiveBytes: 1, archiveSha256: '0'.repeat(64) }), boundAssets: [record] });
    expect(plan.manifest.assets.map((asset) => asset.key)).toContain('bg');
  });
});

describe('exportThemePackage zip 回环', () => {
  it('导出的包可由 loader 重新载入并校验通过', async () => {
    const manifest = await defaultManifest();
    const state = createEditorState({ manifest, assets: new Map(), archiveBytes: 1, archiveSha256: '0'.repeat(64) });
    const next = editorReducer(state, {
      kind: 'setValue',
      parameterId: 'accent_color',
      value: { type: 'color', argb: 0xff22aa22 }
    });
    const plan = buildExportManifest({ manifest, state: next, boundAssets: [] });
    const result = await exportThemePackage(plan, []);
    const sha = await smokeValidateExportedArchive(result.blob);
    expect(sha).toBe(result.sha256);

    const bytes = new Uint8Array(await result.blob.arrayBuffer());
    const zip = await readThemeZip(bytes);
    expect(zip.comment).toBe('Operit Theme Package');
    const loaded = await loadThemePackageFromBytes(bytes);
    expect(loaded.manifest.parameters.find((parameter) => parameter.id === 'accent_color')?.defaultValue).toEqual({ type: 'color', argb: 0xff22aa22 });
    expect(loaded.manifest.parameters.find((parameter) => parameter.id === 'accent_color')?.control.type).toBe('color_palette');
    expect(loaded.manifest.presentation.behavior.conversation.bubbleShowAvatar).toBe(true);
  });

  it('本地化文本在导出时包装 values 并回读', async () => {
    const manifest = await defaultManifest();
    const plan = buildExportManifest({ manifest, state: createEditorState({ manifest, assets: new Map(), archiveBytes: 1, archiveSha256: '0'.repeat(64) }), boundAssets: [] });
    const result = await exportThemePackage(plan, []);
    const bytes = new Uint8Array(await result.blob.arrayBuffer());
    const zip = await readThemeZip(bytes);
    const raw = zip.entries.get(THEME_PACKAGE_MANIFEST_ENTRY)!;
    const text = new TextDecoder().decode(raw);
    const parsed = JSON.parse(text) as { displayName: { values: Record<string, string> } };
    expect(parsed.displayName.values['*']).toBeTruthy();
    const decoded = decodeThemePackageManifest(text);
    expect(decoded.displayName['*']).toBeTruthy();
  });
});

describe('exportFileName', () => {
  it('生成 包ID-版本.otheme', () => {
    expect(exportFileName('operit.my_theme', '0.1.0')).toBe('operit.my_theme-0.1.0.otheme');
  });
});
