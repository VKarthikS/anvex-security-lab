import { useMemo } from 'react';
import type { EventEnvelope } from '../api/types';
import { useEventStream, type StreamStatus } from './useEventStream';

/** Event names published by the defender event pipeline. */
export const DEFENDER_EVENT_NAMES = [
  'LOGIN_REQUEST',
  'AUTH_SUCCESS',
  'AUTH_FAILURE',
  'DETECTION_DECISION',
  'ALERT_UPSERT',
  'BLOCK_CREATED',
  'BLOCK_RELEASED',
  'PROTECTION_CHANGED',
  'ANALYST_ACTION',
  'SETTINGS_CHANGED',
  'AGGREGATED_TELEMETRY',
] as const;

export interface LiveEvent extends EventEnvelope {}

export interface UseLiveEventsOptions {
  /** Invoked on connect, on reconnect and whenever the server asks for a re-snapshot. */
  onResync?: () => void;
  maxEvents?: number;
}

export interface UseLiveEventsResult {
  status: StreamStatus;
  isStale: boolean;
  events: LiveEvent[];
  lastEventAt: number | null;
  resyncToken: number;
}

/**
 * Live defender activity.
 *
 * The ribbon only ever contains envelopes that the defender actually published. When the
 * stream drops the status becomes `stale`, the caller greys out live-only figures and the
 * snapshot is re-fetched as soon as the subscription is restored.
 */
export function useLiveEvents(options: UseLiveEventsOptions = {}): UseLiveEventsResult {
  const { onResync, maxEvents = 60 } = options;
  const stream = useEventStream({
    url: '/api/v1/events/stream',
    eventNames: DEFENDER_EVENT_NAMES,
    maxEnvelopes: maxEvents,
    onResync,
  });

  const events = useMemo<LiveEvent[]>(() => stream.envelopes, [stream.envelopes]);

  return {
    status: stream.status,
    isStale: stream.status === 'stale',
    events,
    lastEventAt: stream.lastMessageAt,
    resyncToken: stream.resyncToken,
  };
}
