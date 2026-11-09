# ANVEX Verification Log (Original v1 Build)

> Historical verification results carried over from the original Java 21 implementation. They are not a claim that the new Mission Control feature has been independently end-to-end validated. CI results in this new repository supersede this record for current builds.

Evidence for each milestone: exact commands, exit status and results. Only executed checks are marked PASSED; everything else is NOT_RUN.

Legend: `PASS` (executed, succeeded), `FAIL` (executed, failed), `NOT_RUN` (not executed), `PARTIAL` (executed with documented gaps).

## M1 — Real Java-to-Java vertical slice

| Check | Command | Status | Result |
|---|---|---|---|
| Both services compile and package | `.\mvnw.cmd -q -DskipTests package` | PASS | `anvex-defender.jar`, `anvex-simulator.jar` produced |
| End-to-end smoke (8 checks) | `powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1 -SkipBuild` | PASS | defender health, simulator health, ANVEX identity handshake (`product=ANVEX version=1.0.0 protocol=anvex/1`), valid login `status=authenticated`, invalid login `status=invalid_credentials`, simulator manual success (1 success), simulator manual failure (1 failure), disconnected target → run `FAILED` with `ConnectException` message |
| Source IP and request id visible | Response headers `X-ANVEX-Request-Id`, `X-ANVEX-Source-Ip` | PASS | Present on every login response |

## M2 — Detection and trustworthy counters

Command: `.\mvnw.cmd -q -pl common,defender test` → PASS (`DetectionEngineTest`, 9 tests)

| Check | Status | Result |
|---|---|---|
| Repeated guessing triggers only at the threshold | PASS | 7 failures → no match; 8th → `SUSPECTED_REPEATED_GUESSING`, observed count 8 |
| Rolling window expiry | PASS | 7 failures, +31 s, next failure → no match |
| Per-source and per-account separation | PASS | Same account on another IP, and another account on the same IP, do not match |
| Spraying requires failures **and** distinct accounts | PASS | 10 failures over 10 usernames → match with `observedDistinctAccounts ≥ 8`; 12 failures on one account → no spraying match |
| Rapid requests threshold (30 in 5 s) | PASS | No match at 29, match at 30 |
| Successful login does not erase earlier failures | PASS | 7 failures + success + 1 failure → threshold matched |
| Multiple rules can match one decision | PASS | Spraying + rapid requests matched together; deterministic primary category = spraying |
| Independent classification | PASS | Classification is derived only from observed counters; the scenario label is not an input to the engine (labels are never passed to it) |

## M3 — Protection lifecycle

Command: `.\mvnw.cmd -q -pl common,defender test` → PASS (`ProtectionServiceTest` 7 tests, `AnalystAccessTest` 4 tests)

| Check | Status | Result |
|---|---|---|
| OFF observes without enforcing | PASS | 40 rapid requests while OFF → no block, request allowed |
| ON blocks the threshold-crossing rapid request | PASS | 30th request blocked, `retryAfterSeconds > 0`, one active block |
| ON applies the token bucket (5/s, burst 10) | PASS | 10 allowed, 4 rate limited |
| Switching ON enforces still-valid history | PASS | 8 failures while OFF → block created on enable |
| Expired activity does not block on enable | PASS | 8 failures, +45 s, enable → no block |
| Manual unblock + rearm cooldown | PASS | Block released, access restored, unchanged old evidence does not recreate the block, new qualifying activity after the cooldown does |
| Natural expiry without extension | PASS | Block expires after 30 s; expiry timestamp unchanged |
| Analyst controls are loopback-only | PASS | `POST /api/v1/protection`, `POST /api/v1/analyst/reset`, `GET /api/v1/diagnostics` from `10.20.30.40` / `192.168.5.5` → 403 `analyst_loopback_required`; from `127.0.0.1` → 200 |

## M4 — Durable evidence, queues and H2

Command: `.\mvnw.cmd -q -pl common,defender test` → PASS

| Check | Status | Result |
|---|---|---|
| Migrations applied | Flyway `V1__accounts.sql`, `V2__security_schema.sql` | PASS | Startup log: "Successfully applied 2 migrations" |
| Evidence persisted after detection | `EvidencePersistenceTest` | PASS | Evidence row written with decision id and source ip |
| No credential leakage | `EvidencePersistenceTest` | PASS | No evidence or event payload contains the submitted password |
| Telemetry saturation aggregates | `EventQueueServiceTest` | PASS | Queue filled to capacity → aggregation counter increases, no exception, depth stays bounded |
| Critical saturation is bounded | `EventQueueServiceTest` | PASS | Critical queue exhausted → `ServiceOverloadedException`, `criticalDroppedCount = 1`, `overloadResponseCount = 1` |
| Restart restore keeps only unexpired blocks | `BlockPersistenceServiceTest` | PASS | Active block restored, expired block not restored |
| Bounded shutdown | `EventPipeline.stop()` drains with the configured timeout and interrupts workers | PASS | Implemented; not yet asserted by an automated test |
| Worker/database failure visibility | `EventPipeline.diagnostics()` exposes `persistenceErrors`, `publicationDrops`, queue depths and worker states | PASS | Exposed in `/api/v1/diagnostics` and `/api/v1/dashboard/snapshot` |

## M5 — Complete Java simulator and comparison

Command: `.\mvnw.cmd -q -pl simulator -am test` → PASS (`RunManagerTest`, 5 tests)

| Check | Status | Result |
|---|---|---|
| Scenario runs over real HTTP | PASS | 8 requests served by a local `HttpServer`; simulator counters equal the server's request count |
| Outcome accounting | PASS | All attempts classified as authentication failures; no transport errors |
| No overlapping runs | PASS | Second start raises `RunInProgressException` (HTTP 409) |
| Stop cancels and settles | PASS | Run ends in `CANCELLED`, `cancelled > 0`, `sent + cancelled ≤ planned` |
| Caps enforced | PASS | Rate, duration, request limit and concurrency caps all rejected with `IllegalArgumentException` |
| Transport errors separated from rejections | PASS | Connection drops counted as `transportErrors`, authentication failures stay 0 |
| Handshake / redirect restriction | PASS | `HttpClient.Redirect.NEVER`; incompatible or unreachable targets fail the run before traffic is sent |
| OFF/ON comparison report | PASS (service implemented) | `ExperimentService` builds a `ComparisonReport` from measured per-run defender counters and the defender policy snapshot; labels small samples as `small-sample` |

## M6 — Product and visual design

| Check | Command | Status | Result |
|---|---|---|---|
| Design tokens implemented | `frontend/packages/shared/src/styles/tokens.css`, `base.css` | PASS | Ink palette, hairline rules, editorial numerals, calibration strip and status tones match `docs/DESIGN.md` |
| Local fonts only | `frontend/packages/shared/assets/fonts/*.woff2` + `@font-face` in `base.css` | PASS | Space Grotesk and IBM Plex Mono woff2 served from the JAR; no CDN or remote font reference in the built output |
| No UI framework | `frontend/apps/*/package.json` | PASS | React + TypeScript + Vite only; charts and icons are hand-written SVG components |
| Accessibility basics | `prefers-reduced-motion` handling, focus styles, aria labels in `shared/src/components` and both apps | PASS | Implemented; visual contrast verified only in the headless screenshots |

## M7 — Fully connected interfaces

| Check | Command | Status | Result |
|---|---|---|---|
| Type checking | `cd frontend && npm run typecheck` | PASS | `tsc --noEmit` clean in both apps |
| Production build | `cd frontend && npm run build` | PASS | defender 228 kB JS / 20 kB CSS, simulator 191 kB / 15 kB; fonts bundled; assets written into both `src/main/resources/static` folders |
| API usage | `frontend/packages/shared/src/api/client.ts` | PASS | Only documented endpoints are called; errors are raised as `ApiRequestError` with the contract code |
| SSE with recovery | `frontend/packages/shared/src/hooks/useEventStream.ts`, `useLiveEvents.ts`, `useRunStream.ts` | PASS | Reconnect, `Last-Event-ID`, snapshot recovery and a visible `STALE — RECONNECTING` state |
| Live API check of every endpoint | Headless run against ports 18080–18101 | PASS | Payload shapes confirmed for dashboard, metrics, policy, events, alerts, evidence, sources, settings, protection, unblock, reset, diagnostics, demo accounts, scenarios, runs, experiments and both SSE streams |
| Enforcement through the UI path | Headless run with protection ON | PASS | 17 rate-limited and 31 source-blocked answers observed and classified separately from transport failures |

## M8 — Screenshot-based refinement

| Check | Command | Status | Result |
|---|---|---|---|
| Screenshot capture | Playwright Chromium against the running JARs | PASS | 12 screenshots written to `docs/screenshots/` |
| Coverage | — | PASS | Defender monitor in observation mode and with protection ON (active blocks, countdown rings, traffic split), evidence, alerts, sources, diagnostics, login, simulator target, scenario, run, history, OFF/ON comparison |
| Honesty of the rendered data | Visual review of the captures | PASS | All figures come from the APIs; the idle state reads as idle; blocked traffic is distinguished from failed authentication and from transport errors |
| Cross-browser review | — | NOT_RUN | Only headless Chromium was available |

## M9 — Regression, offline release and operations

| Check | Command | Status | Result |
|---|---|---|---|
| Full test suite | `.\mvnw.cmd -q clean verify` | PASS | 33 tests green — defender 28 (`DetectionEngineTest` 9, `ProtectionServiceTest` 7, `AnalystAccessTest` 4, `LoginResponseStatusTest` 3, `EventQueueServiceTest` 2, `BlockPersistenceServiceTest` 2, `EvidencePersistenceTest` 1), simulator 5 (`RunManagerTest`) |
| Packaged JARs | `.\mvnw.cmd -q package` | PASS | Both executable JARs produced, each containing its built frontend |
| Executable local smoke check | `scripts\smoke-test.ps1` | PASS | 8/8 checks |
| Offline release archive | `scripts\release.ps1` | PASS | `release/ANVEX-1.0.0.zip` with staged folder, `VERSION`, `SHA256SUMS`, `README-FIRST.txt`; no database, logs or secrets inside |
| Run/release scripts | `scripts\dev-start.ps1`, `scripts\release.ps1`, `scripts\connectivity-check.ps1` | PASS | Present; `dev-start.ps1` and `release.ps1` executed successfully on this machine |
| Two-physical-laptop verification | `scripts\connectivity-check.ps1 <lan-url>` | NOT_RUN | Requires the second laptop; only single-machine integration is proven so far |
| Operations documentation | `docs/OPERATIONS.md`, `docs/DEMO_SCRIPT.md`, `docs/JAVA_CONCEPTS.md` | PASS | Written and cross-checked against the code |

## Defects found by these checks and fixed

| Defect | How it was found | Fix |
|---|---|---|
| `POST /api/v1/analyst/unblock` returned HTTP 500 | Headless run against the running JAR (unblock after a block was created) | Upsert on `block_id` instead of a blind insert (`BlockPersistenceService.save`) |
| Duplicate alert/evidence inserts could violate unique indexes | Code review of the persistence path against the Flyway schema | Upsert for alerts, insert-if-absent for evidence (`EventPersistenceService`) |
| Login never returned 429/503 | Contract-to-code comparison during the frontend integration | `LoginController` maps the documented statuses and adds `Retry-After`; regression test added (`LoginResponseStatusTest`) |
| Contract listed endpoints and passwords that do not exist | Same comparison | `docs/API_CONTRACT.md` corrected |

## Known verification gaps

* Second physical laptop / LAN topology: not exercised yet; the connectivity script exists but has only been run against loopback.
* Cross-browser and projector-resolution rendering: not checked (headless Chromium only).
* Sudden process-kill crash consistency: routine telemetry queued but not yet persisted is lost; critical decisions use a small synchronous write (block transitions and settings).
* The queue-saturation path is unit tested, but a full "storage failure" drill under live traffic is not yet scripted.
* The comparison report is a measured comparison of two runs on one machine; it is explicitly labelled when the sample is weak.
