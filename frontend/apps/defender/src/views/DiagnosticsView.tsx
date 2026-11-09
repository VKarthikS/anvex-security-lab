import { useCallback, useEffect, useState } from 'react';
import {
  ApiRequestError,
  Banner,
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

export function DiagnosticsView() {
  const { api } = useDefender();
  const [diagnostics, setDiagnostics] = useState<DiagnosticsSnapshot | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const now = useNow(1000);

  const load = useCallback(async () => {
    setError(null);
    try {
      const result = await api.diagnostics();
      setDiagnostics(result.data);
      setForbidden(false);
    } catch (cause) {
      setDiagnostics(null);
      if (cause instanceof ApiRequestError && cause.isLoopbackRequired) {
        setForbidden(true);
      } else {
        setError(
          cause instanceof ApiRequestError
            ? cause
            : new ApiRequestError('The diagnostics could not be read.', { status: 0, code: 'unknown_error' }),
        );
      }
    }
  }, [api]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => {
      void load();
    }, 5_000);
    return () => window.clearInterval(timer);
  }, [load]);

  if (forbidden) {
    return (
      <div className="stack">
        <Banner tone="warn" title="Diagnostics require a loopback caller">
          The defender answered with <span className="mono">403 analyst_loopback_required</span>. The diagnostics view is
          an analyst-only surface and is only served to <span className="mono">127.0.0.0/8</span> or{' '}
          <span className="mono">::1</span>. No runtime data is shown because none could be read.
        </Banner>
        <Panel title="Diagnostics">
          <EmptyState title="No runtime data available" note="Open this view from the defender host itself." />
        </Panel>
      </div>
    );
  }

  if (error) {
    return (
      <div className="stack">
        <Banner tone="alert" title="Diagnostics unavailable">
          {error.message} ({error.code})
        </Banner>
      </div>
    );
  }

  if (!diagnostics) {
    return (
      <div className="stack">
        <Panel title="Diagnostics">
          <p className="mono dim">Reading runtime diagnostics…</p>
        </Panel>
      </div>
    );
  }

  const pipeline = diagnostics.eventPipeline;
  const lagMs = pipeline.lastPersistedAt ? now - new Date(pipeline.lastPersistedAt).getTime() : null;

  return (
    <div className="stack">
      <div className="stat-row">
        <div className="stat">
          <span className="label">Java version</span>
          <span className="stat__value numeral stat__value--sm">{diagnostics.javaVersion}</span>
          <span className="stat__unit">{diagnostics.javaVendor}</span>
        </div>
        <div className="stat">
          <span className="label">Workers</span>
          <span className="stat__value numeral stat__value--sm">{formatInteger(pipeline.workerStates.length)}</span>
          <span className="stat__unit">named threads</span>
        </div>
        <div className="stat">
          <span className="label">Persistence lag</span>
          <span className="stat__value numeral stat__value--sm">{lagMs === null ? '—' : formatDuration(lagMs)}</span>
          <span className="stat__unit">since last persisted event</span>
        </div>
        <div className="stat">
          <span className="label">Stored evidence</span>
          <span className="stat__value numeral stat__value--sm">{formatInteger(diagnostics.evidenceCount)}</span>
          <span className="stat__unit">snapshots in memory</span>
        </div>
      </div>

      <div className="grid grid--two">
        <Panel title="Worker threads" meta={`${formatInteger(pipeline.workerStates.length)} thread(s)`} flush>
          {pipeline.workerStates.length === 0 ? (
            <EmptyState title="No workers reported" />
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Thread</th>
                  <th>State</th>
                </tr>
              </thead>
              <tbody>
                {pipeline.workerStates.map((worker) => (
                  <tr key={worker.name}>
                    <td className="mono">{worker.name}</td>
                    <td>
                      <StatusPill tone={worker.state === 'RUNNABLE' || worker.state === 'TIMED_WAITING' ? 'signal' : 'warn'}>
                        {worker.state}
                      </StatusPill>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel title="Queues and counters">
          <div className="stack stack--tight">
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
            <div className="row row--between">
              <span className="label">Publish queue</span>
              <span className="mono">{formatInteger(pipeline.publishQueueDepth)}</span>
            </div>
            <Meter value={pipeline.publishQueueDepth} max={2000} tone="warn" label="Publish queue depth" />

            <dl className="kv">
              <dt className="kv__key">Published</dt>
              <dd className="kv__value">{formatInteger(pipeline.publishedEvents)}</dd>
              <dt className="kv__key">Aggregated</dt>
              <dd className="kv__value">{formatInteger(pipeline.aggregatedEvents)}</dd>
              <dt className="kv__key">Dropped</dt>
              <dd className="kv__value">{formatInteger(pipeline.droppedEvents)}</dd>
              <dt className="kv__key">Critical dropped</dt>
              <dd className="kv__value">{formatInteger(pipeline.criticalDroppedEvents)}</dd>
              <dt className="kv__key">Overload responses</dt>
              <dd className="kv__value">{formatInteger(pipeline.overloadResponses)}</dd>
              <dt className="kv__key">Persisted</dt>
              <dd className="kv__value">{formatInteger(pipeline.persistedEvents)}</dd>
              <dt className="kv__key">Publication drops</dt>
              <dd className="kv__value">{formatInteger(pipeline.publicationDrops)}</dd>
              <dt className="kv__key">Persistence errors</dt>
              <dd className="kv__value">{formatInteger(pipeline.persistenceErrors)}</dd>
              <dt className="kv__key">Last persisted</dt>
              <dd className="kv__value">{formatTimestamp(pipeline.lastPersistedAt)}</dd>
              <dt className="kv__key">Subscribers</dt>
              <dd className="kv__value">{formatInteger(pipeline.subscriberCount)}</dd>
            </dl>
          </div>
        </Panel>
      </div>

      <div className="grid grid--two">
        <Panel title="Block history" meta={`${formatInteger(diagnostics.blockHistory.length)} block(s)`} flush>
          {diagnostics.blockHistory.length === 0 ? (
            <EmptyState title="No block has been created" note="Block transitions are recorded here with their expiry." />
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Source</th>
                  <th>Rule</th>
                  <th>State</th>
                  <th>Created</th>
                  <th>Expiry</th>
                  <th>Released</th>
                </tr>
              </thead>
              <tbody>
                {diagnostics.blockHistory.map((block) => (
                  <tr key={block.blockId}>
                    <td className="mono">{block.sourceIp}</td>
                    <td>{block.ruleId}</td>
                    <td>
                      <StatusPill tone={block.state === 'ACTIVE' ? 'alert' : 'mute'}>{block.state}</StatusPill>
                    </td>
                    <td className="mono">{formatTimestamp(block.createdAt)}</td>
                    <td className="mono">{formatTimestamp(block.expiry)}</td>
                    <td className="mono">{block.releasedAt ? formatTimestamp(block.releasedAt) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <div className="stack">
          <Panel title="Protection events" meta={`${formatInteger(diagnostics.protectionEvents.length)} entry(s)`}>
            {diagnostics.protectionEvents.length === 0 ? (
              <EmptyState title="No protection transition recorded" />
            ) : (
              <ul className="reflist">
                {diagnostics.protectionEvents.map((event) => (
                  <li key={event} className="mono reflist__item">
                    {event}
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="History gaps" meta={`retained ${formatInteger(diagnostics.historyGaps.retainedWindowSeconds)}s`}>
            <p className="muted">{diagnostics.historyGaps.note}</p>
          </Panel>

          <Panel title="Tracked sources" meta={`${formatInteger(diagnostics.sources.length)} source(s)`} flush>
            {diagnostics.sources.length === 0 ? (
              <EmptyState title="No source is tracked" />
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Source</th>
                    <th className="table__num">Incoming</th>
                    <th className="table__num">Failures</th>
                    <th className="table__num">Successes</th>
                    <th className="table__num">Accounts</th>
                  </tr>
                </thead>
                <tbody>
                  {diagnostics.sources.map((source) => (
                    <tr key={source.sourceIp}>
                      <td className="mono">{source.sourceIp}</td>
                      <td className="table__num">{formatInteger(source.totalIncoming)}</td>
                      <td className="table__num">{formatInteger(source.totalFailures)}</td>
                      <td className="table__num">{formatInteger(source.totalSuccesses)}</td>
                      <td className="table__num">{formatInteger(source.trackedAccounts)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Panel>
        </div>
      </div>
    </div>
  );
}
