import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import {
  ApiRequestError,
  createDefenderApi,
  useLiveEvents,
  type DashboardSnapshot,
  type DefenderApi,
  type Identity,
  type LiveEvent,
  type StreamStatus,
} from '@anvex/shared';

const POLL_INTERVAL_MS = 2_000;

/** One rolling sample: the counters observed since the previous snapshot. */
export interface TrafficSample {
  at: number;
  incoming: number;
  admitted: number;
  failures: number;
  successes: number;
  rateLimited: number;
  sourceBlocked: number;
}

export const MAX_SAMPLES = 120;

export interface DefenderState {
  api: DefenderApi;
  snapshot: DashboardSnapshot | null;
  snapshotError: ApiRequestError | null;
  identity: Identity | null;
  streamStatus: StreamStatus;
  events: LiveEvent[];
  lastSnapshotAt: number | null;
  /** Bounded rolling window of per-sample traffic deltas. */
  samples: TrafficSample[];
  /** Re-reads the dashboard snapshot; used after a stream reconnect. */
  refreshSnapshot: () => void;
}

const DefenderContext = createContext<DefenderState | null>(null);

export function DefenderProvider({ children }: { children: ReactNode }) {
  const api = useMemo(() => createDefenderApi(), []);
  const [snapshot, setSnapshot] = useState<DashboardSnapshot | null>(null);
  const [snapshotError, setSnapshotError] = useState<ApiRequestError | null>(null);
  const [identity, setIdentity] = useState<Identity | null>(null);
  const [lastSnapshotAt, setLastSnapshotAt] = useState<number | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);
  const [samples, setSamples] = useState<TrafficSample[]>([]);
  const previousCounters = useRef<DashboardSnapshot['counters'] | null>(null);
  const reloadRef = useRef<() => void>(() => undefined);

  const refreshSnapshot = useCallback(() => setRefreshToken((token) => token + 1), []);

  const live = useLiveEvents({ onResync: refreshSnapshot });

  useEffect(() => {
    let cancelled = false;
    api
      .identity()
      .then((result) => {
        if (!cancelled) {
          setIdentity(result.data);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [api]);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const result = await api.dashboardSnapshot();
        if (cancelled) {
          return;
        }
        setSnapshot(result.data);
        setSnapshotError(null);
        setLastSnapshotAt(Date.now());
        setSamples((current) => {
          const previous = previousCounters.current;
          previousCounters.current = result.data.counters;
          if (!previous) {
            return current;
          }
          const delta = (key: keyof DashboardSnapshot['counters']) => {
            const now = result.data.counters[key] ?? 0;
            const before = previous[key] ?? 0;
            // A counter reset (analyst reset) would otherwise produce a negative delta.
            return now >= before ? now - before : 0;
          };
          const sample: TrafficSample = {
            at: Date.now(),
            incoming: delta('incomingRequests'),
            admitted: delta('requestsReachingAuthentication'),
            failures: delta('authenticationFailure'),
            successes: delta('authenticationSuccess'),
            rateLimited: delta('rateLimitedRequests'),
            sourceBlocked: delta('sourceBlockedRequests'),
          };
          const next = [...current, sample];
          return next.length > MAX_SAMPLES ? next.slice(next.length - MAX_SAMPLES) : next;
        });
      } catch (error) {
        if (cancelled) {
          return;
        }
        setSnapshotError(
          error instanceof ApiRequestError
            ? error
            : new ApiRequestError('The dashboard snapshot could not be read.', {
                status: 0,
                code: 'unknown_error',
              }),
        );
      }
    };
    reloadRef.current = () => {
      void load();
    };
    void load();
    const timer = window.setInterval(() => {
      void load();
    }, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [api]);

  useEffect(() => {
    if (refreshToken > 0) {
      reloadRef.current();
    }
  }, [refreshToken]);

  const value = useMemo<DefenderState>(
    () => ({
      api,
      snapshot,
      snapshotError,
      identity,
      streamStatus: live.status,
      events: live.events,
      lastSnapshotAt,
      samples,
      refreshSnapshot,
    }),
    [api, identity, lastSnapshotAt, live.events, live.status, refreshSnapshot, samples, snapshot, snapshotError],
  );

  return <DefenderContext.Provider value={value}>{children}</DefenderContext.Provider>;
}

export function useDefender(): DefenderState {
  const value = useContext(DefenderContext);
  if (!value) {
    throw new Error('useDefender must be used inside a DefenderProvider.');
  }
  return value;
}
