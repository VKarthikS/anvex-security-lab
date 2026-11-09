# ANVEX — Portfolio & Interview Notes

## 20-second elevator pitch

“ANVEX is my Java-based authentication threat detection and response lab. A separate simulator generates controlled HTTP login traffic, while the defender independently classifies behavioral patterns using rolling windows. It records explainable evidence and can enforce rate limits or temporary source blocks. I built the project to demonstrate defensive security engineering and to compare the measured results with protection on and off.”

## Technical interview talking points

**How do you avoid trusting the simulated attack label?** The Java defender evaluates incoming request timestamps, account-level failures and unique usernames. The scenario label and run ID are metadata only.

**Why not just use `ConcurrentHashMap`?** A detection decision reads counters, increments state, evaluates thresholds and may trigger evidence/protection. The combined operation needs per-source synchronization; an atomic map entry alone would not make the sequence atomic.

**What are the false positives?** Shared public/NAT IP addresses may contain legitimate users; rapid login attempts may be benign automation. Source-IP blocking trades convenience for protection. Suspected spraying cannot prove repeated passwords without recording secrets, which ANVEX avoids.

**How do you measure a defense?** Compare two completed runs on the same target, same scenario, same username set, rate, duration, request cap and concurrency. Examine defender-received versus authentication-admitted requests, rather than claiming that inbound traffic disappears. ANVEX explicitly distinguishes HTTP 429 responses from transport errors.

**What would you improve next?** Capture protection/policy snapshots with each run, extend bounded, request-ID-based run evidence correlation beyond the 20 recent records, test noisy legitimate baselines, add authenticated analyst access and TLS for non-lab deployment, and benchmark false positives/latency.

## Résumé bullet candidates

Only use work you personally implemented or can explain and verify. Avoid claiming sole authorship for team components.

- Developed a **Java 21/Spring Boot authentication-defense lab** with a controlled HTTP traffic simulator, independent rolling-window detection, evidence recording, and application-layer source protections.
- Implemented or extended **explainable detection and response workflows**, including configurable rules, rate limiting, bounded event processing, and live React/TypeScript dashboards.
- Built **reproducible OFF/ON experiments** with server-measured outcomes, transport-error separation, and safeguards against comparing incompatible workloads.

**Stack:** Java 21 · Spring Boot · React · TypeScript · H2 · Flyway · JUnit 5 · SSE · GitHub Actions

## GitHub profile description

> ANVEX is a blue-team engineering lab that simulates authentication abuse, detects suspicious behavior independently, records evidence, and evaluates defensive controls using real HTTP traffic.

## Attribution and accuracy

ANVEX began as a student team project. Credit collaborators and avoid implying all modules were independently authored by one individual. The strongest answer in an interview is a concrete architectural decision you can navigate to in the source and defend under questioning.
