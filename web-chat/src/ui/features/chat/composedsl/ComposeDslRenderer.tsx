import { useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import type { WebThemeSnapshot } from '../util/chatTypes';
import type { ComposeChildren, ComposeNode } from './composeDslTypes';
import {
  DSL_COMPONENT_WHITELIST,
  createDslContext,
  executeScreenModule
} from './composeDslRuntime';

// ComposeNode → React 渲染器。布局与字号按 Compose dp 语义映射到 px，
// 属于预览近似：组件间距与真机 Compose 存在亚像素级差异

const TYPOGRAPHY: Record<string, CSSProperties> = {
  labelLarge: { fontSize: 14, fontWeight: 500, lineHeight: '20px' },
  labelMedium: { fontSize: 12, fontWeight: 500, lineHeight: '16px' },
  labelSmall: { fontSize: 11, fontWeight: 500, lineHeight: '16px' },
  bodySmall: { fontSize: 12, fontWeight: 400, lineHeight: '16px' },
  bodyMedium: { fontSize: 14, fontWeight: 400, lineHeight: '20px' },
  bodyLarge: { fontSize: 16, fontWeight: 400, lineHeight: '24px' },
  titleSmall: { fontSize: 14, fontWeight: 500, lineHeight: '20px' },
  titleMedium: { fontSize: 16, fontWeight: 500, lineHeight: '24px' },
  titleLarge: { fontSize: 22, fontWeight: 400, lineHeight: '28px' },
  headlineSmall: { fontSize: 24, fontWeight: 400, lineHeight: '32px' },
  headlineMedium: { fontSize: 28, fontWeight: 400, lineHeight: '36px' }
};

const ALIGN_ITEMS: Record<string, CSSProperties> = {
  start: { alignItems: 'flex-start' },
  center: { alignItems: 'center' },
  'centerHorizontally': { alignItems: 'center' },
  end: { alignItems: 'flex-end' },
  left: { alignItems: 'flex-start' },
  right: { alignItems: 'flex-end' }
};

const JUSTIFY_CONTENT: Record<string, CSSProperties> = {
  start: { justifyContent: 'flex-start' },
  center: { justifyContent: 'center' },
  end: { justifyContent: 'flex-end' },
  'spaceBetween': { justifyContent: 'space-between' },
  'spaceAround': { justifyContent: 'space-around' },
  'spaceEvenly': { justifyContent: 'space-evenly' }
};

type Props = Record<string, unknown>;

function num(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function str(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

function bool(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
}

function paddingStyle(props: Props): CSSProperties {
  const all = num(props.padding);
  if (all !== undefined) {
    return { padding: `${all}px` };
  }
  const style: CSSProperties = {};
  const horizontal = num(props.paddingHorizontal);
  const vertical = num(props.paddingVertical);
  if (horizontal !== undefined) {
    style.paddingLeft = horizontal;
    style.paddingRight = horizontal;
  }
  if (vertical !== undefined) {
    style.paddingTop = vertical;
    style.paddingBottom = vertical;
  }
  const start = num(props.paddingStart ?? props.paddingLeft);
  const end = num(props.paddingEnd ?? props.paddingRight);
  const top = num(props.paddingTop);
  const bottom = num(props.paddingBottom);
  if (start !== undefined) {
    style.paddingLeft = start;
  }
  if (end !== undefined) {
    style.paddingRight = end;
  }
  if (top !== undefined) {
    style.paddingTop = top;
  }
  if (bottom !== undefined) {
    style.paddingBottom = bottom;
  }
  return style;
}

function commonStyle(props: Props): CSSProperties {
  const style: CSSProperties = {
    ...paddingStyle(props),
    boxSizing: 'border-box'
  };
  if (bool(props.fillMaxWidth) || bool(props.fillMaxSize)) {
    style.width = '100%';
  }
  if (bool(props.fillMaxHeight) || bool(props.fillMaxSize)) {
    style.height = '100%';
  }
  const width = num(props.width);
  const height = num(props.height);
  if (width !== undefined) {
    style.width = width;
  }
  if (height !== undefined) {
    style.height = height;
  }
  const background = str(props.background ?? props.backgroundColor ?? props.containerColor);
  if (background) {
    style.background = background;
  }
  const zIndexValue = num(props.zIndex);
  if (zIndexValue !== undefined) {
    style.zIndex = zIndexValue;
  }
  return style;
}

function textColorOf(props: Props, fallback: string) {
  return str(props.color) ?? fallback;
}

function NodeText({ node }: { node: ComposeNode }) {
  const props = node.props ?? {};
  const styleName = str(props.style);
  const typography = styleName ? TYPOGRAPHY[styleName] : undefined;
  const style: CSSProperties = {
    ...typography,
    ...commonStyle(props),
    color: textColorOf(props, 'var(--chat-text-main)'),
    margin: 0
  };
  const fontSize = num(props.fontSize);
  const lineHeight = num(props.lineHeight);
  const fontWeight = str(props.fontWeight);
  if (fontSize !== undefined) {
    style.fontSize = fontSize;
  }
  if (lineHeight !== undefined) {
    style.lineHeight = `${lineHeight}px`;
  }
  if (fontWeight) {
    style.fontWeight = fontWeight === 'bold' ? 700 : Number(fontWeight) || 500;
  }
  const maxLines = num(props.maxLines);
  if (maxLines !== undefined) {
    style.display = '-webkit-box';
    style.WebkitLineClamp = maxLines;
    style.WebkitBoxOrient = 'vertical';
    style.overflow = 'hidden';
  }
  const text = str(props.text) ?? '';
  const textAlign = str(props.textAlign);
  if (textAlign === 'left' || textAlign === 'right' || textAlign === 'center') {
    style.textAlign = textAlign;
  }
  return <span style={style}>{text}</span>;
}

function NodeButton({ node }: { node: ComposeNode }) {
  const props = node.props ?? {};
  const onClick = typeof props.onClick === 'function' ? (props.onClick as () => void) : undefined;
  const enabled = bool(props.enabled) ?? true;
  return (
    <button
      className="dsl-button"
      disabled={!enabled}
      onClick={onClick}
      style={commonStyle(props)}
      type="button"
    >
      {node.children?.map((child, index) => (
        <ComposeNodeView key={index} node={child} />
      ))}
      {str(props.text)}
    </button>
  );
}

function NodeSwitch({ node }: { node: ComposeNode }) {
  const props = node.props ?? {};
  const checked = bool(props.checked) ?? false;
  const onCheckedChange =
    typeof props.onCheckedChange === 'function'
      ? (props.onCheckedChange as (checked: boolean) => void)
      : undefined;
  return (
    <input
      checked={checked}
      onChange={onCheckedChange ? (event) => onCheckedChange(event.target.checked) : undefined}
      style={commonStyle(props)}
      type="checkbox"
    />
  );
}

function NodeProgress({ node }: { node: ComposeNode }) {
  const props = node.props ?? {};
  const progress = Math.max(0, Math.min(1, num(props.progress) ?? 0));
  return (
    <div className="dsl-progress" style={commonStyle(props)}>
      <div className="dsl-progress-bar" style={{ width: `${progress * 100}%` }} />
    </div>
  );
}

function NodeIcon({ node }: { node: ComposeNode }) {
  const props = node.props ?? {};
  const size = num(props.size) ?? 18;
  const tint = str(props.tint) ?? 'var(--chat-primary)';
  // 图标库映射是预览近似的边界：未知 icon 名渲染为实心圆点
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'inline-block',
        width: size,
        height: size,
        borderRadius: '50%',
        background: tint
      }}
      title={str(props.name) ?? 'icon'}
    />
  );
}

function layoutChildren(node: ComposeNode) {
  return node.children?.map((child, index) => <ComposeNodeView key={index} node={child} />);
}

function ComposeNodeView({ node }: { node: ComposeNode }) {
  const props = node.props ?? {};
  const onLoad = typeof props.onLoad === 'function' ? (props.onLoad as () => void) : undefined;
  useEffect(() => {
    onLoad?.();
  }, [onLoad]);

  switch (node.type) {
    case 'Column':
    case 'LazyColumn': {
      const horizontalAlignment = str(props.horizontalAlignment);
      const style: CSSProperties = {
        display: 'flex',
        flexDirection: 'column',
        gap: num(props.spacing) ?? 0,
        ...commonStyle(props),
        ...(horizontalAlignment ? ALIGN_ITEMS[horizontalAlignment] : null)
      };
      return <div style={style}>{layoutChildren(node)}</div>;
    }
    case 'Row': {
      const verticalAlignment = str(props.verticalAlignment);
      const horizontalArrangement = str(props.horizontalArrangement);
      const style: CSSProperties = {
        display: 'flex',
        flexDirection: 'row',
        gap: num(props.spacing) ?? 0,
        ...commonStyle(props),
        ...(verticalAlignment ? ALIGN_ITEMS[verticalAlignment] : null),
        ...(horizontalArrangement ? JUSTIFY_CONTENT[horizontalArrangement] : null)
      };
      return <div style={style}>{layoutChildren(node)}</div>;
    }
    case 'Box': {
      const style: CSSProperties = {
        position: 'relative',
        ...commonStyle(props)
      };
      return <div style={style}>{layoutChildren(node)}</div>;
    }
    case 'Spacer': {
      const style: CSSProperties = {};
      const width = num(props.width);
      const height = num(props.height);
      if (width !== undefined) {
        style.width = width;
      }
      if (height !== undefined) {
        style.height = height;
      }
      return <div style={style} />;
    }
    case 'Text':
      return <NodeText node={node} />;
    case 'Button':
      return <NodeButton node={node} />;
    case 'Card': {
      const style: CSSProperties = {
        borderRadius: 12,
        ...commonStyle(props),
        boxShadow: `0 ${Math.max(0, num(props.elevation) ?? 0)}px ${
          (num(props.elevation) ?? 0) * 2 + 2
        }px var(--chat-shadow)`
      };
      return <div style={style}>{layoutChildren(node)}</div>;
    }
    case 'Surface': {
      const style: CSSProperties = {
        borderRadius: 12,
        ...commonStyle(props)
      };
      return <div style={style}>{layoutChildren(node)}</div>;
    }
    case 'Icon':
      return <NodeIcon node={node} />;
    case 'Switch':
      return <NodeSwitch node={node} />;
    case 'LinearProgressIndicator':
      return <NodeProgress node={node} />;
    default: {
      if (DSL_COMPONENT_WHITELIST.has(node.type)) {
        break;
      }
      // 白名单外的组件显式报错而不是静默丢弃，让插件作者立刻看到能力边界
      return (
        <div className="dsl-unsupported">
          预览暂不支持组件 {node.type}（白名单：{' '}
          {[...DSL_COMPONENT_WHITELIST].join(', ')}）
        </div>
      );
    }
  }
  return <div style={commonStyle(props)}>{layoutChildren(node)}</div>;
}

export function ComposeDslScreenView({
  screenSource,
  state,
  theme
}: {
  screenSource: string;
  state?: Record<string, unknown>;
  theme: WebThemeSnapshot | null;
}) {
  const [, setRevision] = useState(0);
  const runtimeState = useMemo(
    () => ({
      values: new Map<string, unknown>(Object.entries(state ?? {})),
      bump: () => setRevision((revision) => revision + 1)
    }),
    // state 仅作为首次执行初始值；重渲染保持实例
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [screenSource]
  );

  const ctx = useMemo(() => createDslContext(theme, runtimeState), [theme, runtimeState]);
  const node = useMemo(() => executeScreenModule(screenSource, ctx), [screenSource, ctx]);

  return (
    <div className="dsl-screen-root">
      <ComposeNodeView node={node} />
    </div>
  );
}
