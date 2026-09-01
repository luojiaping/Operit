import { useState } from 'react';
import type { WebThemeSnapshot } from '../../util/chatTypes';
import { deriveCustomPalette } from '../../util/chatTheme';
import {
  InfoBanner,
  OptionCards,
  SectionTitle,
  SettingsCard,
  SliderRow,
  SwitchRow
} from './SettingsSubpage';

// 主题设置页，结构对齐 app ThemeSettingsContentEditor：
// 主题对象选择卡 → 5 Tab（基础/背景/聊天/输入栏/界面栏）→ 保存/重置页脚。
// 预览站中控件即时经 onPatchTheme 生效，页脚按钮为占位
const TABS = ['基础', '背景', '聊天', '输入栏', '界面栏'] as const;

export function ThemeSettingsPage({
  theme,
  onPatchTheme
}: {
  theme: WebThemeSnapshot | null;
  onPatchTheme: (patch: Partial<WebThemeSnapshot>) => void;
}) {
  const [tab, setTab] = useState<(typeof TABS)[number]>('基础');
  if (!theme) {
    return null;
  }
  const snapshot = theme;

  function patchPalette(patch: Partial<WebThemeSnapshot['palette']>) {
    onPatchTheme({ palette: { ...snapshot.palette, ...patch } });
  }

  function patchBackground(patch: Partial<WebThemeSnapshot['background']>) {
    onPatchTheme({ background: { ...snapshot.background, ...patch } });
  }

  function patchBubble(patch: Partial<WebThemeSnapshot['bubble']>) {
    onPatchTheme({ bubble: { ...snapshot.bubble, ...patch } });
  }

  function patchInput(patch: Partial<WebThemeSnapshot['input']>) {
    onPatchTheme({ input: { ...snapshot.input, ...patch } });
  }

  const isLight = snapshot.theme_mode === 'light';

  return (
    <div className="settings-subpage">
      <div className="settings-subpage-scroll">
        <SettingsCard className="settings-theme-target">
          <div className="settings-theme-target-row">
            <span className="settings-theme-target-avatar" />
            <span>
              <strong>全局主题</strong>
              <small>所有会话默认使用</small>
            </span>
          </div>
        </SettingsCard>

        <div className="settings-tabs">
          {TABS.map((name) => (
            <button
              className={`settings-tab ${tab === name ? 'is-active' : ''}`}
              key={name}
              onClick={() => setTab(name)}
              type="button"
            >
              {name}
            </button>
          ))}
        </div>

        {tab === '基础' ? (
          <>
            <section>
              <SectionTitle title="主题模式" />
              <SettingsCard>
                <SwitchRow
                  checked={snapshot.use_system_theme}
                  onChange={(useSystem) =>
                    onPatchTheme({
                      use_system_theme: useSystem,
                      theme_mode: useSystem ? 'light' : snapshot.theme_mode
                    })
                  }
                  subtitle="关闭后可选择浅色或深色主题"
                  title="跟随系统主题"
                />
                {!snapshot.use_system_theme ? (
                  <OptionCards
                    onChange={(mode) => onPatchTheme({ theme_mode: mode })}
                    options={[
                      { id: 'light', label: '浅色主题' },
                      { id: 'dark', label: '深色主题' }
                    ]}
                    value={isLight ? 'light' : 'dark'}
                  />
                ) : null}
              </SettingsCard>
            </section>
            <section>
              <SectionTitle title="自定义配色" />
              <SettingsCard>
                <SwitchRow
                  checked={snapshot.use_custom_colors}
                  onChange={(useCustom) => onPatchTheme({ use_custom_colors: useCustom })}
                  subtitle="使用主色与次色自定义主题颜色"
                  title="使用自定义颜色"
                />
                {snapshot.use_custom_colors ? (
                  <>
                    <label className="settings-color-swatch">
                      <span>主色</span>
                      <input
                        onChange={(event) =>
                          patchPalette(
                            deriveCustomPalette(
                              event.target.value,
                              snapshot.palette.secondary_color,
                              snapshot.theme_mode === 'light'
                            )
                          )
                        }
                        type="color"
                        value={snapshot.palette.primary_color}
                      />
                      <code>{snapshot.palette.primary_color}</code>
                    </label>
                    <label className="settings-color-swatch">
                      <span>次色</span>
                      <input
                        onChange={(event) =>
                          patchPalette(
                            deriveCustomPalette(
                              snapshot.palette.primary_color,
                              event.target.value,
                              snapshot.theme_mode === 'light'
                            )
                          )
                        }
                        type="color"
                        value={snapshot.palette.secondary_color}
                      />
                      <code>{snapshot.palette.secondary_color}</code>
                    </label>
                  </>
                ) : null}
              </SettingsCard>
            </section>
            <section>
              <SectionTitle title="字体设置" />
              <SettingsCard>
                <SliderRow
                  label="字体缩放"
                  max={1.6}
                  min={0.8}
                  onChange={(scale) => onPatchTheme({ font: { ...snapshot.font, scale } })}
                  step={0.05}
                  value={snapshot.font.scale}
                  valueLabel={`${snapshot.font.scale.toFixed(2)}x`}
                />
              </SettingsCard>
            </section>
          </>
        ) : null}

        {tab === '背景' ? (
          <>
            <section>
              <SectionTitle title="聊天背景" />
              <SettingsCard>
                <OptionCards
                  onChange={(type) => patchBackground({ type })}
                  options={[
                    { id: 'image', label: '图片' },
                    { id: 'video', label: '视频' }
                  ]}
                  value={snapshot.background.type === 'video' ? 'video' : 'image'}
                />
                {snapshot.background.asset_url ? (
                  <>
                    <SliderRow
                      label="背景不透明度"
                      max={1}
                      min={0.05}
                      onChange={(opacity) => patchBackground({ opacity })}
                      step={0.05}
                      value={snapshot.background.opacity}
                      valueLabel={`${Math.round(snapshot.background.opacity * 100)}%`}
                    />
                    <SwitchRow
                      checked={snapshot.background.use_blur ?? false}
                      onChange={(useBlur) => patchBackground({ use_blur: useBlur })}
                      title="背景模糊"
                    />
                    {snapshot.background.use_blur ? (
                      <SliderRow
                        label="模糊半径"
                        max={30}
                        min={0}
                        onChange={(radius) => patchBackground({ blur_radius: radius })}
                        step={1}
                        value={snapshot.background.blur_radius ?? 10}
                        valueLabel={`${snapshot.background.blur_radius ?? 10} dp`}
                      />
                    ) : null}
                  </>
                ) : (
                  <InfoBanner text="当前未设置背景媒体。真机可在相册中选择图片或视频，预览站可从左侧主题面板上传本地图片。" />
                )}
              </SettingsCard>
            </section>
          </>
        ) : null}

        {tab === '聊天' ? (
          <>
            <section>
              <SectionTitle title="聊天风格" />
              <SettingsCard>
                <OptionCards
                  onChange={(style) => onPatchTheme({ chat_style: style })}
                  options={[
                    { id: 'cursor', label: '光标', sublabel: '终端式' },
                    { id: 'bubble', label: '气泡', sublabel: '经典气泡' }
                  ]}
                  value={snapshot.chat_style === 'cursor' ? 'cursor' : 'bubble'}
                />
              </SettingsCard>
            </section>
            <section>
              <SectionTitle title="气泡设置" />
              <SettingsCard>
                <SwitchRow
                  checked={snapshot.bubble.show_avatar}
                  onChange={(showAvatar) => patchBubble({ show_avatar: showAvatar })}
                  title="显示头像"
                />
                <SwitchRow
                  checked={snapshot.bubble.wide_layout}
                  onChange={(wide) => patchBubble({ wide_layout: wide })}
                  subtitle="头像与名称在气泡上方"
                  title="宽布局"
                />
                <SwitchRow
                  checked={snapshot.bubble.user_rounded}
                  onChange={(rounded) =>
                    patchBubble({ user_rounded: rounded, assistant_rounded: rounded })
                  }
                  title="圆角气泡"
                />
                <SwitchRow
                  checked={snapshot.bubble.user_liquid_glass}
                  onChange={(liquid) => patchBubble({ user_liquid_glass: liquid })}
                  title="用户气泡液态玻璃"
                />
                <SwitchRow
                  checked={snapshot.bubble.assistant_liquid_glass}
                  onChange={(liquid) => patchBubble({ assistant_liquid_glass: liquid })}
                  title="AI 气泡液态玻璃"
                />
              </SettingsCard>
            </section>
          </>
        ) : null}

        {tab === '输入栏' ? (
          <section>
            <SectionTitle title="输入框样式" />
            <SettingsCard>
              <OptionCards
                onChange={(style) => patchInput({ style })}
                options={[
                  { id: 'classic', label: '经典', sublabel: '单行输入条' },
                  { id: 'agent', label: 'Agent', sublabel: '卡片式输入区' }
                ]}
                value={snapshot.input.style === 'classic' ? 'classic' : 'agent'}
              />
            </SettingsCard>
            <div className="settings-card" style={{ marginTop: 12 }}>
              <SwitchRow
                checked={snapshot.input.transparent}
                onChange={(transparent) => patchInput({ transparent })}
                title="透明输入栏"
              />
              <SwitchRow
                checked={snapshot.input.floating}
                onChange={(floating) => patchInput({ floating })}
                title="悬浮输入栏"
              />
              {snapshot.input.transparent ? (
                <SwitchRow
                  checked={snapshot.input.liquid_glass}
                  onChange={(liquid) => patchInput({ liquid_glass: liquid })}
                  subtitle="透明模式下可叠加磨砂玻璃效果"
                  title="液态玻璃"
                />
              ) : null}
            </div>
          </section>
        ) : null}

        {tab === '界面栏' ? (
          <section>
            <SectionTitle title="界面栏颜色" />
            <SettingsCard>
              <InfoBanner text="界面栏颜色定制在真机包含状态栏、工具栏与导航抽屉的水玻璃、液态玻璃开关与各色定制；预览站先提供工具栏与抽屉的开关演示。" />
              <SwitchRow
                checked={snapshot.header.transparent}
                onChange={(transparent) =>
                  onPatchTheme({ header: { ...snapshot.header, transparent } })
                }
                title="透明工具栏"
              />
            </SettingsCard>
          </section>
        ) : null}
      </div>
      <div className="settings-footer-actions">
        <button className="settings-reset-button" type="button">
          重置
        </button>
        <button className="settings-save-button" type="button">
          保存
        </button>
      </div>
    </div>
  );
}
