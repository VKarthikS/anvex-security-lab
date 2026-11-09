import type { ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

export type BannerTone = 'info' | 'warn' | 'alert' | 'violet';

export interface BannerProps {
  tone?: BannerTone;
  title: string;
  children?: ReactNode;
  icon?: IconName;
  actions?: ReactNode;
}

const DEFAULT_ICON: Record<BannerTone, IconName> = {
  info: 'pulse',
  warn: 'warning',
  alert: 'warning',
  violet: 'evidence',
};

/** Explicit notice band. Used for stale streams, loopback refusals and failed calls. */
export function Banner({ tone = 'info', title, children, icon, actions }: BannerProps) {
  return (
    <div className={`banner banner--${tone}`} role={tone === 'alert' ? 'alert' : 'status'}>
      <Icon name={icon ?? DEFAULT_ICON[tone]} size={16} />
      <div className="grow">
        <p className="banner__title">{title}</p>
        {children ? <div className="banner__body">{children}</div> : null}
      </div>
      {actions}
    </div>
  );
}
