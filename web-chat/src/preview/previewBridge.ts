import type { WebThemeSnapshot } from '../ui/features/chat/util/chatTypes';
import type { InputSlotContentMap } from '../ui/features/chat/composedsl/composeDslTypes';

// 预览站桥协议：外层工作台（面板所在文档）与手机壳内 iframe（独立手机视口）通信。
// parent → iframe：preview-theme / preview-slots
// iframe → parent：preview-ready / preview-state
export type PreviewBridgeMessage =
  | { type: 'preview-ready' }
  | {
      type: 'preview-state';
      chatId: string | null;
      inputStyle: string;
      isProcessing: boolean;
      inputText: string;
    }
  | { type: 'preview-theme'; theme: WebThemeSnapshot }
  | { type: 'preview-slots'; contents: InputSlotContentMap };

export function isPreviewBridgeMessage(value: unknown): value is PreviewBridgeMessage {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const type = (value as { type?: unknown }).type;
  return (
    type === 'preview-ready' ||
    type === 'preview-state' ||
    type === 'preview-theme' ||
    type === 'preview-slots'
  );
}
