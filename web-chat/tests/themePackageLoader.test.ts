import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { beforeAll, describe, expect, it } from 'vitest';
import { createStoredZip } from '../src/preview/zipWriter';
import { readThemeZip } from '../src/shared/theme/zipReader';
import {
  loadThemePackageFromBytes,
  sha256Hex
} from '../src/shared/theme/packageLoader';
import {
  createBlankPackageManifest,
  validateBlankPackageForm
} from '../src/shared/theme/blankPackage';

const TEMPLATES_DIR = resolve(__dirname, '../public/templates');
const DEFAULT_ARCHIVE = readFileSync(resolve(TEMPLATES_DIR, 'operit-default-3.0.0.otheme'));
const CYBER_ARCHIVE = readFileSync(resolve(TEMPLATES_DIR, 'operit-cyber-grid-3.0.0.otheme'));

describe('zipReader 与 zipWriter 往返', () => {
  it('写入 comment 后可读回，条目字节一致', async () => {
    const zip = createStoredZip(
      [
        { name: 'operit-theme.json', bytes: new TextEncoder().encode('{"a":1}') },
        { name: 'assets/image/photo.png', bytes: new Uint8Array([1, 2, 3, 4]) }
      ],
      'Operit Theme Package'
    );
    const bytes = new Uint8Array(await zip.arrayBuffer());
    const contents = await readThemeZip(bytes);
    expect(contents.comment).toBe('Operit Theme Package');
    expect(new TextDecoder().decode(contents.entries.get('operit-theme.json')!)).toBe('{"a":1}');
    expect(Array.from(contents.entries.get('assets/image/photo.png')!)).toEqual([1, 2, 3, 4]);
  });

  it('comment 长度不符时拒绝', async () => {
    const zip = createStoredZip(
      [{ name: 'a.txt', bytes: new Uint8Array([1]) }],
      'Operit Theme Package'
    );
    const bytes = new Uint8Array(await zip.arrayBuffer());
    const truncated = bytes.slice(0, bytes.byteLength - 2);
    await expect(readThemeZip(truncated)).rejects.toThrow(/结束记录/);
  });

  it('不安全路径被拒绝', async () => {
    const zip = createStoredZip([{ name: '../evil.txt', bytes: new Uint8Array([1]) }]);
    await expect(
      readThemeZip(new Uint8Array(await zip.arrayBuffer()))
    ).rejects.toThrow(/不安全/);
  });
});

describe('loadThemePackageFromBytes 真实归档', () => {
  it('default 3.0.0 完整通过并锁定坐标', async () => {
    const studioPackage = await loadThemePackageFromBytes(
      new Uint8Array(DEFAULT_ARCHIVE)
    );
    expect(studioPackage.manifest.packageId).toBe('operit.default');
    expect(studioPackage.manifest.version).toBe('3.0.0');
    expect(studioPackage.manifest.schemaVersion).toBe(4);
    expect(studioPackage.archiveSha256).toBe(
      '5a96abf521ec22a8c486ccb5b4d7f561a4bed4f6f90dfe742647024eb79db91f'
    );
    expect(studioPackage.manifest.parameters.length).toBeGreaterThan(5);
  });

  it('cyber 3.0.0 basis 指向 default 坐标', async () => {
    const studioPackage = await loadThemePackageFromBytes(new Uint8Array(CYBER_ARCHIVE));
    expect(studioPackage.manifest.packageId).toBe('operit.cyber_grid');
    expect(studioPackage.manifest.basis?.archiveSha256).toBe(
      '5a96abf521ec22a8c486ccb5b4d7f561a4bed4f6f90dfe742647024eb79db91f'
    );
    expect(
      studioPackage.manifest.parameters.every((parameter) => parameter.id.startsWith('cyber_'))
    ).toBe(true);
  });

  it('坏 comment 的归档被拒绝', async () => {
    const zip = createStoredZip(
      [{ name: 'operit-theme.json', bytes: new TextEncoder().encode('{}') }],
      'Wrong Comment'
    );
    await expect(
      loadThemePackageFromBytes(new Uint8Array(await zip.arrayBuffer()))
    ).rejects.toThrow(/结构不合法|comment/);
  });

  it('缺根 manifest 被拒绝', async () => {
    const zip = createStoredZip([{ name: 'other.json', bytes: new Uint8Array([1]) }]);
    await expect(
      loadThemePackageFromBytes(new Uint8Array(await zip.arrayBuffer()))
    ).rejects.toThrow(/operit-theme\.json/);
  });
});

describe('blankPackage 派生', () => {
  const baseCoordinate = {
    packageId: 'operit.default',
    version: '3.0.0',
    archiveSha256:
      '5a96abf521ec22a8c486ccb5b4d7f561a4bed4f6f90dfe742647024eb79db91f'
  };
  let base: Awaited<ReturnType<typeof loadThemePackageFromBytes>>['manifest'];

  beforeAll(async () => {
    base = (await loadThemePackageFromBytes(new Uint8Array(DEFAULT_ARCHIVE))).manifest;
  });

  it('表单校验：非法 packageId/版本/空名被拒绝', () => {
    expect(
      validateBlankPackageForm({ packageId: 'BadId', version: '1.0.0', displayName: 'x' })
    ).toHaveLength(1);
    expect(
      validateBlankPackageForm({ packageId: 'operit.ok', version: '1.0', displayName: 'x' })
    ).toHaveLength(1);
    expect(
      validateBlankPackageForm({ packageId: 'operit.ok', version: '1.0.0', displayName: ' ' })
    ).toHaveLength(1);
    expect(
      validateBlankPackageForm({ packageId: 'operit.ok', version: '1.0.0', displayName: '我的主题' })
    ).toHaveLength(0);
  });

  it('派生包通过语义校验并继承全量参数', () => {
    const derived = createBlankPackageManifest(base, baseCoordinate, {
      packageId: 'operit.my_theme',
      version: '0.1.0',
      displayName: '我的主题'
    });
    expect(derived.basis).toEqual(baseCoordinate);
    expect(derived.parameters).toHaveLength(base.parameters.length);
    expect(derived.variants).toEqual([]);
  });

  it('与基础包同名被拒绝', () => {
    expect(() =>
      createBlankPackageManifest(base, baseCoordinate, {
        packageId: 'operit.default',
        version: '9.9.9',
        displayName: '冲突'
      })
    ).toThrow(/相同/);
  });

  it('基础坐标缺 SHA 被拒绝', () => {
    expect(() =>
      createBlankPackageManifest(
        base,
        { packageId: 'operit.default', version: '3.0.0', archiveSha256: '' },
        { packageId: 'operit.my_theme', version: '0.1.0', displayName: '我的主题' }
      )
    ).toThrow(/archiveSha256/);
  });
});

describe('sha256Hex', () => {
  it('输出小写十六进制', async () => {
    const digest = await sha256Hex(new TextEncoder().encode('abc'));
    expect(digest).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
    );
  });
});
