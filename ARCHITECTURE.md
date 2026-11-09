# ANVEX Architecture

ANVEX is a controlled two-service cybersecurity lab where a Java **defender** observes, detects, explains and (optionally) blocks suspicious authentication activity, and a Java **simulator** generates controlled traffic against the defender over HTTP/JSON.


## 1. Module boundaries

| Module | Responsibility | Runtime |
|---|---|---|
| `common/` | Shared protocol DTOs and error contracts (records + Jackson annotations). No detection logic, no scenario labels used for classification. | library jar |
| `defender/` | Authentication, detection engine, protection enforcement, evidence generation, event queue + workers, H2 persistence, metrics, analyst controls, APIs, defender web UI. | Spring Boot app, port 8080 |
| `simulator/` | Scenario strategies, run lifecycle/state machine, Java `HttpClient`, target handshake, progress/metrics, comparison reports, simulator web UI. | Spring Boot app, port 8081 |
| `frontend/` | npm workspace: `packages/shared` (design system, identity, API client), `apps/defender`, `apps/simulator` (React + TypeScript + Vite). Build output is copied into each Spring Boot app's `src/main/resources/static`. | build-time only |
| `scripts/` | Build, run, release, connectivity helpers (PowerShell + POSIX). | build-time only |
| `docs/` | Architecture, API contract, verification evidence, design direction, Java concepts, operations, demo script. | documentation |

The frontend never classifies traffic, never generates attack traffic and never manufactures counters: it renders Java APIs only.

## 2. Java request lifecycle (defender, synchronous decision path)

`POST /api/v1/auth/login`

1. **Ingress** — `LoginController` assigns `requestId` (UUID) and `serverTime`; remote socket IP is read from `HttpServletRequest.getRemoteAddr()` (forwarded headers are not trusted by default).
2. **Incoming counter** — `DetectionEngine.recordIncoming(...)` increments the source's rolling request counter under the engine's lock and evaluates the *request-rate* rule. This happens before credential verification.
3. **Protection gate** — if protection is ON: an active `source_blocks` row for the source short-circuits with `429 source_blocked`; the per-source token bucket (`5 req/s`, burst `10`) may short-circuit with `429 rate_limited` plus `Retry-After`.
4. **Admission** — admitted requests increment `requestsReachingAuthentication` and are handed to `AuthenticationService` (BCrypt verification against seeded dummy accounts).
5. **Result + failure rules** — successful/failed result is recorded; failure counters are updated and *failure-based* rules (repeated guessing, spraying) are evaluated. A successful login never erases earlier failures.
6. **Evidence + restriction** — matched rules produce an immutable `Evidence` snapshot and (when protection is ON) a 30-second source block. The threshold-crossing failure itself reaches authentication; later requests are rejected.
7. **Publish** — immutable events are offered to a bounded `BlockingQueue`; persistence and live UI delivery happen in background workers and never block the decision path.

Lock order (documented, always acquired in this order): **settings snapshot → source lock → rule/state locks**. Database writes and SSE publication never happen while a detection lock is held.

`ConcurrentHashMap` alone is insufficient: a threshold decision spans counter read, counter update, rule match, block creation and evidence emission. `DetectionEngine` therefore uses a per-source `ReentrantLock` plus an `AtomicReference` settings snapshot, so a multi-step decision is atomic per source while different sources proceed concurrently.

## 3. Detection rules

Rolling windows are evaluated on server timestamps using a `Clock` abstraction (injectable for deterministic tests).

| Rule id | Category | Condition (defaults) |
|---|---|---|
| `repeated-guessing` | `SUSPECTED_REPEATED_GUESSING` | ≥ 8 failed authentications for one (source, account) within 30 s |
| `password-spraying` | `SUSPECTED_PASSWORD_SPRAYING` | ≥ 10 failures spanning ≥ 8 distinct normalized usernames from one source within 60 s |
| `rapid-requests` | `SUSPECTED_RAPID_REQUESTS` | ≥ 30 incoming login requests from one source within 5 s |

Semantics: window is half-open `[now - window, now]`; the threshold-crossing sample **is included** in the count; "within W seconds" means `eventTime >= now - W` and `eventTime <= now`. The category is inferred from observed counters only — a submitted scenario/run label is never consulted. Multiple rules may match simultaneously; the deterministic priority is `repeated-guessing > password-spraying > rapid-requests` (documented and tested).

Spraying is always labelled *suspected*: without recording password material, spread failures cannot prove password reuse.

## 4. Protection

* Fresh installation starts **OFF** (conspicuously displayed); the setting is persisted and restored on restart.
* OFF = observation only: detections and evidence are still produced, but no rate limiting or blocking is enforced.
* ON = active source block (30 s) and per-source token bucket (5 req/s, burst 10).
* Switching ON re-evaluates still-valid rolling activity immediately and emits `PROTECTION_ENABLED_ENFORCEMENT` with the policy snapshot. Expired activity can never create a new block.
* Switching OFF suspends enforcement but keeps audit history; existing block expiry instants are never extended.
* Manual unblock releases the block and records an analyst action. Rearm policy: a released block cannot be re-created by the *same* evidence; only newly qualifying activity after release (or after natural expiry) can create a new block.
* No account lockout in v1. Blocking is source-scoped, so a legitimate user behind the same IP is affected as well.
* Analyst endpoints are reachable only from a validated loopback remote address (`127.0.0.0/8`, `::1`); `X-Forwarded-For` is ignored. The simulator therefore cannot switch protection off or reset defender state.

## 5. Evidence and event pipeline

* Every decision stores: decision id, source, affected usernames, server times, triggering event references, observed counts/rates, window bounds, matched rules and exact thresholds, policy version + snapshot, protection mode, action (observation-only/enforced), reason, block expiry, and later recovery/analyst actions.
* A real bounded `BlockingQueue` (default capacity 10 000, configurable with validation) receives immutable events from the request path.
* Named worker threads (`anvex-event-persist-*`, `anvex-event-publish-*`) consume the queue: persistence workers batch inserts; publish workers fan out to SSE subscribers.
* **Saturation policy:** a separate bounded *critical* queue carries decisions, alerts and block transitions. If the critical queue is also full, the service returns a bounded overload error (`503`, `status=service_overloaded`) and increments an overload counter; enforcement stays active. Low-priority telemetry may be aggregated (exact counters retained, explicit coverage/gap markers) or dropped with an explicit drop counter — never silently.
* Diagnostics expose queue depth/capacity, aggregation and drop counters, persistence lag and worker health.
* Crash consistency: asynchronous events queued but not yet persisted are lost on a process kill. Block transitions are written with a small synchronous critical write before the triggering operation is acknowledged.

## 6. Persistence

* Embedded H2, file-backed, explicit configurable data directory (`ANVEX_DEFENDER_DATA_DIR`, default `./data`), never exposed to the LAN (H2 console disabled).
* Flyway migrations own the schema: `accounts`, `security_events`, `alerts`, `evidence`, `detection_observations`, `settings_versions`, `analyst_actions`, `source_blocks`.
* Passwords are stored as BCrypt hashes (salted by BCrypt itself). Submitted passwords, request bodies containing passwords and password-derived fingerprints are never stored or logged.
* On restart: settings version and unexpired source blocks are restored; valid recent detection history is reconstructed from persisted observations, and any known gaps are exposed via diagnostics.

## 7. Simulator

* Scenario strategies implement `ScenarioStrategy` (`repeated-guessing`, `password-spraying`, `rapid-requests`, `manual-login`).
* Defaults: repeated guessing 2 req/s × 12 s; spraying 2 req/s × 12 s across ≥ 12 seeded accounts; rapid requests 12 req/s × 10 s; concurrency 4; duration cap 120 s; request cap 1 000; rate cap 20 req/s; concurrency cap 8; connect timeout 2 s; request timeout 3 s.
* Scheduling uses a bounded `ScheduledExecutorService` + bounded in-flight semaphore; requests are never scheduled thousands of futures upfront. No hidden retries.
* Counters are explicit: planned, sent, completed, cancelled, transport errors, auth success/failure, rate limited, source blocked, invalid/overload, latency samples, achieved rate.
* The simulator performs an ANVEX identity/version handshake before traffic and refuses redirects (`HttpClient.Redirect.NEVER`).
* OFF/ON comparison: identical scenario parameters, fresh rolling baseline per run, retained reports, frozen rule settings snapshot, reductions computed from measured values only (no fixed percentage claims).

## 8. Live updates and APIs

* HTTP/JSON everywhere; SSE (`text/event-stream`) for defender events and simulator run progress.
* Every SSE stream is snapshot-first: clients fetch `GET /api/v1/.../snapshot` and then subscribe; reconnect re-fetches the snapshot, so no events are assumed to survive a disconnect.
* API version `v1`; see `docs/API_CONTRACT.md`.

## 9. Frontend stack and design decisions

React 18 + TypeScript + Vite, no UI component library. Shared package holds the ANVEX design tokens, typography, motion primitives and the API client. Fonts and icons are bundled locally (no CDN, no remote fonts, no analytics). Charts are hand-built SVG so the visual language stays bespoke. See `docs/DESIGN.md`.

```
frontend/
  packages/shared/     design tokens, base CSS, shared components, typed API client, SSE hooks
  apps/defender/       monitor, sources, evidence, alerts, settings, diagnostics, login
  apps/simulator/      target, scenario, run, history and comparison
```

* `packages/shared/src/api/client.ts` is the only place that talks to the services. It raises `ApiRequestError` carrying the contract error code, so a view can distinguish "analyst controls need a loopback caller" from "the service is unreachable".
* `packages/shared/src/hooks/useEventStream.ts` implements the SSE contract: subscribe after the snapshot, track the last sequence, re-fetch the snapshot after a reconnect and surface a `STALE — RECONNECTING` state instead of animating invented activity.
* Vite writes each app into `defender/src/main/resources/static` and `simulator/src/main/resources/static`, so the executable JAR ships its UI and the release needs only a Java 21 runtime.

## 10. Release layout

`scripts/release.ps1` (and `release.sh`) build the JARs and assemble `release/ANVEX-<version>/`:

```
defender/anvex-defender.jar      simulator/anvex-simulator.jar
docs/                            README.md  ARCHITECTURE.md
scripts/                         VERSION    SHA256SUMS    README-FIRST.txt
```

No database, log or secret is packaged; runtime data is created next to the JARs in `data/` and `logs/`. The ZIP is the only artefact that has to be copied to the second laptop.

## 11. Known tradeoffs / limitations

* Source-level blocking can affect legitimate users sharing an IP (accepted, documented).
* Suspicious activity is behavioural suspicion, never proof of compromise.
* Async queue cannot survive a sudden process kill for routine telemetry; critical decisions use a small synchronous write.
* The H2 console is disabled; database inspection is done through the analyst API or the file directly while the service is stopped.
* Local two-app development shares `127.0.0.1` for both roles, which differs from the two-laptop topology where the simulator sees the defender's LAN address.
