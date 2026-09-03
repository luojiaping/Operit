import { useMemo } from 'react';
import { ChatScreenContent } from '../components/ChatScreenContent';
import { buildChatFontFaceCss, buildChatThemeStyle } from '../util/chatTheme';
import { useChatViewModel } from '../viewmodel/ChatViewModel';
import type { ChatViewModel } from '../viewmodel/ChatViewModel';

export function AIChatScreen() {
  const viewModel = useChatViewModel();
  return <AIChatScreenView viewModel={viewModel} />;
}

// 预览站复用：viewModel 由外部注入（PreviewApp 持有同一实例驱动工具栏）
export function AIChatScreenView({ viewModel }: { viewModel: ChatViewModel }) {
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

  return (
    <div
      className={[
        'ai-chat-screen',
        viewModel.activeChatStyle === 'bubble' ? 'chat-style-bubble' : 'chat-style-cursor',
        viewModel.theme?.theme_mode === 'light' ? 'theme-light' : 'theme-dark'
      ].join(' ')}
      data-theme-target="chat.screen"
      style={chatThemeStyle}
    >
      {fontFaceCss ? <style>{fontFaceCss}</style> : null}
      <div
        aria-hidden="true"
        className="chat-glass-backdrop-source"
        data-theme-target="background"
        style={backdropBaseStyle}
      >
        {backdropVideo && backdropVideo.asset_url ? (
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

      {!viewModel.token ? (
        <div className="chat-auth-banner" role="alert">
          <strong>未连接</strong>
          <span>
            在手机 Operit 的设置页打开「局域网页面」，复制带 Token 的访问地址
            （https://…:8094/#token=…）直接打开即可，无需手动输入。
          </span>
          {viewModel.error ? <em>{viewModel.error}</em> : null}
        </div>
      ) : null}
    </div>
  );
}
