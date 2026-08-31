import { beforeEach, describe, expect, it } from 'vitest';
import { createMockChatTransportInternal } from '../src/ui/features/chat/util/mock/mockTransport';
import { createRealChatTransport } from '../src/ui/features/chat/util/chatTransport';

describe('MockTransport 端点行为', () => {
  const transport = createMockChatTransportInternal();
  const token = 'any';

  beforeEach(async () => {
    await transport.bootstrap(token);
  });

  it('bootstrap 返回主会话与能力集', async () => {
    const boot = await transport.bootstrap(token);
    expect(boot.current_chat_id).toBe('chat-main');
    expect(boot.capabilities.streaming).toBe(true);
  });

  it('listChats 提供三个预置会话且含锁定状态', async () => {
    const chats = await transport.listChats(token);
    expect(chats.map((chat) => chat.id)).toEqual(['chat-main', 'chat-light', 'chat-scenic']);
    expect(chats.find((chat) => chat.id === 'chat-scenic')?.locked).toBe(true);
  });

  it('各会话主题携带 web 扩展字段', async () => {
    const theme = await transport.getTheme(token, 'chat-scenic');
    expect(theme.background.use_blur).toBe(true);
    expect(theme.bubble.user_image.enabled).toBe(true);
    expect(theme.header.history_icon_color).toBe('#ffd28a');
  });

  it('getMessages 默认按时间升序返回', async () => {
    const page = await transport.getMessages(token, 'chat-main');
    const timestamps = page.messages.map((message) => message.timestamp);
    expect([...timestamps].sort((a, b) => a - b)).toEqual(timestamps);
  });

  it('updateChat 修改标题并返回新快照', async () => {
    const updated = await transport.updateChat(token, 'chat-main', { title: '改名后' });
    expect(updated.title).toBe('改名后');
  });

  it('RealTransport 形状与 MockTransport 一致（方法集合相等）', () => {
    const real = createRealChatTransport();
    const realKeys = Object.keys(real)
      .filter((key) => key !== 'ApiError')
      .sort();
    const mockKeys = Object.keys(transport).sort();
    expect(mockKeys).toEqual(realKeys);
  });
});
