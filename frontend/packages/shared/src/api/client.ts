import type {
  Alert,
  AlertDetailResponse,
  AlertEvidenceResponse,
  ComparisonReport,
  DashboardSnapshot,
  DemoAccount,
  DiagnosticsSnapshot,
  DefenderOverview,
  EvidenceSnapshot,
  HealthStatus,
  Identity,
  LoginResponse,
  ManualLoginRequest,
  MetricsSnapshot,
  PersistedEvent,
  PolicySettings,
  ProtectionResponse,
  ResetResponse,
  RunConfig,
  RunSnapshot,
  ScenarioDescriptor,
  SettingsUpdate,
  SourceDetail,
  SourceSummary,
  TargetConfig,
  TargetHandshake,
  UnblockResponse,
} from './types';

export interface ApiErrorInit {
  status: number;
  code: string;
  requestId?: string;
  retryAfterSeconds?: number;
  body?: unknown;
}

/** Every failed call carries the contract error code so the UI can react honestly. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;
  readonly retryAfterSeconds?: number;
  readonly body?: unknown;

  constructor(message: string, init: ApiErrorInit) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = init.status;
    this.code = init.code;
    this.requestId = init.requestId;
    this.retryAfterSeconds = init.retryAfterSeconds;
    this.body = init.body;
  }

  /** Analyst controls are only reachable from a loopback caller. */
  get isLoopbackRequired(): boolean {
    return this.code === 'analyst_loopback_required';
  }

  /** The service could not be reached at all (network/transport failure). */
  get isTransportFailure(): boolean {
    return this.code === 'network_error';
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

export interface LoginResult {
  response: LoginResponse;
  requestId: string | null;
  sourceIp: string | null;
}

const JSON_HEADERS: Record<string, string> = {
  Accept: 'application/json',
  'Content-Type': 'application/json',
};

function retryAfterFrom(response: Response): number | undefined {
  const header = response.headers.get('Retry-After');
  if (!header) {
    return undefined;
  }
  const parsed = Number.parseInt(header, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export interface RequestOptions {
  /**
   * Additional HTTP statuses whose body still carries the documented payload of the call.
   * The login endpoint is the case in the contract: a `429` carries a `LoginResponse` with
   * `status = rate_limited | source_blocked`, not an `ApiError`.
   */
  acceptStatuses?: number[];
}

async function requestJson<T>(
  baseUrl: string,
  path: string,
  init?: RequestInit,
  options?: RequestOptions,
): Promise<ApiResponse<T>> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: { ...JSON_HEADERS, ...(init?.headers as Record<string, string> | undefined) },
    });
  } catch (cause) {
    throw new ApiRequestError(
      `The service could not be reached (${cause instanceof Error ? cause.message : 'network error'}).`,
      { status: 0, code: 'network_error', body: cause },
    );
  }

  const text = await response.text();
  let payload: unknown;
  if (text.length > 0) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = undefined;
    }
  }

  const accepted = response.ok || (options?.acceptStatuses ?? []).includes(response.status);

  if (!accepted) {
    const errorBody = payload as { code?: string; message?: string; requestId?: string; retryAfterSeconds?: number } | undefined;
    throw new ApiRequestError(errorBody?.message ?? `Request failed with HTTP ${response.status}.`, {
      status: response.status,
      code: errorBody?.code ?? 'http_error',
      requestId: errorBody?.requestId,
      retryAfterSeconds: errorBody?.retryAfterSeconds ?? retryAfterFrom(response),
      body: payload,
    });
  }

  return { data: payload as T, status: response.status, headers: response.headers };
}

function bodyJson(value: unknown): string {
  return JSON.stringify(value);
}

/** Typed access to the defender service (`/api/v1/...` on the local origin). */
export class DefenderApi {
  constructor(private readonly baseUrl = '') {}

  health(): Promise<ApiResponse<HealthStatus>> {
    return requestJson<HealthStatus>(this.baseUrl, '/api/v1/health');
  }

  identity(): Promise<ApiResponse<Identity>> {
    return requestJson<Identity>(this.baseUrl, '/api/v1/identity');
  }

  demoAccounts(): Promise<ApiResponse<DemoAccount[]>> {
    return requestJson<DemoAccount[]>(this.baseUrl, '/api/v1/demo/accounts');
  }

  dashboardSnapshot(): Promise<ApiResponse<DashboardSnapshot>> {
    return requestJson<DashboardSnapshot>(this.baseUrl, '/api/v1/dashboard/snapshot');
  }

  metricsSnapshot(runId?: string): Promise<ApiResponse<MetricsSnapshot>> {
    const query = runId ? `?runId=${encodeURIComponent(runId)}` : '';
    return requestJson<MetricsSnapshot>(this.baseUrl, `/api/v1/metrics/snapshot${query}`);
  }

  policy(): Promise<ApiResponse<PolicySettings>> {
    return requestJson<PolicySettings>(this.baseUrl, '/api/v1/policy');
  }

  events(limit = 50): Promise<ApiResponse<PersistedEvent[]>> {
    return requestJson<PersistedEvent[]>(this.baseUrl, `/api/v1/events?limit=${limit}`);
  }

  alerts(limit = 50): Promise<ApiResponse<Alert[]>> {
    return requestJson<Alert[]>(this.baseUrl, `/api/v1/alerts?limit=${limit}`);
  }

  alert(alertId: string): Promise<ApiResponse<AlertDetailResponse>> {
    return requestJson<AlertDetailResponse>(this.baseUrl, `/api/v1/alerts/${encodeURIComponent(alertId)}`);
  }

  alertEvidence(alertId: string): Promise<ApiResponse<AlertEvidenceResponse>> {
    return requestJson<AlertEvidenceResponse>(this.baseUrl, `/api/v1/alerts/${encodeURIComponent(alertId)}/evidence`);
  }

  evidence(decisionId: string): Promise<ApiResponse<EvidenceSnapshot>> {
    return requestJson<EvidenceSnapshot>(this.baseUrl, `/api/v1/evidence/${encodeURIComponent(decisionId)}`);
  }

  sources(): Promise<ApiResponse<SourceSummary[]>> {
    return requestJson<SourceSummary[]>(this.baseUrl, '/api/v1/sources');
  }

  source(sourceIp: string): Promise<ApiResponse<SourceDetail>> {
    return requestJson<SourceDetail>(this.baseUrl, `/api/v1/sources/${encodeURIComponent(sourceIp)}`);
  }

  async login(username: string, password: string, runId?: string): Promise<LoginResult> {
    // A `429` or `503` carries a LoginResponse (rate_limited / source_blocked / service_overloaded)
    // and is therefore not a transport error for this call.
    const result = await requestJson<LoginResponse>(
      this.baseUrl,
      '/api/v1/auth/login',
      {
        method: 'POST',
        body: bodyJson({ username, password, runId }),
      },
      { acceptStatuses: [429, 503] },
    );
    return {
      response: result.data,
      requestId: result.headers.get('X-ANVEX-Request-Id'),
      sourceIp: result.headers.get('X-ANVEX-Source-Ip'),
    };
  }

  settings(): Promise<ApiResponse<PolicySettings>> {
    return requestJson<PolicySettings>(this.baseUrl, '/api/v1/settings');
  }

  updateSettings(update: SettingsUpdate): Promise<ApiResponse<PolicySettings>> {
    return requestJson<PolicySettings>(this.baseUrl, '/api/v1/settings', {
      method: 'PUT',
      body: bodyJson(update),
    });
  }

  setProtection(mode: 'ON' | 'OFF'): Promise<ApiResponse<ProtectionResponse>> {
    return requestJson<ProtectionResponse>(this.baseUrl, '/api/v1/protection', {
      method: 'POST',
      body: bodyJson({ mode }),
    });
  }

  unblock(sourceIp: string, note: string): Promise<ApiResponse<UnblockResponse>> {
    return requestJson<UnblockResponse>(this.baseUrl, '/api/v1/analyst/unblock', {
      method: 'POST',
      body: bodyJson({ sourceIp, note }),
    });
  }

  reset(scope: 'counters' | 'demo'): Promise<ApiResponse<ResetResponse>> {
    return requestJson<ResetResponse>(this.baseUrl, '/api/v1/analyst/reset', {
      method: 'POST',
      body: bodyJson({ scope }),
    });
  }

  diagnostics(): Promise<ApiResponse<DiagnosticsSnapshot>> {
    return requestJson<DiagnosticsSnapshot>(this.baseUrl, '/api/v1/diagnostics');
  }
}

/** Typed access to the simulator service (`/api/v1/...` on the local origin). */
export class SimulatorApi {
  constructor(private readonly baseUrl = '') {}

  health(): Promise<ApiResponse<HealthStatus>> {
    return requestJson<HealthStatus>(this.baseUrl, '/api/v1/health');
  }

  identity(): Promise<ApiResponse<Identity>> {
    return requestJson<Identity>(this.baseUrl, '/api/v1/identity');
  }

  target(): Promise<ApiResponse<TargetConfig>> {
    return requestJson<TargetConfig>(this.baseUrl, '/api/v1/target');
  }

  updateTarget(baseUrl: string): Promise<ApiResponse<TargetConfig>> {
    return requestJson<TargetConfig>(this.baseUrl, '/api/v1/target', {
      method: 'PUT',
      body: bodyJson({ baseUrl }),
    });
  }

  defenderOverview(runId?: string): Promise<ApiResponse<DefenderOverview>> {
    const path = runId ? `/api/v1/target/overview?runId=${encodeURIComponent(runId)}` : '/api/v1/target/overview';
    return requestJson<DefenderOverview>(this.baseUrl, path);
  }

  testTarget(): Promise<ApiResponse<TargetHandshake>> {
    return requestJson<TargetHandshake>(this.baseUrl, '/api/v1/target/test', { method: 'POST' });
  }

  scenarios(): Promise<ApiResponse<ScenarioDescriptor[]>> {
    return requestJson<ScenarioDescriptor[]>(this.baseUrl, '/api/v1/scenarios');
  }

  startRun(config: RunConfig): Promise<ApiResponse<RunSnapshot>> {
    return requestJson<RunSnapshot>(this.baseUrl, '/api/v1/runs', {
      method: 'POST',
      body: bodyJson(config),
    });
  }

  manualLogin(request: ManualLoginRequest): Promise<ApiResponse<RunSnapshot>> {
    return requestJson<RunSnapshot>(this.baseUrl, '/api/v1/runs/manual', {
      method: 'POST',
      body: bodyJson(request),
    });
  }

  stopRun(runId: string): Promise<ApiResponse<RunSnapshot>> {
    return requestJson<RunSnapshot>(this.baseUrl, `/api/v1/runs/${encodeURIComponent(runId)}/stop`, {
      method: 'POST',
    });
  }

  currentRun(): Promise<ApiResponse<RunSnapshot>> {
    return requestJson<RunSnapshot>(this.baseUrl, '/api/v1/runs/current');
  }

  runs(): Promise<ApiResponse<RunSnapshot[]>> {
    return requestJson<RunSnapshot[]>(this.baseUrl, '/api/v1/runs');
  }

  run(runId: string): Promise<ApiResponse<RunSnapshot>> {
    return requestJson<RunSnapshot>(this.baseUrl, `/api/v1/runs/${encodeURIComponent(runId)}`);
  }

  createExperiment(baselineRunId: string, candidateRunId: string): Promise<ApiResponse<ComparisonReport>> {
    return requestJson<ComparisonReport>(this.baseUrl, '/api/v1/experiments', {
      method: 'POST',
      body: bodyJson({ baselineRunId, candidateRunId }),
    });
  }

  experiments(): Promise<ApiResponse<ComparisonReport[]>> {
    return requestJson<ComparisonReport[]>(this.baseUrl, '/api/v1/experiments');
  }
}

export function createDefenderApi(baseUrl = ''): DefenderApi {
  return new DefenderApi(baseUrl);
}

export function createSimulatorApi(baseUrl = ''): SimulatorApi {
  return new SimulatorApi(baseUrl);
}
