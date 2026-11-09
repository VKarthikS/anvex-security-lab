import { useEffect, useMemo, useState } from 'react';
import {
  ApiRequestError,
  Banner,
  EmptyState,
  formatDateTime,
  formatDecimal,
  formatInteger,
  formatPercent,
  Panel,
  StatusPill,
  type ComparisonReport,
  type RunSnapshot,
} from '@anvex/shared';

import { useSimulator } from '../state/SimulatorContext';

const LABEL_TONE: Record<string, 'signal' | 'warn' | 'alert' | 'mute'> = {
  'measured-comparison': 'signal',
  'small-sample': 'warn',
  'incomplete-comparison': 'alert',
};

function RunTable({
  runs,
  baseline,
  candidate,
  onBaseline,
  onCandidate,
  onOpen,
}: {
  runs: ReturnType<typeof useSimulator>['runs'];
  baseline: string | null;
  candidate: string | null;
  onBaseline: (id: string) => void;
  onCandidate: (id: string) => void;
  onOpen: (id: string) => void;
}) {
  if (runs.length === 0) {
    return (
      <EmptyState
        title="No run has been recorded"
        note="The simulator retains the runs of this session. Start a run to create the first one."
      />
    );
  }

  return (
    <table className="table">
      <thead>
        <tr>
          <th>Run</th>
          <th>Scenario</th>
          <th>State</th>
          <th className="table__num">Sent</th>
          <th className="table__num">Completed</th>
          <th className="table__num">Failed auth</th>
          <th className="table__num">Rate limited</th>
          <th className="table__num">Blocked</th>
          <th className="table__num">Transport</th>
          <th>Started</th>
          <th>Compare</th>
        </tr>
      </thead>
      <tbody>
        {runs.map((run) => (
          <tr key={run.runId ?? 'unknown'}>
            <td className="mono">{run.runId?.slice(0, 8) ?? '—'}</td>
            <td>{run.scenarioId ?? '—'}</td>
            <td className="mono">{run.state}</td>
            <td className="table__num">{formatInteger(run.counters.sent)}</td>
            <td className="table__num">{formatInteger(run.counters.completed)}</td>
            <td className="table__num">{formatInteger(run.counters.authenticationFailure)}</td>
            <td className="table__num">{formatInteger(run.counters.rateLimited)}</td>
            <td className="table__num">{formatInteger(run.counters.sourceBlocked)}</td>
            <td className="table__num">{formatInteger(run.counters.transportErrors)}</td>
            <td className="mono">{formatDateTime(run.startedAt)}</td>
            <td>
              <div className="row">
                <button
                  type="button"
                  className={`btn btn--sm btn--ghost${baseline === run.runId ? ' is-active' : ''}`}
                  onClick={() => onBaseline(run.runId ?? '')}
                  aria-pressed={baseline === run.runId}
                >
                  Base
                </button>
                <button
                  type="button"
                  className={`btn btn--sm btn--ghost${candidate === run.runId ? ' is-active' : ''}`}
                  onClick={() => onCandidate(run.runId ?? '')}
                  aria-pressed={candidate === run.runId}
                >
                  Cand
                </button>
                <button
                  type="button"
                  className="btn btn--sm btn--ghost"
                  onClick={() => onOpen(run.runId ?? '')}
                  aria-label={`Open the full snapshot of run ${run.runId ?? ''}`}
                >
                  Detail
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ReportPanel({ report }: { report: ComparisonReport }) {
  const reduction = report.reduction;
  return (
    <div className="stack stack--tight">
      <div className="row row--between row--wrap">
        <span className="mono muted">{report.comparisonId}</span>
        <StatusPill tone={LABEL_TONE[reduction.label] ?? 'mute'}>{reduction.label}</StatusPill>
      </div>

      <div className="stat-row">
        <div className="stat">
          <span className="label">Requests sent reduction</span>
          <span className="stat__value numeral stat__value--sm">{formatPercent(reduction.sentReductionPercent)}</span>
          <span className="stat__unit">measured</span>
        </div>
        <div className="stat">
          <span className="label">Defender received reduction</span>
          <span className="stat__value numeral stat__value--sm">
            {formatPercent(reduction.defenderReceivedReductionPercent)}
          </span>
          <span className="stat__unit">measured</span>
        </div>
        <div className="stat">
          <span className="label">Admission reduction</span>
          <span className="stat__value numeral stat__value--sm">
            {formatPercent(reduction.authenticationAdmissionReductionPercent)}
          </span>
          <span className="stat__unit">measured</span>
        </div>
        <div className="stat">
          <span className="label">Rate limited increase</span>
          <span className="stat__value numeral stat__value--sm">
            {reduction.rateLimitedIncrease === null ? '—' : formatDecimal(reduction.rateLimitedIncrease, 0)}
          </span>
          <span className="stat__unit">requests</span>
        </div>
      </div>

      <dl className="kv">
        <dt className="kv__key">Baseline run</dt>
        <dd className="kv__value">{report.baseline.runId}</dd>
        <dt className="kv__key">Candidate run</dt>
        <dd className="kv__value">{report.candidate.runId}</dd>
        <dt className="kv__key">Baseline sent</dt>
        <dd className="kv__value">{formatInteger(report.baseline.sent)}</dd>
        <dt className="kv__key">Candidate sent</dt>
        <dd className="kv__value">{formatInteger(report.candidate.sent)}</dd>
        <dt className="kv__key">Baseline reached auth</dt>
        <dd className="kv__value">{formatInteger(report.baseline.defenderReachedAuthentication)}</dd>
        <dt className="kv__key">Candidate reached auth</dt>
        <dd className="kv__value">{formatInteger(report.candidate.defenderReachedAuthentication)}</dd>
        <dt className="kv__key">Baseline rate limited</dt>
          <dd className="kv__value">{formatInteger(report.baseline.defenderRateLimited)}</dd>
        <dt className="kv__key">Candidate rate limited</dt>
          <dd className="kv__value">{formatInteger(report.candidate.defenderRateLimited)}</dd>
        <dt className="kv__key">Identical parameters</dt>
        <dd className="kv__value">{String(report.parameters.identicalParameters ?? 'not published')}</dd>
        <dt className="kv__key">Policy version</dt>
        <dd className="kv__value">{String(report.settingsSnapshot.policyVersion ?? 'not published')}</dd>
        <dt className="kv__key">Created</dt>
        <dd className="kv__value">{formatDateTime(report.createdAt)}</dd>
      </dl>

      <div>
        <p className="label">Caveats</p>
        <ul className="stack stack--tight muted report-caveats">
          {report.caveats.map((caveat) => (
            <li key={caveat}>{caveat}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function RunDetail({ run }: { run: RunSnapshot }) {
  const latency = run.latency;
  return (
    <div className="stack stack--tight">
      <div className="row row--between row--wrap">
        <span className="mono muted">{run.runId}</span>
        <StatusPill tone={run.state === 'FAILED' ? 'alert' : run.state === 'RUNNING' ? 'signal' : 'mute'}>{run.state}</StatusPill>
      </div>
      <dl className="kv">
        <dt className="kv__key">Scenario</dt>
        <dd className="kv__value">{run.scenarioId ?? '—'}</dd>
        <dt className="kv__key">Target</dt>
        <dd className="kv__value">{run.targetBaseUrl ?? '—'}</dd>
        <dt className="kv__key">Started</dt>
        <dd className="kv__value">{formatDateTime(run.startedAt)}</dd>
        <dt className="kv__key">Ended</dt>
        <dd className="kv__value">{formatDateTime(run.endedAt)}</dd>
        <dt className="kv__key">Achieved rate</dt>
        <dd className="kv__value">{formatDecimal(run.achievedRatePerSecond, 2)} req/s</dd>
        <dt className="kv__key">Planned / sent</dt>
        <dd className="kv__value">
          {formatInteger(run.counters.planned)} / {formatInteger(run.counters.sent)}
        </dd>
        <dt className="kv__key">Completed / cancelled</dt>
        <dd className="kv__value">
          {formatInteger(run.counters.completed)} / {formatInteger(run.counters.cancelled)}
        </dd>
        <dt className="kv__key">Transport errors</dt>
        <dd className="kv__value">{formatInteger(run.counters.transportErrors)}</dd>
        <dt className="kv__key">Auth success / failure</dt>
        <dd className="kv__value">
          {formatInteger(run.counters.authenticationSuccess)} / {formatInteger(run.counters.authenticationFailure)}
        </dd>
        <dt className="kv__key">Rate limited / blocked</dt>
        <dd className="kv__value">
          {formatInteger(run.counters.rateLimited)} / {formatInteger(run.counters.sourceBlocked)}
        </dd>
        <dt className="kv__key">Latency avg / p50 / p95 / max</dt>
        <dd className="kv__value">
          {formatDecimal(latency.avgMs, 1)} / {formatDecimal(latency.p50Ms, 1)} / {formatDecimal(latency.p95Ms, 1)} /{' '}
          {formatDecimal(latency.maxMs, 1)} ms
        </dd>
        <dt className="kv__key">Message</dt>
        <dd className="kv__value">{run.message ?? '—'}</dd>
      </dl>
      {run.config ? (
        <p className="field__hint">
          Configuration as accepted by the service: {run.config.scenarioId} · {run.config.ratePerSecond} req/s ·{' '}
          {run.config.durationSeconds}s · limit {run.config.requestLimit} · concurrency {run.config.concurrency}
          {run.config.targetUsernames.length > 0 ? ` · accounts ${run.config.targetUsernames.join(', ')}` : ''}
        </p>
      ) : null}
    </div>
  );
}

export function HistoryView() {
  const { api, runs, experiments, refreshRuns, refreshExperiments } = useSimulator();
  const [baseline, setBaseline] = useState<string | null>(null);
  const [candidate, setCandidate] = useState<string | null>(null);
  const [report, setReport] = useState<ComparisonReport | null>(null);
  const [runDetail, setRunDetail] = useState<RunSnapshot | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void refreshRuns();
    void refreshExperiments();
  }, [refreshExperiments, refreshRuns]);

  const selectable = useMemo(() => runs.filter((run) => run.runId), [runs]);

  const openRun = async (runId: string) => {
    if (runId === '') {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await api.run(runId);
      setRunDetail(result.data);
    } catch (cause) {
      setRunDetail(null);
      setError(
        cause instanceof ApiRequestError
          ? cause
          : new ApiRequestError('The run snapshot could not be read.', { status: 0, code: 'unknown_error' }),
      );
    } finally {
      setBusy(false);
    }
  };

  const compare = async () => {
    if (!baseline || !candidate) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await api.createExperiment(baseline, candidate);
      setReport(result.data);
      await refreshExperiments();
    } catch (cause) {
      setError(
        cause instanceof ApiRequestError
          ? cause
          : new ApiRequestError('The comparison could not be created.', { status: 0, code: 'unknown_error' }),
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="stack">
      {error ? (
        <Banner tone="alert" title="Comparison failed">
          {error.message} ({error.code})
        </Banner>
      ) : null}

      <Panel
        title="Retained runs"
        meta={`${formatInteger(selectable.length)} run(s)`}
        actions={
          <button type="button" className="btn btn--sm btn--ghost" onClick={() => void refreshRuns()}>
            Refresh
          </button>
        }
        flush
      >
        <RunTable
          runs={selectable}
          baseline={baseline}
          candidate={candidate}
          onBaseline={setBaseline}
          onCandidate={setCandidate}
          onOpen={(id) => void openRun(id)}
        />
      </Panel>

      <Panel
        title="Run detail"
        meta={runDetail ? 'read from GET /api/v1/runs/{id}' : 'no run opened'}
        actions={
          runDetail ? (
            <button type="button" className="btn btn--sm btn--ghost" onClick={() => void openRun(runDetail.runId ?? '')}>
              Reload
            </button>
          ) : null
        }
      >
        {!runDetail ? (
          <EmptyState
            title="No run opened"
            note="Choose Detail on a retained run to read its full snapshot from the simulator, including the configuration it was started with."
          />
        ) : (
          <RunDetail run={runDetail} />
        )}
      </Panel>

      <Panel
        title="OFF versus ON comparison"
        meta={baseline && candidate ? 'baseline and candidate selected' : 'select two runs'}
      >
        <div className="stack stack--tight">
          <p className="muted">
            The comparison is computed by the simulator from the counters that were actually measured in both runs. No
            fixed percentage is assumed and nothing is extrapolated.
          </p>
          <div className="row row--wrap">
            <span className="mono comparison__slot">
              baseline: {baseline ? baseline.slice(0, 8) : 'not selected'}
            </span>
            <span className="mono comparison__slot">
              candidate: {candidate ? candidate.slice(0, 8) : 'not selected'}
            </span>
            <button
              type="button"
              className="btn btn--primary"
              disabled={busy || !baseline || !candidate || baseline === candidate}
              onClick={() => void compare()}
            >
              {busy ? 'Comparing…' : 'Create comparison'}
            </button>
          </div>
          {report ? <ReportPanel report={report} /> : null}
        </div>
      </Panel>

      <Panel title="Retained comparison reports" meta={`${formatInteger(experiments.length)} report(s)`}>
        {experiments.length === 0 ? (
          <EmptyState
            title="No comparison has been created"
            note="Comparisons are retained by the simulator; both runs stay available in the run history."
          />
        ) : (
          <div className="stack">
            {experiments.map((item) => (
              <ReportPanel key={item.comparisonId} report={item} />
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}
