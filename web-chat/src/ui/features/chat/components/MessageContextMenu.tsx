import { useEffect, useRef, useState } from 'react';
import type { WebChatMessage } from '../util/chatTypes';

// 消息上下文菜单，对齐 app ChatArea.kt 的 DropdownMenu（180dp 宽、6dp 圆角、
// 项高 36dp/13sp）+ 删除确认 + MessageInfoDialog 三卡信息弹窗。
// 桌面右键触发；删除类操作带二次确认
export interface MessageContextMenuAction {
  messageId: string;
  message: WebChatMessage;
  x: number;
  y: number;
  onEditResend: (message: WebChatMessage) => void;
  onRollback: (message: WebChatMessage) => void;
  onRegenerate: (message: WebChatMessage) => void;
  onDelete: (message: WebChatMessage) => void;
  onClose: () => void;
}

function formatDuration(durationMs: number) {
  if (durationMs >= 1000) {
    return `${(durationMs / 1000).toFixed(1)}s`;
  }
  return `${durationMs}ms`;
}

function formatTimestamp(timestamp: number) {
  const date = new Date(timestamp);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function MessageInfoDialog({
  message,
  onClose
}: {
  message: WebChatMessage;
  onClose: () => void;
}) {
  return (
    <div className="message-menu-scrim" onClick={onClose}>
      <div className="message-info-dialog" onClick={(event) => event.stopPropagation()}>
        <header>消息信息</header>
        <div className="message-info-card">
          <small>发送者 / 角色</small>
          <strong>
            {message.sender} · {message.role_name ?? '-'}
          </strong>
          <small>
            {message.provider ?? '-'} / {message.model_name ?? '-'}
          </small>
        </div>
        <div className="message-info-card">
          <small>时间与耗时</small>
          <strong>{formatTimestamp(message.timestamp)}</strong>
          <small>
            等待 {formatDuration(message.wait_duration_ms ?? 0)} · 输出{' '}
            {formatDuration(message.output_duration_ms ?? 0)}
          </small>
        </div>
        <div className="message-info-card">
          <small>Token 统计</small>
          <strong>↑ {message.input_tokens ?? 0} ↓ {message.output_tokens ?? 0}</strong>
          <small>缓存命中 {message.cached_input_tokens ?? 0}</small>
        </div>
        <button className="message-info-close" onClick={onClose} type="button">
          关闭
        </button>
      </div>
    </div>
  );
}

export function MessageContextMenu(action: MessageContextMenuAction) {
  const { message, x, y } = action;
  const [confirmState, setConfirmState] = useState<'none' | 'delete' | 'rollback'>('none');
  const [showInfo, setShowInfo] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (!menuRef.current?.contains(event.target as Node)) {
        action.onClose();
      }
    }
    window.addEventListener('pointerdown', handlePointerDown);
    return () => window.removeEventListener('pointerdown', handlePointerDown);
  }, [action]);

  function copyMessage() {
    void navigator.clipboard.writeText(message.content_raw);
    action.onClose();
  }

  const isUser = message.sender === 'user';
  const isAssistant = message.sender === 'assistant';

  return (
    <>
      <div
        className="message-context-menu"
        ref={menuRef}
        style={{ left: x, top: y }}
      >
        {confirmState !== 'none' ? (
          <>
            <div className="message-menu-confirm-text">
              {confirmState === 'delete' ? '删除这条消息？' : '回滚到此处？之后的消息都会被删除。'}
            </div>
            <button
              className="is-danger"
              onClick={() => {
                if (confirmState === 'delete') {
                  action.onDelete(message);
                } else {
                  action.onRollback(message);
                }
                action.onClose();
              }}
              type="button"
            >
              确认
            </button>
            <button onClick={() => setConfirmState('none')} type="button">
              取消
            </button>
          </>
        ) : (
          <>
            <button onClick={copyMessage} type="button">
              复制消息
            </button>
            <button onClick={() => setShowInfo(true)} type="button">
              信息
            </button>
            {isUser ? (
              <button
                onClick={() => {
                  action.onEditResend(message);
                  action.onClose();
                }}
                type="button"
              >
                编辑并重发
              </button>
            ) : null}
            {isUser ? (
              <button onClick={() => setConfirmState('rollback')} type="button">
                回滚到此处
              </button>
            ) : null}
            {isAssistant ? (
              <button
                onClick={() => {
                  action.onRegenerate(message);
                  action.onClose();
                }}
                type="button"
              >
                重新生成
              </button>
            ) : null}
            <button className="is-danger" onClick={() => setConfirmState('delete')} type="button">
              删除
            </button>
          </>
        )}
      </div>
      {showInfo ? (
        <MessageInfoDialog message={message} onClose={() => setShowInfo(false)} />
      ) : null}
    </>
  );
}
