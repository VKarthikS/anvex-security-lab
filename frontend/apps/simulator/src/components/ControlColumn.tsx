import { formatInteger, Meter, Panel, StatusPill } from '@anvex/shared';

import { useSimulator } from '../state/SimulatorContext';

const RUN_TONE: Record<string, 'signal' | 'warn' | 'alert' | 'mute'> = {
  IDLE: 'mute',
  RUNNING: 'signal',
  STOPPING: 'warn',
  COMPLETED: 'signal',
  CANCELLED: 'warn',
  FAILED: 'alert',
};

export function ControlColumn() {
  const { target, handshake, scenarios, form, currentRun, streamStatus, busy, startRun, stopRun, testTarget } =
    useSimulator();
  const scenario = scenarios.find((item) => item.id === form.scenarioId);
  const running = currentRun?.state === 'RUNNING' || currentRun?.state === 'STOPPING';
  const manual = form.scenarioId === 'manual-login';
  const progress =
    currentRun && currentRun.counters.planned > 0
      ? Math.min(1, currentRun.counters.sent / currentRun.counters.planned)
      : 0;

  return (
    <div className="stack">
      <Panel title="Target" meta={target?.baseUrl ?? 'not configured'}>
        <div className="stack stack--tight">
          <p className="mono control__url">{target?.baseUrl ?? '—'}</p>
          {handshake ? (
            <div className="row row--wrap">
              <StatusPill tone={handshake.reachable ? 'signal' : 'alert'}>
                {handshake.reachable ? 'Reachable' : 'Unreachable'}
              </StatusPill>
              <StatusPill tone={handshake.compatible ? 'signal' : 'warn'}>
                {handshake.compatible ? 'Compatible' : 'Not compatible'}
              </StatusPill>
            </div>
          ) : (
            <span className="mono dim">handshake not tested yet</span>
          )}
          <button type="button" className="btn btn--sm" disabled={busy} onClick={() => void testTarget()}>
            Test handshake
          </button>
        </div>
      </Panel>

      <Panel title="Scenario" meta={scenario?.displayName ?? 'none selected'}>
        {scenario ? (
          <div className="stack stack--tight">
            <p className="muted control__description">{scenario.description}</p>
            <dl className="kv">
              <dt className="kv__key">Rate</dt>
              <dd className="kv__value">{formatInteger(form.ratePerSecond)} req/s</dd>
              <dt className="kv__key">Duration</dt>
              <dd className="kv__value">{formatInteger(form.durationSeconds)} s</dd>
              <dt className="kv__key">Request limit</dt>
              <dd className="kv__value">{formatInteger(form.requestLimit)}</dd>
              <dt className="kv__key">Concurrency</dt>
              <dd className="kv__value">{formatInteger(form.concurrency)}</dd>
            </dl>
          </div>
        ) : (
          <p className="mono dim">No scenario selected.</p>
        )}
      </Panel>

      <Panel title="Run" meta={currentRun?.state ?? 'IDLE'}>
        <div className="stack stack--tight">
          <div className="row row--between">
            <StatusPill tone={RUN_TONE[currentRun?.state ?? 'IDLE'] ?? 'mute'}>{currentRun?.state ?? 'IDLE'}</StatusPill>
            <span className="mono dim">
              {currentRun?.counters.sent ?? 0}/{currentRun?.counters.planned ?? 0} sent
            </span>
          </div>
          <Meter value={progress * 100} max={100} label="Requests sent against the plan" />
          <div className="row">
            <button
              type="button"
              className="btn btn--primary"
              disabled={busy || running || !form.scenarioId || manual}
              onClick={() => void startRun()}
            >
              Start run
            </button>
            <button type="button" className="btn btn--danger" disabled={busy || !running} onClick={() => void stopRun()}>
              Stop
            </button>
          </div>
          {manual ? (
            <p className="mono dim">manual-login is driven by the manual login control on the Run screen.</p>
          ) : null}
          <p className="mono dim">
            progress stream: {streamStatus === 'live' ? 'live' : streamStatus === 'connecting' ? 'connecting' : 'stale'}
          </p>
        </div>
      </Panel>
    </div>
  );
}
