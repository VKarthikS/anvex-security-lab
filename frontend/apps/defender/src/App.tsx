import { useEffect, useState } from 'react';
import { Icon, StatusPill, Wordmark, type IconName } from '@anvex/shared';

import { CalibrationStrip } from './components/CalibrationStrip';
import { ProtectionColumn } from './components/ProtectionColumn';
import { AnnouncerProvider } from './state/Announcer';
import { DefenderProvider, useDefender } from './state/DefenderContext';
import { AlertsView } from './views/AlertsView';
import { DiagnosticsView } from './views/DiagnosticsView';
import { EvidenceView } from './views/EvidenceView';
import { LoginView } from './views/LoginView';
import { MonitorView } from './views/MonitorView';
import { SettingsView } from './views/SettingsView';
import { SourcesView } from './views/SourcesView';

interface ViewDefinition {
  id: string;
  label: string;
  icon: IconName;
}

const VIEWS: ViewDefinition[] = [
  { id: 'monitor', label: 'Monitor', icon: 'activity' },
  { id: 'sources', label: 'Sources', icon: 'sources' },
  { id: 'evidence', label: 'Evidence', icon: 'evidence' },
  { id: 'alerts', label: 'Alerts', icon: 'alerts' },
  { id: 'settings', label: 'Settings', icon: 'settings' },
  { id: 'diagnostics', label: 'Diagnostics', icon: 'diagnostics' },
  { id: 'login', label: 'Login', icon: 'login' },
];

const DEFAULT_VIEW = 'monitor';

function readViewFromHash(): string {
  const hash = window.location.hash.replace('#', '').trim();
  return VIEWS.some((view) => view.id === hash) ? hash : DEFAULT_VIEW;
}

function ConnectionPill() {
  const { streamStatus } = useDefender();
  if (streamStatus === 'live') {
    return <StatusPill tone="signal">Stream live</StatusPill>;
  }
  if (streamStatus === 'connecting') {
    return <StatusPill tone="mute">Stream connecting</StatusPill>;
  }
  return <StatusPill tone="warn">Stale — reconnecting</StatusPill>;
}

function TopBar({ view, onSelect }: { view: string; onSelect: (id: string) => void }) {
  const { identity } = useDefender();
  return (
    <header className="topbar">
      <div className="topbar__brand">
        <Wordmark station="DEFENDER" />
        <span className="topbar__build mono">
          {identity ? `${identity.product} ${identity.version} · ${identity.apiVersion}` : 'connecting…'}
        </span>
      </div>

      <nav className="rail" aria-label="Defender sections">
        {VIEWS.map((item) => (
          <button
            key={item.id}
            type="button"
            className={`rail__item${view === item.id ? ' is-active' : ''}`}
            aria-current={view === item.id ? 'page' : undefined}
            onClick={() => onSelect(item.id)}
          >
            <Icon name={item.icon} size={15} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>

      <div className="topbar__status">
        <ConnectionPill />
      </div>
    </header>
  );
}

function renderView(view: string) {
  switch (view) {
    case 'sources':
      return <SourcesView />;
    case 'evidence':
      return <EvidenceView />;
    case 'alerts':
      return <AlertsView />;
    case 'settings':
      return <SettingsView />;
    case 'diagnostics':
      return <DiagnosticsView />;
    case 'login':
      return <LoginView />;
    default:
      return <MonitorView />;
  }
}

function Shell() {
  const [view, setView] = useState<string>(readViewFromHash);

  useEffect(() => {
    const onHashChange = () => setView(readViewFromHash());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  const select = (id: string) => {
    window.location.hash = id;
    setView(id);
  };

  return (
    <div className="shell">
      <TopBar view={view} onSelect={select} />
      <CalibrationStrip />
      <div className="workspace">
        <main className="workspace__main">{renderView(view)}</main>
        <aside className="workspace__side" aria-label="Protection state">
          <ProtectionColumn />
        </aside>
      </div>
    </div>
  );
}

export function App() {
  return (
    <AnnouncerProvider>
      <DefenderProvider>
        <Shell />
      </DefenderProvider>
    </AnnouncerProvider>
  );
}
