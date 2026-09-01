import { ChevronRightIcon } from '../../util/chatIcons';
import { SETTINGS_SECTIONS } from './settingsItems';
import type { SettingsItem } from './settingsItems';

// 设置主页：分组卡片列表，对齐 app SettingsScreen.kt（无页面内大标题，
// 标题由全局顶栏承载；Column padding h16/v8 滚动）
export function SettingsHome({
  onOpenItem
}: {
  onOpenItem: (item: SettingsItem) => void;
}) {
  return (
    <div className="settings-home">
      {SETTINGS_SECTIONS.map((section) => (
        <section key={section.id}>
          <div className="settings-section-title">
            <section.icon size={18} />
            <h4>{section.title}</h4>
          </div>
          <div className="settings-card">
            {section.items.map((item) => (
              <button
                className="settings-item"
                key={item.id}
                onClick={() => onOpenItem(item)}
                type="button"
              >
                <item.icon size={20} />
                <span className="settings-item-copy">
                  <span>{item.title}</span>
                  <small>{item.subtitle}</small>
                </span>
                <ChevronRightIcon size={16} />
              </button>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
