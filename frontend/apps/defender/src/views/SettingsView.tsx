import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ApiRequestError,
  Banner,
  EmptyState,
  formatInteger,
  formatTimestamp,
  Panel,
  StatusPill,
  type PolicySettings,
  type SettingsUpdate,
} from '@anvex/shared';

import { RuntimeDiagnosticsPanel } from '../components/RuntimeDiagnosticsPanel';
import { useDefender } from '../state/DefenderContext';
import { useAnnounce } from '../state/Announcer';

interface FieldSpec {
  key: keyof Omit<SettingsUpdate, 'policyVersion'>;
  label: string;
  min: number;
  max: number;
  unit: string;
  hint: string;
}

const FIELDS: FieldSpec[] = [
  {
    key: 'repeatedGuessingFailureThreshold',
    label: 'Repeated guessing failures',
    min: 2,
    max: 100,
    unit: 'failures',
    hint: 'Failed authentications for one account from one source.',
  },
  {
    key: 'repeatedGuessingWindowSeconds',
    label: 'Repeated guessing window',
    min: 5,
    max: 600,
    unit: 'seconds',
    hint: 'Rolling window in which the failures are counted.',
  },
  {
    key: 'sprayingFailureThreshold',
    label: 'Spraying failures',
    min: 2,
    max: 200,
    unit: 'failures',
    hint: 'Failed authentications from one source across accounts.',
  },
  {
    key: 'sprayingDistinctAccountThreshold',
    label: 'Spraying distinct accounts',
    min: 2,
    max: 200,
    unit: 'accounts',
    hint: 'Distinct usernames inside the spraying window.',
  },
  {
    key: 'sprayingWindowSeconds',
    label: 'Spraying window',
    min: 5,
    max: 900,
    unit: 'seconds',
    hint: 'Rolling window for the spraying rule.',
  },
  {
    key: 'rapidRequestThreshold',
    label: 'Rapid request threshold',
    min: 5,
    max: 500,
    unit: 'requests',
    hint: 'Incoming login requests counted before the rule matches.',
  },
  {
    key: 'rapidRequestWindowSeconds',
    label: 'Rapid request window',
    min: 1,
    max: 120,
    unit: 'seconds',
    hint: 'Rolling window for the request-rate rule.',
  },
  {
    key: 'tokenBucketRequestsPerSecond',
    label: 'Token bucket rate',
    min: 1,
    max: 100,
    unit: 'requests/second',
    hint: 'Sustained admission rate per source while protection is ON.',
  },
  {
    key: 'tokenBucketBurstCapacity',
    label: 'Token bucket burst',
    min: 1,
    max: 200,
    unit: 'requests',
    hint: 'Burst allowance; must be at least the sustained rate.',
  },
  {
    key: 'blockDurationSeconds',
    label: 'Block duration',
    min: 5,
    max: 600,
    unit: 'seconds',
    hint: 'How long a source block stays active.',
  },
];

type Draft = Record<string, string>;

function draftFrom(settings: PolicySettings): Draft {
  const draft: Draft = {};
  for (const field of FIELDS) {
    draft[field.key] = String(settings[field.key]);
  }
  draft.policyVersion = settings.policyVersion;
  return draft;
}

function validate(draft: Draft): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of FIELDS) {
    const raw = draft[field.key] ?? '';
    const value = Number(raw);
    if (raw.trim() === '' || !Number.isFinite(value) || !Number.isInteger(value)) {
      errors[field.key] = 'Enter a whole number.';
    } else if (value < field.min || value > field.max) {
      errors[field.key] = `Allowed range is ${field.min}–${field.max}.`;
    }
  }
  const distinct = Number(draft.sprayingDistinctAccountThreshold);
  const failures = Number(draft.sprayingFailureThreshold);
  if (Number.isFinite(distinct) && Number.isFinite(failures) && distinct > failures) {
    errors.sprayingDistinctAccountThreshold = 'Distinct accounts must not exceed the failure threshold.';
  }
  const burst = Number(draft.tokenBucketBurstCapacity);
  const rate = Number(draft.tokenBucketRequestsPerSecond);
  if (Number.isFinite(burst) && Number.isFinite(rate) && burst < rate) {
    errors.tokenBucketBurstCapacity = 'Burst capacity must be at least the sustained rate.';
  }
  if ((draft.policyVersion ?? '').length > 80) {
    errors.policyVersion = 'Policy version is limited to 80 characters.';
  }
  return errors;
}

export function SettingsView() {
  const { api, snapshot, refreshSnapshot } = useDefender();
  const announce = useAnnounce();

  const [settings, setSettings] = useState<PolicySettings | null>(null);
  const [policy, setPolicy] = useState<PolicySettings | null>(null);
  const [draft, setDraft] = useState<Draft>({});
  const [forbidden, setForbidden] = useState(false);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sourceIp, setSourceIp] = useState('');
  const [note, setNote] = useState('');

  const errors = useMemo(() => validate(draft), [draft]);

  const load = useCallback(async () => {
    setError(null);
    try {
      const result = await api.settings();
      setSettings(result.data);
      setDraft(draftFrom(result.data));
      setForbidden(false);
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.isLoopbackRequired) {
        setForbidden(true);
        setSettings(null);
      } else {
        setError(
          cause instanceof ApiRequestError
            ? cause
            : new ApiRequestError('The settings could not be read.', { status: 0, code: 'unknown_error' }),
        );
      }
    }
    try {
      const policyResult = await api.policy();
      setPolicy(policyResult.data);
    } catch {
      setPolicy(null);
    }
  }, [api]);

  useEffect(() => {
    void load();
  }, [load]);

  const runAction = async (action: () => Promise<string>) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const message = await action();
      setNotice(message);
      announce(message);
    } catch (cause) {
      const requestError =
        cause instanceof ApiRequestError
          ? cause
          : new ApiRequestError('The request could not be completed.', { status: 0, code: 'unknown_error' });
      setError(requestError);
      if (requestError.isLoopbackRequired) {
        setForbidden(true);
      }
    } finally {
      setBusy(false);
    }
  };

  const saveSettings = () =>
    runAction(async () => {
      if (Object.keys(errors).length > 0) {
        throw new ApiRequestError('The form contains invalid values.', { status: 400, code: 'invalid_request' });
      }
      const update: SettingsUpdate = { policyVersion: draft.policyVersion };
      for (const field of FIELDS) {
        update[field.key] = Number(draft[field.key]);
      }
      const result = await api.updateSettings(update);
      setSettings(result.data);
      setDraft(draftFrom(result.data));
      refreshSnapshot();
      return `Settings saved as policy version ${result.data.policyVersion}.`;
    });

  const toggleProtection = (mode: 'ON' | 'OFF') =>
    runAction(async () => {
      const result = await api.setProtection(mode);
      refreshSnapshot();
      return `Protection is now ${result.data.mode}. Active blocks: ${formatInteger(result.data.activeBlocks)}.`;
    });

  const unblock = () =>
    runAction(async () => {
      const result = await api.unblock(sourceIp.trim(), note.trim());
      if (!result.data.released) {
        throw new ApiRequestError(`No active block was found for ${sourceIp.trim()}.`, {
          status: 404,
          code: 'not_found',
        });
      }
      refreshSnapshot();
      setSourceIp('');
      setNote('');
      return `Block ${result.data.blockId ?? ''} for ${result.data.sourceIp ?? sourceIp.trim()} was released.`;
    });

  const reset = (scope: 'counters' | 'demo') =>
    runAction(async () => {
      const result = await api.reset(scope);
      refreshSnapshot();
      return `Reset scope "${result.data.scope}" applied at ${formatTimestamp(result.data.resetAt)}.`;
    });

  const protectionMode = snapshot?.protection.mode ?? settings?.protectionMode ?? policy?.protectionMode ?? null;

  return (
    <div className="stack">
      {forbidden ? (
        <Banner tone="warn" title="Analyst controls require a loopback caller">
          The defender refused the analyst request with <span className="mono">analyst_loopback_required</span>. Analyst
          endpoints are only reachable from <span className="mono">127.0.0.0/8</span> or{' '}
          <span className="mono">::1</span>; forwarded IP headers are ignored. Nothing was changed. The read-only
          policy below is served by the public <span className="mono">GET /api/v1/policy</span> endpoint.
        </Banner>
      ) : null}

      {error ? (
        <Banner tone="alert" title="Request failed">
          {error.message} ({error.code})
          {error.retryAfterSeconds ? ` · retry after ${error.retryAfterSeconds}s` : ''}
        </Banner>
      ) : null}

      {notice ? (
        <Banner tone="info" title="Confirmed by the defender">
          {notice}
        </Banner>
      ) : null}

      <div className="grid grid--split">
        <Panel
          title="Detection and protection settings"
          meta={settings ? `policy ${settings.policyVersion}` : 'read-only'}
          actions={
            <button type="button" className="btn btn--sm btn--ghost" onClick={() => void load()}>
              Reload
            </button>
          }
        >
          {!settings ? (
            <EmptyState
              title="Settings are not editable from this address"
              note="The editable settings live behind the loopback-only analyst API. The current policy is shown below."
            />
          ) : (
            <div className="stack">
              <div className="field">
                <label className="field__label" htmlFor="policyVersion">
                  Policy version
                </label>
                <input
                  id="policyVersion"
                  className="input"
                  value={draft.policyVersion ?? ''}
                  onChange={(event) => setDraft({ ...draft, policyVersion: event.target.value })}
                  aria-invalid={errors.policyVersion ? 'true' : undefined}
                />
                {errors.policyVersion ? <span className="field__error">{errors.policyVersion}</span> : null}
                <span className="field__hint">Creating a new version never rewrites the evidence already stored.</span>
              </div>

              <div className="form-grid">
                {FIELDS.map((field) => (
                  <div className="field" key={field.key}>
                    <label className="field__label" htmlFor={field.key}>
                      {field.label}
                    </label>
                    <input
                      id={field.key}
                      className="input"
                      inputMode="numeric"
                      value={draft[field.key] ?? ''}
                      onChange={(event) => setDraft({ ...draft, [field.key]: event.target.value })}
                      aria-invalid={errors[field.key] ? 'true' : undefined}
                      aria-describedby={`${field.key}-hint`}
                    />
                    {errors[field.key] ? <span className="field__error">{errors[field.key]}</span> : null}
                    <span className="field__hint" id={`${field.key}-hint`}>
                      {field.hint} · {field.min}–{field.max} {field.unit}
                    </span>
                  </div>
                ))}
              </div>

              <div className="row">
                <button type="button" className="btn btn--primary" disabled={busy} onClick={() => void saveSettings()}>
                  Apply settings
                </button>
                <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => void load()}>
                  Discard changes
                </button>
              </div>
            </div>
          )}
        </Panel>

        <div className="stack">
          <Panel title="Protection mode" meta={protectionMode ? `mode ${protectionMode}` : 'unknown'}>
            <div className="stack stack--tight">
              <p className="muted">
                Switching protection ON starts enforcing rate limits and source blocks. Switching it OFF keeps
                detecting and recording evidence while enforcing nothing.
              </p>
              <div className="row">
                <button
                  type="button"
                  className="btn btn--primary"
                  disabled={busy || protectionMode === 'ON'}
                  onClick={() => void toggleProtection('ON')}
                >
                  Switch on
                </button>
                <button
                  type="button"
                  className="btn btn--danger"
                  disabled={busy || protectionMode === 'OFF'}
                  onClick={() => void toggleProtection('OFF')}
                >
                  Switch off
                </button>
                {protectionMode ? (
                  <StatusPill tone={protectionMode === 'ON' ? 'signal' : 'warn'}>
                    {protectionMode === 'ON' ? 'Enforcing' : 'Observation only'}
                  </StatusPill>
                ) : null}
              </div>
            </div>
          </Panel>

          <Panel title="Manual unblock" meta="analyst control">
            <div className="stack stack--tight">
              <div className="field">
                <label className="field__label" htmlFor="unblockIp">
                  Source address
                </label>
                <input
                  id="unblockIp"
                  className="input"
                  value={sourceIp}
                  placeholder="192.168.1.50"
                  onChange={(event) => setSourceIp(event.target.value)}
                />
              </div>
              <div className="field">
                <label className="field__label" htmlFor="unblockNote">
                  Analyst note
                </label>
                <input
                  id="unblockNote"
                  className="input"
                  value={note}
                  placeholder="Verified with the classroom host"
                  onChange={(event) => setNote(event.target.value)}
                />
              </div>
              <button
                type="button"
                className="btn"
                disabled={busy || sourceIp.trim() === ''}
                onClick={() => void unblock()}
              >
                Release block
              </button>
            </div>
          </Panel>

          <Panel title="Reset" meta="analyst control">
            <div className="stack stack--tight">
              <p className="muted">
                Resetting the counters clears the rolling detection state. Resetting the demo scope additionally removes
                stored events, evidence, alerts and observations.
              </p>
              <div className="row">
                <button type="button" className="btn" disabled={busy} onClick={() => void reset('counters')}>
                  Reset counters
                </button>
                <button type="button" className="btn btn--danger" disabled={busy} onClick={() => void reset('demo')}>
                  Reset demo history
                </button>
              </div>
            </div>
          </Panel>

          {policy ? (
            <Panel title="Current policy (read-only)" meta={policy.policyVersion}>
              <dl className="kv">
                <dt className="kv__key">Protection mode</dt>
                <dd className="kv__value">{policy.protectionMode}</dd>
                <dt className="kv__key">Repeated guessing</dt>
                <dd className="kv__value">
                  ≥ {policy.repeatedGuessingFailureThreshold} failures / {policy.repeatedGuessingWindowSeconds}s
                </dd>
                <dt className="kv__key">Spraying</dt>
                <dd className="kv__value">
                  ≥ {policy.sprayingFailureThreshold} failures across ≥ {policy.sprayingDistinctAccountThreshold}{' '}
                  accounts / {policy.sprayingWindowSeconds}s
                </dd>
                <dt className="kv__key">Rapid requests</dt>
                <dd className="kv__value">
                  ≥ {policy.rapidRequestThreshold} requests / {policy.rapidRequestWindowSeconds}s
                </dd>
                <dt className="kv__key">Token bucket</dt>
                <dd className="kv__value">
                  {policy.tokenBucketRequestsPerSecond} req/s · burst {policy.tokenBucketBurstCapacity}
                </dd>
                <dt className="kv__key">Block duration</dt>
                <dd className="kv__value">{policy.blockDurationSeconds}s</dd>
                <dt className="kv__key">Created</dt>
                <dd className="kv__value">{formatTimestamp(policy.createdAt)}</dd>
              </dl>
            </Panel>
          ) : null}

          <RuntimeDiagnosticsPanel />
        </div>
      </div>
    </div>
  );
}
