import { useEffect, useRef, useState } from 'react';
import type { EventEnvelope } from '../api/types';

/**
 * Stream health as shown in the calibration strip.
 *  - `connecting`: the subscription is opening, no snapshot has been confirmed yet.
 *  - `live`: the server sent `hello` and messages keep arriving.
 *  - `stale`: the stream dropped; the last known snapshot stays visible but is not live.
 */
export type StreamStatus = 'connecting' | 'live' | 'stale';

export interface EventStreamOptions {
  url: string;
  eventNames: readonly string[];
  maxEnvelopes?: number;
  /** Called on every (re)connect and whenever the server asks for a re-snapshot. */
  onResync?: () => void;
}

export interface EventStreamState {
  status: StreamStatus;
  envelopes: EventEnvelope[];
  lastMessageAt: number | null;
  /** Increments whenever the consumer must re-fetch its snapshot. */
  resyncToken: number;
}

const DEFAULT_MAX_ENVELOPES = 240;

function parseEnvelope(raw: string, fallbackType: string): EventEnvelope | null {
  try {
    const parsed = JSON.parse(raw) as Partial<EventEnvelope> & { payload?: unknown };
    if (typeof parsed !== 'object' || parsed === null) {
      return null;
    }
    return {
      eventId: typeof parsed.eventId === 'string' ? parsed.eventId : `${fallbackType}-${Date.now()}`,
      type: typeof parsed.type === 'string' ? parsed.type : fallbackType,
      occurredAt: typeof parsed.occurredAt === 'string' ? parsed.occurredAt : new Date().toISOString(),
      sequence: typeof parsed.sequence === 'number' ? parsed.sequence : 0,
      payload:
        typeof parsed.payload === 'object' && parsed.payload !== null
          ? (parsed.payload as Record<string, unknown>)
          : {},
    };
  } catch {
    return null;
  }
}

/**
 * Subscribes to an ANVEX SSE endpoint.
 *
 * The server sends `hello` with the current sequence whenever a subscription opens, so every
 * (re)connect is treated as a snapshot recovery point: the consumer re-fetches its snapshot
 * instead of assuming that events survived the disconnect. `lag` and `resync` do the same.
 */
export function useEventStream(options: EventStreamOptions): EventStreamState {
  const { url, eventNames, maxEnvelopes = DEFAULT_MAX_ENVELOPES } = options;
  const [status, setStatus] = useState<StreamStatus>('connecting');
  const [envelopes, setEnvelopes] = useState<EventEnvelope[]>([]);
  const [lastMessageAt, setLastMessageAt] = useState<number | null>(null);
  const [resyncToken, setResyncToken] = useState(0);
  const resyncRef = useRef(options.onResync);
  resyncRef.current = options.onResync;
  const lastMessageRef = useRef<number | null>(null);

  useEffect(() => {
    let disposed = false;
    const source = new EventSource(url);

    const markMessage = () => {
      if (disposed) {
        return;
      }
      lastMessageRef.current = Date.now();
      setLastMessageAt(lastMessageRef.current);
    };

    const requestResync = () => {
      if (disposed) {
        return;
      }
      setResyncToken((token) => token + 1);
      resyncRef.current?.();
    };

    const handleEnvelope = (type: string) => (event: MessageEvent<string>) => {
      markMessage();
      const envelope = parseEnvelope(event.data, type);
      if (!envelope) {
        return;
      }
      setEnvelopes((current) => {
        const next = [envelope, ...current];
        return next.length > maxEnvelopes ? next.slice(0, maxEnvelopes) : next;
      });
    };

    const handleHello = () => {
      markMessage();
      setStatus('live');
      requestResync();
    };

    const handleResync = () => {
      markMessage();
      requestResync();
    };

    source.addEventListener('hello', handleHello);
    source.addEventListener('lag', handleResync);
    source.addEventListener('resync', handleResync);
    source.onopen = () => {
      markMessage();
    };
    source.onerror = () => {
      if (!disposed) {
        setStatus('stale');
      }
    };
    eventNames.forEach((name) => source.addEventListener(name, handleEnvelope(name)));

    const watchdog = window.setInterval(() => {
      if (disposed) {
        return;
      }
      // An idle defender publishes nothing, so silence is not a dropped stream. The connection
      // itself is the signal: `EventSource` reports CONNECTING or CLOSED once it lost the server.
      if (source.readyState !== EventSource.OPEN) {
        setStatus('stale');
      }
    }, 2_000);

    return () => {
      disposed = true;
      window.clearInterval(watchdog);
      source.close();
    };
  }, [eventNames, maxEnvelopes, url]);

  return { status, envelopes, lastMessageAt, resyncToken };
}
