import {
  MEMBER_ID_PATTERN,
  PACKAGE_ID_PATTERN,
  SHA256_PATTERN,
  SEMVER_PATTERN,
  THEME_PACKAGE_EXTENSION,
  THEME_PACKAGE_MANIFEST_ENTRY,
  THEME_PACKAGE_SCHEMA_VERSION,
  THEME_PACKAGE_ZIP_COMMENT
} from './manifest';
import type {
  ThemePackageManifest,
  ThemeParameterCondition,
  ThemeParameterControl,
  ThemeParameterDefinition,
  ThemeParameterEffect,
  ThemeParameterType,
  ThemePresentationTarget,
  ThemePackageAssetEntry
} from './manifest';

// schema 4 语义校验：跨字段约束，与 app ThemePackageArchiveValidatorV2 行为一致。
// decode 层已确保字段类型/值域；本层只处理结构关系与 archive 预算。

export interface ThemeArchiveBudget {
  maxArchiveBytes: number;
  maxEntries: number;
  maxUncompressedBytes: number;
  maxSingleEntryBytes: number;
  maxCompressionRatio: number;
}

export const THEME_ARCHIVE_BUDGET: ThemeArchiveBudget = {
  maxArchiveBytes: 128 * 1024 * 1024,
  maxEntries: 512,
  maxUncompressedBytes: 64 * 1024 * 1024,
  maxSingleEntryBytes: 48 * 1024 * 1024,
  maxCompressionRatio: 100
};

export interface ThemeValidationIssue {
  path: string;
  message: string;
}

export interface ThemeValidationResult {
  ok: boolean;
  issues: ThemeValidationIssue[];
}

export function validateThemePackageManifest(manifest: ThemePackageManifest): ThemeValidationResult {
  const issues: ThemeValidationIssue[] = [];
  push(issues, 'schemaVersion', manifest.schemaVersion !== THEME_PACKAGE_SCHEMA_VERSION);
  push(issues, 'packageId', !PACKAGE_ID_PATTERN.test(manifest.packageId));
  push(issues, 'version', !SEMVER_PATTERN.test(manifest.version));

  const variantIds = new Set<string>();
  manifest.variants.forEach((variant) => {
    push(issues, `variants[${variant.id}]`, !MEMBER_ID_PATTERN.test(variant.id));
    if (variantIds.has(variant.id)) {
      push(issues, `variants[${variant.id}]`, '重复变体 ID');
    }
    variantIds.add(variant.id);
  });

  if (manifest.basis != null) {
    const basis = manifest.basis;
    push(issues, 'basis.archiveSha256', !SHA256_PATTERN.test(basis.archiveSha256));
    if (basis.packageId === manifest.packageId) {
      push(issues, 'basis', 'basis 不能引用自身');
    }
  }

  validateParameterDefinitions(manifest, issues);
  validateAssetEntries(manifest, issues);
  validateSurfaces(manifest, issues);

  return { ok: issues.length === 0, issues };
}

export function validateThemeArchiveShape(archive: {
  zipComment: string | null;
  hasRootManifestEntry: boolean;
  entryCount: number;
  archiveBytes: number;
  uncompressedBytes: number;
  singleEntryMaxBytes: number;
  compressionRatio: number;
}): ThemeValidationResult {
  const issues: ThemeValidationIssue[] = [];
  push(issues, 'zipComment', archive.zipComment !== THEME_PACKAGE_ZIP_COMMENT);
  push(issues, 'manifestEntry', !archive.hasRootManifestEntry);
  push(issues, 'entryCount', archive.entryCount > THEME_ARCHIVE_BUDGET.maxEntries);
  push(issues, 'archiveBytes', archive.archiveBytes > THEME_ARCHIVE_BUDGET.maxArchiveBytes);
  push(
    issues,
    'uncompressedBytes',
    archive.uncompressedBytes > THEME_ARCHIVE_BUDGET.maxUncompressedBytes
  );
  push(
    issues,
    'singleEntryBytes',
    archive.singleEntryMaxBytes > THEME_ARCHIVE_BUDGET.maxSingleEntryBytes
  );
  push(
    issues,
    'compressionRatio',
    archive.compressionRatio > THEME_ARCHIVE_BUDGET.maxCompressionRatio
  );
  return { ok: issues.length === 0, issues };
}

export function validatePackageAssetList(assets: ThemePackageAssetEntry[]): ThemeValidationResult {
  const issues: ThemeValidationIssue[] = [];
  const keys = new Set<string>();
  assets.forEach((asset) => {
    push(issues, `assets[${asset.key}]`, !MEMBER_ID_PATTERN.test(asset.key));
    push(issues, `assets[${asset.key}]`, !SHA256_PATTERN.test(asset.sha256));
    push(issues, `assets[${asset.key}]`, asset.byteSize <= 0);
    push(
      issues,
      `assets[${asset.key}]`,
      !isPortableRelativePath(asset.path)
    );
    if (keys.has(asset.key)) {
      push(issues, `assets[${asset.key}]`, '重复素材 key');
    }
    keys.add(asset.key);
  });
  return { ok: issues.length === 0, issues };
}

function validateParameterDefinitions(manifest: ThemePackageManifest, issues: ThemeValidationIssue[]) {
  const ids = new Set<string>();
  manifest.parameters.forEach((parameter) => {
    push(issues, `parameters[${parameter.id}]`, !MEMBER_ID_PATTERN.test(parameter.id));
    if (ids.has(parameter.id)) {
      push(issues, `parameters[${parameter.id}]`, '重复参数 ID');
    }
    ids.add(parameter.id);

    if (parameter.control.type === 'author_value') {
      if (parameter.visibility !== 'AUTHOR') {
        push(issues, `parameters[${parameter.id}]`, 'author_value 控件仅限 AUTHOR 参数');
      }
      if (parameter.section != null) {
        push(issues, `parameters[${parameter.id}]`, 'AUTHOR 参数不能声明 section');
      }
    } else {
      if (parameter.visibility === 'AUTHOR') {
        push(issues, `parameters[${parameter.id}]`, 'AUTHOR 参数必须使用 author_value 控件');
      }
      if (parameter.section == null) {
        push(issues, `parameters[${parameter.id}]`, 'USER 参数必须声明 section');
      }
      if (!supportsUserSettingsSurface(parameter.control)) {
        push(issues, `parameters[${parameter.id}]`, '控件不在紧凑设置界面支持范围内');
      }
      if (parameter.defaultValue == null && !isUriType(parameter.type)) {
        push(issues, `parameters[${parameter.id}]`, 'USER 非资源参数必须声明默认值');
      }
    }

    if (isUriType(parameter.type) && parameter.defaultValue != null) {
      push(issues, `parameters[${parameter.id}]`, 'URI 参数不能声明包默认值');
    }

    const controlAllows = (type: ThemeParameterType): boolean =>
      applyControlType(parameter.control) === type || parameter.control.type === 'author_value';
    if (!controlAllows(parameter.type)) {
      push(issues, `parameters[${parameter.id}]`, `控件 ${parameter.control.type} 与类型 ${parameter.type} 不匹配`);
    }

    if (parameter.control.type === 'choice' && parameter.defaultValue?.type === 'option') {
      const options = parameter.control.options.map((option) => option.id);
      if (!options.includes(parameter.defaultValue.value)) {
        push(issues, `parameters[${parameter.id}]`, 'choice 默认值必须是声明选项之一');
      }
    }
    if (parameter.control.type === 'color_palette') {
      const color = parameter.defaultValue?.type === 'color' ? parameter.defaultValue : null;
      if (color == null) {
        push(issues, `parameters[${parameter.id}]`, 'color_palette 参数必须声明不透明颜色默认值');
      } else if ((color.argb >>> 24) !== 0xff) {
        push(issues, `parameters[${parameter.id}]`, '颜色默认值必须不透明');
      }
      const presets = parameter.control.presetArgb;
      if (presets.length === 0 && !parameter.control.allowCustom) {
        push(issues, `parameters[${parameter.id}]`, '调色板必须有预设或允许自定义');
      }
      if (new Set(presets).size !== presets.length) {
        push(issues, `parameters[${parameter.id}]`, '调色板预设必须唯一');
      }
      presets.forEach((preset) => {
        if ((preset >>> 24) !== 0xff) {
          push(issues, `parameters[${parameter.id}]`, '调色板预设必须不透明');
        }
      });
    }

    const effects = parameter.effects;
    if (effects.length === 0) {
      push(issues, `parameters[${parameter.id}]`, '参数必须声明至少一个 effect');
    }
    effects.forEach((effect) => {
      validateEffectForParameter(parameter, effect, issues);
    });

    validateConditions(parameter, manifest, issues);
  });
}

function validateEffectForParameter(
  parameter: ThemeParameterDefinition,
  effect: ThemeParameterEffect,
  issues: ThemeValidationIssue[]
) {
  const path = `parameters[${parameter.id}].effects[${effect.type}]`;
  const type = parameter.type;
  switch (effect.type) {
    case 'accent_palette':
      if (type !== 'COLOR') {
        push(issues, path, 'accent_palette 仅适用 COLOR');
      }
      break;
    case 'token_color':
    case 'token_color_pair':
      if (type !== 'COLOR' && type !== 'COLOR_PAIR') {
        push(issues, path, `${effect.type} 仅适用 COLOR/COLOR_PAIR`);
      }
      if (effect.tokenIds.length === 0 || new Set(effect.tokenIds).size !== effect.tokenIds.length) {
        push(issues, path, 'token 目标必须非空且唯一');
      }
      break;
    case 'stage_image':
      if (type !== 'IMAGE_URI') {
        push(issues, path, 'stage_image 仅适用 IMAGE_URI');
      }
      if (effect.opacity < 0 || effect.opacity > 1) {
        push(issues, path, 'opacity 必须在 [0,1]');
      }
      break;
    case 'typography_scale':
    case 'shape_scale':
      if (type !== 'FLOAT') {
        push(issues, path, `${effect.type} 仅适用 FLOAT`);
      }
      break;
    case 'component_frame_scale':
    case 'component_content_insets':
      if (effect.type === 'component_frame_scale' && type !== 'FLOAT') {
        push(issues, path, 'component_frame_scale 仅适用 FLOAT');
      }
      if (effect.type === 'component_content_insets' && type !== 'INSETS') {
        push(issues, path, 'component_content_insets 仅适用 INSETS');
      }
      if (effect.componentIds.length === 0 || new Set(effect.componentIds).size !== effect.componentIds.length) {
        push(issues, path, 'component 目标必须非空且唯一');
      }
      break;
    case 'presentation': {
      if (effect.targets.length === 0 || new Set(effect.targets).size !== effect.targets.length) {
        push(issues, path, 'targets 必须非空且唯一');
      }
      effect.targets.forEach((target) => {
        const expected = presentationTargetType(target);
        if (expected !== type) {
          push(issues, path, `target ${target} 期望 ${expected} 而非 ${type}`);
        }
        if (type === 'OPTION' && parameter.control.type === 'choice') {
          const allowed = presentationTargetOptionIds(target);
          const options = parameter.control.options.map((option) => option.id);
          for (const option of options) {
            if (allowed !== null && !allowed.includes(option)) {
              push(issues, path, `选项 ${option} 超出 target ${target} 域`);
            }
          }
        }
      });
      break;
    }
  }
}

function validateConditions(
  parameter: ThemeParameterDefinition,
  manifest: ThemePackageManifest,
  issues: ThemeValidationIssue[]
) {
  const byId = new Map(manifest.parameters.map((item) => [item.id, item]));
  parameter.visibleWhen.forEach((condition: ThemeParameterCondition) => {
    const dependency = byId.get(condition.parameterId);
    const path = `parameters[${parameter.id}].visibleWhen[${condition.type}]`;
    if (dependency == null) {
      push(issues, path, `依赖未知参数 ${condition.parameterId}`);
      return;
    }
    switch (condition.type) {
      case 'boolean_equals':
        if (dependency.type !== 'BOOLEAN') {
          push(issues, path, `布尔条件只能依赖 BOOLEAN 参数 ${dependency.id}`);
        }
        break;
      case 'option_equals':
        if (dependency.type !== 'OPTION' || dependency.control.type !== 'choice') {
          push(issues, path, `选项条件只能依赖带 choice 的 OPTION 参数 ${dependency.id}`);
        } else if (!dependency.control.options.some((option) => option.id === condition.expected)) {
          push(issues, path, `未知选项 ${condition.expected}`);
        }
        break;
      case 'resource_present':
        if (!isUriType(dependency.type)) {
          push(issues, path, `资源条件只能依赖 URI 参数 ${dependency.id}`);
        }
        break;
    }
  });
}

function validateSurfaces(manifest: ThemePackageManifest, issues: ThemeValidationIssue[]) {
  const surfaceIds = new Set<string>();
  const sceneIds = new Set<string>();
  for (const scene of manifest.scenes) {
    const id = (scene as { sceneId?: unknown }).sceneId;
    if (typeof id === 'string') {
      sceneIds.add(id);
    }
  }
  manifest.surfaces.forEach((surface) => {
    if (surfaceIds.has(surface.surfaceId)) {
      push(issues, `surfaces[${surface.surfaceId}]`, '重复 surface ID');
    }
    surfaceIds.add(surface.surfaceId);
    if (surface.kind === 'SCENE' && (surface.sceneId == null || !sceneIds.has(surface.sceneId))) {
      push(issues, `surfaces[${surface.surfaceId}]`, 'SCENE surface 引用缺失场景');
    }
    if (surface.kind !== 'SCENE' && surface.sceneId != null) {
      push(issues, `surfaces[${surface.surfaceId}]`, '非 SCENE surface 不能携带 sceneId');
    }
  });
}

function validateAssetEntries(manifest: ThemePackageManifest, issues: ThemeValidationIssue[]) {
  const result = validatePackageAssetList(manifest.assets);
  for (const issue of result.issues) {
    push(issues, issue.path, issue.message);
  }
}

function applyControlType(control: ThemeParameterControl): ThemeParameterType | 'author_value' {
  switch (control.type) {
    case 'color_palette':
      return 'COLOR';
    case 'color_pair_palette':
      return 'COLOR_PAIR';
    case 'toggle':
      return 'BOOLEAN';
    case 'choice':
      return 'OPTION';
    case 'slider':
      return 'FLOAT';
    case 'image_picker':
      return 'IMAGE_URI';
    case 'video_picker':
      return 'VIDEO_URI';
    case 'font_picker':
      return 'FONT_URI';
    case 'author_value':
      return 'author_value';
  }
}

function supportsUserSettingsSurface(control: ThemeParameterControl): boolean {
  switch (control.type) {
    case 'color_palette':
    case 'toggle':
    case 'choice':
    case 'slider':
    case 'image_picker':
    case 'video_picker':
    case 'font_picker':
      return true;
    case 'color_pair_palette':
    case 'author_value':
      return false;
  }
}

function isUriType(type: ThemeParameterType): boolean {
  return type === 'IMAGE_URI' || type === 'VIDEO_URI' || type === 'FONT_URI';
}

function presentationTargetType(target: ThemePresentationTarget): ThemeParameterType {
  switch (target) {
    case 'TYPOGRAPHY_USE_CUSTOM_FONT':
    case 'BACKGROUND_USE_IMAGE':
    case 'BACKGROUND_BLUR_ENABLED':
    case 'BACKGROUND_VIDEO_MUTED':
    case 'BACKGROUND_VIDEO_LOOP':
    case 'CURSOR_USER_BUBBLE_FOLLOW_THEME':
    case 'CURSOR_USER_BUBBLE_LIQUID_GLASS':
    case 'CURSOR_USER_BUBBLE_WATER_GLASS':
    case 'BUBBLE_SHOW_AVATAR':
    case 'BUBBLE_WIDE_LAYOUT':
    case 'BUBBLE_USER_LIQUID_GLASS':
    case 'BUBBLE_USER_WATER_GLASS':
    case 'BUBBLE_ASSISTANT_LIQUID_GLASS':
    case 'BUBBLE_ASSISTANT_WATER_GLASS':
    case 'BUBBLE_USER_ROUNDED_CORNERS':
    case 'BUBBLE_ASSISTANT_ROUNDED_CORNERS':
    case 'BUBBLE_USER_USE_CUSTOM_FONT':
    case 'BUBBLE_ASSISTANT_USE_CUSTOM_FONT':
    case 'COMPOSER_TRANSPARENT':
    case 'COMPOSER_FLOATING':
    case 'COMPOSER_LIQUID_GLASS':
    case 'COMPOSER_WATER_GLASS':
    case 'CHROME_STATUS_BAR_HIDDEN':
    case 'CHROME_STATUS_BAR_TRANSPARENT':
    case 'CHROME_TOOLBAR_TRANSPARENT':
    case 'CHROME_NAVIGATION_WATER_GLASS':
    case 'CHROME_NAVIGATION_BUTTON_LIQUID_GLASS':
    case 'CHROME_CHAT_HEADER_TRANSPARENT':
      return 'BOOLEAN';
    case 'TYPOGRAPHY_FAMILY':
    case 'BACKGROUND_MEDIA_TYPE':
    case 'BUBBLE_IMAGE_RENDER_MODE':
    case 'BUBBLE_USER_FONT_FAMILY':
    case 'BUBBLE_ASSISTANT_FONT_FAMILY':
    case 'AVATAR_SHAPE':
    case 'CHROME_CHAT_HEADER_OVERLAY_MODE':
    case 'CHROME_APP_BAR_CONTENT_COLOR_MODE':
      return 'OPTION';
    case 'TYPOGRAPHY_SCALE':
    case 'BACKGROUND_OPACITY':
    case 'BACKGROUND_BLUR_RADIUS':
    case 'AVATAR_CORNER_RADIUS':
      return 'FLOAT';
    case 'BACKGROUND_IMAGE_URI':
    case 'BUBBLE_USER_IMAGE_URI':
    case 'BUBBLE_ASSISTANT_IMAGE_URI':
      return 'IMAGE_URI';
    case 'BACKGROUND_VIDEO_URI':
      return 'VIDEO_URI';
    case 'TYPOGRAPHY_FONT_URI':
    case 'BUBBLE_USER_FONT_URI':
    case 'BUBBLE_ASSISTANT_FONT_URI':
      return 'FONT_URI';
    case 'BUBBLE_USER_IMAGE_LAYOUT':
    case 'BUBBLE_ASSISTANT_IMAGE_LAYOUT':
      return 'IMAGE_LAYOUT';
    case 'BUBBLE_USER_CONTENT_INSETS':
    case 'BUBBLE_ASSISTANT_CONTENT_INSETS':
      return 'INSETS';
    case 'CURSOR_USER_BUBBLE_COLOR':
    case 'BUBBLE_USER_COLOR':
    case 'BUBBLE_ASSISTANT_COLOR':
    case 'BUBBLE_USER_TEXT_COLOR':
    case 'BUBBLE_ASSISTANT_TEXT_COLOR':
    case 'CHROME_STATUS_BAR_COLOR':
    case 'CHROME_TOOLBAR_COLOR':
    case 'CHROME_NAVIGATION_BACKGROUND_COLOR':
    case 'CHROME_NAVIGATION_ACCENT_COLOR':
    case 'CHROME_CHAT_HEADER_HISTORY_ICON_COLOR':
    case 'CHROME_CHAT_HEADER_PIP_ICON_COLOR':
      return 'COLOR';
  }
}

function presentationTargetOptionIds(target: ThemePresentationTarget): string[] | null {
  switch (target) {
    case 'TYPOGRAPHY_FAMILY':
    case 'BUBBLE_USER_FONT_FAMILY':
    case 'BUBBLE_ASSISTANT_FONT_FAMILY':
      return ['default', 'sans_serif', 'serif', 'monospace', 'cursive'];
    case 'BACKGROUND_MEDIA_TYPE':
      return ['none', 'image', 'video'];
    case 'BUBBLE_IMAGE_RENDER_MODE':
      return ['tiled_nine_slice', 'nine_patch'];
    case 'AVATAR_SHAPE':
      return ['circle', 'square', 'rounded'];
    case 'CHROME_CHAT_HEADER_OVERLAY_MODE':
      return ['none', 'overlay'];
    case 'CHROME_APP_BAR_CONTENT_COLOR_MODE':
      return ['auto', 'light', 'dark'];
    default:
      return null;
  }
}

function isPortableRelativePath(path: string): boolean {
  if (path.length === 0 || path.startsWith('/') || path.includes('\\') || path.includes(':')) {
    return false;
  }
  return !path.split('/').some((segment) => segment === '..');
}

function push(
  issues: ThemeValidationIssue[],
  path: string,
  error: boolean | string
): void {
  if (error === false) {
    return;
  }
  issues.push({
    path,
    message: typeof error === 'string' ? error : '校验失败'
  });
}

export function describeThemePackageFile(extension: string): string {
  return extension === THEME_PACKAGE_EXTENSION ? '主题包' : '不支持的文件类型';
}

export { THEME_PACKAGE_MANIFEST_ENTRY };

export function isThemePackageFileName(name: string): boolean {
  return name.toLocaleLowerCase().endsWith(THEME_PACKAGE_EXTENSION);
}
