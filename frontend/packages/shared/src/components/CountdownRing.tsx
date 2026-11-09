export interface CountdownRingProps {
  /** Milliseconds still remaining on the block. */
  remainingMs: number;
  /** Total lifetime of the block, used for the sweep fraction. */
  totalMs: number;
  size?: number;
  label?: string;
}

function formatRemaining(ms: number): string {
  if (ms <= 0) {
    return '0s';
  }
  const totalSeconds = Math.ceil(ms / 1000);
  if (totalSeconds < 60) {
    return `${totalSeconds}s`;
  }
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${String(seconds).padStart(2, '0')}s`;
}

/**
 * Countdown ring for an active source block. The sweep is the real remaining fraction of the
 * block lifetime; it stops under `prefers-reduced-motion` and the number is always present.
 */
export function CountdownRing({ remainingMs, totalMs, size = 56, label }: CountdownRingProps) {
  const clamped = Math.max(0, Math.min(1, totalMs > 0 ? remainingMs / totalMs : 0));
  const radius = (size - 6) / 2;
  const circumference = 2 * Math.PI * radius;
  const dash = circumference * clamped;
  const centre = size / 2;

  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label ?? 'Block time remaining'}>
        <circle cx={centre} cy={centre} r={radius} fill="none" stroke="rgba(148,163,184,0.18)" strokeWidth="2.5" />
        <circle
          cx={centre}
          cy={centre}
          r={radius}
          fill="none"
          stroke="var(--alert)"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circumference - dash}`}
          transform={`rotate(-90 ${centre} ${centre})`}
        />
      </svg>
      <span className="ring__value mono">{formatRemaining(remainingMs)}</span>
    </div>
  );
}
