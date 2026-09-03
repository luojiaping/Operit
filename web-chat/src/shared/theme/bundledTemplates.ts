// 内置基础模板：随 vite public/ 分发的官方 default / cyber 归档。
// SHA 与已发布 prerelease 资产一致（schema4-preview-20260903-*）。

import { loadThemePackageFromBytes } from './packageLoader';
import type { StudioPackage } from './packageLoader';

export interface BundledTemplateMeta {
  id: string;
  label: string;
  file: string;
  archiveSha256: string;
  packageId: string;
  version: string;
}

export const BUNDLED_TEMPLATE_METAS: BundledTemplateMeta[] = [
  {
    id: 'default',
    label: '官方默认主题',
    file: 'templates/operit-default-3.0.0.otheme',
    archiveSha256:
      '5a96abf521ec22a8c486ccb5b4d7f561a4bed4f6f90dfe742647024eb79db91f',
    packageId: 'operit.default',
    version: '3.0.0'
  },
  {
    id: 'cyber',
    label: 'Cyber Grid 主题',
    file: 'templates/operit-cyber-grid-3.0.0.otheme',
    archiveSha256:
      '77c65c812d81a3379edf53fb741e847786eb4ebd6250c4be7c5e4ab7379c4d97',
    packageId: 'operit.cyber_grid',
    version: '3.0.0'
  }
];

export async function loadBundledTemplate(meta: BundledTemplateMeta): Promise<StudioPackage> {
  const url = new URL(meta.file, `${globalThis.location.origin}/`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`内置模板 ${meta.id} 加载失败: ${String(response.status)}`);
  }
  const bytes = new Uint8Array(await response.arrayBuffer());
  const studioPackage = await loadThemePackageFromBytes(bytes);
  if (studioPackage.archiveSha256 !== meta.archiveSha256) {
    throw new Error(`内置模板 ${meta.id} 的 SHA 与登记坐标不符`);
  }
  return studioPackage;
}
