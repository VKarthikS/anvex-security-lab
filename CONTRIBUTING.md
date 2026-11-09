# Contributing

ANVEX welcomes issues and review suggestions focused on defensive engineering, correctness, evidence quality, testing, and usability. Keep the simulator bound to explicitly configured ANVEX test applications. No functionality for scanning arbitrary internet hosts, importing credential dumps, or bypassing authorization should be introduced.

## Development checks

```bash
npm ci --prefix frontend
npm run typecheck --prefix frontend
npm run build --prefix frontend
bash ./mvnw -B clean verify
```

Add deterministic tests for any new detection/protection rule, document changed thresholds, and distinguish actual measured results from proposed improvements. Avoid committing generated static frontend bundles, Maven outputs, databases, or local secrets.

## Credit

The original implementation was a student team effort. Contributors should be credited accurately, with author details confirmed before publishing.
