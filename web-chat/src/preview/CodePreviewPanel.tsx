import { useRef, useState } from 'react';
import type { InputSlotContentMap } from '../ui/features/chat/composedsl/composeDslTypes';
import type { InputSlotName } from '../ui/features/chat/composedsl/composeDslTypes';
import type { PreviewRuntimeState } from './main';
import { buildRunnerPayload, resolveSlotContent, runToolPkgMain } from './slotRunner';
import { findFileBySuffix, readToolpkgZip } from './toolpkgLoader';
import { SLOT_TEMPLATES, buildToolpkgSources } from './templates';
import type { SlotTemplate } from './templates';
import { createStoredZip } from './zipWriter';

type PanelMode = 'demo' | 'paste' | 'template' | 'upload';

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
  onContentsChange,
  onResetDemo,
  runtimeState
}: {
  onContentsChange: (contents: InputSlotContentMap) => void;
  onResetDemo: () => void;
  runtimeState: PreviewRuntimeState;
}) {
  const [mode, setMode] = useState<PanelMode>('demo');
  const [mainSource, setMainSource] = useState('');
  const [screenSource, setScreenSource] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string>('演示模式');
  const [templateId, setTemplateId] = useState<string>(SLOT_TEMPLATES[0].id);
  const [templateSlot, setTemplateSlot] = useState<InputSlotName>(
    SLOT_TEMPLATES[0].slots[0]
  );
  const [templateValues, setTemplateValues] = useState<
    Record<string, string | number | boolean>
  >({});
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const template: SlotTemplate =
    SLOT_TEMPLATES.find((item) => item.id === templateId) ?? SLOT_TEMPLATES[0];

  function applyTemplateToSimulator() {
    setError(null);
    try {
      const state = template.buildState(templateValues);
      const sources = buildToolpkgSources({
        template,
        slot: templateSlot,
        pluginId: `${template.id}_${Date.now().toString(36)}`,
        displayName: `${template.name}（Preview Studio）`,
        state
      });
      const registrations = runToolPkgMain(sources.mainSource);
      runRegistrations(registrations, () => sources.screenSource);
    } catch (templateError: unknown) {
      console.error('applyTemplateToSimulator failed', templateError);
      setError((templateError as Error).message);
      setStatus('模板预览失败');
    }
  }

  function downloadTemplateToolpkg() {
    setError(null);
    try {
      const state = template.buildState(templateValues);
      const sources = buildToolpkgSources({
        template,
        slot: templateSlot,
        pluginId: `${template.id}_${Date.now().toString(36)}`,
        displayName: `${template.name}（Preview Studio）`,
        state
      });
      const encoder = new TextEncoder();
      const blob = createStoredZip([
        { name: 'manifest.json', bytes: encoder.encode(sources.manifestJson) },
        { name: 'dist/main.js', bytes: encoder.encode(sources.mainSource) },
        { name: sources.screenPath, bytes: encoder.encode(sources.screenSource) }
      ]);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `${template.id}-slot-plugin.toolpkg`;
      anchor.click();
      URL.revokeObjectURL(url);
      setStatus('已生成 .toolpkg 下载');
    } catch (downloadError: unknown) {
      console.error('downloadTemplateToolpkg failed', downloadError);
      setError((downloadError as Error).message);
      setStatus('生成失败');
    }
  }

  function basePayload() {
    // 手机视口（iframe）上报的运行状态，与真机 payload 字段一致
    return {
      chatId: runtimeState.chatId,
      inputStyle: runtimeState.inputStyle,
      isProcessing: runtimeState.isProcessing,
      inputText: runtimeState.inputText
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
            ['template', '模板生成'],
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

      {mode === 'template' ? (
        <div className="preview-panel-editors">
          <label className="preview-panel-label">
            模板
            <select
              onChange={(event) => {
                const nextTemplate =
                  SLOT_TEMPLATES.find((item) => item.id === event.target.value) ??
                  SLOT_TEMPLATES[0];
                setTemplateId(nextTemplate.id);
                setTemplateSlot(nextTemplate.slots[0]);
                setTemplateValues({});
              }}
              value={template.id}
            >
              {SLOT_TEMPLATES.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <p className="preview-panel-hint">{template.description}</p>
          <label className="preview-panel-label">
            插槽位置
            <select
              onChange={(event) => setTemplateSlot(event.target.value as InputSlotName)}
              value={templateSlot}
            >
              {template.slots.map((slot) => (
                <option key={slot} value={slot}>
                  {slot}
                </option>
              ))}
            </select>
          </label>
          {template.fields.map((field) => (
            <label className="preview-panel-label" key={field.key}>
              {field.label}
              {field.type === 'multiline' ? (
                <textarea
                  onChange={(event) =>
                    setTemplateValues((current) => ({
                      ...current,
                      [field.key]: event.target.value
                    }))
                  }
                  placeholder={field.placeholder}
                  spellCheck={false}
                  value={String(templateValues[field.key] ?? '')}
                />
              ) : field.type === 'boolean' ? (
                <input
                  checked={templateValues[field.key] === true}
                  onChange={(event) =>
                    setTemplateValues((current) => ({
                      ...current,
                      [field.key]: event.target.checked
                    }))
                  }
                  type="checkbox"
                />
              ) : (
                <input
                  onChange={(event) =>
                    setTemplateValues((current) => ({
                      ...current,
                      [field.key]:
                        field.type === 'number'
                          ? Number(event.target.value)
                          : event.target.value
                    }))
                  }
                  placeholder={field.placeholder}
                  type={field.type === 'number' ? 'number' : 'text'}
                  value={String(templateValues[field.key] ?? '')}
                />
              )}
            </label>
          ))}
          <div className="preview-panel-actions">
            <button
              className="preview-panel-apply"
              onClick={applyTemplateToSimulator}
              type="button"
            >
              预览到模拟器
            </button>
            <button
              className="preview-panel-secondary"
              onClick={downloadTemplateToolpkg}
              type="button"
            >
              下载 .toolpkg
            </button>
          </div>
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
