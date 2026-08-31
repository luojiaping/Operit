import { useRef, useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import type { ChatViewModel } from '../ui/features/chat/viewmodel/ChatViewModel';
import type { InputSlotContentMap } from '../ui/features/chat/composedsl/composeDslTypes';
import { buildRunnerPayload, resolveSlotContent, runToolPkgMain } from './slotRunner';
import { findFileBySuffix, readToolpkgZip } from './toolpkgLoader';

type PanelMode = 'demo' | 'paste' | 'upload';

const DEMO_MAIN_PLACEHOLDER = `exports.registerToolPkg = function () {
  ToolPkg.registerInputSlotPlugin({
    id: "my_above_input",
    slot: "above_input",
    function: function (event) {
      return { handled: true, text: "hello slot" };
    },
  });
};`;

export function CodePreviewPanel({
  viewModel,
  onContentsChange,
  onResetDemo
}: {
  viewModel: ChatViewModel;
  onContentsChange: Dispatch<SetStateAction<InputSlotContentMap>>;
  onResetDemo: () => void;
}) {
  const [mode, setMode] = useState<PanelMode>('demo');
  const [mainSource, setMainSource] = useState('');
  const [screenSource, setScreenSource] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string>('演示模式');
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  function basePayload() {
    return {
      chatId: viewModel.selectedChatId,
      inputStyle: viewModel.activeInputStyle,
      isProcessing: viewModel.isStreaming,
      inputText: viewModel.messageInput
    };
  }

  function runRegistrations(
    registrations: ReturnType<typeof runToolPkgMain>,
    resolveScreenSource: (path: string) => string
  ) {
    const contents: InputSlotContentMap = {};
    const errors: string[] = [];
    for (const registration of registrations) {
      if (contents[registration.slot]) {
        continue;
      }
      try {
        const result = registration.render({
          eventPayload: {
            ...buildRunnerPayload(basePayload(), registration.slot)
          }
        });
        const content = resolveSlotContent(result, resolveScreenSource);
        if (content) {
          contents[registration.slot] = content;
        }
      } catch (renderError: unknown) {
        console.error('slot render failed', registration.id, renderError);
        errors.push(`${registration.id}: ${(renderError as Error).message}`);
      }
    }
    onContentsChange(contents);
    setStatus(
      `已应用 ${registrations.length} 个注册，渲染 ${
        Object.keys(contents).length
      } 个插槽${errors.length ? `，${errors.length} 个失败` : ''}`
    );
    setError(errors.length > 0 ? errors.join('\n') : null);
  }

  function applyPasted() {
    setError(null);
    try {
      const registrations = runToolPkgMain(mainSource);
      runRegistrations(registrations, () => {
        if (!screenSource.trim()) {
          throw new Error('请在 screen 源码框提供 DSL 脚本，或让 render 返回 text');
        }
        return screenSource;
      });
    } catch (applyError: unknown) {
      console.error('applyPasted failed', applyError);
      setError((applyError as Error).message);
      setStatus('应用失败');
    }
  }

  async function applyUpload(file: File) {
    setError(null);
    try {
      const files = await readToolpkgZip(file);
      const manifestEntry = findFileBySuffix(files, 'manifest.json');
      if (!manifestEntry) {
        throw new Error('包内缺少 manifest.json');
      }
      const manifest = JSON.parse(new TextDecoder().decode(manifestEntry.bytes)) as {
        main?: string;
      };
      const mainEntry = findFileBySuffix(files, manifest.main ?? 'main.js');
      if (!mainEntry) {
        throw new Error(`包内缺少入口 ${manifest.main ?? 'main.js'}`);
      }
      const mainCode = new TextDecoder().decode(mainEntry.bytes);
      const registrations = runToolPkgMain(mainCode);
      runRegistrations(registrations, (screenPath) => {
        const normalized = screenPath.replace(/^\.\//, '');
        const exact = files.get(normalized);
        if (exact) {
          return new TextDecoder().decode(exact);
        }
        const bySuffix = findFileBySuffix(files, normalized.split('/').pop() ?? normalized);
        if (bySuffix) {
          return new TextDecoder().decode(bySuffix.bytes);
        }
        throw new Error(`包内找不到 screen 文件：${screenPath}`);
      });
    } catch (uploadError: unknown) {
      console.error('applyUpload failed', uploadError);
      setError((uploadError as Error).message);
      setStatus('导入失败');
    }
  }

  return (
    <aside className="preview-panel">
      <div className="preview-panel-tabs">
        {(
          [
            ['demo', '演示'],
            ['paste', '粘贴代码'],
            ['upload', '上传 .toolpkg']
          ] as const
        ).map(([value, label]) => (
          <button
            className={`preview-panel-tab ${mode === value ? 'is-active' : ''}`}
            key={value}
            onClick={() => {
              setMode(value);
              if (value === 'demo') {
                onResetDemo();
                setStatus('演示模式');
                setError(null);
              }
            }}
            type="button"
          >
            {label}
          </button>
        ))}
      </div>

      {mode === 'demo' ? (
        <p className="preview-panel-hint">
          内置 examples/input_slot_demo 的三个插槽演示。切换到其他模式可预览自己的插件代码。
        </p>
      ) : null}

      {mode === 'paste' ? (
        <div className="preview-panel-editors">
          <label className="preview-panel-label">
            main.js（必须导出 registerToolPkg）
            <textarea
              onChange={(event) => setMainSource(event.target.value)}
              placeholder={DEMO_MAIN_PLACEHOLDER}
              spellCheck={false}
              value={mainSource}
            />
          </label>
          <label className="preview-panel-label">
            Compose DSL screen 源码（可选，composeDsl.screen 的内容）
            <textarea
              onChange={(event) => setScreenSource(event.target.value)}
              placeholder="function SlotScreen(ctx) { const { UI } = ctx; return UI.Text({ text: 'hi' }); }"
              spellCheck={false}
              value={screenSource}
            />
          </label>
          <button className="preview-panel-apply" onClick={applyPasted} type="button">
            应用到模拟器
          </button>
        </div>
      ) : null}

      {mode === 'upload' ? (
        <div className="preview-panel-editors">
          <p className="preview-panel-hint">
            选择 .toolpkg（ZIP）：读取 manifest.json 与 main.js，
            composeDsl.screen 从包内文件解析。文件不会离开浏览器。
          </p>
          <input
            accept=".toolpkg,.zip"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) {
                void applyUpload(file);
              }
              event.target.value = '';
            }}
            ref={fileInputRef}
            type="file"
          />
        </div>
      ) : null}

      <div className="preview-panel-status">{status}</div>
      {error ? <pre className="preview-panel-error">{error}</pre> : null}
      <p className="preview-panel-note">
        预览说明：DSL 渲染器为白名单近似（Card/Column/Row/Box/Text/Button/Surface/Icon/Switch/LinearProgressIndicator/Spacer），
        与真机 Compose 存在布局细节差异。
      </p>
    </aside>
  );
}
