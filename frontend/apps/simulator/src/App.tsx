import { useEffect, useState } from 'react';
import { Icon, StatusPill, Wordmark, type IconName } from '@anvex/shared';

import { ControlColumn } from './components/ControlColumn';
import { AnnouncerProvider } from './state/Announcer';
import { SimulatorProvider, useSimulator } from './state/SimulatorContext';
import { HistoryView } from './views/HistoryView';
import { MissionView } from './views/MissionView';
import { RunView } from './views/RunView';
import { ScenarioView } from './views/ScenarioView';
import { TargetView } from './views/TargetView';

interface ViewDefinition {
  id: string;
  label: string;
  icon: IconName;
}

const VIEWS: ViewDefinition[] = [
  { id: 'mission', label: 'Mission Control', icon: 'shield' },
  { id: 'target', label: 'Target', icon: 'target' },
  { id: 'scenario', label: 'Scenario', icon: 'scenario' },
  { id: 'run', label: 'Run', icon: 'run' },
  { id: 'history', label: 'History', icon: 'history' },
];

function readViewFromHash(): string {
  const hash = window.location.hash.replace('#', '').trim();
  return VIEWS.some((view) => view.id === hash) ? hash : 'mission';
}

function ConnectionPill() {
  const { streamStatus } = useSimulator();
  if (streamStatus === 'live') {
    return <StatusPill tone="signal">Progress live</StatusPill>;
  }
  if (streamStatus === 'connecting') {
    return <StatusPill tone="mute">Connecting</StatusPill>;
  }
  return <StatusPill tone="warn">Stale — reconnecting</StatusPill>;
}

function TopBar({ view, onSelect, presenter, onTogglePresenter }: {
  view: string;
  onSelect: (id: string) => void;
  presenter: boolean;
  onTogglePresenter: () => void;
}) {
  return (
    <header className="topbar">
      <div className="topbar__brand">
        <Wordmark station="SIMULATOR" />
        <span className="topbar__build mono">control station · run lifecycle</span>
      </div>

      <nav className="rail" aria-label="Simulator sections">
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
        {view === 'mission' && (
          <button
            type="button"
            className={`presenter-toggle${presenter ? ' is-active' : ''}`}
            onClick={onTogglePresenter}
            aria-pressed={presenter}
            title={presenter ? 'Restore controls and navigation' : 'Expand Mission Control for a demo or screen recording'}
          >
            <Icon name={presenter ? 'close' : 'evidence'} size={15} />
            {presenter ? 'Exit presenter view' : 'Presenter view'}
          </button>
        )}
        <ConnectionPill />
      </div>
    </header>
  );
}

function renderView(view: string) {
  switch (view) {
    case 'mission':
      return <MissionView />;
    case 'target':
      return <TargetView />;
    case 'scenario':
      return <ScenarioView />;
    case 'history':
      return <HistoryView />;
    default:
      return <RunView />;
  }
}

function Shell() {
  const [view, setView] = useState<string>(readViewFromHash);
  const [presenter, setPresenter] = useState(false);

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
    <div className={`shell${presenter && view === 'mission' ? ' is-presenter' : ''}`}>
      <TopBar view={view} onSelect={select} presenter={presenter} onTogglePresenter={() => setPresenter((value) => !value)} />
      <div className="workspace">
        <aside className="workspace__control" aria-label="Run controls">
          <ControlColumn />
        </aside>
        <main className="workspace__main">{renderView(view)}</main>
      </div>
    </div>
  );
}

export function App() {
  return (
    <AnnouncerProvider>
      <SimulatorProvider>
        <Shell />
      </SimulatorProvider>
    </AnnouncerProvider>
  );
}
