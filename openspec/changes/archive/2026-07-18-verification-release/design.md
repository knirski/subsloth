## Context

CI/release infrastructure landed in `release-and-ci-foundation`. This change is the final verification gate. Each earlier change has its own scoped tests; this change adds the cross-cutting verification that requires multiple features to be present.

## Goals / Non-Goals

Goals:

- Verify domain, network, persistence, media, UI, accessibility, TV focus, and screenshot behavior end-to-end.
- Run baseline profiles and macrobenchmarks for startup, scroll, detail open, and playback start.
- Document manual device acceptance for the required device targets.

Non-goals:

- Do not redefine CI workflow, release-please, version.txt, or CHANGELOG — those live in `release-and-ci-foundation`.
- Do not add new feature behavior — verification only.

## Decisions

- Use Roborazzi for screenshot tests because it runs offline in unit-test JVM.
- Use macrobenchmark + baseline profile for startup and key flows.
- Treat manual device acceptance as a documented checklist, not an automated gate.
- Roborazzi is the default screenshot mechanism (offline, JVM). On-device screenshots are only used for TV focus/rendering cases Roborazzi cannot represent.

## Risks / Trade-offs

- Some UI/TV focus tests need a real device or emulator — document them as manual acceptance.
- Screenshot tests can leak sensitive state — credential-sensitive screens must use fake credentials/redacted state.

## Migration Plan

1. Add architecture and unit tests across modules.
2. Add Compose UI, TV focus, accessibility, and Roborazzi screenshot tests.
3. Add baseline profile generation and macrobenchmarks.
4. Add `docs/testing/device-acceptance.md`.
5. Run final full-app verification before archiving the change.

## Open Questions

- None at this layer; all open questions belong to feature changes or `release-and-ci-foundation`.
