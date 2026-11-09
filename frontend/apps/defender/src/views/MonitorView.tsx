import { useEffect, useMemo, useState } from 'react';
import {
  Banner,
  EmptyState,
  formatInteger,
  formatTimestamp,
  Meter,
  Panel,
  ruleLabel,
  SparklineFrame,
  Stat,
  StatusPill,
  categoryLabel,
  type PersistedEvent,
} from '@anvex/shared';

import { EventRibbon } from '../components/EventRibbon';
import { useDefender } from '../state/DefenderContext';

function TrafficSplit() {
  const { snapshot } = useDefender();
  const counters = snapshot?.counters;
  if (!counters) {
    return <p className="mono dim">Counters are not available yet.</p>;
  }

  const admitted = counters.requestsReachingAuthentication;
  const gated = counters.rateLimitedRequests + counters.sourceBlockedRequests;
  const total = Math.max(1, admitted + gated);
  const admittedWidth = (admitted / total) * 100;

  return (
    <div className="stack stack--tight">
      <div className="row row--between">
        <span className="label">Incoming → admitted</span>
        <span className="mono dim">
          {formatInteger(counters.incomingRequests)} incoming
        </span>
      </div>
      <div className="split" role="img" aria-label={`${formatInteger(admitted)} admitted, ${formatInteger(gated)} gated`}>
        <div className="split__admitted" style={{ width: `${admittedWidth}%` }} />
        <div className="split__gated" style={{ width: `${100 - admittedWidth}%` }} />
      </div>
      <div className="row row--between split__legend">
        <span className="mono split__legend-item split__legend-item--admitted">
          {formatInteger(admitted)} reached authentication
        </span>
        <span className="mono split__legend-item split__legend-item--gated">
          {formatInteger(gated)} gated by protection
        </span>
      </div>

      <div className="split__stats">
        <Stat label="Failures" value={formatInteger(counters.authenticationFailure)} unit="attempts" small />
        <Stat label="Successes" value={formatInteger(counters.authenticationSuccess)} unit="attempts" small />
        <Stat label="Rate limited" value={formatInteger(counters.rateLimitedRequests)} unit="requests" small tone="warn" />
        <Stat label="Source blocked" value={formatInteger(counters.sourceBlockedRequests)} unit="requests" small tone="alert" />
      </div>
    </div>
  );
}

function RuleMatches() {
  const { snapshot } = useDefender();
  const alerts = snapshot?.recentAlerts ?? [];

  const grouped = useMemo(() => {
    const map = new Map<string, { ruleId: string; severity: string; alerts: number; occurrences: number; lastSeen: string }>();
    for (const alert of alerts) {
      const current = map.get(alert.ruleId) ?? {
        ruleId: alert.ruleId,
        severity: alert.severity,
        alerts: 0,
        occurrences: 0,
        lastSeen: alert.lastSeen,
      };
      current.alerts += 1;
      current.occurrences += alert.occurrenceCount;
      if (alert.lastSeen > current.lastSeen) {
        current.lastSeen = alert.lastSeen;
        current.severity = alert.severity;
      }
      map.set(alert.ruleId, current);
    }
    return [...map.values()].sort((left, right) => right.occurrences - left.occurrences);
  }, [alerts]);

  if (grouped.length === 0) {
    return (
      <EmptyState
        title="No rule has matched"
        note="Detections appear here only after the defender evaluates a source against the configured thresholds."
      />
    );
  }

  return (
    <table className="table">
      <thead>
        <tr>
          <th>Rule</th>
          <th>Severity</th>
          <th className="table__num">Alerts</th>
          <th className="table__num">Occurrences</th>
          <th>Last seen</th>
        </tr>
      </thead>
      <tbody>
        {grouped.map((row) => (
          <tr key={row.ruleId}>
            <td>{ruleLabel(row.ruleId)}</td>
            <td>
              <StatusPill tone={row.severity === 'HIGH' ? 'alert' : row.severity === 'MEDIUM' ? 'warn' : 'mute'}>
                {row.severity}
              </StatusPill>
            </td>
            <td className="table__num">{formatInteger(row.alerts)}</td>
            <td className="table__num">{formatInteger(row.occurrences)}</td>
            <td className="mono">{formatTimestamp(row.lastSeen)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function EvidenceRail() {
  const { snapshot } = useDefender();
  const evidence = snapshot?.recentEvidence ?? [];

  if (evidence.length === 0) {
    return (
      <EmptyState
        title="No evidence recorded"
        note="A decision dossier is written whenever a rule threshold is reached."
      />
    );
  }

  return (
    <ul className="evidence-rail">
      {evidence.slice(0, 6).map((item) => (
        <li key={item.decisionId} className="evidence-rail__item">
          <div className="row row--between">
            <span className="mono evidence-rail__id">{item.decisionId}</span>
            <StatusPill tone={item.action === 'enforced' ? 'alert' : 'violet'}>
              {item.action === 'enforced' ? 'Enforced' : 'Observation only'}
            </StatusPill>
          </div>
          <p className="evidence-rail__title">{categoryLabel(item.category)}</p>
          <p className="mono dim">
            {item.sourceIp} · {formatTimestamp(item.createdAt)}
            {item.affectedUsernames.length > 0 ? ` · ${item.affectedUsernames.join(', ')}` : ''}
          </p>
        </li>
      ))}
    </ul>
  );
}

function PersistedEvents({ events }: { events: PersistedEvent[] }) {
  if (events.length === 0) {
    return (
      <p className="empty__note">
        The defender persists recent security events. Nothing has been persisted for the retained page yet.
      </p>
    );
  }
  return (
    <ul className="timeline">
      {events.slice(0, 12).map((event) => (
        <li key={event.eventId} className="timeline__item">
          <span className="mono timeline__time">{formatTimestamp(event.occurredAt)}</span>
          <span className="timeline__type">{event.type}</span>
          <span className="mono dim">{event.sourceIp}</span>
          <span className="mono dim">{event.critical ? 'critical' : 'telemetry'}</span>
        </li>
      ))}
    </ul>
  );
}

export function MonitorView() {
  const { api, snapshot, snapshotError, streamStatus, events, samples } = useDefender();
  const [persisted, setPersisted] = useState<PersistedEvent[]>([]);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const result = await api.events(40);
        if (!cancelled) {
          setPersisted(result.data);
        }
      } catch {
        if (!cancelled) {
          setPersisted([]);
        }
      }
    };
    void load();
    const timer = window.setInterval(() => {
      void load();
    }, 3_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [api]);

  const stale = streamStatus === 'stale';
  const counters = snapshot?.counters;
  const incomingSeries = samples.map((sample) => sample.incoming);
  const admittedSeries = samples.map((sample) => sample.admitted);

  return (
    <div className="stack">
      {stale ? (
        <Banner tone="warn" title="Stale — reconnecting">
          The live event stream dropped. The figures below are the last known snapshot and are not live. The snapshot is
          re-read as soon as the subscription is restored.
        </Banner>
      ) : null}

      {snapshotError ? (
        <Banner tone="alert" title="Snapshot unavailable">
          {snapshotError.message} ({snapshotError.code})
        </Banner>
      ) : null}

      <div className="grid grid--split">
        <Panel
          title="Live activity"
          meta={`${formatInteger(events.length)} event(s) in buffer`}
          tone={stale ? 'warn' : 'signal'}
        >
          <EventRibbon
            events={events}
            stale={stale}
            snapshotIncoming={snapshot?.counters.incomingRequests ?? 0}
          />
          <div className="ribbon-history">
            <p className="label">Recent persisted events</p>
            <PersistedEvents events={persisted} />
          </div>
        </Panel>

        <div className="stack">
          <Panel title="Traffic split" meta={snapshot?.protection.policyVersion ?? '—'}>
            <TrafficSplit />
          </Panel>

          <Panel title="Event rate" meta="rolling window · 120 samples">
            <div className="stack stack--tight">
              <SparklineFrame
                values={incomingSeries}
                label="Incoming requests"
                unit="requests per 2 second snapshot interval"
              />
              <SparklineFrame
                values={admittedSeries}
                tone="violet"
                label="Requests reaching authentication"
                unit="requests per 2 second snapshot interval"
              />
            </div>
          </Panel>
        </div>
      </div>

      <Panel title="Counters" meta={snapshot ? 'authoritative defender counters' : '—'}>
        <div className="stat-row">
          <Stat
            label="Incoming requests"
            value={counters ? formatInteger(counters.incomingRequests) : '—'}
            unit="requests"
            stale={stale}
          />
          <Stat
            label="Reached authentication"
            value={counters ? formatInteger(counters.requestsReachingAuthentication) : '—'}
            unit="requests"
            stale={stale}
          />
          <Stat
            label="Rate limited"
            value={counters ? formatInteger(counters.rateLimitedRequests) : '—'}
            unit="requests"
            tone="warn"
            stale={stale}
          />
          <Stat
            label="Source blocked"
            value={counters ? formatInteger(counters.sourceBlockedRequests) : '—'}
            unit="requests"
            tone="alert"
            stale={stale}
          />
          <Stat
            label="Invalid / overload"
            value={counters ? formatInteger(counters.invalidOrOverloadRequests) : '—'}
            unit="requests"
            tone="violet"
            stale={stale}
          />
        </div>
        {counters ? (
          <div className="queue-note mono">
            pipeline queue depth {formatInteger(snapshot?.eventPipeline.telemetryQueueDepth ?? 0)} /{' '}
            {formatInteger(snapshot?.eventPipeline.telemetryQueueCapacity ?? 0)} · critical{' '}
            {formatInteger(snapshot?.eventPipeline.criticalQueueDepth ?? 0)} /{' '}
            {formatInteger(snapshot?.eventPipeline.criticalQueueCapacity ?? 0)}
          </div>
        ) : null}
        {snapshot ? (
          <div className="queue-note">
            <Meter
              value={snapshot.eventPipeline.telemetryQueueDepth}
              max={snapshot.eventPipeline.telemetryQueueCapacity}
              label="Telemetry queue depth"
            />
          </div>
        ) : null}
      </Panel>

      <div className="grid grid--split">
        <Panel title="Rule matches" meta={snapshot ? `${formatInteger(snapshot.recentAlerts.length)} recent alert(s)` : '—'}>
          <RuleMatches />
        </Panel>

        <Panel title="Evidence rail" meta="most recent decisions">
          <EvidenceRail />
        </Panel>
      </div>
    </div>
  );
}
