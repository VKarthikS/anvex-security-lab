import type { ReactNode } from 'react';

export type PillTone = 'signal' | 'warn' | 'alert' | 'violet' | 'mute';

export interface StatusPillProps {
  tone: PillTone;
  children: ReactNode;
  /** Status is never colour only: the label always carries the state as text. */
  title?: string;
}

export function StatusPill({ tone, children, title }: StatusPillProps) {
  return (
    <span className={`pill pill--${tone}`} title={title}>
      <span className="pill__dot" aria-hidden="true" />
      {children}
    </span>
  );
}
