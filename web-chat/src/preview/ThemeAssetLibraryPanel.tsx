import { useEffect, useMemo, useRef, useState } from 'react';
import type {
  ThemeParameterDefinition,
  ThemeParameterValue
} from '../shared/theme/manifest';
import { effectiveValue, resolveLocalizedText } from '../shared/theme/editorState';
import type { StudioEditorState } from '../shared/theme/editorState';
import type { AssetKind, AssetLibrary, AssetRecord } from '../shared/theme/assets/library';
import { makeAssetUri } from '../shared/theme/assets/library';
import { assetKeyForName, validateAssetUpload } from '../shared/theme/assets/validator';

type ThemeResourceControl = Extract<
  ThemeParameterDefinition['control'],
  { type: 'image_picker' | 'video_picker' | 'font_picker' }
>;

function isResourceParameter(
  parameter: ThemeParameterDefinition
): parameter is ThemeParameterDefinition & { control: ThemeResourceControl } {
  return (
    parameter.control.type === 'image_picker' ||
    parameter.control.type === 'video_picker' ||
    parameter.control.type === 'font_picker'
  );
}

function acceptsMimeType(allowed: readonly string[], actual: string): boolean {
  return allowed.some((pattern) =>
    pattern.endsWith('/*') ? actual.startsWith(pattern.slice(0, -1)) : pattern === actual
  );
}

function assetKindForMime(mimeType: string): AssetKind | null {
  if (mimeType.startsWith('font/')) {
    return 'FONT';
  }
  if (mimeType.startsWith('image/') || mimeType.startsWith('video/')) {
    return 'BITMAP';
  }
  return null;
}

function resourceValueForAsset(
  parameter: ThemeParameterDefinition,
  uri: string
): ThemeParameterValue | null {
  switch (parameter.type) {
    case 'IMAGE_URI':
      return { type: 'image_uri', uri };
    case 'VIDEO_URI':
      return { type: 'video_uri', uri };
    case 'FONT_URI':
      return { type: 'font_uri', uri };
    default:
      return null;
  }
}

function isAssetAllowedForParameter(
  asset: AssetRecord,
  parameter: ThemeParameterDefinition | undefined
): boolean {
  if (parameter == null || !isResourceParameter(parameter)) {
    return true;
  }
  return acceptsMimeType(parameter.control.mimeTypes, asset.mimeType);
}

export function ThemeAssetLibraryPanel({
  assetLibrary,
  assetRevision,
  protectedAssetKeys,
  resourceParameters,
  state,
  onResetValue,
  onSetValue
}: {
  assetLibrary: AssetLibrary;
  assetRevision: number;
  protectedAssetKeys: readonly string[];
  resourceParameters: readonly ThemeParameterDefinition[];
  state: StudioEditorState;
  onResetValue: (parameterId: string) => void;
  onSetValue: (parameterId: string, value: ThemeParameterValue) => void;
}) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [assets, setAssets] = useState<AssetRecord[]>([]);
  const [selectedParameterId, setSelectedParameterId] = useState(resourceParameters[0]?.id ?? '');
  const [error, setError] = useState<string | null>(null);
  const selectedParameter = resourceParameters.find(
    (parameter) => parameter.id === selectedParameterId
  );
  const protectedKeys = useMemo(() => new Set(protectedAssetKeys), [protectedAssetKeys]);

  useEffect(() => {
    if (resourceParameters.length === 0) {
      setSelectedParameterId('');
      return;
    }
    if (!resourceParameters.some((parameter) => parameter.id === selectedParameterId)) {
      setSelectedParameterId(resourceParameters[0].id);
    }
  }, [resourceParameters, selectedParameterId]);
  const selectedAssetKeys = useMemo(() => {
    const keys = new Set<string>();
    for (const parameter of resourceParameters) {
      const value = effectiveValue(state, parameter);
      if (value?.type === 'image_uri' || value?.type === 'video_uri' || value?.type === 'font_uri') {
        if (value.uri.startsWith('asset://')) {
          keys.add(value.uri.slice('asset://'.length));
        }
      }
    }
    return keys;
  }, [resourceParameters, state]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const records = await assetLibrary.list();
        if (!cancelled) {
          setAssets(records);
        }
      } catch (loadError) {
        console.error('素材库读取失败', loadError);
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : '素材库读取失败');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [assetLibrary, assetRevision]);

  async function refreshAssets() {
    try {
      setAssets(await assetLibrary.list());
    } catch (loadError) {
      console.error('素材库刷新失败', loadError);
      setError(loadError instanceof Error ? loadError.message : '素材库刷新失败');
    }
  }

  async function handleUpload(file: File) {
    setError(null);
    try {
      if (selectedParameter != null && isResourceParameter(selectedParameter)) {
        if (!acceptsMimeType(selectedParameter.control.mimeTypes, file.type)) {
          setError(`当前参数不接受 ${file.type || '(未知)'}`);
          return;
        }
      }
      const kind = assetKindForMime(file.type);
      if (kind == null) {
        setError(`不支持的素材类型 ${file.type || '(未知)'}`);
        return;
      }
      const key = `${assetKeyForName(file.name)}_${Math.random().toString(36).slice(2, 8)}`;
      const validation = await validateAssetUpload({
        key,
        file,
        kind,
        mimeType: file.type
      });
      if (!validation.ok) {
        setError(validation.error);
        return;
      }
      const directory = file.type.startsWith('font/')
        ? 'font'
        : file.type.startsWith('video/')
          ? 'video'
          : 'image';
      const record: AssetRecord = {
        blob: file,
        byteSize: validation.byteSize,
        key,
        kind,
        mimeType: file.type,
        path: `assets/${directory}/${key}`,
        sha256: validation.sha256
      };
      await assetLibrary.put(record);
      setAssets((current) => [
        ...current.filter((item) => item.key !== key),
        record
      ]);
      if (selectedParameter != null) {
        const value = resourceValueForAsset(selectedParameter, makeAssetUri(key));
        if (value != null) {
          onSetValue(selectedParameter.id, value);
        }
      }
    } catch (uploadError) {
      console.error('素材上传失败', uploadError);
      setError(uploadError instanceof Error ? uploadError.message : '素材上传失败');
    }
  }

  async function removeAsset(record: AssetRecord) {
    setError(null);
    try {
      const deleted = await assetLibrary.delete(record.key);
      if (!deleted) {
        throw new Error(`素材 ${record.key} 删除失败`);
      }
      for (const parameter of resourceParameters) {
        const value = effectiveValue(state, parameter);
        if (
          (value?.type === 'image_uri' || value?.type === 'video_uri' || value?.type === 'font_uri') &&
          value.uri === makeAssetUri(record.key)
        ) {
          onResetValue(parameter.id);
        }
      }
      await refreshAssets();
    } catch (removeError) {
      console.error('素材删除失败', removeError);
      setError(removeError instanceof Error ? removeError.message : '素材删除失败');
    }
  }

  return (
    <section className="studio-asset-library">
      <div className="studio-asset-library-heading">
        <div>
          <span className="studio-eyebrow">Assets</span>
          <strong>自定义素材</strong>
        </div>
        <span>{assets.length} 个素材</span>
      </div>
      <div className="studio-asset-library-toolbar">
        <button
          className="studio-file-button"
          onClick={() => fileInputRef.current?.click()}
          type="button"
        >
          上传图片 / 视频 / 字体
        </button>
        <input
          accept="image/*,video/*,font/*"
          className="studio-hidden-input"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file != null) {
              void handleUpload(file);
            }
            event.target.value = '';
          }}
          ref={fileInputRef}
          type="file"
        />
        {resourceParameters.length > 0 ? (
          <select
            className="studio-resource-select"
            onChange={(event) => setSelectedParameterId(event.target.value)}
            value={selectedParameterId}
          >
            <option value="">绑定到 URI 参数…</option>
            {resourceParameters.map((parameter) => (
              <option key={parameter.id} value={parameter.id}>
                {resolveLocalizedText(parameter.label)}
              </option>
            ))}
          </select>
        ) : null}
      </div>
      <div className="studio-asset-library-list">
        {assets.map((asset) => (
          <div className="studio-asset-item" key={asset.key}>
            <div className="studio-asset-item-copy">
              <strong>{asset.key}</strong>
              <span>{asset.mimeType} · {(asset.byteSize / 1024).toFixed(1)} KB</span>
            </div>
            <div className="studio-asset-item-actions">
              {selectedParameter != null && isAssetAllowedForParameter(asset, selectedParameter) ? (
                <button
                  onClick={() => {
                    const value = resourceValueForAsset(selectedParameter, makeAssetUri(asset.key));
                    if (value != null) {
                      onSetValue(selectedParameter.id, value);
                    }
                  }}
                  type="button"
                >
                  绑定
                </button>
              ) : null}
              {selectedAssetKeys.has(asset.key) ? <span className="studio-asset-bound">已绑定</span> : null}
              {protectedKeys.has(asset.key) ? (
                <span className="studio-asset-bound">内置</span>
              ) : (
                <button onClick={() => void removeAsset(asset)} type="button">
                  删除
                </button>
              )}
            </div>
          </div>
        ))}
        {assets.length === 0 ? (
          <span className="studio-resource-hint">还没有素材，上传后会保存在当前浏览器。</span>
        ) : null}
      </div>
      {resourceParameters.length === 0 ? (
        <p className="studio-asset-library-note">当前目标没有声明 URI 参数；素材已登记，可在主题包增加资源参数后绑定。</p>
      ) : null}
      {error != null ? <span className="studio-invalid">{error}</span> : null}
    </section>
  );
}
