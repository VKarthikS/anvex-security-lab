import type { ReactNode } from 'react';

export type PanelTone = 'default' | 'signal' | 'warn' | 'alert' | 'violet';

export interface PanelProps {
  title: string;
  meta?: ReactNode;
  tone?: PanelTone;
  flush?: boolean;
  actions?: ReactNode;
  className?: string;
  children: ReactNode;
}

const TONE_CLASS: Record<PanelTone, string> = {
  default: '',
  signal: 'panel--signal',
  warn: 'panel--warn',
  alert: 'panel--alert',
  violet: 'panel--violet',
};

/** Tonal panel: elevation instead of heavy borders. */
export function Panel({ title, meta, tone = 'default', flush, actions, className, children }: PanelProps) {
  const classes = ['panel', TONE_CLASS[tone], className].filter(Boolean).join(' ');
  return (
    <section className={classes}>
      <header className="panel__head">
        <h2 className="panel__title">{title}</h2>
        <div className="row">
          {meta ? <span className="panel__meta">{meta}</span> : null}
          {actions}
        </div>
      </header>
      <div className={flush ? 'panel__body panel__body--flush' : 'panel__body'}>{children}</div>
    </section>
  );
}
