import { useMemo } from 'react';
import { ChatScreenContent } from '../components/ChatScreenContent';
import { ConfigurationScreen } from './ConfigurationScreen';
import { buildChatFontFaceCss, buildChatThemeStyle } from '../util/chatTheme';
import { useChatViewModel } from '../viewmodel/ChatViewModel';

export function AIChatScreen() {
  const viewModel = useChatViewModel();
  const fontFaceCss = buildChatFontFaceCss(viewModel.theme);
  const chatThemeStyle = useMemo(() => buildChatThemeStyle(viewModel.theme), [viewModel.theme]);
  const backdropBaseStyle = useMemo(
    () => ({
      background: String(chatThemeStyle['--chat-root-background'] ?? 'transparent')
    }),
    [chatThemeStyle]
  );
  const backdropImageStyle = useMemo(
    () => ({
      backgroundImage: String(chatThemeStyle['--chat-background-image'] ?? 'none'),
      opacity: String(chatThemeStyle['--chat-background-opacity'] ?? '0')
    }),
    [chatThemeStyle]
  );
  const backdropTintStyle = useMemo(
    () => ({
      background: String(chatThemeStyle['--chat-background-tint'] ?? 'transparent')
    }),
    [chatThemeStyle]
  );
  // A2：视频壁纸渲染 video 元素；muted/loop 缺省对齐 app 默认值 true
  const background = viewModel.theme?.background;
  const isVideoBackground =
    background?.type === 'video' && Boolean(background.asset_url);
  const suggestedUrl = useMemo(() => {
    if (typeof window === 'undefined') {
      return 'http://127.0.0.1:8094/';
    }

    const { protocol, hostname, port } = window.location;
    const resolvedPort = port || '8094';
    return `${protocol}//${hostname}:${resolvedPort}/`;
  }, []);

  return (
    <div
      className={[
        'ai-chat-screen',
        viewModel.activeChatStyle === 'bubble' ? 'chat-style-bubble' : 'chat-style-cursor',
        viewModel.theme?.theme_mode === 'light' ? 'theme-light' : 'theme-dark'
      ].join(' ')}
      style={chatThemeStyle}
    >
      {fontFaceCss ? <style>{fontFaceCss}</style> : null}
      <div
        aria-hidden="true"
        className="chat-glass-backdrop-source"
        style={backdropBaseStyle}
      >
        {isVideoBackground && background?.asset_url ? (
          <video
            aria-hidden="true"
            autoPlay
            className="chat-glass-backdrop-video"
            loop={background.loop ?? true}
            muted={background.muted ?? true}
            playsInline
            src={background.asset_url}
            style={backdropImageStyle}
          />
        ) : (
          <div className="chat-glass-backdrop-image" style={backdropImageStyle} />
        )}
        <div className="chat-glass-backdrop-tint" style={backdropTintStyle} />
      </div>

      <ChatScreenContent viewModel={viewModel} />

      {viewModel.showConnectionOverlay ? (
        <ConfigurationScreen
          error={viewModel.error}
          onCopyUrl={() => {
            if (typeof navigator !== 'undefined' && navigator.clipboard) {
              void navigator.clipboard.writeText(suggestedUrl);
            }
          }}
          onSubmit={viewModel.submitToken}
          onTokenDraftChange={viewModel.setTokenDraft}
          suggestedUrl={suggestedUrl}
          tokenDraft={viewModel.tokenDraft}
        />
      ) : null}
    </div>
  );
}
