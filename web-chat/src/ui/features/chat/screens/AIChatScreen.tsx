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
  const backdropMedia = viewModel.theme?.background.media;
  const backdropVideo = backdropMedia?.type === 'video' ? backdropMedia : null;
  const backdropMediaImageStyle = useMemo(
    () => ({
      backgroundImage: String(chatThemeStyle['--chat-media-background-image'] ?? 'none'),
      filter: `blur(${String(chatThemeStyle['--chat-media-background-blur'] ?? '0px')})`,
      opacity: String(chatThemeStyle['--chat-media-background-opacity'] ?? '0')
    }),
    [chatThemeStyle]
  );
  const backdropStageImageStyle = useMemo(
    () => ({
      backgroundImage: String(chatThemeStyle['--chat-stage-background-image'] ?? 'none'),
      backgroundSize: String(chatThemeStyle['--chat-stage-background-size'] ?? 'cover'),
      opacity: String(chatThemeStyle['--chat-stage-background-opacity'] ?? '0')
    }),
    [chatThemeStyle]
  );
  const backdropTintStyle = useMemo(
    () => ({
      background: String(chatThemeStyle['--chat-background-tint'] ?? 'transparent')
    }),
    [chatThemeStyle]
  );
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
        {backdropVideo !== null && backdropVideo.asset_url ? (
          <video
            aria-hidden="true"
            autoPlay
            className="chat-glass-backdrop-video"
            loop={backdropVideo.loop}
            muted={backdropVideo.muted}
            playsInline
            src={backdropVideo.asset_url}
            style={{
              filter: `blur(${String(chatThemeStyle['--chat-media-background-blur'] ?? '0px')})`,
              opacity: String(chatThemeStyle['--chat-media-background-opacity'] ?? '0')
            }}
          />
        ) : null}
        <div className="chat-glass-backdrop-media-image" style={backdropMediaImageStyle} />
        <div className="chat-glass-backdrop-stage-image" style={backdropStageImageStyle} />
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
