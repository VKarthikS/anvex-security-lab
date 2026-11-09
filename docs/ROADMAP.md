# ANVEX roadmap

This roadmap distinguishes existing implementation from independently validated operation and planned work.

## Existing in the supplied source

- Java defender and simulator, controlled scenarios, independent rule-based detection
- Protection ON/OFF, token bucket, temporary source blocks, evidence/alerts
- H2/Flyway persistence, API endpoints, two React dashboards, original screenshots
- Simulator Mission Control view and same-origin read-only defender overview bridge (new integration)

## Still to verify

- Run clean-room cross-platform build, Java tests and frontend build in GitHub Actions
- Exercise Mission Control end-to-end with live defender and simulator using freshly built frontend
- Exercise two physical machines and document network/proxy behavior
- Review the UX at laptop and projector resolutions, and on different browsers
- Add end-to-end tests for live Mission Control overview requests and target switching (pure matcher tests cover exact ID correlation)
- Run the new comparison-integrity regression tests under GitHub Actions (local Java source compilation and five label smoke checks passed)

## Future improvements

- Expand decision correlation beyond the defender dashboard’s 20 most recent records; preserve request IDs across simulator restarts only if a justified persistence policy is designed
- Incident timeline with rule decision-to-action explanations
- Store the defender policy/protection-mode snapshot **per run** rather than only at comparison creation
- Baseline/false-positive experiments; measurement methodology and repeatable benchmarks
- Better accessibility and responsive presentation
- A short reproducible demo video with real traffic and observable evidence

No items above should be advertised as completed without corresponding reproducible evidence.
