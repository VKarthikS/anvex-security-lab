import { useCallback, useEffect, useState } from 'react';
import {
  ApiRequestError,
  Banner,
  EmptyState,
  formatInteger,
  formatTimestamp,
  Panel,
  ruleLabel,
  StatusPill,
  categoryLabel,
  type Alert,
  type AlertEvidenceResponse,
  type EvidenceSnapshot,
} from '@anvex/shared';

import { EvidenceDossier } from '../components/EvidenceDossier';
import { useDefender } from '../state/DefenderContext';
import { takePendingAlert } from '../state/navigation';

function AlertList({
  alerts,
  selected,
  onSelect,
}: {
  alerts: Alert[];
  selected: string | null;
  onSelect: (alertId: string) => void;
}) {
  if (alerts.length === 0) {
    return (
      <EmptyState
        title="No alerts recorded"
        note="An alert is raised the first time a rule matches a source and is updated while it keeps matching."
      />
    );
  }

  return (
    <ul className="alertlist">
      {alerts.map((alert) => (
        <li key={alert.alertId}>
          <button
            type="button"
            className={`alertlist__item${selected === alert.alertId ? ' is-selected' : ''}`}
            onClick={() => onSelect(alert.alertId)}
            aria-pressed={selected === alert.alertId}
          >
            <span className="row row--between">
              <StatusPill tone={alert.severity === 'HIGH' ? 'alert' : alert.severity === 'MEDIUM' ? 'warn' : 'mute'}>
                {alert.severity}
              </StatusPill>
              <span className="mono dim">{formatTimestamp(alert.lastSeen)}</span>
            </span>
            <span className="alertlist__title">{ruleLabel(alert.ruleId)}</span>
            <span className="mono alertlist__meta">
              {alert.sourceIp} · {formatInteger(alert.occurrenceCount)} occurrence(s)
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

export function EvidenceView() {
  const { api } = useDefender();
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [dossier, setDossier] = useState<AlertEvidenceResponse | null>(null);
  const [standalone, setStandalone] = useState<EvidenceSnapshot | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadAlerts = useCallback(async () => {
    try {
      const result = await api.alerts(50);
      setAlerts(result.data);
      setError(null);
      setSelected((current) => {
        const pending = takePendingAlert();
        if (pending) {
          return pending;
        }
        if (current && result.data.some((alert) => alert.alertId === current)) {
          return current;
        }
        return result.data.length > 0 ? result.data[0].alertId : null;
      });
    } catch (cause) {
      setError(
        cause instanceof ApiRequestError
          ? cause
          : new ApiRequestError('The alert list could not be read.', { status: 0, code: 'unknown_error' }),
      );
    }
  }, [api]);

  useEffect(() => {
    void loadAlerts();
  }, [loadAlerts]);

  useEffect(() => {
    setStandalone(null);
    setNotice(null);
    if (!selected) {
      setDossier(null);
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const result = await api.alertEvidence(selected);
        if (!cancelled) {
          setDossier(result.data);
          setError(null);
        }
      } catch (cause) {
        if (!cancelled) {
          setDossier(null);
          setError(
            cause instanceof ApiRequestError
              ? cause
              : new ApiRequestError('The evidence dossier could not be read.', { status: 0, code: 'unknown_error' }),
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [api, selected]);

  const openStandalone = async (decisionId: string) => {
    try {
      const result = await api.evidence(decisionId);
      setStandalone(result.data);
      setNotice(null);
      setError(null);
    } catch (cause) {
      setStandalone(null);
      if (cause instanceof ApiRequestError) {
        setError(cause);
        setNotice(
          cause.isNotFound
            ? `No evidence snapshot is stored for ${decisionId}. The defender references this identifier in the alert, but the evidence store holds no record for it.`
            : null,
        );
      }
    }
  };

  return (
    <div className="stack">
      {error ? (
        <Banner tone="alert" title="Evidence request failed">
          {error.message} ({error.code})
          {notice ? <p className="banner__body">{notice}</p> : null}
        </Banner>
      ) : null}

      <div className="grid grid--split">
        <Panel
          title="Alerts"
          meta={`${formatInteger(alerts.length)} alert(s)`}
          actions={
            <button type="button" className="btn btn--sm btn--ghost" onClick={() => void loadAlerts()}>
              Refresh
            </button>
          }
          flush
        >
          <AlertList alerts={alerts} selected={selected} onSelect={setSelected} />
        </Panel>

        <div className="stack">
          <Panel title="Alert detail" meta={dossier?.alert.alertId ?? 'no alert selected'}>
            {!dossier ? (
              <EmptyState title="Select an alert" note="The alert detail, matched rules and evidence dossier appear here." />
            ) : (
              <div className="stack stack--tight">
                <div className="row row--between row--wrap">
                  <span className="dossier__title">{categoryLabel(dossier.alert.category)}</span>
                  <div className="row">
                    <StatusPill tone={dossier.alert.severity === 'HIGH' ? 'alert' : 'warn'}>{dossier.alert.severity}</StatusPill>
                    <StatusPill tone="mute">{dossier.alert.status}</StatusPill>
                  </div>
                </div>
                <dl className="kv">
                  <dt className="kv__key">Source</dt>
                  <dd className="kv__value">{dossier.alert.sourceIp}</dd>
                  <dt className="kv__key">Rule</dt>
                  <dd className="kv__value">{ruleLabel(dossier.alert.ruleId)}</dd>
                  <dt className="kv__key">First seen</dt>
                  <dd className="kv__value">{formatTimestamp(dossier.alert.firstSeen)}</dd>
                  <dt className="kv__key">Last seen</dt>
                  <dd className="kv__value">{formatTimestamp(dossier.alert.lastSeen)}</dd>
                  <dt className="kv__key">Occurrences</dt>
                  <dd className="kv__value">{formatInteger(dossier.alert.occurrenceCount)}</dd>
                </dl>
                {dossier.alert.decisionIds.length > 0 ? (
                  <div className="stack stack--tight">
                    <p className="label">Decision references</p>
                    <div className="row row--wrap">
                      {dossier.alert.decisionIds.map((decisionId) => (
                        <button
                          key={decisionId}
                          type="button"
                          className="btn btn--sm btn--ghost mono"
                          onClick={() => void openStandalone(decisionId)}
                        >
                          {decisionId}
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>
            )}
          </Panel>

          {standalone ? (
            <Panel title="Standalone evidence snapshot" meta={standalone.decisionId}>
              <EvidenceDossier evidence={standalone} standalone />
            </Panel>
          ) : null}

          {dossier && dossier.evidence.length > 0 ? (
            dossier.evidence.map((evidence) => (
              <Panel key={evidence.decisionId} title="Evidence dossier" meta={evidence.decisionId}>
                <EvidenceDossier evidence={evidence} />
              </Panel>
            ))
          ) : dossier ? (
            <Panel title="Evidence dossier">
              <EmptyState
                title="No evidence stored for this alert"
                note="The defender references decision identifiers on the alert, but the evidence store holds no matching snapshot."
              />
            </Panel>
          ) : null}
        </div>
      </div>
    </div>
  );
}
