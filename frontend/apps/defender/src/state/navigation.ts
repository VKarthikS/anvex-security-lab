/** Cross-view navigation intent: the Alerts view hands a selected alert to the Evidence view. */
let pendingAlertId: string | null = null;

export function requestAlert(alertId: string): void {
  pendingAlertId = alertId;
}

export function takePendingAlert(): string | null {
  const value = pendingAlertId;
  pendingAlertId = null;
  return value;
}
