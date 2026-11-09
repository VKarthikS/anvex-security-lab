import type { ReactNode } from 'react';

export type IconName =
  | 'activity'
  | 'sources'
  | 'evidence'
  | 'alerts'
  | 'settings'
  | 'diagnostics'
  | 'login'
  | 'target'
  | 'scenario'
  | 'run'
  | 'stop'
  | 'history'
  | 'compare'
  | 'check'
  | 'warning'
  | 'close'
  | 'chevron'
  | 'shield'
  | 'lock'
  | 'refresh'
  | 'pulse';

const PATHS: Record<IconName, ReactNode> = {
  activity: <path d="M3 12h4l2.5 7 5-14 2.5 7h4" />,
  sources: (
    <>
      <circle cx="5" cy="12" r="2" />
      <circle cx="12" cy="5" r="2" />
      <circle cx="19" cy="12" r="2" />
      <circle cx="12" cy="19" r="2" />
      <path d="M6.5 10.8 10.6 6.4M13.4 6.4l4.1 4.4M17.5 13.2l-4.1 4.4M10.6 17.6 6.5 13.2" />
    </>
  ),
  evidence: (
    <>
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5M9 13h6M9 17h4" />
    </>
  ),
  alerts: (
    <>
      <path d="M12 4 2.8 20h18.4z" />
      <path d="M12 10v4.5" />
      <path d="M12 17.6h.01" />
    </>
  ),
  settings: (
    <>
      <path d="M4 7h10M18 7h2M4 17h4M12 17h8" />
      <circle cx="16" cy="7" r="2" />
      <circle cx="10" cy="17" r="2" />
    </>
  ),
  diagnostics: (
    <>
      <rect x="7" y="7" width="10" height="10" rx="1.5" />
      <path d="M4 10h3M4 14h3M17 10h3M17 14h3M10 4v3M14 4v3M10 17v3M14 17v3" />
    </>
  ),
  login: (
    <>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
    </>
  ),
  target: (
    <>
      <circle cx="12" cy="12" r="7" />
      <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
    </>
  ),
  scenario: (
    <>
      <path d="M12 3 3 8l9 5 9-5z" />
      <path d="M3 13l9 5 9-5" />
    </>
  ),
  run: <path d="M8 5.5 19 12 8 18.5z" />,
  stop: <rect x="6.5" y="6.5" width="11" height="11" rx="1.5" />,
  history: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3.5 2" />
    </>
  ),
  compare: (
    <>
      <path d="M4 8h12M13 5l3 3-3 3" />
      <path d="M20 16H8M11 13l-3 3 3 3" />
    </>
  ),
  check: <path d="M4.5 12.5 9.5 17.5 19.5 6.5" />,
  warning: (
    <>
      <path d="M12 4 2.8 20h18.4z" />
      <path d="M12 10v4.5" />
      <path d="M12 17.6h.01" />
    </>
  ),
  close: <path d="M6 6l12 12M18 6 6 18" />,
  chevron: <path d="M9.5 5.5 16 12l-6.5 6.5" />,
  shield: <path d="M12 3 5 5.8v5.4c0 4.3 2.9 7.8 7 9.3 4.1-1.5 7-5 7-9.3V5.8z" />,
  lock: (
    <>
      <rect x="5" y="10.5" width="14" height="9.5" rx="2" />
      <path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 12a8 8 0 1 1-2.6-5.9" />
      <path d="M20 4v4h-4" />
    </>
  ),
  pulse: (
    <>
      <path d="M3 12h3.5L9 5.5 13 18.5 16 12h5" />
    </>
  ),
};

export interface IconProps {
  name: IconName;
  size?: number;
  className?: string;
  title?: string;
}

/** Hand-built geometric icons: 1.5px strokes that inherit the palette exactly. */
export function Icon({ name, size = 18, className, title }: IconProps) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? 'img' : 'presentation'}
      aria-hidden={title ? undefined : true}
      aria-label={title}
      focusable="false"
    >
      {title ? <title>{title}</title> : null}
      {PATHS[name]}
    </svg>
  );
}
