import { useCallback, useEffect, useState } from 'react';
import {
  ApiRequestError,
  Banner,
  EmptyState,
  formatTimestamp,
  Panel,
  StatusPill,
  type AwarenessNotice,
  type DemoAccount,
  type LoginResponse,
} from '@anvex/shared';

import { useDefender } from '../state/DefenderContext';
import { useAnnounce } from '../state/Announcer';

interface LoginOutcome {
  response: LoginResponse;
  requestId: string | null;
  sourceIp: string | null;
}

const STATUS_TONE: Record<string, 'signal' | 'warn' | 'alert' | 'mute'> = {
  authenticated: 'signal',
  invalid_credentials: 'warn',
  rate_limited: 'alert',
  source_blocked: 'alert',
  service_overloaded: 'alert',
};

function AwarenessList({ notices }: { notices: AwarenessNotice[] }) {
  if (notices.length === 0) {
    return <p className="muted">No awareness notices were attached to this response.</p>;
  }
  return (
    <ul className="stack stack--tight">
      {notices.map((notice) => (
        <li key={`${notice.title}-${notice.scope}`} className={`notice notice--${notice.level}`}>
          <div className="row row--between">
            <span className="notice__title">{notice.title}</span>
            <StatusPill tone={notice.level === 'warning' ? 'warn' : 'mute'}>{notice.scope}</StatusPill>
          </div>
          <p className="notice__message">{notice.message}</p>
          {notice.relatedEventIds && notice.relatedEventIds.length > 0 ? (
            <p className="mono dim">
              related events: {notice.relatedEventIds.join(', ')}
            </p>
          ) : null}
        </li>
      ))}
    </ul>
  );
}

export function LoginView() {
  const { api } = useDefender();
  const announce = useAnnounce();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [runId, setRunId] = useState('');
  const [outcome, setOutcome] = useState<LoginOutcome | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [accounts, setAccounts] = useState<DemoAccount[]>([]);
  const [busy, setBusy] = useState(false);

  const loadAccounts = useCallback(async () => {
    try {
      const result = await api.demoAccounts();
      setAccounts(result.data);
    } catch {
      setAccounts([]);
    }
  }, [api]);

  useEffect(() => {
    void loadAccounts();
  }, [loadAccounts]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setOutcome(null);
    try {
      const result = await api.login(username.trim(), password, runId.trim() || undefined);
      setOutcome(result);
      announce(`Login result: ${result.response.status}.`);
    } catch (cause) {
      const requestError =
        cause instanceof ApiRequestError
          ? cause
          : new ApiRequestError('The login request could not be completed.', { status: 0, code: 'unknown_error' });
      setError(requestError);
      announce(`Login request failed: ${requestError.code}.`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="stack">
      <div className="grid grid--split">
        <Panel title="Dummy application login" meta="POST /api/v1/auth/login">
          <form className="stack" onSubmit={(event) => void submit(event)}>
            <div className="field">
              <label className="field__label" htmlFor="login-username">
                Username
              </label>
              <input
                id="login-username"
                className="input"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
                required
              />
            </div>
            <div className="field">
              <label className="field__label" htmlFor="login-password">
                Password
              </label>
              <input
                id="login-password"
                className="input"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
            <div className="field">
              <label className="field__label" htmlFor="login-runid">
                Run id (optional metadata)
              </label>
              <input
                id="login-runid"
                className="input"
                value={runId}
                onChange={(event) => setRunId(event.target.value)}
                placeholder="untrusted experiment metadata"
              />
              <span className="field__hint">
                The run id is metadata only. It groups counters for a comparison and never influences detection.
              </span>
            </div>
            <div className="row">
              <button type="submit" className="btn btn--primary" disabled={busy}>
                {busy ? 'Sending…' : 'Sign in'}
              </button>
              <span className="mono dim">dummy application · classroom credentials</span>
            </div>
          </form>

          {error ? (
            <Banner tone="alert" title="Login request failed">
              {error.message} ({error.code})
              {error.requestId ? <p className="mono banner__body">request id {error.requestId}</p> : null}
            </Banner>
          ) : null}

          {outcome ? (
            <div className="stack stack--tight">
              <div className="row row--between row--wrap">
                <StatusPill tone={STATUS_TONE[outcome.response.status] ?? 'mute'}>{outcome.response.status}</StatusPill>
                {outcome.response.retryAfterSeconds ? (
                  <span className="mono dim">retry after {outcome.response.retryAfterSeconds}s</span>
                ) : null}
              </div>
              <dl className="kv">
                <dt className="kv__key">Request id</dt>
                <dd className="kv__value">{outcome.requestId ?? outcome.response.requestId ?? '—'}</dd>
                <dt className="kv__key">Source address</dt>
                <dd className="kv__value">{outcome.sourceIp ?? 'not published in the response headers'}</dd>
                <dt className="kv__key">Server time</dt>
                <dd className="kv__value">{formatTimestamp(outcome.response.serverTime)}</dd>
                <dt className="kv__key">User</dt>
                <dd className="kv__value">
                  {outcome.response.user
                    ? `${outcome.response.user.displayName} (${outcome.response.user.username}) · ${outcome.response.user.role}`
                    : 'no user returned'}
                </dd>
              </dl>
              <p className="muted">
                The defender returns only the outcome, the request id and account-scoped awareness. It never returns
                passwords, password hashes or detection internals.
              </p>
            </div>
          ) : null}
        </Panel>

        <div className="stack">
          <Panel title="Awareness notices" meta={outcome ? 'attached to the last response' : 'no response yet'}>
            <AwarenessList notices={outcome?.response.awareness ?? []} />
          </Panel>

          <Panel title="Published demo accounts" meta={`${accounts.length} account(s)`} flush>
            {accounts.length === 0 ? (
              <EmptyState title="No demo accounts published" note="GET /api/v1/demo/accounts returned nothing." />
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Username</th>
                    <th>Display name</th>
                    <th>Role</th>
                    <th>Demo password</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((account) => (
                    <tr key={account.username}>
                      <td className="mono">{account.username}</td>
                      <td>{account.displayName}</td>
                      <td className="mono">{account.role}</td>
                      <td className="mono">{account.demoPassword}</td>
                      <td>
                        <button
                          type="button"
                          className="btn btn--sm btn--ghost"
                          onClick={() => {
                            setUsername(account.username);
                            setPassword(account.demoPassword);
                          }}
                          aria-label={`Fill the form with the credentials of ${account.username}`}
                        >
                          Use
                        </button>
                      </td>
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
