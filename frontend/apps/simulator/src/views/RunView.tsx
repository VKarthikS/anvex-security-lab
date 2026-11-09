import { useState, type ReactNode } from 'react';
import {
  Banner,
  EmptyState,
  formatDateTime,
  formatDecimal,
  formatInteger,
  Panel,
  StatusPill,
  type RunState,
} from '@anvex/shared';

import { useSimulator } from '../state/SimulatorContext';

const RUN_TONE: Record<RunState, 'signal' | 'warn' | 'alert' | 'mute'> = {
  IDLE: 'mute',
  RUNNING: 'signal',
  STOPPING: 'warn',
  COMPLETED: 'signal',
  CANCELLED: 'warn',
  FAILED: 'alert',
};

export function RunView() {
  const { currentRun, streamStatus, error, manualLogin, busy } = useSimulator();
  const stale = streamStatus === 'stale';
  const counters = currentRun?.counters;
  const latency = currentRun?.latency;
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  return (
    <div className="stack">
      {stale ? (
        <Banner tone="warn" title="Stale — reconnecting">
          The progress stream dropped. The figures below are the last snapshot the simulator published and are not live.
          The current run is re-read as soon as the subscription is restored.
        </Banner>
      ) : null}

      {error ? (
        <Banner tone="alert" title="Simulator request failed">
          {error.message} ({error.code})
        </Banner>
      ) : null}

      <Panel
        title="Current run"
        meta={currentRun?.runId ?? 'no run id'}
        tone={currentRun?.state === 'FAILED' ? 'alert' : stale ? 'warn' : 'signal'}
      >
        {!currentRun ? (
          <EmptyState
            title="No run is active"
            note="Choose a scenario in the control column and start a run. The counters below are filled from the simulator service."
          />
        ) : (
          <div className="stack stack--tight">
            <div className="row row--between row--wrap">
              <div className="row">
                <StatusPill tone={RUN_TONE[currentRun.state]}>{currentRun.state}</StatusPill>
                <span className="mono muted">{currentRun.scenarioId ?? '—'}</span>
              </div>
              <span className="mono dim">
                connection: {streamStatus === 'live' ? 'live' : streamStatus === 'connecting' ? 'connecting' : 'stale'}
              </span>
            </div>
            <dl className="kv">
              <dt className="kv__key">Started</dt>
              <dd className="kv__value">{formatDateTime(currentRun.startedAt)}</dd>
              <dt className="kv__key">Ended</dt>
              <dd className="kv__value">{formatDateTime(currentRun.endedAt)}</dd>
              <dt className="kv__key">Target</dt>
              <dd className="kv__value">{currentRun.targetBaseUrl ?? '—'}</dd>
              <dt className="kv__key">Achieved rate</dt>
              <dd className="kv__value">{formatDecimal(currentRun.achievedRatePerSecond, 2)} req/s</dd>
              <dt className="kv__key">Message</dt>
              <dd className="kv__value">{currentRun.message ?? '—'}</dd>
            </dl>
          </div>
        )}
      </Panel>

      <Panel title="Progress" meta="planned against sent requests">
        <div className="stat-row">
          <StatBox label="Planned" value={counters?.planned} unit="requests" />
          <StatBox label="Sent" value={counters?.sent} unit="requests" />
          <StatBox label="Completed" value={counters?.completed} unit="requests" />
          <StatBox label="Cancelled" value={counters?.cancelled} unit="requests" tone="warn" />
        </div>
      </Panel>

      <div className="grid grid--two">
        <Panel title="Defender answers" meta="the defender replied">
          <div className="stat-row">
            <StatBox label="Authenticated" value={counters?.authenticationSuccess} unit="requests" tone="signal" />
            <StatBox label="Failed authentication" value={counters?.authenticationFailure} unit="requests" tone="warn" />
          </div>
          <p className="muted run-note">
            A failed authentication is a completed request with a negative result. It is not a transport failure.
          </p>
        </Panel>

        <Panel title="Defender rejections" meta="HTTP 429 / 503" tone="violet">
          <div className="stat-row">
            <StatBox label="Rate limited" value={counters?.rateLimited} unit="requests" tone="warn" />
            <StatBox label="Source blocked" value={counters?.sourceBlocked} unit="requests" tone="alert" />
            <StatBox label="Invalid / overload" value={counters?.invalidOrOverload} unit="requests" tone="alert" />
          </div>
          <p className="muted run-note">
            These requests reached the defender and were rejected by its protection gate. They are counted separately
            from transport failures.
          </p>
        </Panel>
      </div>

      <div className="grid grid--two">
        <Panel title="Transport failures" meta="no answer from the defender" tone={counters?.transportErrors ? 'alert' : 'default'}>
          <div className="stat-row">
            <StatBox label="Transport errors" value={counters?.transportErrors} unit="requests" tone="alert" />
          </div>
          <p className="muted run-note">
            A transport error means the request never produced a defender answer: the target could not be reached or the
            connection failed. It is not a rejection.
          </p>
        </Panel>

        <Panel title="Latency" meta={`${formatInteger(latency?.count ?? 0)} sample(s)`}>
          <div className="stat-row">
            <StatBox label="Average" value={formatDecimal(latency?.avgMs ?? null, 1)} unit="ms" />
            <StatBox label="p50" value={formatDecimal(latency?.p50Ms ?? null, 1)} unit="ms" />
            <StatBox label="p95" value={formatDecimal(latency?.p95Ms ?? null, 1)} unit="ms" />
            <StatBox label="Max" value={formatDecimal(latency?.maxMs ?? null, 1)} unit="ms" />
          </div>
        </Panel>
      </div>

      <Panel title="Manual login" meta="POST /api/v1/runs/manual">
        <div className="stack stack--tight">
          <p className="muted">
            One login attempt with operator supplied credentials. The result is counted exactly like a scenario request.
          </p>
          <div className="form-grid">
            <div className="field">
              <label className="field__label" htmlFor="manual-username">
                Username
              </label>
              <input
                id="manual-username"
                className="input"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="off"
              />
            </div>
            <div className="field">
              <label className="field__label" htmlFor="manual-password">
                Password
              </label>
              <input
                id="manual-password"
                className="input"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="off"
              />
            </div>
          </div>
          <div className="row">
            <button
              type="button"
              className="btn"
              disabled={busy || username.trim() === '' || password === ''}
              onClick={() => {
                void manualLogin(username.trim(), password);
                setPassword('');
              }}
            >
              Send manual login
            </button>
          </div>
        </div>
      </Panel>
    </div>
  );
}

function StatBox({
  label,
  value,
  unit,
  tone = 'default',
}: {
  label: string;
  value: ReactNode;
  unit: string;
  tone?: 'default' | 'signal' | 'warn' | 'alert';
}) {
  return (
    <div className="stat">
      <span
        className={`label${tone === 'signal' ? ' label--signal' : tone === 'warn' ? ' label--warn' : tone === 'alert' ? ' label--alert' : ''}`}
      >
        {label}
      </span>
      <span className="stat__value numeral stat__value--sm">
        {typeof value === 'number' ? formatInteger(value) : value}
      </span>
      <span className="stat__unit">{unit}</span>
    </div>
  );
}
