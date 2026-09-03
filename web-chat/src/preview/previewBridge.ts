import type { WebThemeSnapshot } from '../ui/features/chat/util/chatTypes';
import type { InputSlotContentMap } from '../ui/features/chat/composedsl/composeDslTypes';
import { isThemeTargetId } from './themeTargets';
import type { ThemeTargetId } from './themeTargets';

// 预览站桥协议：外层工作台（面板所在文档）与手机壳内 iframe（独立手机视口）通信。
// parent → iframe：preview-theme / preview-slots / preview-highlight
// iframe → parent：preview-ready / preview-state / preview-selection
export type PreviewBridgeMessage =
  | { type: 'preview-ready' }
  | {
      type: 'preview-state';
      chatId: string | null;
      inputStyle: string;
      isProcessing: boolean;
      inputText: string;
    }
  | { type: 'preview-selection'; target: ThemeTargetId }
  | { type: 'preview-theme'; theme: WebThemeSnapshot }
  | { type: 'preview-slots'; contents: InputSlotContentMap }
  | { type: 'preview-highlight'; target: ThemeTargetId | null };

export function isPreviewBridgeMessage(value: unknown): value is PreviewBridgeMessage {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const type = (value as { type?: unknown }).type;
  if (
    type === 'preview-ready' ||
    type === 'preview-state' ||
    type === 'preview-theme' ||
    type === 'preview-slots'
  ) {
    return true;
  }
  if (type === 'preview-selection') {
    const target = (value as { target?: unknown }).target;
    return typeof target === 'string' && isThemeTargetId(target);
  }
  if (type === 'preview-highlight') {
    const target = (value as { target?: unknown }).target;
    return target === null || (typeof target === 'string' && isThemeTargetId(target));
  }
  return false;
}
