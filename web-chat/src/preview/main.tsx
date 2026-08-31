import { StrictMode, useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AIChatScreenView } from '../ui/features/chat/screens/AIChatScreen';
import { useChatViewModel } from '../ui/features/chat/viewmodel/ChatViewModel';
import { isMockMode } from '../ui/features/chat/util/chatTransport';
import { PreviewSlotProvider } from '../ui/features/chat/composedsl/InputSlotHost';
import type { InputSlotContentMap } from '../ui/features/chat/composedsl/composeDslTypes';
import { ThemeStudioPanel } from './ThemeStudioPanel';
import { CodePreviewPanel } from './CodePreviewPanel';
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

// 预览站强制 mock 模式：直接访问 /preview.html 无参数时自动补 ?mock=1
function ensureMockParam() {
  if (typeof window === 'undefined' || isMockMode()) {
    return;
  }
  const url = new URL(window.location.href);
  url.searchParams.set('mock', '1');
  window.location.replace(url.toString());
}

// 手机壳（Google Pixel 6 Pro，devices.css）包裹的模拟器。
// 壳实体尺寸固定（404x862），按舞台实际尺寸自适应缩放，任何视口都不溢出
function DeviceMockup({ children }: { children: React.ReactNode }) {
  const stageRef = useRef<HTMLElement | null>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const element = stageRef.current;
    if (!element) {
      return;
    }
    const update = () => {
      const rect = element.getBoundingClientRect();
      const next = Math.min(
        1,
        (rect.height - 28) / 866,
        (rect.width - 20) / 408
      );
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
              <div className="device-app">{children}</div>
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
  const viewModel = useChatViewModel();
  const [studioMode, setStudioMode] = useState<StudioMode>('theme');
  const [slotContents, setSlotContents] = useState<InputSlotContentMap>(INITIAL_SLOT_CONTENTS);

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
        <span className="studio-hint">mock 数据驱动 · 一切渲染都在浏览器本地</span>
      </header>
      <div className="studio-body">
        <aside className="studio-side">
          {studioMode === 'theme' ? (
            <ThemeStudioPanel viewModel={viewModel} />
          ) : (
            <CodePreviewPanel
              onContentsChange={setSlotContents}
              onResetDemo={() => setSlotContents(INITIAL_SLOT_CONTENTS)}
              viewModel={viewModel}
            />
          )}
        </aside>
        <DeviceMockup>
          {/* 主题模式展示纯净界面，插件模式挂载插槽内容 */}
          <PreviewSlotProvider contents={studioMode === 'plugin' ? slotContents : {}}>
            <AIChatScreenView viewModel={viewModel} />
          </PreviewSlotProvider>
        </DeviceMockup>
      </div>
    </div>
  );
}

ensureMockParam();

const container = document.getElementById('preview-root');
if (container) {
  createRoot(container).render(
    <StrictMode>
      <PreviewApp />
    </StrictMode>
  );
}
