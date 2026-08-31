import * as chatApi from './chatApi';
import { createMockChatTransportInternal } from './mock/mockTransport';

// 以真实现的模块形状作为契约：RealTransport 直接复用 chatApi，
// MockTransport 用同一形状在内存中模拟全部端点与 SSE 流。
export type ChatTransportApi = Omit<typeof chatApi, 'ApiError'>;

export const MOCK_TOKEN = 'mock';

export function isMockMode() {
  if (typeof window === 'undefined') {
    return false;
  }
  return new URLSearchParams(window.location.search).has('mock');
}

export function createRealChatTransport(): ChatTransportApi {
  return chatApi;
}

export function createMockChatTransport(): ChatTransportApi {
  return createMockChatTransportInternal();
}

export function resolveChatTransport(): ChatTransportApi {
  return isMockMode() ? createMockChatTransport() : createRealChatTransport();
}
