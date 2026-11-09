import { useCallback, useEffect, useState } from 'react';
import {
  ApiRequestError,
  EmptyState,
  formatDuration,
  formatInteger,
  formatTimestamp,
  Meter,
  Panel,
  StatusPill,
  useNow,
  type DiagnosticsSnapshot,
} from '@anvex/shared';

import { useDefender } from '../state/DefenderContext';

/**
 * Compact runtime view of the defender, read from `GET /api/v1/diagnostics`.
 *
 * The endpoint is analyst-only: it is only served to a loopback caller. When the defender
 * refuses the read the panel says exactly that instead of showing an empty runtime.
 */
export function RuntimeDiagnosticsPanel() {
  const { api } = useDefender();
  const [diagnostics, setDiagnostics] = useState<DiagnosticsSnapshot | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const now = useNow(1000);

  const load = useCallback(async () => {
    try {
      const result = await api.diagnostics();
      setDiagnostics(result.data);
      setError(null);
    } catch (cause) {
      setDiagnostics(null);
      setError(
        cause instanceof ApiRequestError
          ? cause
          : new ApiRequestError('The diagnostics could not be read.', { status: 0, code: 'unknown_error' }),
      );
    }
  }, [api]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => {
      void load();
    }, 5_000);
    return () => window.clearInterval(timer);
  }, [load]);

  if (error?.isLoopbackRequired) {
    return (
      <Panel title="Runtime diagnostics" meta="analyst endpoint">
        <EmptyState
          title="Not readable from this address"
          note="The defender answered 403 analyst_loopback_required. Diagnostics are only served to 127.0.0.0/8 or ::1; forwarded IP headers are ignored."
        />
      </Panel>
    );
  }

  if (error) {
    return (
      <Panel title="Runtime diagnostics" meta="unavailable">
        <p className="muted">
          {error.message} ({error.code})
        </p>
      </Panel>
    );
  }

  if (!diagnostics) {
    return (
      <Panel title="Runtime diagnostics" meta="reading…">
        <p className="mono dim">Reading runtime diagnostics…</p>
      </Panel>
    );
  }

  const pipeline = diagnostics.eventPipeline;
  const lagMs = pipeline.lastPersistedAt ? now - new Date(pipeline.lastPersistedAt).getTime() : null;

  return (
    <Panel
      title="Runtime diagnostics"
      meta={`java ${diagnostics.javaVersion}`}
      actions={
        <button type="button" className="btn btn--sm btn--ghost" onClick={() => void load()}>
          Refresh
        </button>
      }
    >
      <div className="stack stack--tight">
        <dl className="kv">
          <dt className="kv__key">Java</dt>
          <dd className="kv__value">
            {diagnostics.javaVersion} · {diagnostics.javaVendor}
          </dd>
          <dt className="kv__key">Workers</dt>
          <dd className="kv__value">
            {formatInteger(pipeline.workerStates.length)} named thread(s) ·{' '}
            {pipeline.workerStates.map((worker) => worker.name).join(', ') || 'none reported'}
          </dd>
          <dt className="kv__key">Persistence lag</dt>
          <dd className="kv__value">
            {lagMs === null ? 'no event persisted yet' : formatDuration(lagMs)}
            {pipeline.lastPersistedAt ? ` · last at ${formatTimestamp(pipeline.lastPersistedAt)}` : ''}
          </dd>
          <dt className="kv__key">Aggregated</dt>
          <dd className="kv__value">{formatInteger(pipeline.aggregatedEvents)}</dd>
          <dt className="kv__key">Dropped</dt>
          <dd className="kv__value">
            {formatInteger(pipeline.droppedEvents)} telemetry · {formatInteger(pipeline.criticalDroppedEvents)} critical
          </dd>
          <dt className="kv__key">Overload responses</dt>
          <dd className="kv__value">{formatInteger(pipeline.overloadResponses)}</dd>
          <dt className="kv__key">History gaps</dt>
          <dd className="kv__value">
            {diagnostics.historyGaps.note} (retained window {formatInteger(diagnostics.historyGaps.retainedWindowSeconds)}s)
          </dd>
        </dl>

        <div className="row row--between">
          <span className="label">Telemetry queue</span>
          <span className="mono">
            {formatInteger(pipeline.telemetryQueueDepth)} / {formatInteger(pipeline.telemetryQueueCapacity)}
          </span>
        </div>
        <Meter value={pipeline.telemetryQueueDepth} max={pipeline.telemetryQueueCapacity} label="Telemetry queue depth" />

        <div className="row row--between">
          <span className="label">Critical queue</span>
          <span className="mono">
            {formatInteger(pipeline.criticalQueueDepth)} / {formatInteger(pipeline.criticalQueueCapacity)}
          </span>
        </div>
        <Meter
          value={pipeline.criticalQueueDepth}
          max={pipeline.criticalQueueCapacity}
          tone="violet"
          label="Critical queue depth"
        />

        <div className="row row--wrap">
          {pipeline.workerStates.map((worker) => (
            <StatusPill
              key={worker.name}
              tone={worker.state === 'RUNNABLE' || worker.state === 'TIMED_WAITING' ? 'signal' : 'warn'}
            >
              {worker.name} · {worker.state}
            </StatusPill>
          ))}
        </div>
      </div>
    </Panel>
  );
}
