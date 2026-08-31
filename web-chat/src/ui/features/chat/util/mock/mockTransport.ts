import type {
  WebActivePromptTarget,
  WebChatMessage,
  WebChatMessagesPage,
  WebChatStreamEvent,
  WebCharacterSelectorResponse,
  WebChatSummary,
  WebInputSettingsState,
  WebMemorySelectorState,
  WebModelSelectorState,
  WebSelectModelResponse,
  WebThemeSnapshot,
  WebUploadedAttachment
} from '../chatTypes';
import type { ChatTransportApi } from '../chatTransport';
import {
  MOCK_BOOTSTRAP,
  MOCK_CHARACTER_SELECTOR,
  MOCK_CHATS,
  MOCK_INPUT_SETTINGS,
  MOCK_MEMORY_SELECTOR,
  MOCK_MESSAGES,
  MOCK_MODEL_SELECTOR,
  MOCK_STREAM_REPLY,
  MOCK_THEMES
} from './fixtures';

const STREAM_CHUNK_INTERVAL_MS = 40;
const STREAM_CHUNK_SIZE = 18;

function cloneChats(chats: WebChatSummary[]): WebChatSummary[] {
  return chats.map((chat) => ({ ...chat }));
}

// 模块级单例状态：transport 实例与 previewControls 共享，
// 同一页面重复创建实例也保持数据一致
const state = {
  chats: cloneChats(MOCK_CHATS),
  messagesByChat: new Map<string, WebChatMessage[]>(
    Object.entries(MOCK_MESSAGES).map(([chatId, messages]) => [
      chatId,
      messages.map((m) => ({ ...m }))
    ])
  ),
  themesByChat: new Map<string, WebThemeSnapshot>(Object.entries(MOCK_THEMES)),
  characterState: {
    active_prompt: { ...MOCK_CHARACTER_SELECTOR.active_prompt },
    cards: MOCK_CHARACTER_SELECTOR.cards.map((c) => ({ ...c })),
    groups: MOCK_CHARACTER_SELECTOR.groups.map((g) => ({ ...g }))
  } as WebCharacterSelectorResponse,
  modelState: JSON.parse(JSON.stringify(MOCK_MODEL_SELECTOR)) as WebModelSelectorState,
  memoryState: JSON.parse(JSON.stringify(MOCK_MEMORY_SELECTOR)) as WebMemorySelectorState,
  inputSettingsState: { ...MOCK_INPUT_SETTINGS } as WebInputSettingsState,
  currentChatId: 'chat-main' as string | null,
  messageIdSeq: 100
};

// 预览站控制接口：SimulatorShell 用来实时覆盖 mock 主题，
// patch 后由调用方触发会话重载来刷新界面
export const previewControls = {
  patchTheme(patch: Partial<WebThemeSnapshot>) {
    const chatId = state.currentChatId ?? 'chat-main';
    const theme = state.themesByChat.get(chatId);
    if (!theme) {
      throw new Error(`mock: cannot patch theme, chat not found: ${chatId}`);
    }
    state.themesByChat.set(chatId, { ...theme, ...patch });
  },
  resetTheme() {
    for (const [chatId, theme] of Object.entries(MOCK_THEMES)) {
      state.themesByChat.set(chatId, JSON.parse(JSON.stringify(theme)) as WebThemeSnapshot);
    }
  }
};

// 内存状态机：全部端点与 SSE 流的 mock 实现，状态保存在上方模块级单例。
// token 参数为形状兼容保留，mock 不做鉴权
export function createMockChatTransportInternal(): ChatTransportApi {
  const { chats, messagesByChat, themesByChat } = state;
  const characterState = state.characterState;
  const modelState = state.modelState;
  const memoryState = state.memoryState;
  const inputSettingsState = state.inputSettingsState;

  function messagesOf(chatId: string): WebChatMessage[] {
    const list = messagesByChat.get(chatId);
    if (list) {
      return list;
    }
    const created: WebChatMessage[] = [];
    messagesByChat.set(chatId, created);
    return created;
  }

  function chatById(chatId: string): WebChatSummary {
    const chat = chats.find((item) => item.id === chatId);
    if (!chat) {
      throw new Error(`mock: chat not found: ${chatId}`);
    }
    return chat;
  }

  function sortMessagesAsc(messages: WebChatMessage[]) {
    return [...messages].sort((a, b) => a.timestamp - b.timestamp);
  }

  function applyChatUpdate(
    chatId: string,
    payload: {
      title?: string;
      group?: string | null;
      update_group?: boolean;
      locked?: boolean;
      update_locked?: boolean;
      character_card_name?: string | null;
      character_group_id?: string | null;
      update_binding?: boolean;
    }
  ): WebChatSummary {
    const chat = chatById(chatId);
    if (payload.title !== undefined) {
      chat.title = payload.title;
    }
    if (payload.update_group && payload.group !== undefined) {
      chat.group = payload.group;
    }
    if (payload.update_locked && payload.locked !== undefined) {
      chat.locked = payload.locked;
    }
    if (payload.update_binding) {
      chat.character_card_name = payload.character_card_name ?? null;
      chat.character_group_id = payload.character_group_id ?? null;
    }
    chat.updated_at = Date.now();
    return { ...chat };
  }

  function pageOf(messages: WebChatMessage[], limit?: number): WebChatMessagesPage {
    const sorted = sortMessagesAsc(messages);
    if (typeof limit === 'number' && limit > 0 && sorted.length > limit) {
      const window = sorted.slice(sorted.length - limit);
      return {
        messages: window,
        has_more_before: true,
        has_more_after: false,
        next_before_timestamp: window[0]?.timestamp ?? null,
        next_after_timestamp: null
      };
    }
    return {
      messages: sorted,
      has_more_before: false,
      has_more_after: false,
      next_before_timestamp: null,
      next_after_timestamp: null
    };
  }

  return {
    async bootstrap() {
      return { ...MOCK_BOOTSTRAP, current_chat_id: state.currentChatId };
    },

    async getCharacterSelector() {
      return JSON.parse(JSON.stringify(characterState));
    },

    async setActivePrompt(_token: string, target: WebActivePromptTarget) {
      if (target.type === 'character_group') {
        const group = characterState.groups.find((item) => item.id === target.id);
        if (!group) {
          throw new Error(`mock: group not found: ${target.id}`);
        }
        characterState.active_prompt = {
          type: 'character_group',
          id: group.id,
          name: group.name,
          avatar_url: group.avatar_url
        };
      } else {
        const card = characterState.cards.find((item) => item.id === target.id);
        if (!card) {
          throw new Error(`mock: card not found: ${target.id}`);
        }
        characterState.active_prompt = {
          type: 'character_card',
          id: card.id,
          name: card.name,
          avatar_url: card.avatar_url
        };
      }
      return JSON.parse(JSON.stringify(characterState));
    },

    async getModelSelector() {
      return JSON.parse(JSON.stringify(modelState));
    },

    async selectModel(
      _token: string,
      payload: { config_id: string; model_index: number; confirm_character_card_switch?: boolean }
    ): Promise<WebSelectModelResponse> {
      const config = modelState.configs.find((item) => item.id === payload.config_id);
      if (!config) {
        throw new Error(`mock: model config not found: ${payload.config_id}`);
      }
      const modelIndex = Math.max(0, Math.min(payload.model_index, config.models.length - 1));
      for (const item of modelState.configs) {
        item.selected = item.id === config.id;
        item.selected_model_index = item.id === config.id ? modelIndex : item.selected_model_index;
      }
      modelState.current_config_id = config.id;
      modelState.current_config_name = config.name;
      modelState.current_model_index = modelIndex;
      modelState.current_model_name = config.models[modelIndex];
      return {
        success: true,
        requires_character_card_switch_confirmation: false,
        selector: JSON.parse(JSON.stringify(modelState))
      };
    },

    async listChats() {
      return cloneChats(chats);
    },

    async createChat(
      _token: string,
      payload?: {
        title?: string;
        group?: string | null;
        character_card_name?: string | null;
        character_group_id?: string | null;
        set_current?: boolean;
      }
    ): Promise<WebChatSummary> {
      const chatId = `chat-mock-${++state.messageIdSeq}`;
      const chat: WebChatSummary = {
        id: chatId,
        title: payload?.title?.trim() || `新会话 ${state.messageIdSeq}`,
        updated_at: Date.now(),
        group: payload?.group ?? null,
        character_card_name: payload?.character_card_name ?? null,
        character_group_id: payload?.character_group_id ?? null,
        character_group_name: null,
        binding_avatar_url: null,
        parent_chat_id: null,
        active_streaming: false,
        locked: false
      };
      chats.unshift(chat);
      messagesByChat.set(chatId, []);
      if (state.currentChatId !== null) {
        const inheritedTheme = themesByChat.get(state.currentChatId);
        if (inheritedTheme) {
          themesByChat.set(chatId, inheritedTheme);
        }
      }
      if (payload?.set_current !== false) {
        state.currentChatId = chatId;
      }
      return { ...chat };
    },

    async renameChat(_token: string, chatId: string, title: string) {
      return applyChatUpdate(chatId, { title });
    },

    async updateChat(_token: string, chatId: string, payload: Parameters<typeof applyChatUpdate>[1]) {
      return applyChatUpdate(chatId, payload);
    },

    async deleteChat(_token: string, chatId: string) {
      const index = chats.findIndex((item) => item.id === chatId);
      if (index >= 0) {
        chats.splice(index, 1);
      }
      messagesByChat.delete(chatId);
      themesByChat.delete(chatId);
      if (state.currentChatId === chatId) {
        state.currentChatId = chats[0]?.id ?? null;
      }
    },

    async selectChat(_token: string, chatId: string) {
      chatById(chatId);
      state.currentChatId = chatId;
    },

    async getMessages(
      _token: string,
      chatId: string,
      options?: { limit?: number; beforeTimestamp?: number | null; afterTimestamp?: number | null }
    ): Promise<WebChatMessagesPage> {
      const messages = messagesOf(chatId);
      const before = options?.beforeTimestamp;
      const after = options?.afterTimestamp;
      if (typeof before === 'number') {
        const older = sortMessagesAsc(messages).filter((m) => m.timestamp < before);
        const window = older.slice(Math.max(0, older.length - (options?.limit ?? 24)));
        return {
          messages: window,
          has_more_before: window.length > 0 && older.length > window.length,
          has_more_after: true,
          next_before_timestamp: window[0]?.timestamp ?? null,
          next_after_timestamp: null
        };
      }
      if (typeof after === 'number') {
        const newer = sortMessagesAsc(messages).filter((m) => m.timestamp > after);
        const window = newer.slice(0, options?.limit ?? 24);
        return {
          messages: window,
          has_more_before: true,
          has_more_after: window.length > 0 && newer.length > window.length,
          next_before_timestamp: null,
          next_after_timestamp: window[window.length - 1]?.timestamp ?? null
        };
      }
      return pageOf(messages, options?.limit);
    },

    async getMessageLocatorEntries(_token: string, chatId: string, query = '') {
      const normalized = query.trim();
      return sortMessagesAsc(messagesOf(chatId))
        .filter((message) => {
          if (!normalized) {
            return true;
          }
          const content = message.display_content ?? message.content_raw;
          return content.includes(normalized);
        })
        .map((message) => ({
          message_index: null,
          timestamp: message.timestamp,
          sender: message.sender,
          preview_content: (message.display_content ?? message.content_raw).slice(0, 48),
          content_length: (message.display_content ?? message.content_raw).length,
          display_mode: 'TEXT',
          is_favorite: false
        }));
    },

    async revealMessageWindow(_token: string, chatId: string, timestamp: number) {
      const sorted = sortMessagesAsc(messagesOf(chatId));
      const anchorIndex = sorted.findIndex((m) => m.timestamp === timestamp);
      const anchor = anchorIndex >= 0 ? anchorIndex : sorted.length - 1;
      const half = 12;
      const start = Math.max(0, anchor - half);
      const window = sorted.slice(start, Math.min(sorted.length, anchor + half));
      return {
        messages: window,
        has_more_before: start > 0,
        has_more_after: start + window.length < sorted.length,
        next_before_timestamp: window[0]?.timestamp ?? null,
        next_after_timestamp: window[window.length - 1]?.timestamp ?? null
      };
    },

    async toggleMessageFavorite(_token: string, _chatId: string, _timestamp: number, _isFavorite: boolean) {},

    async reorderChats(_token: string, items: { chat_id: string; display_order: number; group?: string | null }[]) {
      const orderById = new Map(items.map((item) => [item.chat_id, item.display_order]));
      chats.sort((a, b) => {
        const orderA = orderById.get(a.id);
        const orderB = orderById.get(b.id);
        if (orderA !== undefined && orderB !== undefined) {
          return orderA - orderB;
        }
        return 0;
      });
    },

    async renameGroup() {},

    async deleteGroup(_token: string, payload: { group_name: string; delete_chats: boolean }) {
      if (payload.delete_chats) {
        for (let i = chats.length - 1; i >= 0; i--) {
          if (chats[i].group === payload.group_name) {
            messagesByChat.delete(chats[i].id);
            themesByChat.delete(chats[i].id);
            chats.splice(i, 1);
          }
        }
      } else {
        for (const chat of chats) {
          if (chat.group === payload.group_name) {
            chat.group = null;
          }
        }
      }
    },

    async getInputSettings() {
      return { ...inputSettingsState };
    },

    async getMemorySelector() {
      return JSON.parse(JSON.stringify(memoryState));
    },

    async selectMemoryProfile(_token: string, profileId: string): Promise<WebMemorySelectorState> {
      if (!memoryState.profiles.some((profile) => profile.id === profileId)) {
        throw new Error(`mock: memory profile not found: ${profileId}`);
      }
      memoryState.current_profile_id = profileId;
      return JSON.parse(JSON.stringify(memoryState));
    },

    async updateInputSettings(
      _token: string,
      payload: Partial<{
        enable_thinking_mode: boolean;
        thinking_option_id: string;
        enable_memory_auto_update: boolean;
        enable_auto_read: boolean;
        enable_max_context_mode: boolean;
        enable_tools: boolean;
        disable_stream_output: boolean;
        disable_user_preference_description: boolean;
        permission_level: string;
      }>
    ): Promise<WebInputSettingsState> {
      Object.assign(inputSettingsState, payload);
      return { ...inputSettingsState };
    },

    async runManualMemoryUpdate() {},

    async runManualConversationSummary() {},

    async getTheme(_token: string, chatId: string) {
      const theme = themesByChat.get(chatId) ?? themesByChat.get('chat-main');
      if (!theme) {
        throw new Error(`mock: theme not found for chat: ${chatId}`);
      }
      return JSON.parse(JSON.stringify(theme));
    },

    async uploadAttachment(_token: string, file: File): Promise<WebUploadedAttachment> {
      return {
        attachment_id: `mock-upload-${++state.messageIdSeq}`,
        file_name: file.name,
        mime_type: file.type || 'application/octet-stream',
        file_size: file.size
      };
    },

    async streamMessage(
      _token: string,
      chatId: string,
      payload: { message: string; attachment_ids: string[]; return_tool_status: boolean },
      callbacks: { onEvent: (event: WebChatStreamEvent) => void },
      signal: AbortSignal
    ): Promise<void> {
      const messages = messagesOf(chatId);
      const now = Date.now();
      const userMessage: WebChatMessage = {
        id: `msg-mock-user-${++state.messageIdSeq}`,
        sender: 'user',
        content_raw: payload.message,
        timestamp: now,
        attachments: [],
        input_tokens: Math.max(1, Math.round(payload.message.length / 4)),
        cached_input_tokens: 0,
        output_tokens: 0,
        wait_duration_ms: 120,
        output_duration_ms: 0,
        completed_at: now
      };
      messages.push(userMessage);

      const emit = (event: WebChatStreamEvent) => {
        if (signal.aborted) {
          return;
        }
        callbacks.onEvent(event);
      };

      emit({ event: 'start', chat_id: chatId });

      const attachments = payload.attachment_ids.map((attachmentId) => ({
        id: attachmentId,
        file_name: 'mock-attachment',
        mime_type: 'application/octet-stream',
        file_size: 0,
        content: null,
        asset_url: null
      }));
      emit({
        event: 'user_message',
        chat_id: chatId,
        message: { ...userMessage, attachments }
      });

      const replyId = `msg-mock-assistant-${state.messageIdSeq}`;
      const chunks: string[] = [];
      for (let index = 0; index < MOCK_STREAM_REPLY.length; index += STREAM_CHUNK_SIZE) {
        chunks.push(MOCK_STREAM_REPLY.slice(index, index + STREAM_CHUNK_SIZE));
      }
      let deltaBuffer = '';
      const startedAt = Date.now();
      let finishTimer: ReturnType<typeof setInterval> | null = null;
      const waitUntilDone = new Promise<void>((resolve) => {
        const handleAbort = () => {
          if (finishTimer) {
            clearInterval(finishTimer);
          }
          resolve();
        };
        signal.addEventListener('abort', handleAbort, { once: true });
        finishTimer = setInterval(() => {
          const chunk = chunks.shift();
          if (chunk === undefined) {
            clearInterval(finishTimer!);
            finishTimer = null;
            const doneAt = Date.now();
            const assistantMessage: WebChatMessage = {
              id: replyId,
              sender: 'assistant',
              content_raw: deltaBuffer,
              timestamp: startedAt,
              role_name: characterState.active_prompt.name,
              input_tokens: userMessage.input_tokens,
              cached_input_tokens: 0,
              output_tokens: Math.max(1, Math.round(deltaBuffer.length / 4)),
              wait_duration_ms: STREAM_CHUNK_INTERVAL_MS,
              output_duration_ms: doneAt - startedAt,
              completed_at: doneAt,
              content_blocks: [{ kind: 'text', content: deltaBuffer }],
              attachments: []
            };
            messages.push(assistantMessage);
            chatById(chatId).updated_at = doneAt;
            emit({ event: 'assistant_done', chat_id: chatId, message: { ...assistantMessage } });
            signal.removeEventListener('abort', handleAbort);
            resolve();
            return;
          }
          deltaBuffer += chunk;
          emit({ event: 'assistant_delta', chat_id: chatId, delta: chunk });
        }, STREAM_CHUNK_INTERVAL_MS);
      });
      await waitUntilDone;
    }
  };
}
