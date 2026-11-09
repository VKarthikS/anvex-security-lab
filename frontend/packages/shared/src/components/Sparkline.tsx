import { useId } from 'react';

export type SparklineTone = 'signal' | 'warn' | 'alert' | 'violet';

export interface SparklineProps {
  /** Rolling samples, oldest first. */
  values: number[];
  width?: number;
  height?: number;
  tone?: SparklineTone;
  ariaLabel?: string;
}

const TONE_STROKE: Record<SparklineTone, string> = {
  signal: 'var(--signal)',
  warn: 'var(--warn)',
  alert: 'var(--alert)',
  violet: 'var(--violet)',
};

/**
 * Hand-built SVG sparkline over a bounded rolling window.
 * The series is drawn exactly as measured: a flat line at the baseline means "no traffic
 * observed", never "a dip in an attack".
 */
export function Sparkline({
  values,
  width = 220,
  height = 44,
  tone = 'signal',
  ariaLabel,
}: SparklineProps) {
  const gradientId = useId();
  const points = values.length > 0 ? values : [0];
  const max = Math.max(...points, 1);
  const step = points.length > 1 ? width / (points.length - 1) : width;
  const baseline = height - 3;
  const coords = points.map((value, index) => {
    const x = points.length > 1 ? index * step : width / 2;
    const y = baseline - (value / max) * (baseline - 3);
    return `${x.toFixed(2)},${y.toFixed(2)}`;
  });
  const line = coords.join(' ');
  const area = `${coords[0]} ${line} ${(points.length > 1 ? width : width / 2).toFixed(2)},${baseline} 0,${baseline}`;
  const idle = points.every((value) => value === 0);

  return (
    <svg
      className="sparkline"
      width="100%"
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      role="img"
      aria-label={ariaLabel ?? 'Event rate sparkline'}
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={TONE_STROKE[tone]} stopOpacity="0.32" />
          <stop offset="100%" stopColor={TONE_STROKE[tone]} stopOpacity="0" />
        </linearGradient>
      </defs>
      <line x1="0" y1={baseline} x2={width} y2={baseline} stroke="var(--line)" strokeWidth="1" />
      {!idle ? (
        <>
          <polygon points={area} fill={`url(#${gradientId})`} />
          <polyline
            points={line}
            fill="none"
            stroke={TONE_STROKE[tone]}
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <circle
            cx={points.length > 1 ? width : width / 2}
            cy={baseline - (points[points.length - 1] / max) * (baseline - 3)}
            r="2.4"
            fill={TONE_STROKE[tone]}
          />
        </>
      ) : null}
    </svg>
  );
}

export interface SparklineFrameProps {
  values: number[];
  tone?: SparklineTone;
  idleLabel?: string;
  label: string;
  unit?: string;
}

/** Sparkline with its label and an explicit idle state. */
export function SparklineFrame({ values, tone = 'signal', idleLabel, label, unit }: SparklineFrameProps) {
  const idle = values.every((value) => value === 0);
  return (
    <div className="sparkline-frame">
      <div className="row row--between">
        <span className="label">{label}</span>
        {idle ? <span className="label label--signal">{idleLabel ?? 'NO NEW TRAFFIC IN THIS WINDOW'}</span> : null}
      </div>
      <Sparkline values={values} tone={tone} ariaLabel={`${label} sparkline`} />
      {unit ? <span className="stat__note">{unit}</span> : null}
    </div>
  );
}
