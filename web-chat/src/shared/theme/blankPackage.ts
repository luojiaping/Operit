// 空白包（派生子包）生成：以基础包 manifest 为模板，复制全量参数定义，
// 仅替换标识信息并锁定 basis 坐标。编辑值在导出时烘焙（见 07）。

import {
  PACKAGE_ID_PATTERN,
  SEMVER_PATTERN,
  THEME_PACKAGE_SCHEMA_VERSION
} from './manifest';
import type { ThemePackageManifest } from './manifest';
import { validateThemePackageManifest } from './validation';
import type { ThemeValidationIssue } from './validation';

export interface BlankPackageFormInput {
  packageId: string;
  version: string;
  displayName: string;
  description?: string;
  author?: string;
}

export function validateBlankPackageForm(
  input: BlankPackageFormInput
): ThemeValidationIssue[] {
  const issues: ThemeValidationIssue[] = [];
  if (!PACKAGE_ID_PATTERN.test(input.packageId)) {
    issues.push({
      path: 'packageId',
      message: '包 ID 必须形如 operit.my_theme（小写字母/数字/下划线 + 至少一个点）'
    });
  }
  if (!SEMVER_PATTERN.test(input.version)) {
    issues.push({ path: 'version', message: '版本必须是语义化版本（如 1.0.0）' });
  }
  if (input.displayName.trim().length === 0) {
    issues.push({ path: 'displayName', message: '显示名不能为空' });
  }
  return issues;
}

export function createBlankPackageManifest(
  base: ThemePackageManifest,
  baseCoordinate: { packageId: string; version: string; archiveSha256: string },
  input: BlankPackageFormInput
): ThemePackageManifest {
  const formIssues = validateBlankPackageForm(input);
  if (formIssues.length > 0) {
    throw new Error(`空白包表单不合法: ${formIssues.map((issue) => issue.message).join('; ')}`);
  }
  if (baseCoordinate.packageId === input.packageId) {
    throw new Error('新包 ID 不能与基础包相同');
  }
  if (baseCoordinate.archiveSha256.length !== 64) {
    throw new Error('基础包坐标缺少 archiveSha256');
  }

  const derived: ThemePackageManifest = {
    ...base,
    schemaVersion: THEME_PACKAGE_SCHEMA_VERSION,
    packageId: input.packageId,
    version: input.version,
    displayName: { '*': input.displayName },
    description: input.description == null || input.description.length === 0
      ? null
      : { '*': input.description },
    author: input.author == null || input.author.length === 0 ? null : { '*': input.author },
    attribution: base.attribution,
    basis: {
      packageId: baseCoordinate.packageId,
      version: baseCoordinate.version,
      archiveSha256: baseCoordinate.archiveSha256
    },
    variants: [],
    parameters: base.parameters.map((parameter) => ({ ...parameter })),
    assets: base.assets.map((asset) => ({ ...asset })),
    scenes: base.scenes,
    surfaces: base.surfaces.map((surface) => ({ ...surface })),
    presentation: base.presentation,
    tokens: base.tokens
  };

  const result = validateThemePackageManifest(derived);
  if (!result.ok) {
    throw new Error(`派生包不合法: ${result.issues.map((issue) => issue.message).join('; ')}`);
  }
  return derived;
}
