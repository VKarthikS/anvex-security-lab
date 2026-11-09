import { useEffect, useState } from 'react';
import { Banner, EmptyState, Panel, StatusPill } from '@anvex/shared';

import { useSimulator } from '../state/SimulatorContext';

export function TargetView() {
  const { api, target, handshake, busy, refreshTarget, testTarget } = useSimulator();
  const [baseUrl, setBaseUrl] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (target) {
      setBaseUrl(target.baseUrl);
    }
  }, [target]);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setNotice(null);
    try {
      const result = await api.updateTarget(baseUrl.trim());
      setBaseUrl(result.data.baseUrl);
      setNotice(`Target saved as ${result.data.baseUrl}.`);
      await refreshTarget();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The target could not be saved.');
    }
  };

  return (
    <div className="stack">
      {error ? (
        <Banner tone="alert" title="Target rejected">
          {error}
        </Banner>
      ) : null}
      {notice ? (
        <Banner tone="info" title="Target updated">
          {notice}
        </Banner>
      ) : null}

      <div className="grid grid--two">
        <Panel title="Defender target" meta={target?.baseUrl ?? 'not configured'}>
          <form className="stack" onSubmit={(event) => void save(event)}>
            <div className="field">
              <label className="field__label" htmlFor="target-url">
                Defender base URL
              </label>
              <input
                id="target-url"
                className="input"
                value={baseUrl}
                onChange={(event) => setBaseUrl(event.target.value)}
                placeholder="http://192.168.1.50:8080"
              />
              <span className="field__hint">
                The simulator service contacts this URL itself, not the browser. It must be reachable from the machine
                that runs the simulator.
              </span>
            </div>
            <div className="row">
              <button type="submit" className="btn btn--primary" disabled={busy || baseUrl.trim() === ''}>
                Save target
              </button>
              <button type="button" className="btn" disabled={busy} onClick={() => void testTarget()}>
                Test handshake
              </button>
            </div>
          </form>
        </Panel>

        <Panel title="Handshake result" meta={handshake ? 'latest test' : 'no test yet'}>
          {!handshake ? (
            <EmptyState
              title="No handshake has been performed"
              note="The handshake asks the defender for its identity and checks that it speaks the ANVEX protocol."
            />
          ) : (
            <div className="stack stack--tight">
              <div className="row row--wrap">
                <StatusPill tone={handshake.reachable ? 'signal' : 'alert'}>
                  {handshake.reachable ? 'Reachable' : 'Unreachable'}
                </StatusPill>
                <StatusPill tone={handshake.compatible ? 'signal' : 'warn'}>
                  {handshake.compatible ? 'Protocol compatible' : 'Protocol not compatible'}
                </StatusPill>
              </div>
              <dl className="kv">
                <dt className="kv__key">Base URL</dt>
                <dd className="kv__value">{handshake.baseUrl}</dd>
                <dt className="kv__key">Product</dt>
                <dd className="kv__value">{handshake.product ?? 'not published'}</dd>
                <dt className="kv__key">Version</dt>
                <dd className="kv__value">{handshake.version ?? 'not published'}</dd>
                <dt className="kv__key">API version</dt>
                <dd className="kv__value">{handshake.apiVersion ?? 'not published'}</dd>
                <dt className="kv__key">Protocol</dt>
                <dd className="kv__value">{handshake.protocol ?? 'not published'}</dd>
              </dl>
              <p className="muted">{handshake.message}</p>
            </div>
          )}
        </Panel>
      </div>

      <div className="grid grid--two">
        <Panel title="Local and LAN hints">
          <ul className="stack stack--tight muted">
            <li>
              On a single machine, the defender runs on <span className="mono">http://127.0.0.1:8080</span> and the
              simulator on <span className="mono">http://127.0.0.1:8081</span>.
            </li>
            <li>
              On two laptops, point the simulator at the defender's LAN address, for example{' '}
              <span className="mono">http://192.168.1.50:8080</span>. The browser in this console cannot reach the
              other laptop; the simulator service does the requesting.
            </li>
            <li>
              The defender binds to <span className="mono">0.0.0.0:8080</span> by default; the simulator binds to{' '}
              <span className="mono">127.0.0.1:8081</span> so it stays on the local machine.
            </li>
            <li>
              The handshake only reads <span className="mono">GET /api/v1/identity</span>. A run additionally reads the
              published demo accounts and posts login attempts to the defender.
            </li>
          </ul>
        </Panel>

        <Panel title="Troubleshooting">
          <ul className="stack stack--tight muted">
            <li>
              <strong className="mono">Unreachable</strong>: the URL is wrong, the defender is stopped, or a firewall
              blocks port 8080. The simulator reports the exception class it saw.
            </li>
            <li>
              <strong className="mono">Reachable but not compatible</strong>: the host answered with something that is
              not an ANVEX defender, or the protocol version differs.
            </li>
            <li>
              A run that cannot reach a compatible defender is returned in the{' '}
              <span className="mono">FAILED</span> state with the service message, and no traffic is generated.
            </li>
            <li>
              <strong className="mono">429 rate_limited / source_blocked</strong> are defender rejections, not transport
              failures. They are counted separately from transport errors in the run counters.
            </li>
          </ul>
        </Panel>
      </div>
    </div>
  );
}
