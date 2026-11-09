export type MeterTone = 'signal' | 'warn' | 'alert' | 'violet';

export interface MeterProps {
  value: number;
  max: number;
  tone?: MeterTone;
  label?: string;
}

export function Meter({ value, max, tone = 'signal', label }: MeterProps) {
  const ratio = max > 0 ? Math.max(0, Math.min(1, value / max)) : 0;
  return (
    <div className="meter" role="img" aria-label={label ?? `${value} of ${max}`}>
      <div className={`meter__fill meter__fill--${tone}`} style={{ width: `${ratio * 100}%` }} />
    </div>
  );
}
