import type { ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

export interface EmptyStateProps {
  title: string;
  note?: ReactNode;
  icon?: IconName;
}

/** Honest idle state: says plainly that nothing was observed. */
export function EmptyState({ title, note, icon = 'pulse' }: EmptyStateProps) {
  return (
    <div className="empty">
      <Icon name={icon} size={22} />
      <p className="empty__title">{title}</p>
      {note ? <p className="empty__note">{note}</p> : null}
    </div>
  );
}
