import {
  Banner,
  formatInteger,
  formatTimestamp,
  Panel,
  ruleLabel,
  StatusPill,
  categoryLabel,
  type EvidenceSnapshot,
} from '@anvex/shared';

function formatMillis(value: number | undefined): string {
  if (value === undefined || value === null) {
    return '—';
  }
  if (value % 1000 === 0) {
    return `${value / 1000}s`;
  }
  return `${value}ms`;
}

export interface EvidenceDossierProps {
  evidence: EvidenceSnapshot;
  /** Rendered when the snapshot was loaded through a single-decision lookup. */
  standalone?: boolean;
}

/**
 * The evidence dossier: matched rules with their exact thresholds and windows, the policy
 * snapshot that was in force, the action taken and the supporting event references.
 */
export function EvidenceDossier({ evidence, standalone }: EvidenceDossierProps) {
  const policy = evidence.policySnapshot;

  return (
    <div className="stack stack--tight">
      <div className="row row--between row--wrap">
        <div>
          <p className="mono dossier__id">{evidence.decisionId}</p>
          <p className="dossier__title">{categoryLabel(evidence.category)}</p>
        </div>
        <div className="row">
          <StatusPill tone={evidence.action === 'enforced' ? 'alert' : 'violet'}>
            {evidence.action === 'enforced' ? 'Enforced' : 'Observation only'}
          </StatusPill>
          <StatusPill tone={evidence.protectionMode === 'ON' ? 'signal' : 'warn'}>
            Protection {evidence.protectionMode}
          </StatusPill>
        </div>
      </div>

      {standalone ? (
        <Banner tone="info" title="Standalone evidence snapshot">
          Loaded from <span className="mono">GET /api/v1/evidence/{evidence.decisionId}</span>.
        </Banner>
      ) : null}

      <dl className="kv">
        <dt className="kv__key">Source</dt>
        <dd className="kv__value">{evidence.sourceIp}</dd>
        <dt className="kv__key">Reason</dt>
        <dd className="kv__value">{evidence.reason}</dd>
        <dt className="kv__key">Affected accounts</dt>
        <dd className="kv__value">
          {evidence.affectedUsernames.length > 0 ? evidence.affectedUsernames.join(', ') : 'none recorded'}
        </dd>
        <dt className="kv__key">Decided at</dt>
        <dd className="kv__value">{formatTimestamp(evidence.createdAt)}</dd>
        <dt className="kv__key">Block expiry</dt>
        <dd className="kv__value">{evidence.blockExpiry ? formatTimestamp(evidence.blockExpiry) : 'no block applied'}</dd>
        <dt className="kv__key">Policy version</dt>
        <dd className="kv__value">{evidence.policyVersion}</dd>
      </dl>

      <Panel title="Matched rules" meta={`${formatInteger(evidence.matchedRules.length)} rule(s)`} flush>
        {evidence.matchedRules.length === 0 ? (
          <p className="empty__note" style={{ padding: '16px 24px' }}>
            No rule was matched in this snapshot.
          </p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Rule</th>
                <th className="table__num">Threshold</th>
                <th className="table__num">Observed</th>
                <th className="table__num">Accounts</th>
                <th>Window</th>
              </tr>
            </thead>
            <tbody>
              {evidence.matchedRules.map((match) => (
                <tr key={`${match.ruleId}-${match.windowStart}`}>
                  <td>
                    {ruleLabel(match.ruleId)}
                    <span className="dossier__desc">{match.description}</span>
                  </td>
                  <td className="table__num">{formatInteger(match.threshold)}</td>
                  <td className="table__num">{formatInteger(match.observedCount)}</td>
                  <td className="table__num">{formatInteger(match.observedDistinctAccounts)}</td>
                  <td className="mono">
                    {formatTimestamp(match.windowStart)} → {formatTimestamp(match.windowEnd)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      {policy ? (
        <Panel title="Policy snapshot" meta={policy.policyVersion}>
          <dl className="kv">
            <dt className="kv__key">Repeated guessing</dt>
            <dd className="kv__value">
              ≥ {policy.repeatedGuessingFailureThreshold} failures / {formatMillis(policy.repeatedGuessingWindowMillis)}
            </dd>
            <dt className="kv__key">Password spraying</dt>
            <dd className="kv__value">
              ≥ {policy.sprayingFailureThreshold} failures across ≥ {policy.sprayingDistinctAccountThreshold} accounts /{' '}
              {formatMillis(policy.sprayingWindowMillis)}
            </dd>
            <dt className="kv__key">Rapid requests</dt>
            <dd className="kv__value">
              ≥ {policy.rapidRequestThreshold} requests / {formatMillis(policy.rapidRequestWindowMillis)}
            </dd>
            <dt className="kv__key">Token bucket</dt>
            <dd className="kv__value">
              {policy.tokenBucketRequestsPerSecond} req/s · burst {policy.tokenBucketBurstCapacity}
            </dd>
            <dt className="kv__key">Block duration</dt>
            <dd className="kv__value">{formatMillis(policy.blockDurationMillis)}</dd>
            <dt className="kv__key">Protection mode</dt>
            <dd className="kv__value">{policy.protectionMode}</dd>
            <dt className="kv__key">Snapshot created</dt>
            <dd className="kv__value">{formatTimestamp(policy.createdAt)}</dd>
          </dl>
        </Panel>
      ) : null}

      <Panel title="Supporting event references" meta={`${formatInteger(evidence.triggeringEventIds.length)} reference(s)`}>
        {evidence.triggeringEventIds.length === 0 ? (
          <p className="empty__note">No event references were stored with this decision.</p>
        ) : (
          <ul className="reflist">
            {evidence.triggeringEventIds.map((id) => (
              <li key={id} className="mono reflist__item">
                {id}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <Panel title="Raw observation payload">
        <div className="grid grid--two">
          <div>
            <p className="label">Observed</p>
            <pre className="codeblock mono">{JSON.stringify(evidence.observed, null, 2)}</pre>
          </div>
          <div>
            <p className="label">Windows</p>
            <pre className="codeblock mono">{JSON.stringify(evidence.windows, null, 2)}</pre>
          </div>
        </div>
      </Panel>
    </div>
  );
}
