// .otheme → StudioPackage：ZIP 读取 + strict 解码 + 语义校验 + 素材提取。
// 与 app ThemePackageArchiveValidatorV2 行为一致：错误结构化（字段路径），不做修复。

import {
  decodeThemePackageManifest,
  THEME_PACKAGE_MANIFEST_ENTRY,
  THEME_PACKAGE_ZIP_COMMENT
} from './manifest';
import type { ThemePackageManifest, ThemePackageAssetEntry } from './manifest';
import { validateThemeArchiveShape, validateThemePackageManifest } from './validation';
import type { ThemeValidationIssue } from './validation';
import { readThemeZip } from './zipReader';
import type { ThemeZipContents } from './zipReader';

export class ThemePackageLoadError extends Error {
  readonly issues: ThemeValidationIssue[];

  constructor(message: string, issues: ThemeValidationIssue[] = []) {
    super(message);
    this.name = 'ThemePackageLoadError';
    this.issues = issues;
  }
}

/** 素材的编辑态载体：manifest 声明 + 解出的字节（blob URL 由素材层管理） */
export interface StudioAsset {
  entry: ThemePackageAssetEntry;
  bytes: Uint8Array;
}

export interface StudioPackage {
  manifest: ThemePackageManifest;
  assets: Map<string, StudioAsset>;
  archiveBytes: number;
  archiveSha256: string;
}

export async function loadThemePackageFromBytes(bytes: Uint8Array): Promise<StudioPackage> {
  const zip: ThemeZipContents = await readThemeZip(bytes);
  const archiveSha256 = await sha256Hex(bytes);

  const manifestBytes = zip.entries.get(THEME_PACKAGE_MANIFEST_ENTRY);
  if (manifestBytes === undefined) {
    throw new ThemePackageLoadError(`缺少根 ${THEME_PACKAGE_MANIFEST_ENTRY}`);
  }

  const shape = validateThemeArchiveShape({
    zipComment: zip.comment,
    hasRootManifestEntry: manifestBytes !== undefined,
    entryCount: zip.entryInfos.length,
    archiveBytes: zip.archiveBytes,
    uncompressedBytes: zip.entryInfos.reduce((sum, entry) => sum + entry.uncompressedSize, 0),
    singleEntryMaxBytes: zip.entryInfos.reduce(
      (max, entry) => Math.max(max, entry.uncompressedSize),
      0
    ),
    compressionRatio: 1
  });
  if (!shape.ok) {
    throw new ThemePackageLoadError('主题包结构不合法', shape.issues);
  }
  if (zip.comment !== THEME_PACKAGE_ZIP_COMMENT) {
    throw new ThemePackageLoadError(`ZIP comment 必须是 ${THEME_PACKAGE_ZIP_COMMENT}`);
  }

  const manifest = decodeThemePackageManifest(new TextDecoder().decode(manifestBytes));
  const semantic = validateThemePackageManifest(manifest);
  if (!semantic.ok) {
    throw new ThemePackageLoadError('主题清单不合法', semantic.issues);
  }

  const assets = new Map<string, StudioAsset>();
  for (const entry of manifest.assets) {
    const assetBytes = zip.entries.get(entry.path);
    if (assetBytes === undefined) {
      throw new ThemePackageLoadError(`素材 ${entry.key} 的文件缺失: ${entry.path}`);
    }
    if (assetBytes.byteLength !== entry.byteSize) {
      throw new ThemePackageLoadError(
        `素材 ${entry.key} 大小与清单不符（${assetBytes.byteLength} ≠ ${entry.byteSize}）`
      );
    }
    assets.set(entry.key, { entry, bytes: assetBytes });
  }

  return { manifest, assets, archiveBytes: zip.archiveBytes, archiveSha256 };
}

export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes as BufferSource);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

export async function loadThemePackageFromFile(file: File): Promise<StudioPackage> {
  const buffer = await file.arrayBuffer();
  return loadThemePackageFromBytes(new Uint8Array(buffer));
}

export function describeLoadIssues(error: unknown): ThemeValidationIssue[] {
  if (error instanceof ThemePackageLoadError) {
    return error.issues.length > 0
      ? error.issues
      : [{ path: 'archive', message: error.message }];
  }
  return [{ path: 'archive', message: error instanceof Error ? error.message : String(error) }];
}
