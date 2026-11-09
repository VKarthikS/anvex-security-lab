import { useMemo } from 'react';
import {
  categoryLabel,
  formatClock,
  formatInteger,
  payloadNumber,
  payloadString,
  ruleLabel,
  type LiveEvent,
} from '@anvex/shared';

export type EventTone = 'signal' | 'warn' | 'alert' | 'violet' | 'mute';

export interface EventDescription {
  tone: EventTone;
  title: string;
  detail: string;
}

const TONE_LABEL: Record<EventTone, string> = {
  signal: 'signal',
  warn: 'warn',
  alert: 'alert',
  violet: 'violet',
  mute: 'mute',
};

/**
 * Describes one published event using only the fields the defender put in the payload.
 * Nothing is inferred: a field the service did not send is simply not shown.
 */
export function describeEvent(event: LiveEvent): EventDescription {
  const payload = event.payload;
  const username = payloadString(payload, 'username');
  const outcome = payloadString(payload, 'outcome');

  switch (event.type) {
    case 'LOGIN_REQUEST':
      if (outcome) {
        return {
          tone: 'alert',
          title: outcome === 'rate_limited' ? 'Rate limited' : outcome === 'source_blocked' ? 'Source blocked' : outcome,
          detail: `Rejected before authentication${
            payloadNumber(payload, 'retryAfterSeconds') !== null
              ? ` · retry after ${payloadNumber(payload, 'retryAfterSeconds')}s`
              : ''
          }`,
        };
      }
      return {
        tone: 'mute',
        title: 'Login request',
        detail: username ? `username "${username}"` : 'incoming authentication request',
      };
    case 'AUTH_SUCCESS':
      return { tone: 'signal', title: 'Authenticated', detail: username ? `username "${username}"` : 'successful login' };
    case 'AUTH_FAILURE':
      return {
        tone: 'warn',
        title: 'Authentication failed',
        detail: username ? `username "${username}"` : 'failed login',
      };
    case 'DETECTION_DECISION': {
      const category = payloadString(payload, 'category');
      const decisionId = payloadString(payload, 'decisionId');
      const action = payloadString(payload, 'action');
      return {
        tone: 'violet',
        title: category ? categoryLabel(category) : 'Detection decision',
        detail: `${action ? `${action} · ` : ''}${decisionId ? `decision ${decisionId}` : 'no decision id in payload'}`,
      };
    }
    case 'ALERT_UPSERT': {
      const severity = payloadString(payload, 'severity');
      const rule = payloadString(payload, 'ruleId');
      const occurrences = payloadNumber(payload, 'occurrenceCount');
      return {
        tone: severity === 'HIGH' ? 'alert' : 'warn',
        title: `Alert ${severity ?? ''}`.trim(),
        detail: `${rule ? ruleLabel(rule) : 'rule'}${occurrences !== null ? ` · ${formatInteger(occurrences)} occurrence(s)` : ''}`,
      };
    }
    case 'BLOCK_CREATED': {
      const rule = payloadString(payload, 'ruleId');
      const reason = payloadString(payload, 'reason');
      const expiry = payloadString(payload, 'expiry');
      return {
        tone: 'alert',
        title: 'Source blocked',
        detail: `${rule ? ruleLabel(rule) : 'rule'}${reason ? ` · ${reason}` : ''}${
          expiry ? ` · until ${formatClock(expiry)}` : ''
        }`,
      };
    }
    case 'BLOCK_RELEASED': {
      const rule = payloadString(payload, 'ruleId');
      const releasedBy = payloadString(payload, 'releasedBy');
      return {
        tone: 'signal',
        title: 'Block released',
        detail: `${rule ? ruleLabel(rule) : 'block'}${releasedBy ? ` · by ${releasedBy}` : ''}`,
      };
    }
    case 'PROTECTION_CHANGED': {
      const mode = payloadString(payload, 'mode');
      const actor = payloadString(payload, 'actor');
      return {
        tone: mode === 'ON' ? 'signal' : 'warn',
        title: `Protection ${mode ?? 'changed'}`,
        detail: `${actor ? `by ${actor}` : 'actor not published'}`,
      };
    }
    case 'SETTINGS_CHANGED':
      return { tone: 'violet', title: 'Settings changed', detail: 'Detection or protection settings were updated.' };
    case 'ANALYST_ACTION':
      return { tone: 'violet', title: 'Analyst action', detail: 'An analyst control was applied.' };
    case 'AGGREGATED_TELEMETRY': {
      const type = payloadString(payload, 'aggregatedType');
      const count = payloadNumber(payload, 'count');
      const coverage = payloadString(payload, 'coverage');
      return {
        tone: 'mute',
        title: 'Aggregated telemetry',
        detail: `${type ?? 'events'}${count !== null ? ` · ${formatInteger(count)} event(s)` : ''}${
          coverage ? ` · ${coverage}` : ''
        }`,
      };
    }
    default:
      return { tone: 'mute', title: event.type, detail: 'Published by the defender event pipeline.' };
  }
}

export interface EventRibbonProps {
  events: LiveEvent[];
  stale: boolean;
  /** Total incoming requests in the last snapshot: used to phrase the idle state honestly. */
  snapshotIncoming: number;
}

/**
 * The live activity ribbon. It shows exactly what the defender published while this page was
 * connected; when the buffer is empty the wording states whether traffic was seen at all.
 */
export function EventRibbon({ events, stale, snapshotIncoming }: EventRibbonProps) {
  const rows = useMemo(() => events.map((event) => ({ event, description: describeEvent(event) })), [events]);

  if (rows.length === 0) {
    return (
      <div className="empty">
        <p className="empty__title">
          {snapshotIncoming > 0 ? 'No live events in the buffer' : 'No traffic observed'}
        </p>
        <p className="empty__note">
          {snapshotIncoming > 0
            ? `${snapshotIncoming} request(s) are counted in the snapshot below. They reached the defender before this stream was opened, so they are not replayed into the live ribbon.`
            : 'The event stream is open and no login request has reached the defender. This is an idle state, not an attack.'}
        </p>
      </div>
    );
  }

  return (
    <ul className={`ribbon${stale ? ' stale' : ''}`} aria-label="Live activity">
      {rows.map(({ event, description }) => (
        <li key={`${event.sequence}-${event.eventId}`} className="ribbon__row">
          <span className="ribbon__time mono">{formatClock(event.occurredAt)}</span>
          <span className={`ribbon__tone ribbon__tone--${TONE_LABEL[description.tone]}`} aria-hidden="true" />
          <span className="ribbon__title">{description.title}</span>
          <span className="ribbon__detail mono">{description.detail}</span>
        </li>
      ))}
    </ul>
  );
}
