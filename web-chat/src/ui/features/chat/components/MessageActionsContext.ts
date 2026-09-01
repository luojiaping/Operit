import { createContext, useContext } from 'react';
import type { WebChatMessage } from '../util/chatTypes';

// 消息级操作的注入通道（预览站 mock 模式由 ChatScreenContent 提供，
// 真机入口不提供 Provider，MessageFooterBar 拿到 null 时按钮不响应）
export interface MessageActions {
  selectVariant: (message: WebChatMessage, variantIndex: number) => void;
}

export const MessageActionsContext = createContext<MessageActions | null>(null);

export function useMessageActions() {
  return useContext(MessageActionsContext);
}
