# Java Concepts Behind ANVEX

A short guide for learners: which Java/Spring concepts the product uses, where they live, and why they are there. Every reference points at real source files.

## 1. Records as immutable protocol objects

`common/src/main/java/com/anvex/common/` defines the wire format between the two laptops as Java **records**:

```java
public record LoginRequest(String username, String password, String runId) { }
```

A record gives you a final class, an all-arguments constructor, accessors (`username()`), `equals`, `hashCode` and `toString` in one line. Because the objects are immutable they can be shared between the HTTP thread, the detection engine and the event workers without defensive copying.

Jackson (`com.fasterxml.jackson`) serialises records directly, and `@JsonInclude(JsonInclude.Include.NON_NULL)` keeps empty fields out of the JSON (`LoginResponse.java`).

**Why it matters:** the API contract in `docs/API_CONTRACT.md` is literally the list of these records.

## 2. Enums and exhaustive `switch` expressions

Detection categories are modelled as an **enum**:

```java
public enum DetectionCategory {
    NONE,
    SUSPECTED_REPEATED_GUESSING,
    SUSPECTED_PASSWORD_SPRAYING,
    SUSPECTED_RAPID_REQUESTS;

    public int priority() {
        return switch (this) {
            case SUSPECTED_REPEATED_GUESSING -> 0;
            case SUSPECTED_PASSWORD_SPRAYING -> 1;
            case SUSPECTED_RAPID_REQUESTS -> 2;
            case NONE -> 3;
        };
    }
}
```

Java's **switch expression** over an enum is exhaustive at compile time: when a new category is added, the compiler refuses to compile any `switch` that forgot to handle it. The same pattern is used for the rule priority (the primary category is decided deterministically) and for the event types in `SecurityEvent`.

A dedicated exception type (`AnalystGuard.AnalystLoopbackRequiredException`) is mapped by the central `@RestControllerAdvice` to the documented `analyst_loopback_required` code, so the closed set of failure reasons is visible in one place instead of being scattered across the controllers.

## 3. Interfaces + Spring beans: dependency injection without `new`

Every rule is a Spring bean implementing `DetectionRule`:

```java
public interface DetectionRule {
    String id();
    Optional<RuleMatch> evaluate(RuleContext context);
}
```

`DetectionEngine` receives the list of rules injected by Spring and evaluates them in priority order. Adding a fourth rule is a new class plus a bean — no changes to the engine.

Spring Boot's stereotype annotations (`@Service`, `@Component`, `@Repository`, `@RestController`) are the mechanism behind injection; the constructor of a bean receives its collaborators as parameters (`LoginPipeline` has nine).

## 4. Concurrency: locks, concurrent maps, atomics

The defender handles many HTTP requests at once, and each request updates shared counters.

* **`ReentrantLock` per source** — `DetectionEngine` keeps a lock per source IP so that two requests from the same source cannot interleave inside a detection window. This is a *correctness* requirement: read counters, decide, write counters must be atomic per source.
* **`ConcurrentHashMap`** — the rule state itself (windows, counters per source/account) lives in concurrent maps.
* **`AtomicLong` / `LongAdder`-style counters** — `DefenderMetrics` counts requests, admissions, successes, failures and detections without locks.

Look at `defender/src/main/java/com/anvex/defender/detection/DetectionEngine.java` and `defender/src/main/java/com/anvex/defender/metrics/DefenderMetrics.java`.

## 5. Bounded queues and back-pressure

`EventQueueService` (`defender/.../events/EventQueueService.java`) owns two `ArrayBlockingQueue`s:

* a **telemetry** queue for routine events (bounded, aggregated when full);
* a **critical** queue for decisions, alerts and blocks (small, never dropped silently).

When the critical queue is full the publisher throws `ServiceOverloadedException`, which the login pipeline converts into `status = service_overloaded` (HTTP 503). This is the classic trade-off: drop routine telemetry under pressure, but refuse to work when the important path is saturated.

## 6. Worker threads with explicit lifecycles

`EventPipeline` (`defender/.../events/EventPipeline.java`) starts **named worker threads** (`anvex-event-persist-N`, `anvex-event-publish-1`). The persistence workers take batches of telemetry from the bounded queue and write them to H2; the publication worker fans events out to the SSE hub. Because the threads are named, `Diagnostics` can report their state and the operating-system tools can find them.

The threads are started and stopped explicitly by the Spring lifecycle (`@PostConstruct` / `@PreDestroy`), so in-flight events are flushed on shutdown instead of being lost with the process.

## 7. Virtual threads and the JDK HTTP client

The simulator uses the modern JDK HTTP stack (`simulator/.../client/DefenderClient.java`):

```java
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/v1/auth/login"))
        .timeout(Duration.ofSeconds(3))
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

* `HttpClient` with `Redirect.NEVER` — a simulation can never be silently redirected to another host.
* explicit connect/request timeouts — a hung defender becomes a *transport failure*, never an infinite hang;
* `runId` travels as untrusted metadata: the defender groups counters by it but never uses it for detection decisions.

`RunManager` executes the scenario with a bounded executor and a scheduler, honouring the rate, duration, request-limit and concurrency caps.

## 8. Persistence with JPA, Flyway and H2

* **Flyway** owns the schema (`defender/src/main/resources/db/migration/V1__accounts.sql`, `V2__security_schema.sql`). Hibernate never creates tables — the schema is versioned SQL that can be reviewed.
* **Spring Data JPA** repositories (`persistence/…Repository.java`) expose typed queries such as `findByState`, `findTop100ByOrderByLastSeenDesc`.
* **H2 in file mode** keeps the data next to the JAR (`./data`), so a restart keeps the history — and the database is never copied to Laptop 2.
* **Upsert instead of blind insert**: the unique indexes on `alert_id`, `decision_id` and `block_id` mean a second write for the same row must update it. `BlockPersistenceService.save` and `EventPersistenceService` therefore look the row up first (a defect corrected during development).

## 9. Validation and error handling

* Bean Validation (`jakarta.validation`) on request bodies: `@Valid`, `@NotBlank`, `@Size`.
* A central `@RestControllerAdvice` (`api/ApiExceptionHandler.java`) turns exceptions into the shared `ApiError` shape (`code`, `message`, `requestId`, `serverTime`, `retryAfterSeconds`).
* The simulator distinguishes three outcomes that are easy to confuse: a defender **rejection** (429/503 with a documented body) versus a **transport failure** (exception) versus a normal **authentication failure**.

## 10. Server-Sent Events instead of WebSockets

Both services push live updates with SSE (`text/event-stream`): the browser gets one-way updates over plain HTTP, with automatic reconnect in `EventSource`. Each message carries an `id` (sequence) so the server can replay a bounded buffer, and the client re-fetches a snapshot after a reconnect to close any gap.

## 11. The frontend is only a viewer

The React apps (`frontend/`) render JSON and send commands:

* `packages/shared/src/api/client.ts` — a typed client that raises `ApiRequestError` carrying the contract error code;
* `packages/shared/src/hooks/useLiveEvents.ts` and `useRunStream.ts` — SSE with reconnect and snapshot recovery;
* every number on screen comes from a Java endpoint. When the stream is down the UI says **STALE — RECONNECTING** instead of animating invented activity.

## 12. Build and release

* **Maven Wrapper** (`mvnw`, `mvnw.cmd`) pins Maven 3.9.9 — no Maven installation required.
* **npm workspaces** build the two frontends; Vite writes the bundles into `src/main/resources/static`, so the Spring Boot JAR ships the UI.
* `scripts/release.ps1` / `release.sh` produce a versioned ZIP with SHA-256 checksums that needs only a Java 21 runtime.

## 13. Concepts deliberately not used

* No reflection-based magic beyond Spring/Jackson (the code stays greppable).
* No global mutable singletons; state lives in services with explicit lifecycles.
* No `Thread.sleep` in the request path; timings use `Clock` so tests can control time (`defender/src/test/java/com/anvex/defender/support/MutableClock.java`).
