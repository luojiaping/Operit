import type { WebChatMessage, WebThemeSnapshot } from '../util/chatTypes';

// A20：对齐 app ChatArea 的 MessageFooterBar——变体切换器与
// token、耗时、时间戳三行 labelSmall 统计（onSurfaceVariant 0.68）
function formatCompactDuration(durationMs: number) {
  if (durationMs <= 0) {
    return '0ms';
  }
  if (durationMs >= 1000) {
    if (durationMs >= 10_000) {
      return `${Math.round(durationMs / 1000)}s`;
    }
    return `${(durationMs / 1000).toFixed(1)}s`;
  }
  return `${durationMs}ms`;
}

function formatCompactTimestamp(completedAt: number) {
  const date = new Date(completedAt);
  const pad = (value: number) => String(value).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

export function hasMessageFooterContent(
  message: WebChatMessage,
  theme: WebThemeSnapshot | null
) {
  const display = theme?.display;
  const hasTokenStats =
    (display?.show_message_token_stats ?? false) &&
    ((message.input_tokens ?? 0) > 0 ||
      (message.cached_input_tokens ?? 0) > 0 ||
      (message.output_tokens ?? 0) > 0);
  const hasTimingStats =
    (display?.show_message_timing_stats ?? false) &&
    ((message.wait_duration_ms ?? 0) > 0 || (message.output_duration_ms ?? 0) > 0);
  const hasTimestamp =
    (display?.show_message_timestamp ?? false) && (message.completed_at ?? 0) > 0;
  const hasVariants = (message.variant_count ?? 1) > 1;
  return hasTokenStats || hasTimingStats || hasTimestamp || hasVariants;
}

export function MessageFooterBar({
  message,
  theme,
  onSelectVariant
}: {
  message: WebChatMessage;
  theme: WebThemeSnapshot | null;
  onSelectVariant?: (index: number) => void;
}) {
  const display = theme?.display;
  const inputTokens = message.input_tokens ?? 0;
  const cachedInputTokens = message.cached_input_tokens ?? 0;
  const outputTokens = message.output_tokens ?? 0;
  const waitDurationMs = message.wait_duration_ms ?? 0;
  const outputDurationMs = message.output_duration_ms ?? 0;
  const completedAt = message.completed_at ?? 0;
  const variantCount = message.variant_count ?? 1;
  const selectedVariantIndex = message.selected_variant_index ?? 0;
  const showVariants = variantCount > 1;
  const hasPrevious = selectedVariantIndex > 0;
  const hasNext = selectedVariantIndex < variantCount - 1;

  const showTokenStats =
    (display?.show_message_token_stats ?? false) &&
    (inputTokens > 0 || cachedInputTokens > 0 || outputTokens > 0);
  const showTimingStats =
    (display?.show_message_timing_stats ?? false) &&
    (waitDurationMs > 0 || outputDurationMs > 0);
  const showTimestamp = (display?.show_message_timestamp ?? false) && completedAt > 0;

  if (!showVariants && !showTokenStats && !showTimingStats && !showTimestamp) {
    return null;
  }

  return (
    <div className="message-footer-bar">
      {showVariants ? (
        <div className="message-footer-variants">
          <button
            aria-label="上一个变体"
            className={`message-footer-variant-button ${hasPrevious ? '' : 'is-disabled'}`}
            disabled={!hasPrevious}
            onClick={() => onSelectVariant?.(selectedVariantIndex - 1)}
            type="button"
          >
            ‹
          </button>
          <span>
            {selectedVariantIndex + 1}/{variantCount}
          </span>
          <button
            aria-label="下一个变体"
            className={`message-footer-variant-button ${hasNext ? '' : 'is-disabled'}`}
            disabled={!hasNext}
            onClick={() => onSelectVariant?.(selectedVariantIndex + 1)}
            type="button"
          >
            ›
          </button>
        </div>
      ) : null}
      {showTokenStats ? (
        <span className="message-footer-stat">
          token: {inputTokens + outputTokens} ↑ {inputTokens} (cache: {cachedInputTokens}) ↓{' '}
          {outputTokens}
        </span>
      ) : null}
      {showTimingStats ? (
        <span className="message-footer-stat">
          usage: {formatCompactDuration(Math.max(0, waitDurationMs + outputDurationMs))} (首{' '}
          {formatCompactDuration(waitDurationMs)} 生成 {formatCompactDuration(outputDurationMs)})
        </span>
      ) : null}
      {showTimestamp ? (
        <span className="message-footer-stat">time: {formatCompactTimestamp(completedAt)}</span>
      ) : null}
    </div>
  );
}
