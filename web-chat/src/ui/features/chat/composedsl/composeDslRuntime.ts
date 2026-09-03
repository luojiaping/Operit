import type { WebThemeSnapshot } from '../util/chatTypes';
import type { ComposeChildren, ComposeNode } from './composeDslTypes';
import { resolveM3SemanticScheme } from '../../../../shared/theme/baseScheme';

// Compose DSL 的浏览器执行环境：对齐 app OperitComposeDslBridge 暴露给
// screen 脚本的 ctx 契约（UI 注册表 + MaterialTheme + 状态钩子）。
// 首期白名单组件见 WHITELIST，未注册组件在渲染层显式报错

type NodeFactory = (props?: Record<string, unknown>, children?: ComposeChildren) => ComposeNode;

const COMPONENT_NAMES = [
  'Column',
  'Row',
  'Box',
  'Spacer',
  'Text',
  'Button',
  'Card',
  'Surface',
  'Icon',
  'Switch',
  'LinearProgressIndicator'
] as const;

export const DSL_COMPONENT_WHITELIST = new Set<string>(COMPONENT_NAMES);

function normalizeChildren(children: ComposeChildren): ComposeNode[] {
  if (children == null) {
    return [];
  }
  if (Array.isArray(children)) {
    return children.filter((child): child is ComposeNode => child != null);
  }
  return [children];
}

function createNodeFactory(type: string): NodeFactory {
  return (props, children) => ({
    type,
    props: props ?? {},
    children: normalizeChildren(children)
  });
}

export interface DslRuntimeState {
  values: Map<string, unknown>;
  bump: () => void;
}

// MaterialTheme.colorScheme 从主题快照 palette 映射，
// key 面对齐 app ComposeColorScheme 的动态字典访问
function buildColorScheme(theme: WebThemeSnapshot | null) {
  const palette = theme?.palette;
  if (!palette) {
    return {};
  }
  const m3 = resolveM3SemanticScheme(palette, theme?.theme_mode === 'light');
  return {
    primary: palette.primary_color,
    onPrimary: m3.on_primary_color,
    secondary: palette.secondary_color,
    onSecondary: m3.on_secondary_color,
    tertiary: m3.tertiary_color,
    error: m3.error_color,
    errorContainer: m3.error_container_color,
    onErrorContainer: m3.on_error_container_color,
    secondaryContainer: m3.secondary_container_color,
    primaryContainer: palette.primary_container_color,
    onPrimaryContainer: palette.on_primary_container_color,
    background: palette.background_color,
    onBackground: palette.on_surface_color,
    surface: palette.surface_color,
    onSurface: palette.on_surface_color,
    surfaceVariant: palette.surface_variant_color,
    onSurfaceVariant: palette.on_surface_variant_color,
    outline: palette.outline_color
  };
}

export function createDslContext(
  theme: WebThemeSnapshot | null,
  runtimeState: DslRuntimeState
) {
  const UI: Record<string, NodeFactory> = {};
  for (const name of COMPONENT_NAMES) {
    UI[name] = createNodeFactory(name);
  }

  const context = {
    UI,
    MaterialTheme: { colorScheme: buildColorScheme(theme) },
    useState<T>(key: string, initialValue: T): [T, (value: T) => void] {
      if (!runtimeState.values.has(key)) {
        runtimeState.values.set(key, initialValue);
      }
      const current = runtimeState.values.get(key) as T;
      const setValue = (next: T) => {
        runtimeState.values.set(key, next);
        runtimeState.bump();
      };
      return [current, setValue];
    },
    useMemo<T>(key: string, factory: () => T): T {
      if (!runtimeState.values.has(`__memo__${key}`)) {
        runtimeState.values.set(`__memo__${key}`, factory());
      }
      return runtimeState.values.get(`__memo__${key}`) as T;
    },
    package: {
      packageName: 'preview-studio'
    }
  };

  return context;
}

export type DslContext = ReturnType<typeof createDslContext>;

// 在浏览器执行 screen 模块源码并返回节点树。
// screen 是普通 JS（exports.default = function(ctx)），new Function 提供模块环境
export function executeScreenModule(source: string, ctx: DslContext): ComposeNode {
  const moduleExports: Record<string, unknown> = {};
  const module = { exports: moduleExports };
  const runner = new Function('ctx', 'exports', 'module', source);
  runner(ctx, moduleExports, module);
  const resolvedExports = (module.exports ?? moduleExports) as Record<string, unknown>;
  const factory = resolvedExports.default;
  if (typeof factory !== 'function') {
    throw new Error('compose dsl screen must export a default function');
  }
  const node = factory(ctx) as ComposeChildren;
  if (Array.isArray(node)) {
    throw new Error('compose dsl screen must return a single root node');
  }
  if (node == null) {
    throw new Error('compose dsl screen returned nothing');
  }
  return node;
}
