/** Formatting helpers. Every value is derived from a backend number; nothing is estimated. */

export function formatInteger(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return new Intl.NumberFormat('en-US').format(value);
}

export function formatDecimal(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return value.toFixed(digits);
}

export function formatPercent(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return `${value.toFixed(digits)}%`;
}

export function formatClock(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return date.toLocaleTimeString('en-GB', { hour12: false });
}

export function formatTimestamp(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  const clock = date.toLocaleTimeString('en-GB', { hour12: false });
  return `${clock}.${String(date.getMilliseconds()).padStart(3, '0')}`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return `${date.toLocaleDateString('en-GB')} ${date.toLocaleTimeString('en-GB', { hour12: false })}`;
}

export function formatDuration(milliseconds: number | null | undefined): string {
  if (milliseconds === null || milliseconds === undefined || Number.isNaN(milliseconds)) {
    return '—';
  }
  if (milliseconds < 1000) {
    return `${Math.max(0, Math.round(milliseconds))} ms`;
  }
  if (milliseconds < 60_000) {
    return `${(milliseconds / 1000).toFixed(1)} s`;
  }
  const minutes = Math.floor(milliseconds / 60_000);
  const seconds = Math.round((milliseconds % 60_000) / 1000);
  return `${minutes} m ${String(seconds).padStart(2, '0')} s`;
}

export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  const delta = now - date.getTime();
  if (delta < 1000) {
    return 'just now';
  }
  if (delta < 60_000) {
    return `${Math.floor(delta / 1000)}s ago`;
  }
  if (delta < 3_600_000) {
    return `${Math.floor(delta / 60_000)}m ago`;
  }
  return `${Math.floor(delta / 3_600_000)}h ago`;
}

/** Human label for a detection category as published by the defender. */
export function categoryLabel(category: string): string {
  switch (category) {
    case 'SUSPECTED_REPEATED_GUESSING':
      return 'Suspected repeated guessing';
    case 'SUSPECTED_PASSWORD_SPRAYING':
      return 'Suspected password spraying';
    case 'SUSPECTED_RAPID_REQUESTS':
      return 'Suspected rapid requests';
    case 'NONE':
      return 'No pattern';
    default:
      return category;
  }
}

export function ruleLabel(ruleId: string): string {
  switch (ruleId) {
    case 'repeated-guessing':
      return 'Repeated guessing';
    case 'password-spraying':
      return 'Password spraying';
    case 'rapid-requests':
      return 'Rapid requests';
    default:
      return ruleId;
  }
}

/** Reads a string field from an arbitrary JSON payload without inventing a fallback. */
export function payloadString(payload: Record<string, unknown>, key: string): string | null {
  const value = payload[key];
  if (typeof value === 'string' && value.length > 0) {
    return value;
  }
  return null;
}

export function payloadNumber(payload: Record<string, unknown>, key: string): number | null {
  const value = payload[key];
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  return null;
}
