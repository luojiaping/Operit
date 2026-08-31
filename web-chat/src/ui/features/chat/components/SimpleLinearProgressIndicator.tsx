export function SimpleLinearProgressIndicator({
  color,
  progress
}: {
  color?: string;
  progress: number;
}) {
  const normalizedProgress = Math.max(0, Math.min(1, progress));

  return (
    <div className="simple-linear-progress">
      <div
        className="simple-linear-progress-bar"
        style={{
          width: `${normalizedProgress * 100}%`,
          ...(color ? { background: color } : null)
        }}
      />
    </div>
  );
}
