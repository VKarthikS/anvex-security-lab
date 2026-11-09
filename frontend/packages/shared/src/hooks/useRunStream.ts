import { useMemo } from 'react';
import type { EventEnvelope } from '../api/types';
import { useEventStream, type StreamStatus } from './useEventStream';

/** Event names published by the simulator run hub. */
export const RUN_EVENT_NAMES = ['RUN_STARTED', 'RUN_PROGRESS', 'RUN_STATE', 'RUN_FAILED'] as const;

export interface RunStreamEvent extends EventEnvelope {}

export interface UseRunStreamOptions {
  onResync?: () => void;
  maxEvents?: number;
}

export interface UseRunStreamResult {
  status: StreamStatus;
  isStale: boolean;
  events: RunStreamEvent[];
  lastEventAt: number | null;
  resyncToken: number;
}

/**
 * Live simulator progress.
 *
 * Progress payloads are published by the Java run manager; the browser only renders them and
 * re-fetches `GET /api/v1/runs/current` whenever the subscription is restored.
 */
export function useRunStream(options: UseRunStreamOptions = {}): UseRunStreamResult {
  const { onResync, maxEvents = 120 } = options;
  const stream = useEventStream({
    url: '/api/v1/runs/stream',
    eventNames: RUN_EVENT_NAMES,
    maxEnvelopes: maxEvents,
    onResync,
  });

  const events = useMemo<RunStreamEvent[]>(() => stream.envelopes, [stream.envelopes]);

  return {
    status: stream.status,
    isStale: stream.status === 'stale',
    events,
    lastEventAt: stream.lastMessageAt,
    resyncToken: stream.resyncToken,
  };
}
