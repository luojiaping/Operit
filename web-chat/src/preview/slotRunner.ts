import type { InputSlotContent } from '../ui/features/chat/composedsl/composeDslTypes';

// main.js 注册执行器：提供最小 ToolPkg 宿主环境，
// 收集 registerInputSlotPlugin 声明后由面板逐 slot 调用 render

export interface SlotRegistration {
  id: string;
  slot: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  render: (event: { eventPayload: Record<string, unknown> }) => any;
}

export interface SlotRunnerPayload {
  slot: string;
  chatId: string | null;
  runtime: string;
  inputStyle: string;
  isProcessing: boolean;
  isInputFocused: boolean;
  inputText: string;
}

export function runToolPkgMain(source: string): SlotRegistration[] {
  const registrations: SlotRegistration[] = [];
  const ToolPkg = {
    registerInputSlotPlugin(registration: {
      id: string;
      slot: string;
      function: (event: { eventPayload: Record<string, unknown> }) => unknown;
    }) {
      if (typeof registration?.id !== 'string' || typeof registration?.slot !== 'string') {
        throw new Error('registerInputSlotPlugin 需要 id 与 slot 字符串');
      }
      if (typeof registration.function !== 'function') {
        throw new Error('registerInputSlotPlugin 的 function 必须是函数');
      }
      registrations.push({
        id: registration.id,
        slot: registration.slot,
        render: registration.function
      });
    }
  };
  const moduleExports: Record<string, unknown> = {};
  const module = { exports: moduleExports };
  const runner = new Function('ToolPkg', 'exports', 'module', source);
  runner(ToolPkg, moduleExports, module);
  const resolved = (module.exports ?? moduleExports) as Record<string, unknown>;
  const registerToolPkg = resolved.registerToolPkg;
  if (typeof registerToolPkg !== 'function') {
    throw new Error('main.js 必须导出 registerToolPkg 函数');
  }
  const result = registerToolPkg();
  if (result === false) {
    throw new Error('registerToolPkg 返回 false，注册未完成');
  }
  return registrations;
}

// render 返回值 → InputSlotContent，对齐 ToolPkgInputSlotBridge 的结果解析：
// 字符串、{text|content}、{handled:false}、{composeDsl:{screen,state}}
export function resolveSlotContent(
  renderResult: unknown,
  resolveScreenSource: (screenPath: string) => string
): InputSlotContent | null {
  if (renderResult == null) {
    return null;
  }
  if (typeof renderResult === 'string') {
    return renderResult.length > 0 ? { kind: 'text', text: renderResult } : null;
  }
  if (typeof renderResult !== 'object') {
    throw new Error(`render 返回了不支持的类型：${typeof renderResult}`);
  }
  const result = renderResult as Record<string, unknown>;
  if (result.handled === false) {
    return null;
  }
  const composeDsl = result.composeDsl as
    | { screen?: unknown; state?: unknown }
    | undefined;
  if (composeDsl && typeof composeDsl === 'object' && typeof composeDsl.screen === 'string') {
    const screenSource = resolveScreenSource(composeDsl.screen);
    return {
      kind: 'dsl',
      screenSource,
      state:
        composeDsl.state && typeof composeDsl.state === 'object'
          ? (composeDsl.state as Record<string, unknown>)
          : undefined
    };
  }
  for (const key of ['text', 'content']) {
    const value = result[key];
    if (typeof value === 'string' && value.length > 0) {
      return { kind: 'text', text: value };
    }
  }
  throw new Error('render 返回对象缺少 text/content 或 composeDsl.screen');
}

export function buildRunnerPayload(
  base: Pick<SlotRunnerPayload, 'chatId' | 'inputStyle' | 'isProcessing' | 'inputText'>,
  slot: string
): SlotRunnerPayload {
  return {
    slot,
    chatId: base.chatId,
    runtime: 'MAIN',
    inputStyle: base.inputStyle,
    isProcessing: base.isProcessing,
    isInputFocused: false,
    inputText: base.inputText
  };
}
