// 素材上传校验：与 app ThemePackageArchiveValidatorV2 / 设置页校验一致。
// 图片解码校验、视频/字体头签名、MIME 白名单、大小上限。

export const ASSET_LIMITS = {
  maxSingleEntryBytes: 48 * 1024 * 1024,
  allowedImageMimeTypes: ['image/jpeg', 'image/png', 'image/webp'],
  allowedVideoMimeTypes: ['video/mp4', 'video/webm'],
  allowedFontMimeTypes: ['font/ttf', 'font/otf']
} as const;

export type AssetKind = 'BITMAP' | 'NINE_SLICE' | 'FONT' | 'PATH';

export interface AssetUpload {
  key: string;
  file: Blob;
  mimeType: string;
  kind: AssetKind;
}

export interface AssetValidationResult {
  ok: boolean;
  error: string | null;
  kind: AssetKind;
  sha256: string;
  byteSize: number;
}

async function sha256Hex(bytes: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

function isJpeg(bytes: Uint8Array): boolean {
  return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

function isPng(bytes: Uint8Array): boolean {
  return (
    bytes.length >= 8 &&
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47
  );
}

function isWebp(bytes: Uint8Array): boolean {
  return (
    bytes.length >= 12 &&
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50
  );
}

function isMp4(bytes: Uint8Array): boolean {
  return bytes.length >= 12 && bytes[4] === 0x66 && bytes[5] === 0x74 && bytes[6] === 0x79 && bytes[7] === 0x70;
}

function isWebm(bytes: Uint8Array): boolean {
  return (
    bytes.length >= 4 &&
    bytes[0] === 0x1a &&
    bytes[1] === 0x45 &&
    bytes[2] === 0xdf &&
    bytes[3] === 0xa3
  );
}

function isTtf(bytes: Uint8Array): boolean {
  return (
    bytes.length >= 4 &&
    bytes[0] === 0x00 &&
    bytes[1] === 0x01 &&
    bytes[2] === 0x00 &&
    bytes[3] === 0x00
  );
}

function isOtf(bytes: Uint8Array): boolean {
  return (
    bytes.length >= 4 &&
    bytes[0] === 0x4f &&
    bytes[1] === 0x54 &&
    bytes[2] === 0x54 &&
    bytes[3] === 0x4f
  );
}

function detectImageFormat(bytes: Uint8Array): 'jpeg' | 'png' | 'webp' | null {
  if (isJpeg(bytes)) return 'jpeg';
  if (isPng(bytes)) return 'png';
  if (isWebp(bytes)) return 'webp';
  return null;
}

async function validateImagePayload(bytes: Uint8Array, decode: (blob: Blob) => Promise<boolean>): Promise<boolean> {
  const blob = new Blob([bytes as BlobPart], { type: 'image/*' });
  return decode(blob);
}

function pathForKey(key: string, kind: AssetKind): string {
  const directory = kind === 'BITMAP' || kind === 'NINE_SLICE' ? 'image' : kind === 'FONT' ? 'font' : 'path';
  return `assets/${directory}/${key}.${extensionForKey(kind)}`;
}

function extensionForKey(kind: AssetKind): string {
  switch (kind) {
    case 'BITMAP':
    case 'NINE_SLICE':
      return 'bin';
    case 'FONT':
      return 'font';
    case 'PATH':
      return 'path';
  }
}

export interface AssetUploadOptions {
  /** 图片解码校验器；浏览器默认 createImageBitmap，测试注入 stub，缺失时抛错 */
  imageDecoder?: (blob: Blob) => Promise<boolean>;
}

async function defaultImageDecoder(blob: Blob): Promise<boolean> {
  if (typeof createImageBitmap !== 'function') {
    throw new Error('图片解码校验仅在现代浏览器可用');
  }
  try {
    const bitmap = await createImageBitmap(blob);
    bitmap.close();
    return true;
  } catch {
    return false;
  }
}

export async function validateAssetUpload(
  upload: AssetUpload,
  options: AssetUploadOptions = {}
): Promise<AssetValidationResult> {
  const decodeImage = options.imageDecoder ?? defaultImageDecoder;
  if (upload.file.size > ASSET_LIMITS.maxSingleEntryBytes) {
    return {
      ok: false,
      error: `素材超过 ${ASSET_LIMITS.maxSingleEntryBytes / 1024 / 1024} MB 上限`,
      kind: upload.kind,
      sha256: '',
      byteSize: upload.file.size
    };
  }
  const buffer = await upload.file.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  const sha256 = await sha256Hex(buffer);

  if ((ASSET_LIMITS.allowedImageMimeTypes as readonly string[]).includes(upload.mimeType)) {
    const format = detectImageFormat(bytes);
    if (format == null) {
      return { ok: false, error: '图片头签名不匹配（jpeg/png/webp）', kind: upload.kind, sha256, byteSize: bytes.byteLength };
    }
    if (!(await validateImagePayload(bytes, decodeImage))) {
      return { ok: false, error: '图片解码失败（损坏或不可解）', kind: upload.kind, sha256, byteSize: bytes.byteLength };
    }
    return { ok: true, error: null, kind: upload.kind, sha256, byteSize: bytes.byteLength };
  }

  if ((ASSET_LIMITS.allowedVideoMimeTypes as readonly string[]).includes(upload.mimeType)) {
    const valid = upload.mimeType === 'video/mp4' ? isMp4(bytes) : isWebm(bytes);
    if (!valid) {
      return { ok: false, error: '视频头签名不匹配（mp4/webm）', kind: upload.kind, sha256, byteSize: bytes.byteLength };
    }
    return { ok: true, error: null, kind: upload.kind, sha256, byteSize: bytes.byteLength };
  }

  if ((ASSET_LIMITS.allowedFontMimeTypes as readonly string[]).includes(upload.mimeType)) {
    const valid = upload.mimeType === 'font/ttf' ? isTtf(bytes) : isOtf(bytes);
    if (!valid) {
      return { ok: false, error: '字体头签名不匹配（ttf/otf）', kind: upload.kind, sha256, byteSize: bytes.byteLength };
    }
    return { ok: true, error: null, kind: upload.kind, sha256, byteSize: bytes.byteLength };
  }

  return { ok: false, error: `不支持的 MIME 类型: ${upload.mimeType}`, kind: upload.kind, sha256, byteSize: bytes.byteLength };
}

export function assetKeyForName(name: string): string {
  const stripped = name.replace(/\.[^.]+$/, '');
  // 非 ASCII 字符（中文等）折叠为下划线，保持 key 可读且仅用 a-z0-9_
  const base = stripped
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 48);
  return base.length > 0 ? base : 'asset';
}

export { pathForKey };
