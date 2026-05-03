# Plan: Testing Improvements

Primary objective: maximise test coverage within reason, without excessive complexity or build delay.

## Completed

- `docs/testing.md` — comprehensive testing documentation replacing the previous 245-line version. Covers test frameworks, module inventory, architecture (abstract base class hierarchy), assertion patterns, full Surefire/Failsafe configuration, parallelization, test data, coverage tooling gaps, disabled tests, anti-patterns, Docker image variant coverage, Helm feature coverage gaps, and CI execution.
- `docs/plans/testing-improvements.md` — standalone improvement plan (Phases 1-5)
- `docs/README.md` — updated testing.md description in the index table

## Outstanding

See `docs/plans/testing-improvements.md` for the full plan. Summary of phases:

| Phase | Focus | Timeline |
|-------|-------|----------|
| Phase 1 | Measure — JaCoCo, XML reports, CI analytics | ~1 day |
| Phase 2 | Quick wins — schema serializers, ClientConfiguration, WebSocket client/registry | ~2-3 days |
| Phase 3 | Structural — Surefire parallelism, test categories, split mega-methods | ~2-3 days |
| Phase 4 | Java coverage expansion — TLS, validators, mappers, listeners | ~5-8 days |
| Phase 5 | Container/Helm — existing tests in CI, Kind→K3d, Docker + Helm coverage expansion | ~3-5 days |
