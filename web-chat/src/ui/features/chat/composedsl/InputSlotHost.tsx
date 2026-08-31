import { createContext, useContext } from 'react';
import type { ReactNode } from 'react';
import type { InputSlotContent, InputSlotContentMap, InputSlotName } from './composeDslTypes';
import { ComposeDslScreenView } from './ComposeDslRenderer';

// 预览站的插槽内容注入通道。真机入口不提供 Provider，
// context 为空表时插槽不渲染，真机 web-chat 行为保持不变
const PreviewSlotContext = createContext<InputSlotContentMap>({});

export function PreviewSlotProvider({
  contents,
  children
}: {
  contents: InputSlotContentMap;
  children: ReactNode;
}) {
  return <PreviewSlotContext.Provider value={contents}>{children}</PreviewSlotContext.Provider>;
}

// app 侧 ChatInputSlotPluginRegistry.RenderSlot 的等价物：
// Text 结果按 bodySmall + onSurface 渲染，DSL 结果走浏览器渲染器。
// inputText 不触发重解析的语义由内容方保证，宿主只负责挂载
export function InputSlotHost({
  slot,
  layout,
  theme
}: {
  slot: InputSlotName;
  layout: 'above-input-agent' | 'above-input-classic' | 'drawer' | 'toolbar-right';
  theme: import('../util/chatTypes').WebThemeSnapshot | null;
}) {
  const contents = useContext(PreviewSlotContext);
  const content: InputSlotContent | undefined = contents[slot];
  if (!content) {
    return null;
  }

  if (content.kind === 'text') {
    return <div className={`input-slot-host input-slot-text is-${layout}`}>{content.text}</div>;
  }

  return (
    <div className={`input-slot-host input-slot-dsl is-${layout}`}>
      <ComposeDslScreenView
        screenSource={content.screenSource}
        state={content.state}
        theme={theme}
      />
    </div>
  );
}
