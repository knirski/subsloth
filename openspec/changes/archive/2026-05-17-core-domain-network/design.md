## Context

The imported baseline requires Kotlin sealed ADTs, exhaustive `when` expressions, typed errors using Kotlin `Result<T>` plus sealed error hierarchies, and tagless-final-inspired capability ports without full HKT machinery. This change turns those broad requirements into the executable core/network slice.

## Goals / Non-Goals

Goals:

- Build pure Kotlin domain models and policies that have no Android framework dependencies.
- Represent expected failures with typed errors instead of nullable sentinels or unchecked exceptions.
- Keep generated DTOs and Retrofit/OkHttp concerns inside `:core:network`.
- Enforce Kodi-compatible request metadata and safe network cadence.

Non-goals:

- Do not implement Compose UI, Room, DataStore, Android Keystore, Media3, WorkManager, or filesystem behavior here.
- Do not implement server-side library writes unless Kodi parity proves exact endpoint behavior.

## Decisions

- Keep sealed interfaces/classes and value classes for identifiers and domain states because invalid media/library/playback states must be hard to represent.
- Prefer Kotlin `Result<T>` at module/API boundaries with a canonical adapter: sealed domain errors map to `Result.failure` via a single wrapper exception type, and consumers unwrap that wrapper back into sealed domain errors for recoverable failures. This keeps error composition explicit and testable without bringing in Arrow.
- Reserve nullable values for DTO/framework boundaries and normalize optional semantics in domain-level value objects.
- Expose small `suspend` capability ports for catalog, library, credentials, downloads, playback, clock, and connectivity to provide testable dependency seams without `Kind<F, A>` complexity.
- Apply explicit retry budget policies around retryable Media network operations and URL refreshes so `Retry-After` behavior and limits remain deterministic.

## Risks / Trade-offs

- Too many tiny model types can slow implementation -> group types by bounded responsibility and keep API examples in tests.
- Media DTOs may change as discovery tightens -> keep DTOs isolated inside `:core:network` and cover mapper tests narrowly to reduce blast radius.
- Overzealous retry behavior could resemble request storms -> centralize request cadence tests and keep retries bounded.

## Migration Plan

1. Add failing tests for domain policies and network request rules.
2. Add `:core:model` immutable domain types.
3. Add `:core:domain` policies and ports.
4. Add `:core:network` client, mappers, interceptors, and request policy.
5. Verify core/domain/network tests.

## Open Questions

- Exact remote filter/sort and server library mutation support remains gated by Kodi parity evidence.
- Exact stream/download/subtitle URL behavior remains dependent on discovery fixtures.
