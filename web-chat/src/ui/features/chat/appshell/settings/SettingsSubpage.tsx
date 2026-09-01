import type { ReactNode } from 'react';
import type { SettingsIcon } from './settingsItems';

// 设置子页通用骨架原子，视觉对齐 app：
// 分组卡 12dp 圆角 surfaceVariant 30%、开关行 6dp 圆角 surface 50%、
// SectionTitle = 图标 primary + titleMedium Bold

export function SectionTitle({
  icon: Icon,
  title
}: {
  icon?: SettingsIcon;
  title: string;
}) {
  return (
    <div className="settings-section-title">
      {Icon ? <Icon size={18} /> : null}
      <h4>{title}</h4>
    </div>
  );
}

export function SettingsCard({
  children,
  className = ''
}: {
  children: ReactNode;
  className?: string;
}) {
  return <div className={`settings-card ${className}`}>{children}</div>;
}

export function SwitchRow({
  title,
  subtitle,
  checked,
  onChange
}: {
  title: string;
  subtitle?: string;
  checked: boolean;
  onChange?: (checked: boolean) => void;
}) {
  return (
    <label className="settings-switch-row">
      <span className="settings-switch-copy">
        <span>{title}</span>
        {subtitle ? <small>{subtitle}</small> : null}
      </span>
      <input
        checked={checked}
        onChange={onChange ? (event) => onChange(event.target.checked) : undefined}
        type="checkbox"
      />
    </label>
  );
}

export function SliderRow({
  label,
  value,
  min = 0,
  max = 100,
  step = 1,
  valueLabel,
  onChange
}: {
  label: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  valueLabel?: string;
  onChange?: (value: number) => void;
}) {
  return (
    <div className="settings-slider-row">
      <div className="settings-slider-head">
        <span>{label}</span>
        <code>{valueLabel ?? String(value)}</code>
      </div>
      <input
        max={max}
        min={min}
        onChange={onChange ? (event) => onChange(Number(event.target.value)) : undefined}
        step={step}
        type="range"
        value={value}
      />
    </div>
  );
}

export function NumberRow({
  label,
  unit,
  value
}: {
  label: string;
  unit: string;
  value: number | string;
}) {
  return (
    <div className="settings-number-row">
      <span>{label}</span>
      <span className="settings-number-field">
        <input readOnly type="text" value={String(value)} />
        <small>{unit}</small>
      </span>
    </div>
  );
}

export function TextFieldRow({
  label,
  value,
  placeholder
}: {
  label: string;
  value?: string;
  placeholder?: string;
}) {
  return (
    <div className="settings-textfield-row">
      <span>{label}</span>
      <input placeholder={placeholder} readOnly type="text" value={value ?? ''} />
    </div>
  );
}

export function OptionCards<T extends string>({
  options,
  value,
  onChange
}: {
  options: { id: T; label: string; sublabel?: string }[];
  value: T;
  onChange?: (id: T) => void;
}) {
  return (
    <div className="settings-option-cards">
      {options.map((option) => (
        <button
          className={`settings-option-card ${option.id === value ? 'is-selected' : ''}`}
          key={option.id}
          onClick={onChange ? () => onChange(option.id) : undefined}
          type="button"
        >
          <strong>{option.label}</strong>
          {option.sublabel ? <small>{option.sublabel}</small> : null}
        </button>
      ))}
    </div>
  );
}

export function RadioList<T extends string>({
  options,
  value,
  onChange
}: {
  options: { id: T; label: string; sublabel?: string }[];
  value: T;
  onChange?: (id: T) => void;
}) {
  return (
    <div className="settings-radio-list">
      {options.map((option) => (
        <button
          className={`settings-radio-item ${option.id === value ? 'is-selected' : ''}`}
          key={option.id}
          onClick={onChange ? () => onChange(option.id) : undefined}
          type="button"
        >
          <span>
            <span>{option.label}</span>
            {option.sublabel ? <small>{option.sublabel}</small> : null}
          </span>
          <span className="settings-radio-check" />
        </button>
      ))}
    </div>
  );
}

export function InfoBanner({ text }: { text: string }) {
  return <div className="settings-info-banner">{text}</div>;
}

export function SubpageScaffold({
  children
}: {
  children: ReactNode;
}) {
  return (
    <div className="settings-subpage">
      <div className="settings-subpage-scroll">{children}</div>
    </div>
  );
}
