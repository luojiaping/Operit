import { describe, expect, it } from 'vitest';
import {
  assetKeyForName,
  validateAssetUpload
} from '../src/shared/theme/assets/validator';
import {
  createMemoryAssetLibrary,
  makeAssetUri,
  parseAssetUri
} from '../src/shared/theme/assets/library';

function pngBytes(): Uint8Array {
  // 最小 PNG 头：magic 8 字节 + 假数据（校验只断头，解码由注入 stub 完成）
  const bytes = new Uint8Array([
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52
  ]);
  return bytes;
}

const stubImageDecoder = async (): Promise<boolean> => true;

describe('validateAssetUpload', () => {
  it('png 图片通过校验并返回 sha256/size', async () => {
    const result = await validateAssetUpload(
      {
        key: 'bg',
        file: new Blob([pngBytes() as BlobPart], { type: 'image/png' }),
        mimeType: 'image/png',
        kind: 'BITMAP'
      },
      { imageDecoder: stubImageDecoder }
    );
    expect(result.ok).toBe(true);
    expect(result.sha256).toMatch(/^[0-9a-f]{64}$/);
    expect(result.byteSize).toBe(pngBytes().byteLength);
  });

  it('png 解码失败（stub false）被拒绝', async () => {
    const result = await validateAssetUpload(
      {
        key: 'bad_decode',
        file: new Blob([pngBytes() as BlobPart], { type: 'image/png' }),
        mimeType: 'image/png',
        kind: 'BITMAP'
      },
      { imageDecoder: async () => false }
    );
    expect(result.ok).toBe(false);
    expect(result.error).toMatch(/解码失败/);
  });

  it('mime 声称 png 但头不符合被拒绝', async () => {
    const result = await validateAssetUpload({
      key: 'bad',
      file: new Blob([new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8])], { type: 'image/png' }),
      mimeType: 'image/png',
      kind: 'BITMAP'
    });
    expect(result.ok).toBe(false);
    expect(result.error).toMatch(/签名不匹配/);
  });

  it('mp4 视频头通过', async () => {
    const bytes = new Uint8Array([0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x6d, 0x70, 0x34, 0x32]);
    const result = await validateAssetUpload({
      key: 'v',
      file: new Blob([bytes as BlobPart], { type: 'video/mp4' }),
      mimeType: 'video/mp4',
      kind: 'BITMAP'
    });
    expect(result.ok).toBe(true);
  });

  it('ttf 字体头通过', async () => {
    const bytes = new Uint8Array([0x00, 0x01, 0x00, 0x00, 0x00]);
    const result = await validateAssetUpload({
      key: 'f',
      file: new Blob([bytes as BlobPart], { type: 'font/ttf' }),
      mimeType: 'font/ttf',
      kind: 'FONT'
    });
    expect(result.ok).toBe(true);
  });

  it('未知 mime 被拒绝', async () => {
    const result = await validateAssetUpload({
      key: 'x',
      file: new Blob([new Uint8Array([0])], { type: 'application/x-unk' }),
      mimeType: 'application/x-unk',
      kind: 'BITMAP'
    });
    expect(result.ok).toBe(false);
    expect(result.error).toMatch(/不支持的 MIME/);
  });

  it('素材名转合法 key', () => {
    // 全中文无 ASCII 内容时退化为通用 key（键必须 a-z0-9_）
    expect(assetKeyForName('我的 背景.png')).toBe('asset');
    // 混合名保留 ASCII 段
    expect(assetKeyForName('背景 photo.png')).toBe('photo');
    expect(assetKeyForName('Cyber-BG v2.otheme')).toBe('cyber_bg_v2');
    expect(assetKeyForName('---')).toBe('asset');
  });
});

describe('memory asset library', () => {
  it('put/get/list/delete 生命周期', async () => {
    const library = createMemoryAssetLibrary();
    const record = {
      key: 'bg',
      path: 'assets/image/bg.png',
      kind: 'BITMAP' as const,
      mimeType: 'image/png',
      sha256: 'a'.repeat(64),
      byteSize: 8,
      blob: new Blob(['x'], { type: 'image/png' })
    };
    await library.put(record);
    expect(await library.get('bg')).not.toBeNull();
    expect((await library.list()).map((item) => item.key)).toEqual(['bg']);
    expect(await library.has('bg')).toBe(true);
    expect(await library.delete('bg')).toBe(true);
    expect(await library.has('bg')).toBe(false);
    expect(await library.delete('bg')).toBe(false);
  });

  it('asset:// uri 解析与生成', () => {
    const uri = makeAssetUri('bg');
    expect(uri).toBe('asset://bg');
    expect(parseAssetUri(uri)).toBe('bg');
    expect(parseAssetUri('content://a')).toBeNull();
    expect(parseAssetUri('asset://')).toBeNull();
  });
});
