import { useEffect, useMemo, useState } from 'react';
import type { CSSProperties } from 'react';
import type { ReactNode } from 'react';
import type { WebThemeSnapshot } from '../util/chatTypes';
import { buildChatFontFaceCss, buildChatThemeStyle } from '../util/chatTheme';
import { AppTopBar } from './AppTopBar';
import { AppDrawer } from './AppDrawer';

// 手机视口内的屏幕路由：聊天页 / 设置主页 / 设置子页。
// 对应真机全局导航（Screen 对象），不引入路由库依赖
export type ShellScreen =
  | { name: 'chat' }
  | { name: 'settings' }
  | { name: 'settings-sub'; page: string };

export interface ShellScreenContext {
  screen: ShellScreen;
  navigate: (screen: ShellScreen) => void;
  goBack: () => void;
  screenTitle: string;
}

export function screenTitleOf(screen: ShellScreen, settingsTitles: Record<string, string>) {
  if (screen.name === 'settings') {
    return '设置';
  }
  if (screen.name === 'settings-sub') {
    return settingsTitles[screen.page] ?? '设置';
  }
  return 'AI对话';
}

export function AppShell({
  theme,
  chatTitle,
  screen,
  onScreenChange,
  settingsTitles,
  children
}: {
  theme: WebThemeSnapshot | null;
  chatTitle: string;
  screen: ShellScreen;
  onScreenChange: (screen: ShellScreen) => void;
  settingsTitles: Record<string, string>;
  children: (context: ShellScreenContext) => ReactNode;
}) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  // 壳层根挂主题变量，TopBar 与抽屉随主题联动（聊天页内部自挂同值变量）
  const chatThemeStyle = useMemo(() => buildChatThemeStyle(theme), [theme]);
  const fontFaceCss = buildChatFontFaceCss(theme);
  const canGoBack = screen.name !== 'chat';
  const toolbarTransparent = theme?.chrome?.toolbar_transparent ?? theme?.header.transparent ?? false;

  // body 在 .app-shell 之外，body { background: var(--chat-root-background) }
  // 只能解析到 :root 的固定暗色默认；把变量同步到 documentElement，
  // 让 body（以及未来任何根级元素）跟随真实主题，消除黑底暴露面
  useEffect(() => {
    const rootStyle = document.documentElement.style;
    const entries = Object.entries(chatThemeStyle);
    for (const [key, value] of entries) {
      rootStyle.setProperty(key, String(value));
    }
    return () => {
      for (const [key] of entries) {
        rootStyle.removeProperty(key);
      }
    };
  }, [chatThemeStyle]);

  const context: ShellScreenContext = useMemo(
    () => ({
      screen,
      navigate: onScreenChange,
      goBack: () => onScreenChange({ name: 'chat' }),
      screenTitle: screenTitleOf(screen, settingsTitles)
    }),
    [screen, onScreenChange, settingsTitles]
  );

  return (
    <div className="app-shell" data-theme-target="app.shell" style={chatThemeStyle as CSSProperties}>
      {fontFaceCss ? <style>{fontFaceCss}</style> : null}
      <AppTopBar
        canGoBack={canGoBack}
        chatTitle={chatTitle}
        isChat={screen.name === 'chat'}
        isTransparent={toolbarTransparent}
        onBack={() => onScreenChange({ name: 'chat' })}
        onMenu={() => setDrawerOpen(true)}
        title={screenTitleOf(screen, settingsTitles)}
      />
      <div className={`app-shell-content ${drawerOpen ? 'is-drawer-open' : ''}`}>
        {children(context)}
      </div>
      <AppDrawer
        navigate={(target) => {
          setDrawerOpen(false);
          onScreenChange(target);
        }}
        onClose={() => setDrawerOpen(false)}
        open={drawerOpen}
      />
    </div>
  );
}
