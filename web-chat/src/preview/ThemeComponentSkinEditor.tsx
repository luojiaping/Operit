import { useMemo, useState, type ReactNode } from 'react';
import type {
  ThemeComponentFrame,
  ThemeComponentSkin,
  ThemeComponentStateSkin,
  ThemePackageManifest,
  ThemeStroke
} from '../shared/theme/manifest';

type ComponentSkinState = 'normal' | 'disabled' | 'selected' | 'focused' | 'error';
type FrameKind = ThemeComponentFrame['kind'];

const COMPONENT_STATES: readonly { id: ComponentSkinState; label: string }[] = [
  { id: 'normal', label: '常态' },
  { id: 'selected', label: '选中' },
  { id: 'focused', label: '聚焦' },
  { id: 'disabled', label: '禁用' },
  { id: 'error', label: '错误' }
];

const FRAME_KINDS: readonly { id: FrameKind; label: string }[] = [
  { id: 'none', label: '无边框' },
  { id: 'round_rect', label: '圆角矩形' },
  { id: 'cut_corners', label: '切角' },
  { id: 'hud_notched', label: 'HUD 缺口' },
  { id: 'corner_brackets', label: '角括号' },
  { id: 'segmented_rail', label: '分段轨道' }
];

const COMPONENT_LABELS: Record<string, string> = {
  app_bar: '应用顶栏',
  navigation: '导航抽屉',
  page: '页面根',
  section: '内容区块',
  list_item: '列表项',
  button: '填充按钮',
  icon_button: '图标按钮',
  input: '输入框',
  composer: '输入栏容器',
  message_user: '用户消息皮肤',
  message_assistant: 'AI 消息皮肤',
  dialog: '对话框',
  sheet: '底部面板',
  menu: '菜单',
  snackbar: '提示条',
  status: '状态条'
};

export function ThemeComponentSkinEditor({
  componentIds,
  manifest,
  skins,
  onChange,
  onReset
}: {
  componentIds: readonly string[];
  manifest: ThemePackageManifest;
  skins: Map<string, ThemeComponentSkin>;
  onChange: (componentId: string, skin: ThemeComponentSkin) => void;
  onReset: (componentId: string) => void;
}) {
  const tokenIds = useMemo(() => {
    const tokens = new Set<string>([
      ...Object.keys(manifest.tokens ?? {}),
      ...Object.values(manifest.presentation.material?.colors ?? {})
    ]);
    for (const componentId of componentIds) {
      const skin = skins.get(componentId);
      if (skin == null) {
        continue;
      }
      for (const state of COMPONENT_STATES) {
        const value = readComponentState(skin, state.id);
        if (value != null) {
          tokens.add(value.containerToken);
          tokens.add(value.contentToken);
          addFrameTokens(tokens, value.frame);
        }
      }
    }
    return Array.from(tokens).sort();
  }, [componentIds, manifest, skins]);

  if (componentIds.length === 0) {
    return null;
  }

  return (
    <div className="studio-group studio-component-skins">
      <div className="studio-component-skin-heading">
        <h4>组件皮肤</h4>
        <span>颜色来自 manifest token，几何参数直接作用于预览</span>
      </div>
      <div className="studio-component-skin-list">
        {componentIds.map((componentId) => {
          const skin = skins.get(componentId);
          if (skin == null) {
            return null;
          }
          return (
            <ComponentSkinRow
              componentId={componentId}
              key={componentId}
              onChange={onChange}
              onReset={onReset}
              skin={skin}
              tokenIds={tokenIds}
            />
          );
        })}
      </div>
    </div>
  );
}

function ComponentSkinRow({
  componentId,
  skin,
  tokenIds,
  onChange,
  onReset
}: {
  componentId: string;
  skin: ThemeComponentSkin;
  tokenIds: readonly string[];
  onChange: (componentId: string, skin: ThemeComponentSkin) => void;
  onReset: (componentId: string) => void;
}) {
  const [selectedState, setSelectedState] = useState<ComponentSkinState>('normal');
  const current = readComponentState(skin, selectedState);
  const label = COMPONENT_LABELS[componentId] ?? componentId;

  function selectState(state: ComponentSkinState) {
    if (readComponentState(skin, state) == null) {
      onChange(componentId, writeComponentState(skin, state, cloneStateSkin(skin.normal)));
    }
    setSelectedState(state);
  }

  function updateState(patch: Partial<ThemeComponentStateSkin>) {
    if (current == null) {
      return;
    }
    onChange(
      componentId,
      writeComponentState(skin, selectedState, {
        ...current,
        ...patch
      })
    );
  }

  return (
    <section className="studio-component-skin-row">
      <div className="studio-component-skin-title">
        <strong>{label}</strong>
        <code>{componentId}</code>
        <button className="studio-parameter-reset" onClick={() => onReset(componentId)} type="button">
          还原
        </button>
      </div>
      <div className="studio-component-state-tabs">
        {COMPONENT_STATES.map((state) => {
          const defined = readComponentState(skin, state.id) != null;
          return (
            <button
              className={selectedState === state.id ? 'is-active' : ''}
              key={state.id}
              onClick={() => selectState(state.id)}
              type="button"
            >
              {state.label}
              {!defined ? ' +' : ''}
            </button>
          );
        })}
      </div>
      {current == null ? (
        <span className="studio-invalid">无法创建组件状态</span>
      ) : (
        <ComponentStateFields
          onChange={updateState}
          state={current}
          tokenIds={tokenIds}
        />
      )}
    </section>
  );
}

function ComponentStateFields({
  state,
  tokenIds,
  onChange
}: {
  state: ThemeComponentStateSkin;
  tokenIds: readonly string[];
  onChange: (patch: Partial<ThemeComponentStateSkin>) => void;
}) {
  return (
    <div className="studio-component-state-fields">
      <div className="studio-token-pair">
        <TokenSelect
          label="背景 token"
          onChange={(containerToken) => onChange({ containerToken })}
          tokenIds={tokenIds}
          value={state.containerToken}
        />
        <TokenSelect
          label="内容 token"
          onChange={(contentToken) => onChange({ contentToken })}
          tokenIds={tokenIds}
          value={state.contentToken}
        />
      </div>
      <div className="studio-component-number-row">
        <NumberField
          label="阴影 dp"
          onChange={(elevationDp) => onChange({ elevationDp })}
          step={0.5}
          value={state.elevationDp}
        />
        <NumberField
          label="左内距"
          onChange={(startDp) =>
            onChange({ contentPadding: { ...state.contentPadding, startDp } })
          }
          value={state.contentPadding.startDp}
        />
        <NumberField
          label="上内距"
          onChange={(topDp) =>
            onChange({ contentPadding: { ...state.contentPadding, topDp } })
          }
          value={state.contentPadding.topDp}
        />
        <NumberField
          label="右内距"
          onChange={(endDp) =>
            onChange({ contentPadding: { ...state.contentPadding, endDp } })
          }
          value={state.contentPadding.endDp}
        />
        <NumberField
          label="下内距"
          onChange={(bottomDp) =>
            onChange({ contentPadding: { ...state.contentPadding, bottomDp } })
          }
          value={state.contentPadding.bottomDp}
        />
      </div>
      <FrameEditor
        frame={state.frame}
        onChange={(frame) => onChange({ frame })}
        tokenIds={tokenIds}
      />
    </div>
  );
}

function TokenSelect({
  label,
  value,
  tokenIds,
  onChange
}: {
  label: string;
  value: string;
  tokenIds: readonly string[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="studio-token-select">
      <span>{label}</span>
      <select onChange={(event) => onChange(event.target.value)} value={value}>
        {tokenIds.map((tokenId) => (
          <option key={tokenId} value={tokenId}>
            {tokenId}
          </option>
        ))}
      </select>
    </label>
  );
}

function NumberField({
  label,
  value,
  step = 1,
  onChange
}: {
  label: string;
  value: number;
  step?: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="studio-author-number">
      <span>{label}</span>
      <input
        onChange={(event) => {
          const next = Number(event.target.value);
          if (Number.isFinite(next)) {
            onChange(next);
          }
        }}
        step={step}
        type="number"
        value={value}
      />
    </label>
  );
}

function FrameEditor({
  frame,
  tokenIds,
  onChange
}: {
  frame: ThemeComponentFrame;
  tokenIds: readonly string[];
  onChange: (frame: ThemeComponentFrame) => void;
}) {
  function updateFrame(patch: Partial<ThemeComponentFrame>) {
    onChange({ ...frame, ...patch } as ThemeComponentFrame);
  }

  return (
    <div className="studio-frame-editor">
      <label className="studio-token-select">
        <span>边框样式</span>
        <select
          onChange={(event) => {
            const kind = parseFrameKind(event.target.value);
            if (kind != null) {
              onChange(frameForKind(kind, frame));
            }
          }}
          value={frame.kind}
        >
          {FRAME_KINDS.map((kind) => (
            <option key={kind.id} value={kind.id}>
              {kind.label}
            </option>
          ))}
        </select>
      </label>
      {renderFrameFields(frame, tokenIds, updateFrame)}
    </div>
  );
}

function renderFrameFields(
  frame: ThemeComponentFrame,
  tokenIds: readonly string[],
  updateFrame: (patch: Partial<ThemeComponentFrame>) => void
): ReactNode {
  switch (frame.kind) {
    case 'none':
      return <span className="studio-resource-hint">当前状态不绘制边框。</span>;
    case 'round_rect':
      return (
        <div className="studio-frame-grid">
          <NumberField
            label="圆角 dp"
            onChange={(cornerRadiusDp) => updateFrame({ cornerRadiusDp })}
            value={frame.cornerRadiusDp}
          />
          <StrokeEditor
            label="边线"
            onChange={(border) => updateFrame({ border })}
            stroke={frame.border ?? null}
            tokenIds={tokenIds}
          />
        </div>
      );
    case 'cut_corners':
      return (
        <div className="studio-frame-grid">
          <NumberField
            label="切角 dp"
            onChange={(cutSizeDp) => updateFrame({ cutSizeDp })}
            value={frame.cutSizeDp}
          />
          <StrokeEditor
            label="边线"
            onChange={(border) => updateFrame({ border })}
            stroke={frame.border ?? null}
            tokenIds={tokenIds}
          />
          <StrokeEditor
            label="强调线"
            onChange={(accent) => updateFrame({ accent })}
            stroke={frame.accent ?? null}
            tokenIds={tokenIds}
          />
        </div>
      );
    case 'hud_notched':
      return (
        <div className="studio-frame-grid">
          <NumberField
            label="切角 dp"
            onChange={(cutSizeDp) => updateFrame({ cutSizeDp })}
            value={frame.cutSizeDp}
          />
          <NumberField
            label="缺口宽比例"
            onChange={(notchWidthFraction) => updateFrame({ notchWidthFraction })}
            step={0.01}
            value={frame.notchWidthFraction}
          />
          <NumberField
            label="缺口深 dp"
            onChange={(notchDepthDp) => updateFrame({ notchDepthDp })}
            value={frame.notchDepthDp}
          />
          <StrokeEditor
            label="边线"
            onChange={(border) => updateFrame({ border })}
            stroke={frame.border ?? null}
            tokenIds={tokenIds}
          />
          <StrokeEditor
            label="强调线"
            onChange={(accent) => updateFrame({ accent })}
            stroke={frame.accent ?? null}
            tokenIds={tokenIds}
          />
        </div>
      );
    case 'corner_brackets':
      return (
        <div className="studio-frame-grid">
          <NumberField
            label="角切 dp"
            onChange={(cornerCutDp) => updateFrame({ cornerCutDp })}
            value={frame.cornerCutDp}
          />
          <NumberField
            label="括号长度 dp"
            onChange={(bracketLengthDp) => updateFrame({ bracketLengthDp })}
            value={frame.bracketLengthDp}
          />
          <StrokeEditor
            label="边线"
            onChange={(border) => updateFrame({ border })}
            stroke={frame.border ?? null}
            tokenIds={tokenIds}
          />
          <StrokeEditor
            label="强调线"
            onChange={(accent) => updateFrame({ accent })}
            stroke={frame.accent ?? null}
            tokenIds={tokenIds}
          />
        </div>
      );
    case 'segmented_rail':
      return (
        <div className="studio-frame-grid">
          <NumberField
            label="角切 dp"
            onChange={(cornerCutDp) => updateFrame({ cornerCutDp })}
            value={frame.cornerCutDp}
          />
          <NumberField
            label="轨道内距 dp"
            onChange={(railInsetDp) => updateFrame({ railInsetDp })}
            value={frame.railInsetDp}
          />
          <NumberField
            label="分段长度 dp"
            onChange={(segmentLengthDp) => updateFrame({ segmentLengthDp })}
            value={frame.segmentLengthDp}
          />
          <StrokeEditor
            label="边线"
            onChange={(border) => updateFrame({ border })}
            stroke={frame.border ?? null}
            tokenIds={tokenIds}
          />
          <StrokeEditor
            label="强调线"
            onChange={(accent) => updateFrame({ accent })}
            stroke={frame.accent ?? null}
            tokenIds={tokenIds}
          />
        </div>
      );
  }
}

function StrokeEditor({
  label,
  stroke,
  tokenIds,
  onChange
}: {
  label: string;
  stroke: ThemeStroke | null;
  tokenIds: readonly string[];
  onChange: (stroke: ThemeStroke | null) => void;
}) {
  return (
    <div className="studio-stroke-editor">
      <span>{label}</span>
      <select
        onChange={(event) => {
          const token = event.target.value;
          onChange(token.length === 0 ? null : { token, widthDp: stroke?.widthDp ?? 1 });
        }}
        value={stroke?.token ?? ''}
      >
        <option value="">无</option>
        {tokenIds.map((tokenId) => (
          <option key={tokenId} value={tokenId}>
            {tokenId}
          </option>
        ))}
      </select>
      {stroke != null ? (
        <NumberField
          label="宽度"
          onChange={(widthDp) => onChange({ ...stroke, widthDp })}
          step={0.5}
          value={stroke.widthDp}
        />
      ) : null}
    </div>
  );
}

function parseFrameKind(value: string): FrameKind | null {
  return FRAME_KINDS.some((kind) => kind.id === value)
    ? FRAME_KINDS.find((kind) => kind.id === value)?.id ?? null
    : null;
}

function frameForKind(kind: FrameKind, current: ThemeComponentFrame): ThemeComponentFrame {
  if (kind === current.kind) {
    return current;
  }
  switch (kind) {
    case 'none':
      return { kind: 'none' };
    case 'round_rect':
      return { kind: 'round_rect', cornerRadiusDp: 8 };
    case 'cut_corners':
      return { kind: 'cut_corners', cutSizeDp: 6 };
    case 'hud_notched':
      return { kind: 'hud_notched', cutSizeDp: 6, notchWidthFraction: 0.28, notchDepthDp: 4 };
    case 'corner_brackets':
      return { kind: 'corner_brackets', cornerCutDp: 6, bracketLengthDp: 16 };
    case 'segmented_rail':
      return { kind: 'segmented_rail', cornerCutDp: 6, railInsetDp: 4, segmentLengthDp: 18 };
  }
}

function readComponentState(
  skin: ThemeComponentSkin,
  state: ComponentSkinState
): ThemeComponentStateSkin | null {
  switch (state) {
    case 'normal':
      return skin.normal;
    case 'disabled':
      return skin.disabled ?? null;
    case 'selected':
      return skin.selected ?? null;
    case 'focused':
      return skin.focused ?? null;
    case 'error':
      return skin.error ?? null;
  }
}

function writeComponentState(
  skin: ThemeComponentSkin,
  state: ComponentSkinState,
  value: ThemeComponentStateSkin
): ThemeComponentSkin {
  switch (state) {
    case 'normal':
      return { ...skin, normal: value };
    case 'disabled':
      return { ...skin, disabled: value };
    case 'selected':
      return { ...skin, selected: value };
    case 'focused':
      return { ...skin, focused: value };
    case 'error':
      return { ...skin, error: value };
  }
}

function cloneStateSkin(state: ThemeComponentStateSkin): ThemeComponentStateSkin {
  return {
    ...state,
    contentPadding: { ...state.contentPadding },
    frame: cloneFrame(state.frame)
  };
}

function cloneFrame(frame: ThemeComponentFrame): ThemeComponentFrame {
  switch (frame.kind) {
    case 'none':
      return { kind: 'none' };
    case 'round_rect':
      return { ...frame, border: frame.border == null ? frame.border : { ...frame.border } };
    case 'cut_corners':
      return {
        ...frame,
        accent: frame.accent == null ? frame.accent : { ...frame.accent },
        border: frame.border == null ? frame.border : { ...frame.border }
      };
    case 'hud_notched':
      return {
        ...frame,
        accent: frame.accent == null ? frame.accent : { ...frame.accent },
        border: frame.border == null ? frame.border : { ...frame.border }
      };
    case 'corner_brackets':
      return {
        ...frame,
        accent: frame.accent == null ? frame.accent : { ...frame.accent },
        border: frame.border == null ? frame.border : { ...frame.border }
      };
    case 'segmented_rail':
      return {
        ...frame,
        accent: frame.accent == null ? frame.accent : { ...frame.accent },
        border: frame.border == null ? frame.border : { ...frame.border }
      };
  }
}

function addFrameTokens(tokens: Set<string>, frame: ThemeComponentFrame): void {
  if (frame.kind === 'none') {
    return;
  }
  if (frame.border != null) {
    tokens.add(frame.border.token);
  }
  if (frame.kind !== 'round_rect' && frame.accent != null) {
    tokens.add(frame.accent.token);
  }
}
