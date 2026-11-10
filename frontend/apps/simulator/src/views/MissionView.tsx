import { useCallback, useEffect, useState } from 'react';
import {
  Banner,
  formatDateTime,
  formatDecimal,
  formatInteger,
  Icon,
  Panel,
  StatusPill,
  type DefenderOverview,
} from '@anvex/shared';

import { useSimulator } from '../state/SimulatorContext';

const REFRESH_INTERVAL_MS = 2500;

function displayScenario(id: string | null) {
  if (!id) return 'No scenario selected';
  return id.split('-').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
}

export function MissionView() {
  const {
    api,
    target,
    scenarios,
    form,
    selectScenario,
    currentRun,
    startRun,
    busy,
    streamStatus,
    error,
  } = useSimulator();
  const [overview, setOverview] = useState<DefenderOverview | null>(null);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [lastCheckedAt, setLastCheckedAt] = useState<string | null>(null);

  const runId = currentRun?.runId;
  const refreshOverview = useCallback(async (stillActive: () => boolean) => {
    try {
      const response = await api.defenderOverview(runId ?? undefined);
      if (!stillActive()) return;
      setOverview(response.data);
      setOverviewError(null);
      setLastCheckedAt(new Date().toISOString());
    } catch (cause) {
      if (!stillActive()) return;
      setOverview(null);
      setOverviewError(cause instanceof Error ? cause.message : 'Could not read defender status.');
    }
  }, [api, runId]);

  useEffect(() => {
    let active = true;
    const isActive = () => active;
    void refreshOverview(isActive);
    const timer = window.setInterval(() => {
      // Poll the read-only backend while this screen is visible; do not fabricate SSE traffic.
      void refreshOverview(isActive);
    }, REFRESH_INTERVAL_MS);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [refreshOverview]);

  const defender = overview?.available ? overview.snapshot : null;
  const correlation = overview?.runEvidence?.runId === runId ? overview?.runEvidence ?? null : null;
  const currentEvidence = correlation?.correlationAvailable ? correlation.decisions : [];
  const counters = currentRun?.counters;
  const sent = counters?.sent ?? 0;
  const planned = counters?.planned ?? 0;
  const succeeded = counters?.authenticationSuccess ?? 0;
  const failed = counters?.authenticationFailure ?? 0;
  const observedAuthentications = succeeded + failed;
  const denied = (counters?.rateLimited ?? 0) + (counters?.sourceBlocked ?? 0);
  const running = currentRun?.state === 'RUNNING' || currentRun?.state === 'STOPPING';
  const verified = Boolean(overview?.available);
  const mode = defender?.protection.mode;
  const progress = planned > 0 ? Math.min(100, (sent / planned) * 100) : 0;
  const isManual = form.scenarioId === 'manual-login';
  const stage = [
    {
      title: 'Connect',
      detail: verified ? 'Defender verified' : 'Waiting for verified defender',
      complete: verified,
      active: !verified,
    },
    {
      title: 'Generate',
      detail: sent > 0 ? `${formatInteger(sent)} real requests sent` : 'Launch controlled traffic',
      complete: sent > 0,
      active: verified && sent === 0,
    },
    {
      title: 'Observe',
      detail: observedAuthentications > 0
        ? `${formatInteger(succeeded)} successful, ${formatInteger(failed)} failed`
        : denied > 0
          ? `${formatInteger(denied)} requests rejected before authentication`
          : 'Await authentication outcomes',
      complete: observedAuthentications > 0 || denied > 0,
      active: sent > 0 && observedAuthentications === 0 && denied === 0,
    },
    {
      title: 'Respond',
      detail: denied > 0
        ? `${formatInteger(denied)} requests rejected`
        : mode === 'OFF'
          ? 'Observation only — no enforced blocks'
          : 'Await measured enforcement',
      complete: denied > 0,
      active: sent > 0 && denied === 0,
    },
  ];

  return (
    <div className="stack mission">
      <section className="mission-hero">
        <div className="mission-hero__main">
          <div className="mission-eyebrow"><span className="mission-eyebrow__mark" /> ANVEX / FIELD LAB 01</div>
          <h1 className="mission-headline">Watch an attack.<br /><span>Understand the defense.</span></h1>
          <p className="mission-intro">
            Run a controlled authentication scenario against the ANVEX defender. Follow the actual request outcomes,
            inspect the detection evidence, and see exactly what protection did — or did not — prevent.
          </p>
          <div className="mission-hero__actions">
            <button
              type="button"
              className="btn btn--primary mission-launch"
              disabled={busy || running || !form.scenarioId || isManual}
              onClick={() => void startRun()}
            >
              <Icon name="run" size={17} /> {running ? 'Simulation running' : 'Launch simulation'}
            </button>
            <a className="mission-text-link" href="#scenario">Configure experiment <Icon name="chevron" size={15}/></a>
          </div>
          {isManual ? <p className="mission-note">Select an automated scenario to launch here. Manual login remains available on the Run page.</p> : null}
        </div>
        <div className="mission-hero__status">
          <div className="mission-live"><span className={running && streamStatus === 'live' ? 'mission-live__dot is-pulsing' : 'mission-live__dot'} /> {running ? 'RUN IN PROGRESS' : 'SYSTEM STANDBY'}</div>
          <div className="mission-hero__number numeral">{formatInteger(sent).padStart(2, '0')}</div>
          <div className="mission-hero__number-label">REAL REQUESTS SENT</div>
          <div className="mission-hero__divider" />
          <div className="mission-hero__mini"><span>RUN STATE</span><strong>{currentRun?.state ?? 'NOT STARTED'}</strong></div>
          <div className="mission-hero__mini"><span>DEFENDER</span><strong>{verified ? 'VERIFIED' : 'NOT VERIFIED'}</strong></div>
          <div className="mission-hero__mini"><span>PROTECTION</span><strong>{mode ?? 'UNKNOWN'}</strong></div>
        </div>
      </section>

      {error ? <Banner tone="alert" title="Simulator returned an error">{error.message} ({error.code})</Banner> : null}
      {overviewError || (overview && !overview.available) ? (
        <Banner tone="warn" title="Defender data unavailable">
          {overviewError ?? overview?.message} The simulator's own run progress can still be viewed, but no defender detection or protection information is assumed.
        </Banner>
      ) : null}
      {streamStatus === 'stale' ? (
        <Banner tone="warn" title="Run progress may be stale">
          The simulator's progress connection is reconnecting; figures are last reported values, not claimed live values.
        </Banner>
      ) : null}

      <div className="mission-section-title">
        <div><span className="eyebrow">01 / experiment path</span><h2>The four checkpoints</h2></div>
        <span className="mono dim mission-small">Only observed conditions advance a checkpoint</span>
      </div>
      <div className="mission-stage-grid" aria-label="Experiment checkpoints">
        {stage.map((item, index) => (
          <div className={`mission-stage${item.complete ? ' is-complete' : ''}${item.active ? ' is-current' : ''}`} key={item.title}>
            <div className="mission-stage__top"><span className="mono">0{index + 1}</span>{item.complete ? <Icon name="check" size={16}/> : <span className="mission-stage__point" />}</div>
            <h3>{item.title}</h3>
            <p>{item.detail}</p>
          </div>
        ))}
      </div>

      <div className="mission-section-title">
        <div><span className="eyebrow">02 / live experiment</span><h2>What the systems reported</h2></div>
        <span className="mono dim mission-small">{lastCheckedAt ? `Defender last checked ${formatDateTime(lastCheckedAt)}` : 'Awaiting first defender snapshot'}</span>
      </div>
      <section className="mission-telemetry" aria-label="Measured run results">
        <Metric name="Auth successes" number={succeeded} detail="authenticated replies" />
        <Metric name="Auth failures" number={failed} detail="defender replies" tone="warn" />
        <Metric name="Rate limited" number={counters?.rateLimited ?? 0} detail="HTTP 429 rejections" tone="violet" />
        <Metric name="Source blocked" number={counters?.sourceBlocked ?? 0} detail="HTTP 429 rejections" tone="alert" />
        <Metric name="Transport errors" number={counters?.transportErrors ?? 0} detail="no defender response" tone="warn" />
      </section>
      <div className="mission-progress">
        <div className="row row--between row--wrap"><span className="label">Run progress</span><span className="mono muted">{formatInteger(sent)} / {formatInteger(planned)} requests sent</span></div>
        <div className="mission-progress__track" role="progressbar" aria-label="Scheduled requests sent" aria-valuenow={sent} aria-valuemin={0} aria-valuemax={Math.max(planned, 1)}>
          <div className="mission-progress__fill" style={{ width: `${progress}%` }} />
        </div>
        <div className="mission-progress__footer"><span>{displayScenario(currentRun?.scenarioId ?? form.scenarioId)}</span><span>{currentRun?.latency.count ? `${formatDecimal(currentRun.latency.avgMs, 1)} ms avg latency` : 'No latency samples yet'}</span></div>
      </div>

      <div className="mission-grid">
        <Panel title="03 / Detection evidence" meta={currentRun?.runId ? `Run ${currentRun.runId.slice(0, 8)}` : 'No run'} tone={currentEvidence.length > 0 ? 'warn' : 'default'}>
          <div className="stack stack--tight">
            {currentEvidence.length === 0 ? (
              <div className="mission-empty">
                <Icon name="evidence" size={28} />
                <strong>No request-linked evidence in the recent snapshot</strong>
                <p>{!runId
                  ? 'Launch a controlled simulation to generate traceable request IDs.'
                  : !correlation?.correlationAvailable
                    ? 'Request ID correlation is unavailable for this run or target.'
                    : 'None of the available recent defender decisions reference a request ID from this run. Older evidence may be outside the snapshot.'}</p>
              </div>
            ) : (
              <div className="mission-evidence-list">
                {currentEvidence.slice(0, 4).map((record) => (
                  <article className="mission-evidence" key={record.decisionId}>
                    <div className="row row--between row--wrap"><span className="label label--warn">{record.category.replaceAll('_', ' ')}</span><span className="mono dim mission-small">{formatDateTime(record.createdAt)}</span></div>
                    <div className="mission-evidence__rule">{record.matchedRules.map((rule) => rule.ruleId).join(' + ') || 'Decision recorded'}</div>
                    <p>{record.reason}</p>
                    <div className="mission-evidence__meta mono">Source {record.sourceIp} · {record.protectionMode === 'ON' ? 'Protection ON' : 'Observation only'}</div>
                    {record.matchedRules.length > 0 && <div className="mission-evidence__facts">{record.matchedRules.map((rule) => (
                      <span key={`${record.decisionId}-${rule.ruleId}`}>{rule.ruleId}: {formatInteger(rule.observedCount)} observed / {formatInteger(rule.threshold)} threshold</span>
                    ))}</div>}
                  </article>
                ))}
              </div>
            )}
            <p className="mission-caveat">{correlation?.correlationAvailable
              ? `${formatInteger(currentEvidence.length)} matched decision(s) in ${formatInteger(correlation.inspectedRecentDecisions)} recent records, using ${formatInteger(correlation.observedRequestIds)} defender-issued request ID(s). ${correlation.caveat}`
              : correlation?.caveat ?? 'Run evidence is available only after the simulator receives server-issued request IDs from the configured defender. No timing-based attribution is shown.'}</p>
          </div>
        </Panel>
        <Panel title="04 / Defender response" meta={verified ? 'Verified backend snapshot' : 'Waiting for defender'} tone={mode === 'ON' ? 'signal' : 'warn'}>
          <div className="mission-response">
            <div className="mission-response__heading"><Icon name="shield" size={28} /><div><span className="label">Protection mode</span><h3>{mode === 'ON' ? 'ENFORCING' : mode === 'OFF' ? 'OBSERVING ONLY' : 'UNAVAILABLE'}</h3></div></div>
            <p className="muted">
              {mode === 'ON'
                ? 'The defender may enforce rate limits and temporary source blocks when its rules match.'
                : mode === 'OFF'
                  ? 'The defender monitors and records threats, but ANVEX does not enforce its additional blocking or rate limiting.'
                  : 'Connect to a compatible ANVEX defender to see its current policy.'}
            </p>
            <div className="mission-response__stats">
              <div><span className="label">Current defender blocks</span><strong className="numeral">{verified ? formatInteger(defender?.activeBlocks.length ?? 0) : '—'}</strong></div>
              <div><span className="label">Recent defender alerts</span><strong className="numeral">{verified ? formatInteger(defender?.recentAlerts.length ?? 0) : '—'}</strong></div>
            </div>
            <div className="mission-response__bottom">
              {mode === 'ON' ? <StatusPill tone="signal">Protection enabled</StatusPill> : mode === 'OFF' ? <StatusPill tone="warn">Observation mode</StatusPill> : <StatusPill tone="mute">Unknown</StatusPill>}
              <span className="mono dim mission-small">Only the defender's local analyst console can change policy</span>
            </div>
            {verified && target?.baseUrl && (
              <a className="mission-defender-link" href={target.baseUrl} target="_blank" rel="noreferrer noopener">Open defender console <Icon name="chevron" size={14} /></a>
            )}
          </div>
        </Panel>
      </div>

      <div className="mission-section-title"><div><span className="eyebrow">05 / launch another controlled test</span><h2>Choose a scenario</h2></div></div>
      <div className="mission-scenario-list">
        {scenarios.filter((scenario) => scenario.id !== 'manual-login').map((scenario) => (
          <button
            type="button"
            key={scenario.id}
            className={`mission-scenario${form.scenarioId === scenario.id ? ' is-selected' : ''}`}
            onClick={() => selectScenario(scenario)}
            disabled={running || busy}
            aria-pressed={form.scenarioId === scenario.id}
          >
            <span className="mission-scenario__top"><Icon name={scenario.id === 'rapid-requests' ? 'pulse' : scenario.id === 'password-spraying' ? 'sources' : 'lock'} size={22}/><span className="mono">{form.scenarioId === scenario.id ? 'SELECTED' : 'SELECT'}</span></span>
            <strong>{scenario.displayName}</strong>
            <span className="mission-scenario__description">{scenario.description}</span>
            <span className="mission-scenario__footer mono">{scenario.defaultRatePerSecond} req/s · {scenario.defaultDurationSeconds} sec</span>
          </button>
        ))}
      </div>
      <p className="mission-footer-note">ANVEX Mission Control · Uses only the configured ANVEX test defender and seeded dummy identities. The experiment is controlled, not a general-purpose attack tool.</p>
    </div>
  );
}

function Metric({name, number, detail, tone = 'default'}: {name: string; number: number; detail: string; tone?: 'default' | 'warn' | 'violet' | 'alert'}) {
  return <div className={`mission-metric mission-metric--${tone}`}><span className="label">{name}</span><strong className="numeral">{formatInteger(number)}</strong><span className="mono dim">{detail}</span></div>;
}
