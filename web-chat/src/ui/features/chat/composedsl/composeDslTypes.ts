// 与 examples/types/compose-dsl.d.ts 的公开契约对齐的精简类型面。
// 预览渲染器只消费结构，不重复声明全部 props 类型

export interface ComposeNode {
  type: string;
  props?: Record<string, unknown>;
  children?: ComposeNode[];
}

export type ComposeChildren = ComposeNode | ComposeNode[] | null | undefined;

// 插槽渲染结果，对应 app ChatInputSlotRenderResult 的两种形态；
// screen 源码由预览站直接提供（真机走 .toolpkg 内文件路径）
export type InputSlotContent =
  | { kind: 'text'; text: string }
  | { kind: 'dsl'; screenSource: string; state?: Record<string, unknown> };

// 预览站注入的 slot 内容表：slot 名 → 已解析内容
export type InputSlotContentMap = Partial<Record<string, InputSlotContent>>;

export const INPUT_SLOT_NAMES = ['above_input', 'input_drawer', 'input_toolbar_right'] as const;

export type InputSlotName = (typeof INPUT_SLOT_NAMES)[number];
