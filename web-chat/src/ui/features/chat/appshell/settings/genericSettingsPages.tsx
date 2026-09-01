import { InfoBanner, NumberRow, OptionCards, SectionTitle, SettingsCard, SliderRow, SwitchRow, TextFieldRow } from './SettingsSubpage';

// 其余设置子页的骨架装配：结构与视觉对齐 app 各子页，
// 内容为演示数据（纯 UI），主题类深度定制在 ThemeSettingsPage
export function GenericSettingsPage({ page }: { page: string }) {
  switch (page) {
    case 'github-account':
      return <GitHubAccountPage />;
    case 'user-preferences':
      return <UserPreferencesPage />;
    case 'language':
      return <LanguagePage />;
    case 'global-display':
      return <GlobalDisplayPage />;
    case 'layout-adjustment':
      return <LayoutAdjustmentPage />;
    case 'model-config':
      return <ModelConfigPage />;
    case 'functional-config':
      return <FunctionalConfigPage />;
    case 'speech-services':
      return <SpeechServicesPage />;
    case 'model-prompts':
      return <ModelPromptsPage />;
    case 'persona-card':
      return <PersonaCardPage />;
    case 'waifu-mode':
      return <WaifuModePage />;
    case 'context-summary':
      return <ContextSummaryPage />;
    case 'tool-permission':
      return <ToolPermissionPage />;
    case 'chat-backup':
      return <ChatBackupPage />;
    case 'chat-history':
      return <ChatHistoryPage />;
    case 'token-usage':
      return <TokenUsagePage />;
    case 'clear-cookie':
      return <ClearCookiePage />;
    case 'external-http':
      return <ExternalHttpPage />;
    default:
      return <PlaceholderPage page={page} />;
  }
}

function Page({ children }: { children: React.ReactNode }) {
  return (
    <div className="settings-subpage">
      <div className="settings-subpage-scroll">{children}</div>
    </div>
  );
}

function GitHubAccountPage() {
  return (
    <Page>
      <SettingsCard>
        <div className="settings-account-hero">
          <span className="settings-account-avatar" />
          <strong>访客</strong>
          <small>未登录</small>
          <span className="settings-account-email">-</span>
        </div>
      </SettingsCard>
      <section>
        <SectionTitle title="账号信息" />
        <SettingsCard>
          <div className="settings-readonly-row">
            <span>ID</span>
            <code>-</code>
          </div>
          <div className="settings-readonly-row">
            <span>公开仓库</span>
            <code>-</code>
          </div>
          <div className="settings-readonly-row">
            <span>关注者</span>
            <code>-</code>
          </div>
        </SettingsCard>
      </section>
      <InfoBanner text="登录 GitHub 后可启用云端备份与 Gist 同步。预览站内此页为界面演示。" />
    </Page>
  );
}

function UserPreferencesPage() {
  return (
    <Page>
      <SettingsCard>
        <div className="settings-profile-toolbar">
          <span>当前资料：默认</span>
        </div>
      </SettingsCard>
      <section>
        <SectionTitle title="资料编辑" />
        <SettingsCard>
          <label className="settings-md-editor">
            <textarea
              defaultValue={'# 用户资料\n\n- 称呼：开发者\n- 偏好：简洁回复\n- 语言：中文'}
              readOnly
              spellCheck={false}
            />
          </label>
          <div className="settings-editor-meta">
            <span>编辑 / 预览</span>
            <code>62 字</code>
          </div>
        </SettingsCard>
      </section>
    </Page>
  );
}

function LanguagePage() {
  return (
    <Page>
      <section>
        <SectionTitle title="选择语言" />
        <SettingsCard>
          <div className="settings-radio-item is-selected">
            <span>
              <span>简体中文</span>
              <small>Simplified Chinese</small>
            </span>
            <span className="settings-radio-check" />
          </div>
          <div className="settings-radio-item">
            <span>
              <span>English</span>
              <small>英语</small>
            </span>
            <span className="settings-radio-check" />
          </div>
          <div className="settings-radio-item">
            <span>
              <span>日本語</span>
              <small>日语</small>
            </span>
            <span className="settings-radio-check" />
          </div>
        </SettingsCard>
      </section>
      <InfoBanner text="语言切换立即生效，无需重启。预览站内此页为界面演示。" />
    </Page>
  );
}

function GlobalDisplayPage() {
  return (
    <Page>
      <section>
        <SectionTitle title="消息显示设置" />
        <SettingsCard>
          <SliderRow label="折叠工具详情" value={1} valueLabel="平衡" />
          <TextFieldRow label="全局用户名" value="开发者" />
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="系统显示设置" />
        <SettingsCard>
          <SwitchRow checked title="回复通知" />
          <SwitchRow checked title="回车发送" />
          <SwitchRow checked={false} title="长文本转文件" />
          <SliderRow label="网页等待秒数" value={3} valueLabel="3 秒" />
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="自动化显示与行为" />
        <SettingsCard>
          <SwitchRow checked={false} subtitle="实验特性：虚拟屏执行" title="实验虚拟屏" />
          <SwitchRow checked title="屏幕常亮" />
        </SettingsCard>
      </section>
    </Page>
  );
}

function LayoutAdjustmentPage() {
  return (
    <Page>
      <InfoBanner text="布局调整影响全局边距与文本排版，单位 dp / 倍数。预览站内此页为界面演示。" />
      <section>
        <SectionTitle title="布局调整设置" />
        <SettingsCard>
          <NumberRow label="聊天设置按钮间距" unit="dp" value={8} />
          <NumberRow label="聊天区水平边距" unit="dp" value={16} />
          <NumberRow label="全局文本行高" unit="x" value={1.4} />
          <NumberRow label="全局字距" unit="sp" value={0} />
          <NumberRow label="段落间距" unit="dp" value={6} />
        </SettingsCard>
      </section>
    </Page>
  );
}

function ModelConfigPage() {
  return (
    <Page>
      <SettingsCard>
        <div className="settings-config-header">
          <strong>选择模型配置</strong>
          <span className="settings-outline-button">新建</span>
        </div>
        <div className="settings-config-dropdown">主力配置</div>
        <div className="settings-config-actions">
          <span>重命名</span>
          <span>删除</span>
          <span>测试连接</span>
        </div>
      </SettingsCard>
      <section>
        <SectionTitle title="API 设置" />
        <SettingsCard>
          <TextFieldRow label="接口地址" value="https://api.example.com/v1" />
          <TextFieldRow label="API Key" value="sk-•••••••••••••••" />
          <TextFieldRow label="模型名称" value="operit-preview-model" />
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="模型参数" />
        <SettingsCard>
          <SliderRow label="温度" max={2} min={0} step={0.1} value={0.7} valueLabel="0.7" />
          <NumberRow label="最大输出长度" unit="K" value={8} />
        </SettingsCard>
      </section>
    </Page>
  );
}

function FunctionalConfigPage() {
  return (
    <Page>
      <InfoBanner text="为不同功能（总结、记忆、搜索等）指定独立的模型配置。" />
      <section>
        <SectionTitle title="功能映射" />
        <SettingsCard>
          <div className="settings-function-row">
            <span>上下文总结</span>
            <code>主力配置</code>
          </div>
          <div className="settings-function-row">
            <span>记忆更新</span>
            <code>快速配置</code>
          </div>
          <div className="settings-function-row">
            <span>搜索摘要</span>
            <code>主力配置</code>
          </div>
        </SettingsCard>
      </section>
    </Page>
  );
}

function SpeechServicesPage() {
  return (
    <Page>
      <div className="settings-tabs">
        <button className="settings-tab is-active" type="button">
          TTS
        </button>
        <button className="settings-tab" type="button">
          STT
        </button>
      </div>
      <section>
        <SectionTitle title="服务配置" />
        <SettingsCard>
          <TextFieldRow label="服务类型" value="系统内置" />
          <TextFieldRow label="接口地址" value="-" />
          <TextFieldRow label="API Key" value="-" />
          <span className="settings-outline-button">测试语音</span>
        </SettingsCard>
      </section>
    </Page>
  );
}

function ModelPromptsPage() {
  return (
    <Page>
      <div className="settings-tabs">
        <button className="settings-tab is-active" type="button">
          角色卡
        </button>
        <button className="settings-tab" type="button">
          标签
        </button>
        <button className="settings-tab" type="button">
          群组
        </button>
      </div>
      <section>
        <SettingsCard>
          <div className="settings-character-card">
            <span className="settings-character-avatar" />
            <span className="settings-character-copy">
              <strong>默认助手</strong>
              <small>通用编程与写作助手</small>
            </span>
            <span className="settings-chip is-active">当前激活</span>
            <span className="settings-more">⋮</span>
          </div>
          <div className="settings-character-card">
            <span className="settings-character-avatar" />
            <span className="settings-character-copy">
              <strong>氛围模式</strong>
              <small>演示背景图与气泡主题</small>
            </span>
            <span className="settings-more">⋮</span>
          </div>
        </SettingsCard>
      </section>
    </Page>
  );
}

function PersonaCardPage() {
  return (
    <Page>
      <InfoBanner text="通过与 AI 对话逐步生成个性人设卡；左侧抽屉编辑字段，右侧对话生成。" />
      <section>
        <SectionTitle title="字段模板" />
        <SettingsCard>
          <TextFieldRow label="名称" value="" placeholder="角色名称" />
          <TextFieldRow label="描述" value="" placeholder="一句话描述" />
          <TextFieldRow label="人物设定" value="" placeholder="性格与背景" />
          <TextFieldRow label="开场白" value="" placeholder="对话开场" />
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="对话生成" />
        <div className="settings-placeholder">对话生成区（演示占位）</div>
      </section>
    </Page>
  );
}

function WaifuModePage() {
  return (
    <Page>
      <SettingsCard>
        <div className="settings-waifu-hero">
          <strong>Waifu 模式</strong>
          <small>AI 回复按分句逐条发送，模拟聊天软件的对话节奏</small>
        </div>
      </SettingsCard>
      <section>
        <SectionTitle title="分句参数" />
        <SettingsCard>
          <SwitchRow checked={false} title="启用 Waifu 模式" />
          <SwitchRow checked title="去除空行" />
          <SwitchRow checked={false} subtitle="为分句附加随机表情" title="附加表情" />
        </SettingsCard>
      </section>
    </Page>
  );
}

function ContextSummaryPage() {
  return (
    <Page>
      <InfoBanner text="上下文窗口决定单次对话可携带的历史长度，自动总结在临近上限时压缩历史。" />
      <section>
        <SectionTitle title="上下文设置" />
        <SettingsCard>
          <NumberRow label="上下文窗口" unit="K" value={64} />
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="总结设置" />
        <SettingsCard>
          <SwitchRow checked title="自动总结" />
          <NumberRow label="触发阈值" unit="%" value={80} />
        </SettingsCard>
      </section>
    </Page>
  );
}

function ToolPermissionPage() {
  return (
    <Page>
      <section>
        <SectionTitle title="全局工具权限" />
        <SettingsCard>
          <OptionCards
            options={[
              { id: 'forbid', label: '拒绝' },
              { id: 'ask', label: '询问' },
              { id: 'allow', label: '允许' }
            ]}
            value="ask"
          />
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="允许的工具" />
        <SettingsCard>
          <div className="settings-chip-row">
            {['文件读取', '终端命令', '浏览器'].map((name) => (
              <span className="settings-chip" key={name}>
                {name}
              </span>
            ))}
          </div>
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="询问的工具" />
        <SettingsCard>
          <div className="settings-chip-row">
            {['发送通知', '安装应用'].map((name) => (
              <span className="settings-chip" key={name}>
                {name}
              </span>
            ))}
          </div>
        </SettingsCard>
      </section>
    </Page>
  );
}

function ChatBackupPage() {
  return (
    <Page>
      <section>
        <SectionTitle title="总览" />
        <SettingsCard>
          <div className="settings-stat-grid">
            <div>
              <strong>3</strong>
              <span>会话</span>
            </div>
            <div>
              <strong>4</strong>
              <span>消息</span>
            </div>
            <div>
              <strong>2</strong>
              <span>记忆库</span>
            </div>
          </div>
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="数据管理" />
        <SettingsCard>
          <div className="settings-action-row">导出聊天数据</div>
          <div className="settings-action-row">导入聊天数据</div>
          <div className="settings-action-row is-danger">删除全部聊天记录</div>
        </SettingsCard>
      </section>
    </Page>
  );
}

function ChatHistoryPage() {
  return (
    <Page>
      <section>
        <SectionTitle title="聊天记录概览" />
        <SettingsCard>
          <div className="settings-stat-grid">
            <div>
              <strong>3</strong>
              <span>会话</span>
            </div>
            <div>
              <strong>1</strong>
              <span>分组</span>
            </div>
            <div>
              <strong>1</strong>
              <span>锁定</span>
            </div>
          </div>
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="批量管理" />
        <div className="settings-placeholder">批量选择与整理（演示占位）</div>
      </section>
    </Page>
  );
}

function TokenUsagePage() {
  return (
    <Page>
      <section>
        <SectionTitle title="周期总览" />
        <SettingsCard>
          <div className="settings-stat-grid">
            <div>
              <strong>12.4K</strong>
              <span>输入 tokens</span>
            </div>
            <div>
              <strong>8.9K</strong>
              <span>输出 tokens</span>
            </div>
            <div>
              <strong>¥1.28</strong>
              <span>估算花费</span>
            </div>
          </div>
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="趋势" />
        <div className="settings-chart-placeholder">趋势图（演示占位）</div>
      </section>
    </Page>
  );
}

function ClearCookiePage() {
  return (
    <Page>
      <InfoBanner text="将清除搜索工具、Browser 包与内置浏览器保存的 Cookie。" />
      <section>
        <SectionTitle title="清除 Cookie" />
        <SettingsCard>
          <div className="settings-action-row">立即清除</div>
        </SettingsCard>
      </section>
    </Page>
  );
}

function ExternalHttpPage() {
  return (
    <Page>
      <section>
        <SectionTitle title="HTTP 聊天接口" />
        <SettingsCard>
          <SwitchRow checked title="启用外部调用" />
          <TextFieldRow label="端口" value="8094" />
          <TextFieldRow label="Bearer Token" value="••••••••" />
        </SettingsCard>
      </section>
      <section>
        <SectionTitle title="访问示例" />
        <SettingsCard>
          <pre className="settings-code-block">curl -X POST "http://DEVICE_IP:8094/api/external-chat" \
  -H "Authorization: Bearer YOUR_TOKEN"</pre>
        </SettingsCard>
      </section>
    </Page>
  );
}

function PlaceholderPage({ page }: { page: string }) {
  return (
    <Page>
      <div className="settings-placeholder">{page} 页面骨架（建设中）</div>
    </Page>
  );
}
