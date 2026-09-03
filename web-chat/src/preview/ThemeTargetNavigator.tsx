import {
  THEME_TARGETS,
  themeTargetGroupLabel,
  type ThemeTargetId
} from './themeTargets';

export function ThemeTargetNavigator({
  selectedTarget,
  onSelectTarget
}: {
  selectedTarget: ThemeTargetId;
  onSelectTarget: (target: ThemeTargetId) => void;
}) {
  return (
    <section className="studio-target-navigator">
      <div className="studio-target-navigator-heading">
        <div>
          <span className="studio-eyebrow">Inspector</span>
          <strong>聊天界面组件</strong>
        </div>
        <span className="studio-target-navigator-hint">点击左侧预览可直接定位</span>
      </div>
      <div className="studio-target-groups">
        {(['global', 'chrome', 'conversation', 'composer', 'overlay'] as const).map((group) => {
          const targets = THEME_TARGETS.filter((target) => target.group === group);
          return (
            <div className="studio-target-group" key={group}>
              <span>{themeTargetGroupLabel(group)}</span>
              <div className="studio-target-buttons">
                {targets.map((target) => (
                  <button
                    className={selectedTarget === target.id ? 'is-active' : ''}
                    key={target.id}
                    onClick={() => onSelectTarget(target.id)}
                    type="button"
                  >
                    {target.label}
                  </button>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
