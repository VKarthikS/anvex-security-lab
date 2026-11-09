import type { ReactNode } from 'react';

export interface StatProps {
  label: string;
  value: ReactNode;
  unit?: string;
  note?: ReactNode;
  /** Renders the figure greyed out when the live stream is stale. */
  stale?: boolean;
  small?: boolean;
  tone?: 'default' | 'signal' | 'warn' | 'alert' | 'violet';
}

const TONE_CLASS: Record<NonNullable<StatProps['tone']>, string> = {
  default: '',
  signal: 'label--signal',
  warn: 'label--warn',
  alert: 'label--alert',
  violet: 'label--violet',
};

/** Large editorial numeral with its unit and context in small mono caps. */
export function Stat({ label, value, unit, note, stale, small, tone = 'default' }: StatProps) {
  const classes = ['stat', stale ? 'stat--stale' : ''].filter(Boolean).join(' ');
  return (
    <div className={classes}>
      <span className={`label ${TONE_CLASS[tone]}`}>{label}</span>
      <span className={small ? 'stat__value numeral stat__value--sm' : 'stat__value numeral'}>{value}</span>
      {unit ? <span className="stat__unit">{unit}</span> : null}
      {note ? <span className="stat__note">{note}</span> : null}
    </div>
  );
}
