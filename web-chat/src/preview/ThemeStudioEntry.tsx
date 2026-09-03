// 主题工作室入口：导入 .otheme / 从内置模板新建空白包 / 当前包状态。
// 用户在 ThemeStudioPanel（快速预览）与 ThemeWorkbench（全参数编辑）间切换。

import { useCallback, useRef, useState } from 'react';
import type { ThemePackageLoadError } from '../shared/theme/packageLoader';
import {
  describeLoadIssues,
  loadThemePackageFromFile
} from '../shared/theme/packageLoader';
import type { StudioPackage } from '../shared/theme/packageLoader';
import {
  BUNDLED_TEMPLATE_METAS,
  loadBundledTemplate
} from '../shared/theme/bundledTemplates';
import { createBlankPackageManifest } from '../shared/theme/blankPackage';

export interface ThemeStudioEntryProps {
  onPackageLoaded: (studioPackage: StudioPackage) => void;
}

interface LoadError {
  message: string;
  issues: { path: string; message: string }[];
}

export function ThemeStudioEntry({ onPackageLoaded }: ThemeStudioEntryProps) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<LoadError | null>(null);
  const [showBlankForm, setShowBlankForm] = useState(false);
  const [blankInput, setBlankInput] = useState({
    packageId: '',
    version: '0.1.0',
    displayName: '',
    description: ''
  });

  const pickTemplate = useCallback(
    async (templateId: string) => {
      const meta = BUNDLED_TEMPLATE_METAS.find((item) => item.id === templateId);
      if (meta == null) {
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const base = await loadBundledTemplate(meta);
        const derived = createBlankPackageManifest(base.manifest, {
          packageId: base.manifest.packageId,
          version: base.manifest.version,
          archiveSha256: base.archiveSha256
        }, {
          packageId: blankInput.packageId.trim(),
          version: blankInput.version.trim(),
          displayName: blankInput.displayName.trim(),
          description: blankInput.description.trim()
        });
        onPackageLoaded({
          ...base,
          manifest: derived
        });
        setShowBlankForm(false);
      } catch (loadError: unknown) {
        setError({
          message: loadError instanceof Error ? loadError.message : String(loadError),
          issues: []
        });
      } finally {
        setLoading(false);
      }
    },
    [blankInput, onPackageLoaded]
  );

  const handleFile = useCallback(
    async (file: File) => {
      setLoading(true);
      setError(null);
      try {
        const studioPackage = await loadThemePackageFromFile(file);
        onPackageLoaded(studioPackage);
      } catch (loadError: unknown) {
        setError({
          message:
            loadError instanceof Error ? loadError.message : String(loadError),
          issues: describeLoadIssues(loadError)
        });
      } finally {
        setLoading(false);
      }
    },
    [onPackageLoaded]
  );

  return (
    <div className="studio-entry">
      <div className="studio-entry-row">
        <button
          className="studio-file-button"
          disabled={loading}
          onClick={() => fileInputRef.current?.click()}
          type="button"
        >
          导入 .otheme
        </button>
        <button
          className="studio-file-button"
          disabled={loading}
          onClick={() => setShowBlankForm((value) => !value)}
          type="button"
        >
          新建空白包
        </button>
        <span className="studio-file-hint">{loading ? '加载中…' : '模板 default@3.0.0 · cyber@3.0.0'}</span>
        <input
          accept=".otheme,application/zip"
          className="studio-hidden-input"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file != null) {
              void handleFile(file);
            }
            event.target.value = '';
          }}
          ref={fileInputRef}
          type="file"
        />
      </div>

      {showBlankForm ? (
        <div className="studio-blank-form">
          <label>
            包 ID
            <input
              onChange={(event) =>
                setBlankInput((current) => ({ ...current, packageId: event.target.value }))
              }
              placeholder="operit.my_theme"
              value={blankInput.packageId}
            />
          </label>
          <label>
            版本
            <input
              onChange={(event) =>
                setBlankInput((current) => ({ ...current, version: event.target.value }))
              }
              value={blankInput.version}
            />
          </label>
          <label>
            显示名
            <input
              onChange={(event) =>
                setBlankInput((current) => ({ ...current, displayName: event.target.value }))
              }
              placeholder="我的主题"
              value={blankInput.displayName}
            />
          </label>
          <label>
            描述（可选）
            <input
              onChange={(event) =>
                setBlankInput((current) => ({ ...current, description: event.target.value }))
              }
              value={blankInput.description}
            />
          </label>
          <div className="studio-blank-templates">
            {BUNDLED_TEMPLATE_METAS.map((meta) => (
              <button
                disabled={
                  blankInput.packageId.trim().length === 0 ||
                  blankInput.displayName.trim().length === 0
                }
                key={meta.id}
                onClick={() => void pickTemplate(meta.id)}
                type="button"
              >
                以 {meta.label} 为基底
              </button>
            ))}
          </div>
        </div>
      ) : null}

      {error != null ? (
        <div className="studio-error" role="alert">
          <strong>{error.message}</strong>
          <ul>
            {error.issues.map((issue) => (
              <li key={`${issue.path}:${issue.message}`}>
                {issue.path}: {issue.message}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
