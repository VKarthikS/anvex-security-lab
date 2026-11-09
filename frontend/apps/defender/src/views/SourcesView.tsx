import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ApiRequestError,
  Banner,
  EmptyState,
  formatInteger,
  formatTimestamp,
  Panel,
  StatusPill,
  categoryLabel,
  type Alert,
  type PersistedEvent,
  type SourceDetail,
  type SourceSummary,
} from '@anvex/shared';

import { BlockList } from '../components/BlockList';
import { useDefender } from '../state/DefenderContext';

/** How many source alerts are read when the account heat is derived from stored evidence. */
const ACCOUNT_HEAT_ALERT_LIMIT = 25;

/**
 * One row of the account heat. Every field comes from evidence snapshots the defender stored:
 * the number of decisions that named the account, the rules that matched and the last decision
 * time. Nothing here is estimated.
 */
interface AccountHeatRow {
  username: string;
  decisions: number;
  rules: string[];
  lastSeen: string | null;
}

function normaliseUsername(username: string): string {
  return username.trim().toLowerCase();
}

/** The evidence snapshots the defender stored behind one alert. */
interface AlertEvidence {
  affectedUsernames: string[];
  createdAt: string;
}

/**
 * Derives the account heat of one source from the evidence behind its alerts.
 *
 * The defender publishes a per-source summary but no per-account breakdown, so the heat is
 * counted from the evidence snapshots themselves: one decision counts once for every distinct
 * normalised username it names.
 */
function deriveAccountHeat(
  alerts: Alert[],
  evidenceByAlert: Map<string, AlertEvidence[]>,
): AccountHeatRow[] {
  const rows = new Map<string, AccountHeatRow>();

  for (const alert of alerts) {
    const decisions = evidenceByAlert.get(alert.alertId) ?? [];
    for (const decision of decisions) {
      const named = new Set(
        decision.affectedUsernames.map(normaliseUsername).filter((username) => username !== ''),
      );
      for (const username of named) {
        const row = rows.get(username) ?? { username, decisions: 0, rules: [], lastSeen: null };
        row.decisions += 1;
        if (!row.rules.includes(alert.ruleId)) {
          row.rules.push(alert.ruleId);
        }
        if (decision.createdAt > (row.lastSeen ?? '')) {
          row.lastSeen = decision.createdAt;
        }
        rows.set(username, row);
      }
    }
  }

  return [...rows.values()].sort((left, right) => right.decisions - left.decisions);
}

function AccountHeat({ rows, readAlerts }: { rows: AccountHeatRow[]; readAlerts: number }) {
  if (rows.length === 0) {
    return (
      <EmptyState
        title="No account is named in the stored evidence"
        note="An account appears here when a decision snapshot names it as affected. The defender publishes no per-account counter of its own."
      />
    );
  }

  return (
    <div className="stack stack--tight">
      <table className="table">
        <thead>
          <tr>
            <th>Account</th>
            <th className="table__num">Decisions naming it</th>
            <th>Rules</th>
            <th>Last decision</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.username}>
              <td className="mono">{row.username}</td>
              <td className="table__num">{formatInteger(row.decisions)}</td>
              <td className="mono dim">{row.rules.join(', ')}</td>
              <td className="mono">{formatTimestamp(row.lastSeen)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="field__hint">
        Counted from the stored evidence behind {readAlerts} alert(s) of this source. The defender publishes no
        per-account counter of its own.
      </p>
    </div>
  );
}

function SourceTable({
  sources,
  selected,
  onSelect,
}: {
  sources: SourceSummary[];
  selected: string | null;
  onSelect: (ip: string) => void;
}) {
  if (sources.length === 0) {
    return (
      <EmptyState
        title="No source activity"
        note="The defender records a source the first time a login request arrives from it."
      />
    );
  }

  return (
    <table className="table">
      <thead>
        <tr>
          <th>Source</th>
          <th className="table__num">Incoming</th>
          <th className="table__num">Failures</th>
          <th className="table__num">Successes</th>
          <th className="table__num">Accounts</th>
          <th>Last activity</th>
        </tr>
      </thead>
      <tbody>
        {sources.map((source) => (
          <tr
            key={source.sourceIp}
            className={selected === source.sourceIp ? 'is-selected' : ''}
            onClick={() => onSelect(source.sourceIp)}
            tabIndex={0}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onSelect(source.sourceIp);
              }
            }}
          >
            <td className="mono">{source.sourceIp}</td>
            <td className="table__num">{formatInteger(source.totalIncoming)}</td>
            <td className="table__num">{formatInteger(source.totalFailures)}</td>
            <td className="table__num">{formatInteger(source.totalSuccesses)}</td>
            <td className="table__num">{formatInteger(source.trackedAccounts)}</td>
            <td className="mono">{formatTimestamp(source.lastActivity)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function SourcesView() {
  const { api, snapshot } = useDefender();
  const [sources, setSources] = useState<SourceSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<SourceDetail | null>(null);
  const [events, setEvents] = useState<PersistedEvent[]>([]);
  const [accountHeat, setAccountHeat] = useState<AccountHeatRow[]>([]);
  const [heatAlertsRead, setHeatAlertsRead] = useState(0);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [loading, setLoading] = useState(true);

  const loadSources = useCallback(async () => {
    setLoading(true);
    try {
      const result = await api.sources();
      setSources(result.data);
      setError(null);
      setSelected((current) => {
        if (current && result.data.some((source) => source.sourceIp === current)) {
          return current;
        }
        return result.data.length > 0 ? result.data[0].sourceIp : null;
      });
    } catch (cause) {
      setError(
        cause instanceof ApiRequestError
          ? cause
          : new ApiRequestError('The source list could not be read.', { status: 0, code: 'unknown_error' }),
      );
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    void loadSources();
  }, [loadSources]);

  useEffect(() => {
    if (!selected) {
      setDetail(null);
      setEvents([]);
      setAccountHeat([]);
      setHeatAlertsRead(0);
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const [detailResult, eventResult, alertResult] = await Promise.all([
          api.source(selected),
          api.events(200),
          api.alerts(200),
        ]);
        if (cancelled) {
          return;
        }
        const alerts = alertResult.data.filter((alert) => alert.sourceIp === selected);
        setDetail(detailResult.data);
        setEvents(eventResult.data.filter((event) => event.sourceIp === selected));
        setError(null);

        // The account heat is derived from the evidence behind the alerts of this source. The
        // number of alert reads is bounded so the drill-down stays a single screen load.
        const readable = alerts.slice(0, ACCOUNT_HEAT_ALERT_LIMIT);
        const evidenceByAlert = new Map<string, AlertEvidence[]>();
        await Promise.all(
          readable.map(async (alert) => {
            try {
              const result = await api.alertEvidence(alert.alertId);
              evidenceByAlert.set(
                alert.alertId,
                result.data.evidence.map((evidence) => ({
                  affectedUsernames: evidence.affectedUsernames,
                  createdAt: evidence.createdAt,
                })),
              );
            } catch {
              evidenceByAlert.set(alert.alertId, []);
            }
          }),
        );
        if (cancelled) {
          return;
        }
        setAccountHeat(deriveAccountHeat(readable, evidenceByAlert));
        setHeatAlertsRead(readable.length);
      } catch (cause) {
        if (!cancelled) {
          setError(
            cause instanceof ApiRequestError
              ? cause
              : new ApiRequestError('The source drill-down could not be read.', { status: 0, code: 'unknown_error' }),
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [api, selected]);

  const sourceBlocks = useMemo(
    () => (snapshot?.activeBlocks ?? []).filter((block) => block.sourceIp === selected),
    [selected, snapshot],
  );

  return (
    <div className="stack">
      {error ? (
        <Banner tone="alert" title="Source data unavailable">
          {error.message} ({error.code})
        </Banner>
      ) : null}

      <div className="grid grid--split">
        <Panel
          title="Sources"
          meta={loading ? 'loading…' : `${formatInteger(sources.length)} tracked`}
          actions={
            <button type="button" className="btn btn--sm btn--ghost" onClick={() => void loadSources()}>
              Refresh
            </button>
          }
          flush
        >
          <SourceTable sources={sources} selected={selected} onSelect={setSelected} />
        </Panel>

        <div className="stack">
          <Panel title="Source drill-down" meta={selected ?? 'no source selected'}>
            {!selected ? (
              <EmptyState title="Select a source" note="Pick a source address to inspect its accounts and rules." />
            ) : !detail ? (
              <p className="mono dim">Loading source detail…</p>
            ) : (
              <div className="stack stack--tight">
                <dl className="kv">
                  <dt className="kv__key">Source</dt>
                  <dd className="kv__value">{detail.source.sourceIp}</dd>
                  <dt className="kv__key">Incoming</dt>
                  <dd className="kv__value">{formatInteger(detail.source.totalIncoming)}</dd>
                  <dt className="kv__key">Failures</dt>
                  <dd className="kv__value">{formatInteger(detail.source.totalFailures)}</dd>
                  <dt className="kv__key">Successes</dt>
                  <dd className="kv__value">{formatInteger(detail.source.totalSuccesses)}</dd>
                  <dt className="kv__key">Tracked accounts</dt>
                  <dd className="kv__value">{formatInteger(detail.source.trackedAccounts)}</dd>
                  <dt className="kv__key">Last activity</dt>
                  <dd className="kv__value">{formatTimestamp(detail.source.lastActivity)}</dd>
                </dl>
              </div>
            )}
          </Panel>

          <Panel title="Rule outcomes" meta={detail ? `${formatInteger(detail.alerts.length)} alert(s)` : '—'}>
            {!detail || detail.alerts.length === 0 ? (
              <EmptyState title="No rule outcomes" note="No alert has been raised for this source." />
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Rule</th>
                    <th>Category</th>
                    <th>Severity</th>
                    <th className="table__num">Count</th>
                    <th>Last seen</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.alerts.map((alert) => (
                    <tr key={alert.alertId}>
                      <td>{alert.ruleId}</td>
                      <td>{categoryLabel(alert.category)}</td>
                      <td>
                        <StatusPill tone={alert.severity === 'HIGH' ? 'alert' : alert.severity === 'MEDIUM' ? 'warn' : 'mute'}>
                          {alert.severity}
                        </StatusPill>
                      </td>
                      <td className="table__num">{formatInteger(alert.occurrenceCount)}</td>
                      <td className="mono">{formatTimestamp(alert.lastSeen)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Panel>

          <Panel title="Account heat" meta={`${formatInteger(accountHeat.length)} account(s)`}>
            <AccountHeat rows={accountHeat} readAlerts={heatAlertsRead} />
          </Panel>

          <Panel title="Blocks on this source" meta={`${formatInteger(sourceBlocks.length)} active`}>
            <BlockList
              blocks={sourceBlocks}
              emptyTitle="This source is not blocked"
              emptyNote="Blocks appear here while protection is ON and a rule matched this source. The full block history is on the Diagnostics screen."
            />
          </Panel>

          <Panel title="Supporting events" meta={selected ? `${formatInteger(events.length)} persisted event(s)` : '—'}>
            {events.length === 0 ? (
              <EmptyState
                title="No persisted events"
                note="The defender persists recent security events; this source has none in the retained page."
              />
            ) : (
              <ul className="timeline">
                {events.map((event) => (
                  <li key={event.eventId} className="timeline__item">
                    <span className="mono timeline__time">{formatTimestamp(event.occurredAt)}</span>
                    <span className="timeline__type">{event.type}</span>
                    <span className="mono dim">
                      {event.critical ? 'critical' : 'telemetry'}
                      {event.aggregated ? ' · aggregated' : ''}
                    </span>
                    {event.requestId ? <span className="mono dim">req {event.requestId}</span> : null}
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </div>
      </div>
    </div>
  );
}
