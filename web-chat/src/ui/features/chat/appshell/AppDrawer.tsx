import {
  AccountTreeIcon,
  AppsIcon,
  BuildIcon,
  EmailIcon,
  ExtensionIcon,
  HelpIcon,
  HistoryIcon,
  InfoIcon,
  SettingsIcon,
  TuneIcon,
  WifiIcon
} from '../util/chatIcons';
import type { ShellScreen } from './AppShell';

// 导航抽屉，对齐 app DrawerContent.kt / PhoneLayout.kt：
// 宽 75% 屏、右侧 16dp 圆角、surface 底；头部信息卡 + 网络胶囊 +
// 三张 76dp 快捷卡 + AI 功能组 4 项 + 底部三卡（关于/使用手册/设置）
export function AppDrawer({
  open,
  onClose,
  navigate
}: {
  open: boolean;
  onClose: () => void;
  navigate: (screen: ShellScreen) => void;
}) {
  const primaryNavItems = [
    { id: 'chat', icon: EmailIcon, label: 'AI 对话' },
    { id: 'assistant-config', icon: TuneIcon, label: '助手配置' },
    { id: 'memory-base', icon: HistoryIcon, label: '记忆库' },
    { id: 'toolbox', icon: AppsIcon, label: '工具箱' }
  ] as const;

  const quickCards = [
    { icon: ExtensionIcon, label: '包管理', badge: '3' },
    { icon: BuildIcon, label: '权限', badge: '正常' },
    { icon: AccountTreeIcon, label: '工作流', badge: '0' }
  ];

  return (
    <div
      className={`app-drawer-root ${open ? 'is-open' : ''}`}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <aside className="app-drawer">
        <div className="app-drawer-scroll">
          <div className="app-drawer-info-card">
            <strong className="app-drawer-brand">Operit AI</strong>
            <span className="app-drawer-status-pill">
              <span className="app-drawer-status-dot" />
              <WifiIcon size={14} />
              <span>已连接</span>
            </span>
          </div>

          <div className="app-drawer-quick-row">
            {quickCards.map((card) => (
              <button className="app-drawer-quick-card" key={card.label} type="button">
                <card.icon size={20} />
                <span>{card.label}</span>
                <span className="app-drawer-quick-badge">{card.badge}</span>
              </button>
            ))}
          </div>

          <h4 className="app-drawer-group-title">AI 功能</h4>
          <nav className="app-drawer-nav">
            {primaryNavItems.map((item) => (
              <button
                className={`app-drawer-nav-item ${item.id === 'chat' ? 'is-selected' : ''}`}
                key={item.id}
                onClick={() =>
                  navigate(item.id === 'chat' ? { name: 'chat' } : { name: 'chat' })
                }
                type="button"
              >
                <item.icon size={20} />
                <span>{item.label}</span>
              </button>
            ))}
          </nav>

          <h4 className="app-drawer-group-title">插件</h4>
          <nav className="app-drawer-nav">
            <button
              className="app-drawer-nav-item"
              onClick={() => navigate({ name: 'chat' })}
              type="button"
            >
              <ExtensionIcon size={20} />
              <span>输入插槽示例</span>
            </button>
          </nav>
        </div>

        <div className="app-drawer-bottom">
          <button className="app-drawer-bottom-card" type="button">
            <InfoIcon size={20} />
            <span>关于</span>
          </button>
          <button className="app-drawer-bottom-card" type="button">
            <HelpIcon size={20} />
            <span>使用手册</span>
          </button>
          <button
            className="app-drawer-bottom-card"
            onClick={() => navigate({ name: 'settings' })}
            type="button"
          >
            <SettingsIcon size={20} />
            <span>设置</span>
          </button>
        </div>
      </aside>
    </div>
  );
}
