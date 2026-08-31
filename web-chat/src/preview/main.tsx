import { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AIChatScreenView } from '../ui/features/chat/screens/AIChatScreen';
import { useChatViewModel } from '../ui/features/chat/viewmodel/ChatViewModel';
import { previewControls } from '../ui/features/chat/util/mock/mockTransport';
import { isMockMode } from '../ui/features/chat/util/chatTransport';
import type { ChatViewModel } from '../ui/features/chat/viewmodel/ChatViewModel';
import './preview.css';

const DEVICE_WIDTHS = [
  { id: 's', label: '360', value: 360 },
  { id: 'm', label: '412', value: 412 },
  { id: 'l', label: '768', value: 768 },
  { id: 'full', label: 'Full', value: null }
] as const;

type DeviceWidthId = (typeof DEVICE_WIDTHS)[number]['id'];

// 预览站强制 mock 模式：直接访问 /preview.html 无参数时自动补 ?mock=1
function ensureMockParam() {
  if (typeof window === 'undefined' || isMockMode()) {
    return;
  }
  const url = new URL(window.location.href);
  url.searchParams.set('mock', '1');
  window.location.replace(url.toString());
}

function ToolbarChip({
  active,
  onClick,
  title,
  children
}: {
  active: boolean;
  onClick: () => void;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <button
      className={`preview-chip ${active ? 'is-active' : ''}`}
      onClick={onClick}
      title={title}
      type="button"
    >
      {children}
    </button>
  );
}

function SimulatorToolbar({
  deviceWidthId,
  onDeviceWidthChange,
  viewModel
}: {
  deviceWidthId: DeviceWidthId;
  onDeviceWidthChange: (id: DeviceWidthId) => void;
  viewModel: ChatViewModel;
}) {
  const chatStyle = viewModel.theme?.chat_style === 'cursor' ? 'cursor' : 'bubble';
  const inputStyle = viewModel.theme?.input.style === 'classic' ? 'classic' : 'agent';

  async function patchThemeAndReload(patch: Parameters<typeof previewControls.patchTheme>[0]) {
    previewControls.patchTheme(patch);
    await viewModel.reloadCurrentConversation();
  }

  return (
    <div className="preview-toolbar">
      <span className="preview-toolbar-title">Operit Preview Studio</span>

      <div className="preview-toolbar-group">
        {DEVICE_WIDTHS.map((width) => (
          <ToolbarChip
            key={width.id}
            active={deviceWidthId === width.id}
            onClick={() => onDeviceWidthChange(width.id)}
            title={`设备宽度 ${width.label}`}
          >
            {width.label}
          </ToolbarChip>
        ))}
      </div>

      <div className="preview-toolbar-group">
        <ToolbarChip
          active={chatStyle === 'bubble'}
          onClick={() => {
            if (chatStyle !== 'bubble') {
              void patchThemeAndReload({ chat_style: 'bubble' });
            }
          }}
          title="气泡消息风格"
        >
          气泡
        </ToolbarChip>
        <ToolbarChip
          active={chatStyle === 'cursor'}
          onClick={() => {
            if (chatStyle !== 'cursor') {
              void patchThemeAndReload({ chat_style: 'cursor' });
            }
          }}
          title="光标消息风格"
        >
          光标
        </ToolbarChip>
      </div>

      <div className="preview-toolbar-group">
        <ToolbarChip
          active={inputStyle === 'agent'}
          onClick={() => {
            if (inputStyle !== 'agent') {
              void patchThemeAndReload({
                input: { ...viewModel.theme!.input, style: 'agent' }
              });
            }
          }}
          title="Agent 输入风格"
        >
          Agent
        </ToolbarChip>
        <ToolbarChip
          active={inputStyle === 'classic'}
          onClick={() => {
            if (inputStyle !== 'classic') {
              void patchThemeAndReload({
                input: { ...viewModel.theme!.input, style: 'classic' }
              });
            }
          }}
          title="Classic 输入风格"
        >
          Classic
        </ToolbarChip>
      </div>

      <div className="preview-toolbar-group">
        <ToolbarChip
          active={false}
          onClick={() => {
            previewControls.resetTheme();
            void viewModel.reloadCurrentConversation();
          }}
          title="重置主题覆盖"
        >
          重置主题
        </ToolbarChip>
      </div>

      <span className="preview-toolbar-hint">
        mock 数据驱动 · 消息可交互 · 主题为模拟近似
      </span>
    </div>
  );
}

function PreviewApp() {
  const viewModel = useChatViewModel();
  const [deviceWidthId, setDeviceWidthId] = useState<DeviceWidthId>('m');
  const deviceWidth = DEVICE_WIDTHS.find((width) => width.id === deviceWidthId)?.value ?? null;

  return (
    <div className="preview-studio">
      <SimulatorToolbar
        deviceWidthId={deviceWidthId}
        onDeviceWidthChange={setDeviceWidthId}
        viewModel={viewModel}
      />
      <div className="preview-stage">
        <div
          className="preview-device"
          style={deviceWidth !== null ? { width: `${deviceWidth}px` } : undefined}
        >
          <AIChatScreenView viewModel={viewModel} />
        </div>
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
