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
  createSimulatorApi,
  useRunStream,
  type ComparisonReport,
  type RunCounters,
  type RunSnapshot,
  type RunState,
  type ScenarioDescriptor,
  type SimulatorApi,
  type StreamStatus,
  type TargetConfig,
  type TargetHandshake,
} from '@anvex/shared';

import { useAnnounce } from './Announcer';

const POLL_INTERVAL_MS = 2_000;

export const CAPS = {
  ratePerSecond: { min: 1, max: 20 },
  durationSeconds: { min: 1, max: 120 },
  requestLimit: { min: 1, max: 1000 },
  concurrency: { min: 1, max: 8 },
} as const;

export interface RunForm {
  scenarioId: string;
  ratePerSecond: number;
  durationSeconds: number;
  requestLimit: number;
  concurrency: number;
  targetUsernames: string;
}

export interface SimulatorState {
  api: SimulatorApi;
  target: TargetConfig | null;
  handshake: TargetHandshake | null;
  scenarios: ScenarioDescriptor[];
  currentRun: RunSnapshot | null;
  runs: RunSnapshot[];
  experiments: ComparisonReport[];
  streamStatus: StreamStatus;
  error: ApiRequestError | null;
  form: RunForm;
  busy: boolean;
  setForm: (form: RunForm) => void;
  selectScenario: (scenario: ScenarioDescriptor) => void;
  startRun: () => Promise<void>;
  stopRun: () => Promise<void>;
  manualLogin: (username: string, password: string) => Promise<RunSnapshot | null>;
  testTarget: () => Promise<void>;
  refreshTarget: () => Promise<void>;
  refreshScenarios: () => Promise<void>;
  refreshRuns: () => Promise<void>;
  refreshExperiments: () => Promise<void>;
  refreshCurrentRun: () => Promise<void>;
}

const SimulatorContext = createContext<SimulatorState | null>(null);

const EMPTY_COUNTERS: RunCounters = {
  planned: 0,
  sent: 0,
  completed: 0,
  cancelled: 0,
  transportErrors: 0,
  authenticationSuccess: 0,
  authenticationFailure: 0,
  rateLimited: 0,
  sourceBlocked: 0,
  invalidOrOverload: 0,
};

function toError(cause: unknown, fallback: string): ApiRequestError {
  if (cause instanceof ApiRequestError) {
    return cause;
  }
  return new ApiRequestError(fallback, { status: 0, code: 'unknown_error' });
}

function snapshotFromEvent(
  payload: Record<string, unknown>,
  previous: RunSnapshot | null,
  fallbackScenarioId: string | null,
): RunSnapshot | null {
  const runId = typeof payload.runId === 'string' ? payload.runId : null;
  if (!runId) {
    return previous;
  }
  const sameRun = previous && previous.runId === runId;
  const counters = (payload.counters as RunCounters | undefined) ?? (sameRun ? previous?.counters : undefined);
  const latency = (payload.latency as RunSnapshot['latency'] | undefined) ?? (sameRun ? previous?.latency : undefined);
  const state = typeof payload.state === 'string' ? (payload.state as RunState) : sameRun ? previous!.state : 'RUNNING';
  const achieved =
    typeof payload.achievedRatePerSecond === 'number'
      ? payload.achievedRatePerSecond
      : sameRun
        ? previous!.achievedRatePerSecond
        : 0;

  return {
    runId,
    state,
    scenarioId:
      typeof payload.scenarioId === 'string'
        ? payload.scenarioId
        : sameRun
          ? previous!.scenarioId
          : fallbackScenarioId,
    startedAt: sameRun ? previous!.startedAt : null,
    endedAt: sameRun ? previous!.endedAt : null,
    counters: counters ?? EMPTY_COUNTERS,
    latency: latency ?? { count: 0, avgMs: 0, p50Ms: 0, p95Ms: 0, maxMs: 0 },
    achievedRatePerSecond: achieved,
    targetBaseUrl: sameRun ? previous!.targetBaseUrl : null,
    config: sameRun ? previous!.config : null,
    message: typeof payload.message === 'string' ? payload.message : sameRun ? previous!.message : null,
  };
}

export function SimulatorProvider({ children }: { children: ReactNode }) {
  const api = useMemo(() => createSimulatorApi(), []);
  const announce = useAnnounce();
  const [target, setTarget] = useState<TargetConfig | null>(null);
  const [handshake, setHandshake] = useState<TargetHandshake | null>(null);
  const [scenarios, setScenarios] = useState<ScenarioDescriptor[]>([]);
  const [currentRun, setCurrentRun] = useState<RunSnapshot | null>(null);
  const [runs, setRuns] = useState<RunSnapshot[]>([]);
  const [experiments, setExperiments] = useState<ComparisonReport[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState<RunForm>({
    scenarioId: '',
    ratePerSecond: 2,
    durationSeconds: 12,
    requestLimit: 100,
    concurrency: 4,
    targetUsernames: '',
  });

  const refreshTarget = useCallback(async () => {
    try {
      const result = await api.target();
      setTarget(result.data);
    } catch (cause) {
      setError(toError(cause, 'The target configuration could not be read.'));
    }
  }, [api]);

  const refreshScenarios = useCallback(async () => {
    try {
      const result = await api.scenarios();
      setScenarios(result.data);
      setForm((current) => {
        if (current.scenarioId && result.data.some((scenario) => scenario.id === current.scenarioId)) {
          return current;
        }
        // The manual scenario needs operator supplied credentials and is driven by its own
        // endpoint, so the first startable strategy is selected by default.
        const first = result.data.find((scenario) => scenario.id !== 'manual-login') ?? result.data[0];
        if (!first) {
          return current;
        }
        return {
          ...current,
          scenarioId: first.id,
          ratePerSecond: first.defaultRatePerSecond,
          durationSeconds: first.defaultDurationSeconds,
        };
      });
    } catch (cause) {
      setError(toError(cause, 'The scenarios could not be read.'));
    }
  }, [api]);

  const testTarget = useCallback(async () => {
    setBusy(true);
    try {
      const result = await api.testTarget();
      setHandshake(result.data);
      setError(null);
    } catch (cause) {
      setHandshake(null);
      setError(toError(cause, 'The handshake test could not be completed.'));
    } finally {
      setBusy(false);
    }
  }, [api]);

  const refreshCurrentRun = useCallback(async () => {    try {
      const result = await api.currentRun();
      setCurrentRun(result.data);
      setError(null);
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.isNotFound) {
        setCurrentRun(null);
        return;
      }
      setError(toError(cause, 'The current run could not be read.'));
    }
  }, [api]);

  const refreshRuns = useCallback(async () => {
    try {
      const result = await api.runs();
      setRuns(result.data);
    } catch (cause) {
      setError(toError(cause, 'The run history could not be read.'));
    }
  }, [api]);

  const refreshExperiments = useCallback(async () => {
    try {
      const result = await api.experiments();
      setExperiments(result.data);
    } catch (cause) {
      setError(toError(cause, 'The comparison reports could not be read.'));
    }
  }, [api]);

  const live = useRunStream({ onResync: () => void refreshCurrentRun() });
  const liveRef = useRef(live);
  liveRef.current = live;

  useEffect(() => {
    void refreshTarget();
    void refreshScenarios();
    void refreshRuns();
    void refreshExperiments();
    void refreshCurrentRun();
  }, [refreshCurrentRun, refreshExperiments, refreshRuns, refreshScenarios, refreshTarget]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void refreshCurrentRun();
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refreshCurrentRun]);

  useEffect(() => {
    const latest = live.events[0];
    if (!latest) {
      return;
    }
    if (latest.type === 'RUN_STARTED') {
      setCurrentRun((previous) => snapshotFromEvent(latest.payload, previous, form.scenarioId));
      void refreshCurrentRun();
      return;
    }
    if (latest.type === 'RUN_PROGRESS' || latest.type === 'RUN_STATE') {
      setCurrentRun((previous) => snapshotFromEvent(latest.payload, previous, form.scenarioId));
      return;
    }
    if (latest.type === 'RUN_FAILED') {
      setCurrentRun((previous) => snapshotFromEvent(latest.payload, previous, form.scenarioId));
      void refreshRuns();
    }
  }, [form.scenarioId, live.events, refreshCurrentRun, refreshRuns]);

  const selectScenario = useCallback((scenario: ScenarioDescriptor) => {
    setForm((current) => ({
      ...current,
      scenarioId: scenario.id,
      ratePerSecond: scenario.defaultRatePerSecond,
      durationSeconds: scenario.defaultDurationSeconds,
    }));
  }, []);

  const startRun = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const usernames = form.targetUsernames
        .split(',')
        .map((value) => value.trim())
        .filter((value) => value.length > 0);
      // The list is not truncated here: the service owns the cap and answers with
      // `invalid_request` when more than 32 usernames are sent.
      const result = await api.startRun({
        scenarioId: form.scenarioId,
        ratePerSecond: form.ratePerSecond,
        durationSeconds: form.durationSeconds,
        requestLimit: form.requestLimit,
        concurrency: form.concurrency,
        targetUsernames: usernames,
      });
      setCurrentRun(result.data);
      announce(`Run ${result.data.state.toLowerCase()}.`);
      void refreshRuns();
    } catch (cause) {
      setError(toError(cause, 'The run could not be started.'));
    } finally {
      setBusy(false);
    }
  }, [announce, api, form, refreshRuns]);

  const stopRun = useCallback(async () => {
    if (!currentRun?.runId) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await api.stopRun(currentRun.runId);
      setCurrentRun(result.data);
      announce(`Run ${result.data.state.toLowerCase()}.`);
      void refreshRuns();
    } catch (cause) {
      setError(toError(cause, 'The run could not be stopped.'));
    } finally {
      setBusy(false);
    }
  }, [announce, api, currentRun, refreshRuns]);

  const manualLogin = useCallback(async (username: string, password: string) => {
    setBusy(true);
    setError(null);
    try {
      const result = await api.manualLogin({ username, password });
      setCurrentRun(result.data);
      announce(`Manual login ${result.data.state.toLowerCase()}.`);
      void refreshRuns();
      return result.data;
    } catch (cause) {
      setError(toError(cause, 'The manual login could not be completed.'));
      return null;
    } finally {
      setBusy(false);
    }
  }, [announce, api, refreshRuns]);

  const value = useMemo<SimulatorState>(
    () => ({
      api,
      target,
      handshake,
      scenarios,
      currentRun,
      runs,
      experiments,
      streamStatus: live.status,
      error,
      form,
      busy,
      setForm,
      selectScenario,
      startRun,
      stopRun,
      manualLogin,
      testTarget,
      refreshTarget,
      refreshScenarios,
      refreshRuns,
      refreshExperiments,
      refreshCurrentRun,
    }),
    [
      api,
      busy,
      currentRun,
      error,
      experiments,
      form,
      handshake,
      live.status,
      manualLogin,
      refreshCurrentRun,
      refreshExperiments,
      refreshRuns,
      refreshScenarios,
      refreshTarget,
      runs,
      scenarios,
      selectScenario,
      startRun,
      stopRun,
      target,
      testTarget,
    ],
  );

  return <SimulatorContext.Provider value={value}>{children}</SimulatorContext.Provider>;
}

export function useSimulator(): SimulatorState {
  const value = useContext(SimulatorContext);
  if (!value) {
    throw new Error('useSimulator must be used inside a SimulatorProvider.');
  }
  return value;
}
