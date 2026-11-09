/**
 * Typed mirror of the ANVEX API contract (docs/API_CONTRACT.md, v1).
 *
 * Every field below is produced by the Java services. Nothing in this file may be
 * synthesised by the browser: values that the services do not publish are typed as
 * absent rather than defaulted to invented content.
 */

/* ------------------------------------------------------------------ common */

export interface ApiErrorBody {
  code: string;
  message: string;
  requestId: string;
  serverTime: string;
  retryAfterSeconds?: number | null;
}

export interface HealthStatus {
  status: string;
  service: string;
  version: string;
  apiVersion: string;
  uptimeMs: number;
  javaVersion: string;
  components: Record<string, string>;
}

export interface Identity {
  product: string;
  version: string;
  apiVersion: string;
  protocol: string;
  buildTime: string;
}

export type AwarenessLevel = 'info' | 'notice' | 'warning';
export type AwarenessScope = 'account' | 'session';

export interface AwarenessNotice {
  level: AwarenessLevel;
  title: string;
  message: string;
  scope: AwarenessScope;
  relatedEventIds?: string[] | null;
}

export interface LoginUser {
  username: string;
  displayName: string;
  role: string;
}

export type LoginStatus =
  | 'authenticated'
  | 'invalid_credentials'
  | 'rate_limited'
  | 'source_blocked'
  | 'service_overloaded';

export interface LoginResponse {
  status: LoginStatus;
  requestId: string;
  serverTime: string;
  user?: LoginUser | null;
  awareness?: AwarenessNotice[] | null;
  retryAfterSeconds?: number | null;
}

export interface DemoAccount {
  username: string;
  displayName: string;
  role: string;
  demoPassword: string;
  seededAt: string;
}

export interface EventEnvelope {
  eventId: string;
  type: string;
  occurredAt: string;
  sequence: number;
  payload: Record<string, unknown>;
}

/* ---------------------------------------------------------------- defender */

export interface DefenderCounters {
  incomingRequests: number;
  requestsReachingAuthentication: number;
  authenticationSuccess: number;
  authenticationFailure: number;
  rateLimitedRequests: number;
  sourceBlockedRequests: number;
  invalidOrOverloadRequests: number;
  detectionAlerts: number;
}

export type ProtectionMode = 'ON' | 'OFF';

export interface ProtectionState {
  mode: ProtectionMode;
  policyVersion: string;
  observationOnly: boolean;
}

export interface SourceSummary {
  sourceIp: string;
  totalIncoming: number;
  totalFailures: number;
  totalSuccesses: number;
  lastActivity: string | null;
  trackedAccounts: number;
}

export type DetectionCategory =
  | 'NONE'
  | 'SUSPECTED_REPEATED_GUESSING'
  | 'SUSPECTED_PASSWORD_SPRAYING'
  | 'SUSPECTED_RAPID_REQUESTS';

export interface RuleMatch {
  ruleId: string;
  category: DetectionCategory;
  threshold: number;
  observedCount: number;
  observedDistinctAccounts: number;
  windowStart: string;
  windowEnd: string;
  description: string;
}

/** The immutable policy snapshot stored inside an evidence record (milliseconds). */
export interface PolicySnapshot {
  policyVersion: string;
  repeatedGuessingFailureThreshold: number;
  repeatedGuessingWindowMillis: number;
  sprayingFailureThreshold: number;
  sprayingDistinctAccountThreshold: number;
  sprayingWindowMillis: number;
  rapidRequestThreshold: number;
  rapidRequestWindowMillis: number;
  tokenBucketRequestsPerSecond: number;
  tokenBucketBurstCapacity: number;
  blockDurationMillis: number;
  protectionMode: ProtectionMode;
  createdAt: string;
}

/** The public policy / analyst settings view (seconds). */
export interface PolicySettings {
  policyVersion: string;
  protectionMode: ProtectionMode;
  repeatedGuessingFailureThreshold: number;
  repeatedGuessingWindowSeconds: number;
  sprayingFailureThreshold: number;
  sprayingDistinctAccountThreshold: number;
  sprayingWindowSeconds: number;
  rapidRequestThreshold: number;
  rapidRequestWindowSeconds: number;
  tokenBucketRequestsPerSecond: number;
  tokenBucketBurstCapacity: number;
  blockDurationSeconds: number;
  createdAt: string;
}

export interface EvidenceSnapshot {
  decisionId: string;
  sourceIp: string;
  affectedUsernames: string[];
  category: DetectionCategory;
  matchedRules: RuleMatch[];
  observed: Record<string, unknown>;
  windows: Record<string, unknown>;
  triggeringEventIds: string[];
  policyVersion: string;
  policySnapshot: PolicySnapshot | null;
  protectionMode: ProtectionMode;
  action: string;
  reason: string;
  blockExpiry: string | null;
  createdAt: string;
}

export type AlertSeverity = 'HIGH' | 'MEDIUM' | 'LOW';

export interface Alert {
  alertId: string;
  sourceIp: string;
  ruleId: string;
  category: DetectionCategory;
  severity: AlertSeverity;
  firstSeen: string;
  lastSeen: string;
  occurrenceCount: number;
  status: string;
  decisionIds: string[];
}

export interface SourceBlock {
  blockId: string;
  sourceIp: string;
  ruleId: string;
  reason: string;
  createdAt: string;
  expiry: string;
  releasedAt: string | null;
  releasedBy: string | null;
  state: string;
}

export interface WorkerState {
  name: string;
  state: string;
}

export interface EventPipelineDiagnostics {
  running: boolean;
  telemetryQueueDepth: number;
  telemetryQueueCapacity: number;
  criticalQueueDepth: number;
  criticalQueueCapacity: number;
  publishQueueDepth: number;
  publishedEvents: number;
  aggregatedEvents: number;
  droppedEvents: number;
  criticalDroppedEvents: number;
  overloadResponses: number;
  persistedEvents: number;
  publicationDrops: number;
  persistenceErrors: number;
  lastPersistedAt: string | null;
  subscriberCount: number;
  workerStates: WorkerState[];
}

export interface DashboardSnapshot {
  counters: DefenderCounters;
  protection: ProtectionState;
  recentAlerts: Alert[];
  recentEvidence: EvidenceSnapshot[];
  activeBlocks: SourceBlock[];
  sources: SourceSummary[];
  eventPipeline: EventPipelineDiagnostics;
}

export interface MetricsSnapshot {
  counters: DefenderCounters;
  protection: ProtectionState;
  runId?: string;
  runCounters?: Partial<DefenderCounters>;
}

export interface PersistedEvent {
  eventId: string;
  type: string;
  occurredAt: string;
  sourceIp: string;
  requestId: string | null;
  critical: boolean;
  aggregated: boolean;
}

export interface SourceDetail {
  source: SourceSummary;
  alerts: Alert[];
}

export interface AlertDetailResponse {
  alert: Alert;
}

export interface AlertEvidenceResponse {
  alert: Alert;
  evidence: EvidenceSnapshot[];
}

export interface SettingsUpdate {
  repeatedGuessingFailureThreshold?: number;
  repeatedGuessingWindowSeconds?: number;
  sprayingFailureThreshold?: number;
  sprayingDistinctAccountThreshold?: number;
  sprayingWindowSeconds?: number;
  rapidRequestThreshold?: number;
  rapidRequestWindowSeconds?: number;
  tokenBucketRequestsPerSecond?: number;
  tokenBucketBurstCapacity?: number;
  blockDurationSeconds?: number;
  policyVersion?: string;
}

export interface ProtectionResponse {
  mode: ProtectionMode;
  policyVersion: string;
  activeBlocks: number;
}

export interface UnblockResponse {
  released: boolean;
  sourceIp?: string;
  blockId?: string;
  releasedAt?: string;
}

export interface ResetResponse {
  scope: string;
  resetAt: string;
  counters: DefenderCounters;
}

export interface DiagnosticsSnapshot {
  javaVersion: string;
  javaVendor: string;
  policy: PolicySettings;
  counters: DefenderCounters;
  activeBlocks: SourceBlock[];
  blockHistory: SourceBlock[];
  protectionEvents: string[];
  sources: SourceSummary[];
  alerts: Alert[];
  evidenceCount: number;
  eventPipeline: EventPipelineDiagnostics;
  historyGaps: {
    note: string;
    retainedWindowSeconds: number;
  };
}

/* --------------------------------------------------------------- simulator */

export interface ScenarioDescriptor {
  id: string;
  displayName: string;
  description: string;
  defaultRatePerSecond: number;
  defaultDurationSeconds: number;
  defaultAccountCount: number;
  requiresAccountCount: boolean;
  defaultTargetUsernames: string[];
}

export interface RunConfig {
  scenarioId: string;
  ratePerSecond: number;
  durationSeconds: number;
  targetUsernames: string[];
  requestLimit: number;
  concurrency: number;
}

export interface RunCounters {
  planned: number;
  sent: number;
  completed: number;
  cancelled: number;
  transportErrors: number;
  authenticationSuccess: number;
  authenticationFailure: number;
  rateLimited: number;
  sourceBlocked: number;
  invalidOrOverload: number;
}

export interface LatencySummary {
  count: number;
  avgMs: number;
  p50Ms: number;
  p95Ms: number;
  maxMs: number;
}

export type RunState = 'IDLE' | 'RUNNING' | 'STOPPING' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

export interface RunSnapshot {
  runId: string | null;
  state: RunState;
  scenarioId: string | null;
  startedAt: string | null;
  endedAt: string | null;
  counters: RunCounters;
  latency: LatencySummary;
  achievedRatePerSecond: number;
  targetBaseUrl: string | null;
  config: RunConfig | null;
  message?: string | null;
}

export interface TargetConfig {
  baseUrl: string;
}

/** Verified, read-only defender data relayed by the simulator's same-origin API. */
export interface DefenderOverview {
  targetBaseUrl: string;
  reachable: boolean;
  compatible: boolean;
  available: boolean;
  message: string;
  snapshot: DashboardSnapshot | null;
}

export interface TargetHandshake {
  baseUrl: string;
  reachable: boolean;
  compatible: boolean;
  product: string | null;
  version: string | null;
  apiVersion: string | null;
  protocol: string | null;
  message: string;
}

export interface ManualLoginRequest {
  username: string;
  password: string;
}

export interface ComparisonSide {
  runId: string;
  state: string;
  scenarioId: string;
  sent: number;
  completed: number;
  authenticationSuccess: number;
  authenticationFailure: number;
  rateLimited: number;
  sourceBlocked: number;
  transportErrors: number;
  defenderReceived: number | null;
  defenderReachedAuthentication: number | null;
  defenderRateLimited: number | null;
  defenderSourceBlocked: number | null;
  achievedRatePerSecond: number;
  startedAt: string | null;
  endedAt: string | null;
}

export type ComparisonLabel = 'measured-comparison' | 'small-sample' | 'incomplete-comparison' | 'incomparable-parameters';

export interface ComparisonReduction {
  sentReductionPercent: number | null;
  defenderReceivedReductionPercent: number | null;
  authenticationAdmissionReductionPercent: number | null;
  rateLimitedIncrease: number | null;
  meaningful: boolean;
  label: ComparisonLabel | string;
}

export interface ComparisonReport {
  comparisonId: string;
  baselineRunId: string;
  candidateRunId: string;
  createdAt: string;
  settingsSnapshot: Record<string, unknown>;
  parameters: Record<string, unknown>;
  baseline: ComparisonSide;
  candidate: ComparisonSide;
  reduction: ComparisonReduction;
  verdict: string;
  caveats: string[];
}
