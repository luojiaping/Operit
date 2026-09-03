import {
  ArrowBackIcon,
  CodeIcon,
  MenuIcon,
  TerminalIcon
} from '../util/chatIcons';

// 全局顶栏，对齐 app AppContent.kt 的 TopAppBar：
// 64dp 高、primary 容器（聊天页注入 Terminal/Code action）、
// 标题 14sp SemiBold，聊天页追加 "- 会话标题" 副标题
export function AppTopBar({
  title,
  chatTitle,
  isChat,
  canGoBack,
  isTransparent,
  onBack,
  onMenu
}: {
  title: string;
  chatTitle: string;
  isChat: boolean;
  canGoBack: boolean;
  // 对齐 app AppContent：toolbarTransparent 时容器透明
  isTransparent: boolean;
  onBack: () => void;
  onMenu: () => void;
}) {
  return (
    <header
      className={`app-top-bar ${isTransparent ? 'is-transparent' : ''}`}
      data-theme-target="chrome.toolbar"
    >
      <button
        aria-label={canGoBack ? '返回' : '菜单'}
        className="app-top-bar-nav"
        onClick={canGoBack ? onBack : onMenu}
        type="button"
      >
        {canGoBack ? <ArrowBackIcon size={22} /> : <MenuIcon size={22} />}
      </button>

      <div className="app-top-bar-title">
        <strong>{title}</strong>
        {isChat && chatTitle ? <span>- {chatTitle}</span> : null}
      </div>

      {isChat ? (
        <div className="app-top-bar-actions">
          <button aria-label="AI电脑" className="app-top-bar-action" type="button">
            <TerminalIcon size={22} />
          </button>
          <button aria-label="Web开发" className="app-top-bar-action" type="button">
            <CodeIcon size={22} />
          </button>
        </div>
      ) : (
        <span className="app-top-bar-actions" />
      )}
    </header>
  );
}
