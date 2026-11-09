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
} from '@anvex/shared';

import { useDefender } from '../state/DefenderContext';
import { requestAlert } from '../state/navigation';

export function AlertsView() {
  const { api } = useDefender();
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);

  const load = useCallback(async () => {
    try {
      const result = await api.alerts(200);
      setAlerts(result.data);
      setError(null);
    } catch (cause) {
      setError(
        cause instanceof ApiRequestError
          ? cause
          : new ApiRequestError('The alert history could not be read.', { status: 0, code: 'unknown_error' }),
      );
    }
  }, [api]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDossier = (alertId: string) => {
    requestAlert(alertId);
    window.location.hash = 'evidence';
  };

  return (
    <div className="stack">
      {error ? (
        <Banner tone="alert" title="Alert history unavailable">
          {error.message} ({error.code})
        </Banner>
      ) : null}

      <Panel
        title="Alert history"
        meta={`${formatInteger(alerts.length)} alert(s)`}
        actions={
          <button type="button" className="btn btn--sm btn--ghost" onClick={() => void load()}>
            Refresh
          </button>
        }
        flush
      >
        {alerts.length === 0 ? (
          <EmptyState
            title="No alerts recorded"
            note="Alerts are created by the defender when a rule threshold is reached and are updated while it keeps matching."
          />
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Severity</th>
                <th>Rule</th>
                <th>Category</th>
                <th>Source</th>
                <th className="table__num">Occurrences</th>
                <th>First seen</th>
                <th>Last seen</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {alerts.map((alert) => (
                <tr key={alert.alertId}>
                  <td>
                    <StatusPill tone={alert.severity === 'HIGH' ? 'alert' : alert.severity === 'MEDIUM' ? 'warn' : 'mute'}>
                      {alert.severity}
                    </StatusPill>
                  </td>
                  <td>{ruleLabel(alert.ruleId)}</td>
                  <td>{categoryLabel(alert.category)}</td>
                  <td className="mono">{alert.sourceIp}</td>
                  <td className="table__num">{formatInteger(alert.occurrenceCount)}</td>
                  <td className="mono">{formatTimestamp(alert.firstSeen)}</td>
                  <td className="mono">{formatTimestamp(alert.lastSeen)}</td>
                  <td className="mono">{alert.status}</td>
                  <td>
                    <button type="button" className="btn btn--sm btn--ghost" onClick={() => openDossier(alert.alertId)}>
                      Dossier
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
