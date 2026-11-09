# ANVEX API Contract (v1)

Base URL conventions:
* Defender (Laptop 1): `http://<defender-host>:8080`
* Simulator (Laptop 2): `http://127.0.0.1:8081`

All payloads are JSON (UTF-8). All endpoints are prefixed `/api/v1`. Timestamps are ISO-8601 UTC with milliseconds (`2026-10-26T10:15:30.123Z`). Durations are milliseconds unless a field name ends in `Seconds`/`At`. API version is exposed as `apiVersion: "v1"`.

Cross-cutting rules:
* Error bodies use the shared `ApiError` shape: `{ "code": "...", "message": "...", "requestId": "...", "serverTime": "...", "retryAfterSeconds": 12 }`.
* The defender never returns passwords, password hashes or detection internals on the login endpoint; only status, request id, retry information and account-scoped awareness.
* Analyst endpoints (`/api/v1/analyst/**`) and defender-side state mutation require a **loopback** caller (`127.0.0.0/8` or `::1`). Forwarded IP headers are ignored.

## 1. Shared DTOs (common module)

`com.anvex.common`:
* `ApiError` — `code`, `message`, `requestId`, `serverTime`, `retryAfterSeconds`
* `ApiEnvelope<T>` — `apiVersion`, `serverTime`, `data`
* `Identity` — `product`, `version`, `apiVersion`, `protocol`, `buildTime`
* `HealthStatus` — `status`, `service`, `version`, `apiVersion`, `uptimeMs`, `javaVersion`, `components`
* `LoginRequest` — `username` (required, ≤ 64 chars), `password` (required, ≤ 256 chars), `runId` (optional untrusted metadata)
* `LoginResponse` — `status` (`authenticated` | `invalid_credentials` | `rate_limited` | `source_blocked` | `service_overloaded`), `requestId`, `serverTime`, `user`, `awareness`, `retryAfterSeconds`
* `LoginUser` — `username`, `displayName`, `role`
* `AwarenessNotice` — `level` (`info` | `notice` | `warning`), `title`, `message`, `scope` (`account` | `session`), `relatedEventIds`
* `ProtocolHandshake` — `product`, `version`, `apiVersion`, `protocol`, `compatible`
* `ScenarioDescriptor` — `id`, `displayName`, `description`, `defaultRatePerSecond`, `defaultDurationSeconds`, `defaultAccountCount`, `requiresAccountCount`
* `RunConfig` — `scenarioId`, `ratePerSecond`, `durationSeconds`, `targetUsernames`, `requestLimit`, `concurrency`
* `RunSnapshot` — `runId`, `state`, `scenarioId`, `startedAt`, `endedAt`, `counters`, `latency`, `achievedRatePerSecond`, `target`, `config`, `error`
* `RunCounters` — `planned`, `sent`, `completed`, `cancelled`, `transportErrors`, `authenticationSuccess`, `authenticationFailure`, `rateLimited`, `sourceBlocked`, `invalidOrOverload`
* `LatencySummary` — `count`, `avgMs`, `p50Ms`, `p95Ms`, `maxMs`
* `EventEnvelope` — `eventId`, `type`, `occurredAt`, `sequence`, `payload`
* `ComparisonReport` — `comparisonId`, `baselineRunId`, `candidateRunId`, `createdAt`, `settingsSnapshot`, `parameters`, `baseline`, `candidate`, `reduction`, `verdict`

## 2. Defender — identity and health

### `GET /api/v1/health`
```json
{ "status": "UP", "service": "anvex-defender", "version": "1.0.0", "apiVersion": "v1",
  "uptimeMs": 123456, "javaVersion": "21.0.12.1",
  "components": { "database": "UP", "eventQueue": "UP", "workers": "UP" } }
```

### `GET /api/v1/identity`
```json
{ "product": "ANVEX", "version": "1.0.0", "apiVersion": "v1", "protocol": "anvex/1", "buildTime": "2026-10-26T09:00:00Z" }
```

## 3. Defender — dummy application authentication

### `POST /api/v1/auth/login`
Request:
```json
{ "username": "asha.raman", "password": "Asha!2345", "runId": "optional-untrusted-metadata" }
```
Responses:
| Status | Body | Meaning |
|---|---|---|
| 200 | `LoginResponse` `status=authenticated` | Credentials valid |
| 200 | `LoginResponse` `status=invalid_credentials` | Generic failure — no detail about user existence |
| 429 | `LoginResponse` `status=rate_limited` + `retryAfterSeconds` + `Retry-After` header | Token bucket exhausted (protection ON) |
| 429 | `LoginResponse` `status=source_blocked` + `retryAfterSeconds` + `Retry-After` header | Active source block (protection ON) |
| 503 | `LoginResponse` `status=service_overloaded` | Critical event queue saturated |
| 400 | `ApiError` `code=invalid_request` | Missing/oversized fields |

Awareness notices are attached to `200` responses (account-scoped, no other users' details).

### `GET /api/v1/demo/accounts`
Seeded dummy usernames, display names and their **published demo passwords** (these accounts are intentionally public for the classroom demo).

## 4. Defender — monitoring, evidence, alerts

| Method & path | Purpose |
|---|---|
| `GET /api/v1/dashboard/snapshot` | Metrics, protection state, active blocks, recent alerts/evidence, source summaries, event-pipeline diagnostics |
| `GET /api/v1/metrics/snapshot` | Machine-readable authoritative counters (read-only, used by simulator comparisons). Optional `?runId=` returns the counters grouped by that simulator run id |
| `GET /api/v1/policy` | Public, read-only policy snapshot (thresholds, windows, token bucket, block duration, protection mode, policy version) |
| `GET /api/v1/events?limit=` | Recent persisted events (bounded page) |
| `GET /api/v1/events/stream` | SSE stream of event envelopes (`text/event-stream`) |
| `GET /api/v1/alerts?limit=` | Alert history |
| `GET /api/v1/alerts/{alertId}` | Alert detail with matched rules and counters |
| `GET /api/v1/alerts/{alertId}/evidence` | Evidence snapshot + supporting event references |
| `GET /api/v1/evidence/{decisionId}` | One evidence snapshot |
| `GET /api/v1/sources` | Per-source activity summary |
| `GET /api/v1/sources/{ip}` | Source drill-down: accounts, rules, timeline, blocks |

The defender does **not** expose experiment endpoints: OFF/ON comparisons are computed and retained by the simulator (section 6), which reads the defender counters through `GET /api/v1/metrics/snapshot?runId=`.

## 5. Defender — analyst controls (loopback only)

| Method & path | Purpose |
|---|---|
| `GET /api/v1/settings` | Current detection/protection settings + policy version |
| `PUT /api/v1/settings` | Update thresholds/windows/rate limits (validated, creates a new settings version) |
| `POST /api/v1/protection` | `{ "mode": "ON" | "OFF" }` — toggle protection |
| `POST /api/v1/analyst/unblock` | `{ "sourceIp": "...", "note": "..." }` — manual release |
| `POST /api/v1/analyst/reset` | `{ "scope": "counters" | "demo" }` — reset rolling state or demo history |
| `GET /api/v1/diagnostics` | Java version, worker names/states, queue depth, lag, counters, history gaps |

Non-loopback callers receive `403` with `code=analyst_loopback_required`.

## 6. Simulator — control plane

| Method & path | Purpose |
|---|---|
| `GET /api/v1/health` | Simulator health |
| `GET /api/v1/identity` | Simulator identity |
| `GET /api/v1/target` | Current defender target configuration |
| `PUT /api/v1/target` | `{ "baseUrl": "http://192.168.1.50:8080" }` (validated http/https URL) |
| `POST /api/v1/target/test` | ANVEX identity/version handshake + reachable login endpoint probe |
| `GET /api/v1/scenarios` | Available scenario strategies and defaults |
| `POST /api/v1/runs` | Start a run (`RunConfig`) → `RunSnapshot`. A run that cannot reach a compatible defender is returned with `state = "FAILED"` and a message (HTTP 200) |
| `POST /api/v1/runs/manual` | One login attempt with operator supplied credentials (`username`, `password`) → `RunSnapshot` |
| `POST /api/v1/runs/{runId}/stop` | Idempotent stop (a finished run is returned unchanged) |
| `GET /api/v1/runs` | Previous run summaries |
| `GET /api/v1/runs/current` | The active run snapshot (404 when no run is active) |
| `GET /api/v1/runs/{runId}` | One run snapshot |
| `GET /api/v1/runs/stream` | SSE stream of run progress (`EventEnvelope`) |
| `POST /api/v1/experiments` | `{ "baselineRunId": "...", "candidateRunId": "..." }` → `ComparisonReport` |
| `GET /api/v1/experiments` | Retained comparison reports |

Comparison reports label mismatched scenario, target URL, or full accepted run configuration as `incomparable-parameters`. For those reports and incomplete runs, reduction percentages are `null` rather than potentially misleading. `small-sample` flags matching runs with fewer than ten sent requests. A report's `settingsSnapshot` is taken **at comparison time** and is **not** proof that the baseline/candidate used different protection modes. The full accepted configurations must be compared, not just the selected scenario name.

Simulator run state machine: `IDLE → RUNNING → STOPPING → COMPLETED | CANCELLED | FAILED`. Overlapping runs are rejected with `409 run_in_progress`.

## 7. Error codes

| Code | HTTP | Meaning |
|---|---|---|
| `invalid_request` | 400 | Missing/oversized/invalid field |
| `invalid_credentials` | 200 | Generic authentication failure (deliberately not 401 to avoid ambiguity with transport errors in the simulator) |
| `rate_limited` | 429 | Token bucket exhausted |
| `source_blocked` | 429 | Active source block |
| `service_overloaded` | 503 | Critical event queue saturated |
| `analyst_loopback_required` | 403 | Analyst control requested from a non-loopback address |
| `run_in_progress` | 409 | A run is already active |
| `run_not_found` | 404 | Unknown run id |
| `target_unreachable` | 502 | Defender target could not be reached / handshake failed |
| `not_found` | 404 | Unknown resource |

## 8. SSE semantics

* Endpoint: `GET /api/v1/events/stream` (defender) and `GET /api/v1/runs/stream` (simulator).
* Content type: `text/event-stream`; each message has `id: <sequence>`, `event: <type>`, `data: <json>`.
* On connect, the client **first** fetches the corresponding snapshot endpoint, then subscribes; the server sends a `hello` event with the current sequence so the client can detect gaps.
* Reconnect: client re-fetches the snapshot and resubscribes (EventSource auto-reconnect plus explicit snapshot recovery). The server keeps a bounded replay buffer (last 500 events) and sends `resync` if the requested `Last-Event-ID` is no longer available.
* Server-side subscription buffers are bounded (64 events per subscriber); overflow emits a `lag` event instructing the client to re-snapshot.
