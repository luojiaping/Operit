// manifest 驱动的参数工作台：按 section 分组渲染 USER 参数，
// AUTHOR 参数折叠分区；visibleWhen 求值显隐；控件只产生合法值。

import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import type {
  ThemeParameterCondition,
  ThemeParameterControl,
  ThemeParameterDefinition,
  ThemeParameterValue
} from '../shared/theme/manifest';
import {
  createEditorState,
  editorReducer,
  effectiveValue,
  isOverridden,
  isVisible,
  resolveLocalizedText
} from '../shared/theme/editorState';
import type { StudioEditorState } from '../shared/theme/editorState';
import type { ParameterValueState } from '../shared/theme/editorState';
import type { StudioPackage } from '../shared/theme/packageLoader';
import { argbToHex, hexToArgb } from '../shared/theme/color';
import {
  buildExportManifest,
  exportFileName,
  exportThemePackage,
  smokeValidateExportedArchive
} from '../shared/theme/exporter';
import type { ThemeExportResult } from '../shared/theme/exporter';
import { makeAssetUri } from '../shared/theme/assets/library';
import type { AssetLibrary, AssetRecord } from '../shared/theme/assets/library';
import { assetKeyForName, validateAssetUpload } from '../shared/theme/assets/validator';
import { ThemeTargetNavigator } from './ThemeTargetNavigator';
import { ThemeComponentSkinEditor } from './ThemeComponentSkinEditor';
import { ThemeAssetLibraryPanel } from './ThemeAssetLibraryPanel';
import {
  componentIdsForThemeTarget,
  parameterBelongsToTarget,
  themeTargetDefinition,
  type ThemeTargetId
} from './themeTargets';

const SECTION_ORDER = ['APPEARANCE', 'CONVERSATION', 'COMPOSER', 'APP_CHROME'] as const;
const SECTION_LABELS: Record<string, string> = {
  APPEARANCE: '外观',
  CONVERSATION: '对话',
  COMPOSER: '输入栏',
  APP_CHROME: '应用栏'
};

export interface ThemeWorkbenchProps {
  studioPackage: StudioPackage;
  assetLibrary: AssetLibrary;
  onPreview: (state: StudioEditorState) => void;
  selectedTarget: ThemeTargetId;
  onSelectTarget: (target: ThemeTargetId) => void;
}

export function ThemeWorkbench({
  studioPackage,
  assetLibrary,
  onPreview,
  selectedTarget,
  onSelectTarget
}: ThemeWorkbenchProps) {
  const [state, dispatch] = useReducer(editorReducer, studioPackage, createEditorState);
  const [assetRevision, setAssetRevision] = useState(0);

  const userParameters = useMemo(
    () =>
      state.manifest.parameters
        .filter((parameter) => parameter.visibility === 'USER')
        .sort((a, b) => a.order - b.order),
    [state.manifest.parameters]
  );
  const authorParameters = useMemo(
    () =>
      state.manifest.parameters
        .filter((parameter) => parameter.visibility === 'AUTHOR')
        .sort((a, b) => a.order - b.order),
    [state.manifest.parameters]
  );
  const scopedUserParameters = useMemo(
    () => userParameters.filter((parameter) => parameterBelongsToTarget(parameter, selectedTarget)),
    [selectedTarget, userParameters]
  );
  const scopedAuthorParameters = useMemo(
    () => authorParameters.filter((parameter) => parameterBelongsToTarget(parameter, selectedTarget)),
    [authorParameters, selectedTarget]
  );
  const scopedComponentIds = useMemo(
    () =>
      componentIdsForThemeTarget(
        selectedTarget,
        Array.from(state.componentSkins.keys()).sort()
      ),
    [selectedTarget, state.componentSkins]
  );
  const scopedResourceParameters = useMemo(
    () =>
      [...scopedUserParameters, ...scopedAuthorParameters].filter(
        (parameter) =>
          parameter.control.type === 'image_picker' ||
          parameter.control.type === 'video_picker' ||
          parameter.control.type === 'font_picker'
      ),
    [scopedAuthorParameters, scopedUserParameters]
  );
  const selectedTargetDefinition = themeTargetDefinition(selectedTarget);
  const inspectorHeadingRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    onPreview(state);
  }, [state, onPreview]);

  useEffect(() => {
    inspectorHeadingRef.current?.scrollIntoView({ block: 'nearest' });
  }, [selectedTarget]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        for (const asset of studioPackage.assets.values()) {
          if (cancelled) {
            return;
          }
          await assetLibrary.put({
            blob: new Blob([asset.bytes as BlobPart], { type: mimeTypeForAssetPath(asset.entry.path) }),
            byteSize: asset.entry.byteSize,
            key: asset.entry.key,
            kind: asset.entry.kind,
            mimeType: mimeTypeForAssetPath(asset.entry.path),
            path: asset.entry.path,
            sha256: asset.entry.sha256
          });
        }
        if (!cancelled) {
          setAssetRevision((revision) => revision + 1);
        }
      } catch (error) {
        console.error('主题包素材登记失败', error);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [assetLibrary, studioPackage]);

  return (
    <div className="studio-panel">
      <ThemeTargetNavigator onSelectTarget={onSelectTarget} selectedTarget={selectedTarget} />
      <div className="studio-package-header">
        <strong>{resolveLocalizedText(state.manifest.displayName)}</strong>
        <code>
          {state.manifest.packageId}@{state.manifest.version}
        </code>
        {state.manifest.basis ? (
          <span className="studio-basis">
            basis: {state.manifest.basis.packageId}@{state.manifest.basis.version}
          </span>
        ) : null}
      </div>

      <div className="studio-inspector-heading" ref={inspectorHeadingRef}>
        <div>
          <span className="studio-eyebrow">Selected surface</span>
          <strong>{selectedTargetDefinition.label}</strong>
          <small>{selectedTargetDefinition.description}</small>
        </div>
        <span>
          {scopedUserParameters.length + scopedAuthorParameters.length + scopedComponentIds.length} 个可调项
        </span>
      </div>

      {SECTION_ORDER.map((section) => {
        const sectionParameters = scopedUserParameters.filter(
          (parameter) => parameter.section === section
        );
        if (sectionParameters.length === 0) {
          return null;
        }
        return (
          <div className="studio-group" key={section}>
            <h4>{SECTION_LABELS[section]}</h4>
            {sectionParameters.map((parameter) =>
              isVisible(state, parameter) ? (
                <ParameterRow
                  definition={parameter}
                  key={parameter.id}
                  state={state}
                  assetRevision={assetRevision}
                  onClear={() => dispatch({ kind: 'resetValue', parameterId: parameter.id })}
                  onSetValue={(value) =>
                    dispatch({ kind: 'setValue', parameterId: parameter.id, value })
                  }
                  onPickResource={assetLibrary}
                />
              ) : null
            )}
          </div>
        );
      })}

      <ThemeComponentSkinEditor
        componentIds={scopedComponentIds}
        manifest={state.manifest}
        onChange={(componentId, skin) =>
          dispatch({ kind: 'setComponentSkin', componentId, skin })
        }
        onReset={(componentId) => dispatch({ kind: 'resetComponentSkin', componentId })}
        skins={state.componentSkins}
      />

      <ThemeAssetLibraryPanel
        assetLibrary={assetLibrary}
        assetRevision={assetRevision}
        onResetValue={(parameterId) => dispatch({ kind: 'resetValue', parameterId })}
        onSetValue={(parameterId, value) =>
          dispatch({ kind: 'setValue', parameterId, value })
        }
        protectedAssetKeys={Array.from(studioPackage.assets.keys())}
        resourceParameters={scopedResourceParameters}
        state={state}
      />

      {scopedUserParameters.length === 0 &&
      scopedAuthorParameters.length === 0 &&
      scopedComponentIds.length === 0 ? (
        <div className="studio-target-empty">
          <strong>这个区域还没有独立参数</strong>
          <span>当前主题通过全局 token 或组件样式控制，可以先切换到「全部参数」查看完整配置。</span>
          <button onClick={() => onSelectTarget('all')} type="button">
            查看全部参数
          </button>
        </div>
      ) : null}

      {scopedAuthorParameters.length > 0 ? (
        <details className="studio-author-group">
          <summary>作者级参数（{scopedAuthorParameters.length}）</summary>
          {scopedAuthorParameters.map((parameter) => (
            <ParameterRow
              definition={parameter}
              key={parameter.id}
              onClear={() => dispatch({ kind: 'resetValue', parameterId: parameter.id })}
              onPickResource={assetLibrary}
              onSetValue={(value) =>
                dispatch({ kind: 'setValue', parameterId: parameter.id, value })
              }
              state={state}
              assetRevision={assetRevision}
            />
          ))}
        </details>
      ) : null}

      <button className="studio-reset" onClick={() => dispatch({ kind: 'resetAll' })} type="button">
        重置为包默认
      </button>

      <ExportBlock
        assetLibrary={assetLibrary}
        state={state}
        studioPackage={studioPackage}
      />
    </div>
  );
}

function ExportBlock({
  state,
  studioPackage,
  assetLibrary
}: {
  state: StudioEditorState;
  studioPackage: StudioPackage;
  assetLibrary: AssetLibrary;
}) {
  const [exporting, setExporting] = useState(false);
  const [result, setResult] = useState<ThemeExportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleExport() {
    setExporting(true);
    setError(null);
    try {
      const boundRecords = await collectBoundAssets(state.values, assetLibrary);
      const plan = buildExportManifest({ manifest: studioPackage.manifest, state, boundAssets: boundRecords });
      const exportResult = await exportThemePackage(plan, boundRecords);
      const verifiedSha = await smokeValidateExportedArchive(exportResult.blob);
      if (verifiedSha !== exportResult.sha256) {
        throw new Error('导出回环校验失败');
      }
      setResult(exportResult);
      const url = URL.createObjectURL(exportResult.blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = exportFileName(plan.manifest.packageId, plan.manifest.version);
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (exportError: unknown) {
      setError(exportError instanceof Error ? exportError.message : String(exportError));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="studio-export">
      <button className="studio-file-button" disabled={exporting} onClick={() => void handleExport()} type="button">
        {exporting ? '导出中…' : '导出 .otheme'}
      </button>
      {result != null ? (
        <div className="studio-export-result">
          <span className="studio-export-ok">
            导出成功 · SHA-256 <code>{result.sha256}</code>
          </span>
          <span className="studio-export-note">
            {result.assetCount} 个素材已打包 · manifest {result.manifestJsonBytes} bytes
          </span>
        </div>
      ) : null}
      {error != null ? (
        <span className="studio-invalid">{error}</span>
      ) : null}
    </div>
  );
}

async function collectBoundAssets(
  values: Map<string, ParameterValueState>,
  library: AssetLibrary
): Promise<AssetRecord[]> {
  const keys = new Set<string>();
  for (const value of values.values()) {
    if (value == null) {
      continue;
    }
    if (value.type === 'image_uri' || value.type === 'video_uri' || value.type === 'font_uri') {
      const key = makeAssetKey(value.uri);
      if (key != null) {
        keys.add(key);
      }
    }
  }
  const records: AssetRecord[] = [];
  for (const key of keys) {
    const record = await library.get(key);
    if (record != null) {
      records.push(record);
    }
  }
  return records;
}

function makeAssetKey(uri: string): string | null {
  const prefix = 'asset://';
  return uri.startsWith(prefix) && uri.length > prefix.length ? uri.slice(prefix.length) : null;
}

interface ParameterRowProps {
  definition: ThemeParameterDefinition;
  state: StudioEditorState;
  onSetValue: (value: ThemeParameterValue) => void;
  onClear: () => void;
  onPickResource: AssetLibrary;
  assetRevision: number;
}

function ParameterRow({
  definition,
  state,
  onSetValue,
  onClear,
  onPickResource,
  assetRevision
}: ParameterRowProps) {
  const value = effectiveValue(state, definition);
  const overridden = isOverridden(state, definition);
  const label = resolveLocalizedText(definition.label);
  const description =
    definition.description == null ? null : resolveLocalizedText(definition.description);

  return (
    <div className="studio-parameter">
      <div className="studio-parameter-label">
        <span>{label}</span>
        {description ? <small>{description}</small> : null}
        {overridden ? (
          <button className="studio-parameter-reset" onClick={onClear} type="button">
            还原默认
          </button>
        ) : null}
      </div>
      <ParameterControl
        control={definition.control}
        definition={definition}
        onClear={onClear}
        onPickResource={onPickResource}
        onSetValue={onSetValue}
        assetRevision={assetRevision}
        value={value}
      />
    </div>
  );
}

function ParameterControl({
  definition,
  control,
  value,
  onSetValue,
  onClear,
  onPickResource,
  assetRevision
}: {
  definition: ThemeParameterDefinition;
  control: ThemeParameterControl;
  value: ThemeParameterValue | null;
  onSetValue: (value: ThemeParameterValue) => void;
  onClear: () => void;
  onPickResource: AssetLibrary;
  assetRevision: number;
}) {
  switch (control.type) {
    case 'color_palette':
      if (value == null || value.type !== 'color') {
        return <span className="studio-invalid">控件与值类型不匹配</span>;
      }
      return (
        <div className="studio-color-row">
          {control.presetArgb.map((preset) => (
            <button
              className={
                value.argb === preset ? 'studio-swatch is-active' : 'studio-swatch'
              }
              key={preset}
              onClick={() => onSetValue({ type: 'color', argb: preset })}
              style={{ background: argbToHex(preset) }}
              type="button"
            />
          ))}
          {control.allowCustom ? (
            <input
              onChange={(event) => onSetValue({ type: 'color', argb: hexToArgb(event.target.value) })}
              type="color"
              value={argbToHex(value.argb)}
            />
          ) : null}
        </div>
      );
    case 'toggle':
      if (value == null || value.type !== 'boolean') {
        return <span className="studio-invalid">控件与值类型不匹配</span>;
      }
      return (
        <input
          checked={value.value}
          onChange={(event) =>
            onSetValue({ type: 'boolean', value: event.target.checked })
          }
          type="checkbox"
        />
      );
    case 'choice':
      if (value == null || value.type !== 'option') {
        return <span className="studio-invalid">控件与值类型不匹配</span>;
      }
      return (
        <div className="studio-segment">
          {control.options.map((option) => (
            <button
              className={value.value === option.id ? 'is-active' : ''}
              key={option.id}
              onClick={() => onSetValue({ type: 'option', value: option.id })}
              type="button"
            >
              {resolveLocalizedText(option.label)}
            </button>
          ))}
        </div>
      );
    case 'slider':
      if (value == null || value.type !== 'float') {
        return <span className="studio-invalid">控件与值类型不匹配</span>;
      }
      return (
        <label className="studio-field is-slider">
          <span>
            <code>{value.value.toFixed(2)}</code>
          </span>
          <input
            max={control.maximum}
            min={control.minimum}
            onChange={(event) =>
              onSetValue({ type: 'float', value: Number(event.target.value) })
            }
            step={control.step}
            type="range"
            value={value.value}
          />
        </label>
      );
    case 'color_pair_palette':
      if (value == null || value.type !== 'color_pair') {
        return <span className="studio-invalid">控件与值类型不匹配</span>;
      }
      return (
        <div className="studio-color-pair">
          <label className="studio-color-pair-field">
            <span>浅色</span>
            <span className="studio-color-pair-swatches">
              {control.lightPresetArgb.map((preset) => (
                <button
                  className={value.lightArgb === preset ? 'studio-swatch is-active' : 'studio-swatch'}
                  key={preset}
                  onClick={() => onSetValue({ ...value, lightArgb: preset })}
                  style={{ background: argbToHex(preset) }}
                  type="button"
                />
              ))}
            </span>
            {control.allowCustom ? <input
              onChange={(event) =>
                onSetValue({
                  type: 'color_pair',
                  lightArgb: hexToArgb(event.target.value),
                  darkArgb: value.darkArgb
                })
              }
              type="color"
              value={argbToHex(value.lightArgb)}
            /> : null}
          </label>
          <label className="studio-color-pair-field">
            <span>深色</span>
            <span className="studio-color-pair-swatches">
              {control.darkPresetArgb.map((preset) => (
                <button
                  className={value.darkArgb === preset ? 'studio-swatch is-active' : 'studio-swatch'}
                  key={preset}
                  onClick={() => onSetValue({ ...value, darkArgb: preset })}
                  style={{ background: argbToHex(preset) }}
                  type="button"
                />
              ))}
            </span>
            {control.allowCustom ? <input
              onChange={(event) =>
                onSetValue({
                  type: 'color_pair',
                  lightArgb: value.lightArgb,
                  darkArgb: hexToArgb(event.target.value)
                })
              }
              type="color"
              value={argbToHex(value.darkArgb)}
            /> : null}
          </label>
        </div>
      );
    case 'image_picker':
    case 'video_picker':
    case 'font_picker':
      return (
        <ResourcePickerControl
           control={control}
           assetRevision={assetRevision}
           library={onPickResource}
          onClear={onClear}
          onSetValue={onSetValue}
          value={value}
        />
      );
    case 'author_value':
      return (
        <AuthorValueControl
          definition={definition}
          onSetValue={onSetValue}
          value={value}
        />
      );
  }
}

function AuthorValueControl({
  definition,
  value,
  onSetValue
}: {
  definition: ThemeParameterDefinition;
  value: ThemeParameterValue | null;
  onSetValue: (value: ThemeParameterValue) => void;
}) {
  if (value == null) {
    return <span className="studio-invalid">未设置</span>;
  }

  const numberInput = (
    label: string,
    current: number,
    update: (next: number) => ThemeParameterValue
  ) => (
    <label className="studio-author-number">
      <span>{label}</span>
      <input
        onChange={(event) => {
          const next = Number(event.target.value);
          if (Number.isFinite(next)) {
            onSetValue(update(next));
          }
        }}
        step="0.01"
        type="number"
        value={current}
      />
    </label>
  );

  switch (definition.type) {
    case 'FLOAT':
      return value.type === 'float'
        ? numberInput('数值', value.value, (next) => ({ type: 'float', value: next }))
        : <span className="studio-invalid">作者参数与值类型不匹配</span>;
    case 'CORNER_RADIUS':
      return value.type === 'corner_radius'
        ? numberInput('dp', value.valueDp, (next) => ({ type: 'corner_radius', valueDp: next }))
        : <span className="studio-invalid">作者参数与值类型不匹配</span>;
    case 'INSETS':
      return value.type === 'insets' ? (
        <div className="studio-author-grid">
          {numberInput('起始', value.startDp, (next) => ({ ...value, startDp: next }))}
          {numberInput('顶部', value.topDp, (next) => ({ ...value, topDp: next }))}
          {numberInput('结束', value.endDp, (next) => ({ ...value, endDp: next }))}
          {numberInput('底部', value.bottomDp, (next) => ({ ...value, bottomDp: next }))}
        </div>
      ) : <span className="studio-invalid">作者参数与值类型不匹配</span>;
    case 'IMAGE_LAYOUT':
      return value.type === 'image_layout' ? (
        <div className="studio-author-grid">
          {numberInput('裁切左', value.cropLeft, (next) => ({ ...value, cropLeft: next }))}
          {numberInput('裁切上', value.cropTop, (next) => ({ ...value, cropTop: next }))}
          {numberInput('裁切右', value.cropRight, (next) => ({ ...value, cropRight: next }))}
          {numberInput('裁切下', value.cropBottom, (next) => ({ ...value, cropBottom: next }))}
          {numberInput('重复起点', value.repeatStart, (next) => ({ ...value, repeatStart: next }))}
          {numberInput('重复终点', value.repeatEnd, (next) => ({ ...value, repeatEnd: next }))}
          {numberInput('纵向起点', value.repeatYStart, (next) => ({ ...value, repeatYStart: next }))}
          {numberInput('纵向终点', value.repeatYEnd, (next) => ({ ...value, repeatYEnd: next }))}
          {numberInput('缩放', value.scale, (next) => ({ ...value, scale: next }))}
        </div>
      ) : <span className="studio-invalid">作者参数与值类型不匹配</span>;
    case 'COLOR':
      return value.type === 'color' ? (
        <input
          onChange={(event) => onSetValue({ type: 'color', argb: hexToArgb(event.target.value) })}
          type="color"
          value={argbToHex(value.argb)}
        />
      ) : <span className="studio-invalid">作者参数与值类型不匹配</span>;
    case 'COLOR_PAIR':
      return value.type === 'color_pair' ? (
        <div className="studio-color-pair">
          <label className="studio-color-pair-field">
            <span>浅色</span>
            <input
              onChange={(event) =>
                onSetValue({
                  ...value,
                  lightArgb: hexToArgb(event.target.value)
                })
              }
              type="color"
              value={argbToHex(value.lightArgb)}
            />
          </label>
          <label className="studio-color-pair-field">
            <span>深色</span>
            <input
              onChange={(event) =>
                onSetValue({
                  ...value,
                  darkArgb: hexToArgb(event.target.value)
                })
              }
              type="color"
              value={argbToHex(value.darkArgb)}
            />
          </label>
        </div>
      ) : <span className="studio-invalid">作者参数与值类型不匹配</span>;
    case 'BOOLEAN':
      return value.type === 'boolean' ? (
        <input
          checked={value.value}
          onChange={(event) => onSetValue({ type: 'boolean', value: event.target.checked })}
          type="checkbox"
        />
      ) : <span className="studio-invalid">作者参数与值类型不匹配</span>;
    case 'OPTION':
      return value.type === 'option' ? (
        <input
          onChange={(event) => onSetValue({ type: 'option', value: event.target.value })}
          type="text"
          value={value.value}
        />
      ) : <span className="studio-invalid">作者参数与值类型不匹配</span>;
    case 'IMAGE_URI':
    case 'VIDEO_URI':
    case 'FONT_URI':
      return <code className="studio-author-value">{value.type === 'image_uri' || value.type === 'video_uri' || value.type === 'font_uri' ? value.uri : JSON.stringify(value)}</code>;
  }
}

function ResourcePickerControl({
  control,
  value,
  onSetValue,
  onClear,
  library,
  assetRevision
}: {
  control:
    | { type: 'image_picker'; mimeTypes: string[] }
    | { type: 'video_picker'; mimeTypes: string[] }
    | { type: 'font_picker'; mimeTypes: string[] };
  value: ThemeParameterValue | null;
  onSetValue: (value: ThemeParameterValue) => void;
  onClear: () => void;
  library: AssetLibrary;
  assetRevision: number;
}) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [available, setAvailable] = useState<AssetRecordLite[]>([]);

  const resourceKind =
    control.type === 'image_picker'
      ? ('BITMAP' as const)
      : control.type === 'video_picker'
        ? ('BITMAP' as const)
        : ('FONT' as const);
  const valueType =
    control.type === 'image_picker'
      ? ('image_uri' as const)
      : control.type === 'video_picker'
        ? ('video_uri' as const)
        : ('font_uri' as const);

  const currentUri = value?.type === valueType ? value.uri : null;

  const mimeFilter =
    control.type === 'image_picker'
      ? ['image/*']
      : control.type === 'video_picker'
        ? ['video/*']
        : ['font/*'];

  useEffect(() => {
    let cancel = false;
    void (async () => {
      const records = await library.list();
      if (cancel) {
        return;
      }
      const eligible = records
        .filter((record) => acceptsMimeType(control.mimeTypes, record.mimeType))
        .map((record) => ({
          key: record.key,
          path: record.path,
          kind: record.kind,
          mimeType: record.mimeType
        }));
      setAvailable(eligible);
    })();
    return () => {
      cancel = true;
    };
  }, [assetRevision, control.type, library]);

  useEffect(() => {
    if (currentUri == null) {
      setPreviewUrl(null);
      return;
    }
    let cancel = false;
    void (async () => {
      const url = await library.resolveUrl(currentUri);
      if (!cancel) {
        setPreviewUrl(url);
      }
    })();
    return () => {
      cancel = true;
    };
  }, [currentUri, library]);

  async function handleFile(file: File) {
    setError(null);
    try {
      if (!acceptsMimeType(control.mimeTypes, file.type)) {
        setError(`不支持的类型 ${file.type || '(未知)'}`);
        return;
      }
      const key = `${assetKeyForName(file.name)}_${Math.random().toString(36).slice(2, 8)}`;
      const validation = await validateAssetUpload({
        key,
        file,
        mimeType: file.type,
        kind: resourceKind
      });
      if (!validation.ok) {
        setError(validation.error);
        return;
      }
      const directory =
        control.type === 'font_picker'
          ? 'font'
          : control.type === 'video_picker'
            ? 'video'
            : 'image';
      const record: AssetRecord = {
        key,
        path: `assets/${directory}/${key}`,
        kind: resourceKind,
        mimeType: file.type,
        sha256: validation.sha256,
        byteSize: validation.byteSize,
        blob: file
      };
      await library.put(record);
      setAvailable((current) => [
        ...current.filter((item) => item.key !== record.key),
        {
          key: record.key,
          path: record.path,
          kind: record.kind,
          mimeType: record.mimeType
        }
      ]);
      onSetValue({ type: valueType, uri: makeAssetUri(key) });
    } catch (error) {
      console.error('素材上传失败', error);
      setError(error instanceof Error ? error.message : '素材上传失败');
    }
  }

  return (
    <div className="studio-resource-row">
      <button
        className="studio-file-button"
        onClick={() => fileInputRef.current?.click()}
        type="button"
      >
        上传素材
      </button>
      <input
        accept={mimeFilter.join(',')}
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
      {available.length > 0 ? (
        <select
          className="studio-resource-select"
          onChange={(event) => {
            const record = available.find((item) => item.key === event.target.value);
            if (record != null) {
              onSetValue({ type: valueType, uri: makeAssetUri(record.key) });
            }
          }}
          value={currentUri == null ? '' : extractKey(currentUri) ?? ''}
        >
          <option value="" disabled>
            从素材库选择
          </option>
          {available.map((record) => (
            <option key={record.key} value={record.key}>
              {record.path}
            </option>
          ))}
        </select>
      ) : null}
      {currentUri != null ? (
        <button
          className="studio-parameter-reset"
          onClick={() => {
            onClear();
            setPreviewUrl(null);
          }}
          type="button"
        >
          清除
        </button>
      ) : null}
      {previewUrl != null ? (
        <div className="studio-resource-preview">
          {control.type === 'image_picker' ? (
            <img alt="素材预览" src={previewUrl} />
          ) : control.type === 'video_picker' ? (
            <video muted playsInline src={previewUrl} />
          ) : (
            <code>{previewUrl}</code>
          )}
        </div>
      ) : null}
      {error != null ? <span className="studio-invalid">{error}</span> : null}
    </div>
  );
}

function extractKey(uri: string): string | null {
  const prefix = 'asset://';
  return uri.startsWith(prefix) && uri.length > prefix.length
    ? uri.slice(prefix.length)
    : null;
}

interface AssetRecordLite {
  key: string;
  path: string;
  kind: string;
  mimeType: string;
}

function acceptsMimeType(allowed: string[], actual: string): boolean {
  return allowed.some((pattern) =>
    pattern.endsWith('/*') ? actual.startsWith(pattern.slice(0, -1)) : pattern === actual
  );
}

function mimeTypeForAssetPath(path: string): string {
  const extension = path.toLowerCase().split('.').pop();
  switch (extension) {
    case 'png':
      return 'image/png';
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg';
    case 'webp':
      return 'image/webp';
    case 'gif':
      return 'image/gif';
    case 'mp4':
      return 'video/mp4';
    case 'webm':
      return 'video/webm';
    case 'woff':
      return 'font/woff';
    case 'woff2':
      return 'font/woff2';
    case 'ttf':
      return 'font/ttf';
    case 'otf':
      return 'font/otf';
    default:
      return 'application/octet-stream';
  }
}
