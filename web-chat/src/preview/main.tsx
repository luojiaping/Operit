import { StrictMode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AIChatScreenView } from '../ui/features/chat/screens/AIChatScreen';
import { useChatViewModel } from '../ui/features/chat/viewmodel/ChatViewModel';
import { isMockMode } from '../ui/features/chat/util/chatTransport';
import { previewControls } from '../ui/features/chat/util/mock/mockTransport';
import { PreviewSlotProvider } from '../ui/features/chat/composedsl/InputSlotHost';
import type { InputSlotContentMap } from '../ui/features/chat/composedsl/composeDslTypes';
import type { WebThemeSnapshot } from '../ui/features/chat/util/chatTypes';
import { ThemeStudioPanel } from './ThemeStudioPanel';
import { ThemeStudioEntry } from './ThemeStudioEntry';
import { ThemeWorkbench } from './ThemeWorkbench';
import type { StudioPackage } from '../shared/theme/packageLoader';
import type { StudioEditorState } from '../shared/theme/editorState';
import { resolveEditorRuntimeTheme } from '../shared/theme/runtime';
import { createBrowserAssetLibrary } from '../shared/theme/assets/library';
import { CodePreviewPanel } from './CodePreviewPanel';
import { isPreviewBridgeMessage } from './previewBridge';
import type { PreviewBridgeMessage } from './previewBridge';
import { isThemeTargetId } from './themeTargets';
import type { ThemeTargetId } from './themeTargets';
import { AppShell } from '../ui/features/chat/appshell/AppShell';
import type { ShellScreen } from '../ui/features/chat/appshell/AppShell';
import { SettingsHome } from '../ui/features/chat/appshell/settings/SettingsHome';
import { SETTINGS_ITEM_TITLES } from '../ui/features/chat/appshell/settings/settingsItems';
import { ThemeSettingsPage } from '../ui/features/chat/appshell/settings/ThemeSettingsPage';
import { GenericSettingsPage } from '../ui/features/chat/appshell/settings/genericSettingsPages';
// 聊天界面全套样式（foundation/messages/composer/dialogs/history/structured/dsl），
// 与真机入口 src/main.tsx 同一份；缺失它 iframe 内就是裸 DOM
import '../ui/features/chat/util/chat-screen.css';
import '../ui/features/chat/appshell/appShell.css';
import '../ui/features/chat/appshell/settings/settings.css';
import './preview.css';
import './deviceFrame.css';

// 初始演示内容 = examples/input_slot_demo 三个插槽的等价物，
// above_input 用 DSL screen、input_drawer 返回文本、toolbar_right 返回文本
const DEMO_SLOT_SCREEN = `function SlotScreen(ctx) {
  const { UI } = ctx;
  return UI.Card(
    { fillMaxWidth: true, containerColor: "rgba(127, 150, 220, 0.16)", elevation: 0 },
    UI.Column(
      { fillMaxWidth: true, padding: 8, spacing: 2 },
      [
        UI.Text({ text: "Input slot Compose DSL", style: "labelLarge", color: "#8ca9ff" }),
        UI.Text({ text: "Rendered above the chat input", style: "bodySmall", color: "#9ca8bb" })
      ]
    )
  );
}
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = SlotScreen;`;

const INITIAL_SLOT_CONTENTS: InputSlotContentMap = {
  above_input: { kind: 'dsl', screenSource: DEMO_SLOT_SCREEN },
  input_drawer: { kind: 'text', text: 'Demo drawer' },
  input_toolbar_right: { kind: 'text', text: 'Slot' }
};

type StudioMode = 'theme' | 'plugin';

export interface PreviewRuntimeState {
  chatId: string | null;
  inputStyle: string;
  isProcessing: boolean;
  inputText: string;
}

const INITIAL_RUNTIME_STATE: PreviewRuntimeState = {
  chatId: 'chat-main',
  inputStyle: 'agent',
  isProcessing: false,
  inputText: ''
};

// 预览站强制 mock 模式：直接访问 /preview.html 无参数时自动补 ?mock=1
function ensureMockParam() {
  if (typeof window === 'undefined' || isMockMode()) {
    return;
  }
  const url = new URL(window.location.href);
  url.searchParams.set('mock', '1');
  window.location.replace(url.toString());
}

function isEmbedMode() {
  if (typeof window === 'undefined') {
    return false;
  }
  return new URLSearchParams(window.location.search).has('embed');
}

// --- iframe 侧：手机视口内的模拟器 ---

// embed 模式：手机视口内的完整 App（壳层 + 聊天 + 设置），
// 主题与插槽由外层消息驱动
function EmbeddedPreviewApp() {
  const viewModel = useChatViewModel();
  const [slotContents, setSlotContents] = useState<InputSlotContentMap>({});
  const [screen, setScreen] = useState<ShellScreen>({ name: 'chat' });
  const [selectedTarget, setSelectedTarget] = useState<ThemeTargetId | null>(null);

  useEffect(() => {
    const shell = document.querySelector<HTMLElement>('.app-shell');
    if (!shell) {
      return;
    }

    function handlePreviewClick(event: MouseEvent) {
      const element = event.target;
      if (!(element instanceof Element)) {
        return;
      }
      const selectable = element.closest<HTMLElement>('[data-theme-target]');
      if (selectable == null || !shell.contains(selectable)) {
        return;
      }
      const target = selectable.dataset.themeTarget;
      if (target == null) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      if (!isThemeTargetId(target)) {
        return;
      }
      setSelectedTarget(target);
      window.parent.postMessage({ type: 'preview-selection', target }, '*');
    }

    shell.addEventListener('click', handlePreviewClick, true);
    return () => shell.removeEventListener('click', handlePreviewClick, true);
  }, []);

  useEffect(() => {
    const shell = document.querySelector<HTMLElement>('.app-shell');
    if (!shell) {
      return;
    }
    shell.querySelectorAll<HTMLElement>('[data-theme-target]').forEach((element) => {
      if (element.dataset.themeTarget === selectedTarget) {
        element.dataset.themeSelected = 'true';
      } else {
        delete element.dataset.themeSelected;
      }
    });
  }, [screen, selectedTarget]);

  // 主题设置页的即时补丁：patch 后重载会话刷新界面
  function patchThemeLive(patch: Partial<WebThemeSnapshot>) {
    previewControls.patchTheme(patch);
    void viewModel.reloadCurrentConversation();
  }

  useEffect(() => {
    function handleMessage(event: MessageEvent) {
      if (event.source !== window.parent) {
        return;
      }
      const data: unknown = event.data;
      if (!isPreviewBridgeMessage(data)) {
        return;
      }
      if (data.type === 'preview-theme') {
        previewControls.setTheme(data.theme);
        void viewModel.reloadCurrentConversation();
        return;
      }
      if (data.type === 'preview-slots') {
        setSlotContents(data.contents);
        return;
      }
      if (data.type === 'preview-highlight') {
        setSelectedTarget(data.target);
      }
    }
    window.addEventListener('message', handleMessage);
    window.parent.postMessage({ type: 'preview-ready' }, '*');
    // 壳层顶栏显示"- 会话标题"需要会话列表
    viewModel.ensureChats();
    return () => window.removeEventListener('message', handleMessage);
    // viewModel 引用稳定（同一 hook 实例）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 向外层上报运行状态（插件 render payload 需要）
  const runtimeState: PreviewRuntimeState = useMemo(
    () => ({
      chatId: viewModel.selectedChatId,
      inputStyle: viewModel.activeInputStyle,
      isProcessing: viewModel.isStreaming,
      inputText: viewModel.messageInput
    }),
    [
      viewModel.selectedChatId,
      viewModel.activeInputStyle,
      viewModel.isStreaming,
      viewModel.messageInput
    ]
  );
  useEffect(() => {
    window.parent.postMessage({ type: 'preview-state', ...runtimeState }, '*');
  }, [runtimeState]);

  return (
    <AppShell
      chatTitle={viewModel.selectedChat?.title ?? ''}
      onScreenChange={setScreen}
      screen={screen}
      settingsTitles={SETTINGS_ITEM_TITLES}
      theme={viewModel.theme}
    >
      {(context) =>
        context.screen.name === 'chat' ? (
          <PreviewSlotProvider contents={slotContents}>
            <AIChatScreenView viewModel={viewModel} />
          </PreviewSlotProvider>
        ) : context.screen.name === 'settings' ? (
          <SettingsHome
            onOpenItem={(item) =>
              setScreen({ name: 'settings-sub', page: item.id === 'github-login' ? 'github-account' : item.id })
            }
          />
        ) : context.screen.page === 'theme' ? (
          <ThemeSettingsPage onPatchTheme={patchThemeLive} theme={viewModel.theme} />
        ) : (
          <GenericSettingsPage page={context.screen.page} />
        )
      }
    </AppShell>
  );
}

// --- 外层：工作台（面板 + 手机壳 iframe） ---

// 手机壳（Google Pixel 6 Pro，devices.css）内的手机视口 iframe。
// 壳实体尺寸固定（404x862），按舞台实际尺寸自适应缩放，任何视口都不溢出
function DeviceMockup({
  iframeRef,
  onReady
}: {
  iframeRef: React.MutableRefObject<HTMLIFrameElement | null>;
  onReady: () => void;
}) {
  const stageRef = useRef<HTMLElement | null>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const element = stageRef.current;
    if (!element) {
      return;
    }
    const update = () => {
      const rect = element.getBoundingClientRect();
      // 手机壳实体 404x866，按舞台实际尺寸整体放大（iframe 逻辑视口固定 376x816，
      // transform 缩放不损清晰度）；上限 1.4 由舞台宽高自然约束
      const next = Math.min(1.4, (rect.height - 12) / 866, (rect.width - 12) / 408);
      setScale(Math.max(0.35, next));
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return (
    <main className="device-stage" ref={stageRef}>
      <div className="device-stage-scale" style={{ '--device-scale': scale } as React.CSSProperties}>
        <div className="device device-google-pixel-6-pro">
          <div className="device-frame">
            <div className="device-screen">
              <iframe
                className="device-viewport"
                onLoad={onReady}
                ref={iframeRef}
                src="/preview.html?mock=1&embed=1&studio=theme-studio-v2"
                title="Operit 手机预览"
              />
            </div>
          </div>
          <div className="device-stripe"></div>
          <div className="device-header"></div>
          <div className="device-sensors"></div>
          <div className="device-btns"></div>
          <div className="device-power"></div>
        </div>
      </div>
    </main>
  );
}

function PreviewApp() {
  const [studioMode, setStudioMode] = useState<StudioMode>('theme');
  const [selectedTarget, setSelectedTarget] = useState<ThemeTargetId>('chat.screen');
  const [editorPackage, setEditorPackage] = useState<StudioPackage | null>(null);
  const [editorState, setEditorState] = useState<StudioEditorState | null>(null);
  // 素材库单例：IndexedDB 持久化 + blob URL 注册表
  const assetLibraryRef = useRef<ReturnType<typeof createBrowserAssetLibrary> | null>(null);
  if (assetLibraryRef.current == null) {
    assetLibraryRef.current = createBrowserAssetLibrary();
  }
  const assetLibrary = assetLibraryRef.current;
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const iframeReadyRef = useRef(false);
  const pendingSelectionRef = useRef<ThemeTargetId | null>('chat.screen');
  // ready 握手前缓存最新一次主题/插槽，握手成功后立即补发
  const pendingThemeRef = useRef<WebThemeSnapshot | null>(null);
  const pendingSlotsRef = useRef<InputSlotContentMap | null>(null);
  const [slotContents, setSlotContents] = useState<InputSlotContentMap>(INITIAL_SLOT_CONTENTS);
  const [runtimeState, setRuntimeState] = useState<PreviewRuntimeState>(INITIAL_RUNTIME_STATE);

  const postToIframe = useCallback((message: PreviewBridgeMessage) => {
    const target = iframeRef.current?.contentWindow;
    if (!target) {
      return false;
    }
    target.postMessage(message, '*');
    return true;
  }, []);

  const handleIframeReady = useCallback(() => {
    iframeReadyRef.current = true;
    if (pendingThemeRef.current) {
      postToIframe({ type: 'preview-theme', theme: pendingThemeRef.current });
    }
    if (pendingSlotsRef.current) {
      postToIframe({ type: 'preview-slots', contents: pendingSlotsRef.current });
    }
    postToIframe({ type: 'preview-highlight', target: pendingSelectionRef.current });
  }, [postToIframe]);

  useEffect(() => {
    function handleMessage(event: MessageEvent) {
      if (event.source !== iframeRef.current?.contentWindow) {
        return;
      }
      const data: unknown = event.data;
      if (!isPreviewBridgeMessage(data)) {
        return;
      }
      if (data.type === 'preview-ready') {
        handleIframeReady();
        return;
      }
      if (data.type === 'preview-state') {
        setRuntimeState({
          chatId: data.chatId,
          inputStyle: data.inputStyle,
          isProcessing: data.isProcessing,
          inputText: data.inputText
        });
        return;
      }
      if (data.type === 'preview-selection') {
        setSelectedTarget(data.target);
        pendingSelectionRef.current = data.target;
      }
    }
    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [handleIframeReady]);

  const selectTarget = useCallback(
    (target: ThemeTargetId) => {
      setSelectedTarget(target);
      pendingSelectionRef.current = target;
      if (iframeReadyRef.current) {
        postToIframe({ type: 'preview-highlight', target });
      }
    },
    [postToIframe]
  );

  // 面板回调：发送（未就绪时缓存，握手后补发）
  const applyTheme = useCallback(
    (theme: WebThemeSnapshot) => {
      pendingThemeRef.current = theme;
      postToIframe({ type: 'preview-theme', theme });
    },
    [postToIframe]
  );

  const applySlots = useCallback(
    (contents: InputSlotContentMap) => {
      setSlotContents(contents);
      pendingSlotsRef.current = contents;
      postToIframe({ type: 'preview-slots', contents });
    },
    [postToIframe]
  );

  // 工作台投影：按 schema 4 runtime 完整派生（asset:// 解析为 blob URL 后过桥）
  const applyEditorProjection = useCallback(
    async (nextState: StudioEditorState) => {
      if (editorPackage == null) {
        return;
      }
      const theme = await resolveEditorRuntimeTheme(
        editorPackage.manifest,
        nextState.values,
        'dark',
        assetLibrary,
        nextState.componentSkins
      );
      applyTheme(theme);
    },
    [applyTheme, assetLibrary, editorPackage]
  );

  // 模式切换时同步插槽可见性：主题模式展示纯净界面，插件模式挂载当前插槽内容
  useEffect(() => {
    postToIframe({ type: 'preview-slots', contents: studioMode === 'plugin' ? slotContents : {} });
    // slotContents 变化由 applySlots 自己发送，这里只响应模式切换
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studioMode, postToIframe]);

  return (
    <div className="preview-studio">
      <header className="studio-topbar">
        <span className="studio-title">Operit Theme Studio</span>
        <div className="studio-mode-tabs">
          <button
            className={studioMode === 'theme' ? 'is-active' : ''}
            onClick={() => setStudioMode('theme')}
            type="button"
          >
            主题预览
          </button>
          <button
            className={studioMode === 'plugin' ? 'is-active' : ''}
            onClick={() => setStudioMode('plugin')}
            type="button"
          >
            插件工坊
          </button>
        </div>
        <span className="studio-hint">mock 数据驱动 · 手机视口独立渲染 · 一切都在浏览器本地</span>
      </header>
      <div className="studio-body">
        <DeviceMockup iframeRef={iframeRef} onReady={handleIframeReady} />
        <aside className="studio-side">
          {studioMode === 'theme' ? (
            <>
              <ThemeStudioEntry
                onPackageLoaded={(studioPackage) => {
                  setEditorPackage(studioPackage);
                  setEditorState(null);
                }}
              />
              {editorPackage != null ? (
                <ThemeWorkbench
                  assetLibrary={assetLibrary}
                  onPreview={(nextState) => {
                    setEditorState(nextState);
                    void applyEditorProjection(nextState);
                  }}
                  onSelectTarget={selectTarget}
                  selectedTarget={selectedTarget}
                  studioPackage={editorPackage}
                  key={editorPackage.archiveSha256}
                />
              ) : (
                <ThemeStudioPanel
                  onApplyTheme={applyTheme}
                  onSelectTarget={selectTarget}
                  selectedTarget={selectedTarget}
                />
              )}
            </>
          ) : (
            <CodePreviewPanel
              onContentsChange={applySlots}
              onResetDemo={() => applySlots(INITIAL_SLOT_CONTENTS)}
              runtimeState={runtimeState}
            />
          )}
        </aside>
      </div>
    </div>
  );
}

ensureMockParam();

const container = document.getElementById('preview-root');
if (container) {
  createRoot(container).render(
    <StrictMode>
      {isEmbedMode() ? <EmbeddedPreviewApp /> : <PreviewApp />}
    </StrictMode>
  );
}
