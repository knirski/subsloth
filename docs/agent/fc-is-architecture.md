# FC/IS Architecture: Agent Instructions

This document describes the Functional Core / Imperative Shell architecture for the `subsloth` Android project. It is the canonical agent reference for how code is partitioned, how modules depend on each other, and what patterns are enforced at build time.

## Overview

FC/IS separates pure transformations (core) from I/O and orchestration (shell). Pure code lives in JVM-only modules with no Android framework dependencies. Side-effectful code lives in Android modules that implement ports defined by the core.

Calling a function twice with the same args always returns the same result? It belongs in the core. Does it read a file, write to the network, print to stdout, or call an external process? It belongs in the shell.

| Layer | Modules | Side effects | Android deps |
|---|---|---|---|
| **Core** | `:core:model`, `:core:domain` | none | none (JVM-only) |
| **Shell** | `:core:network`, `:core:database`, `:core:media`, `:core:preferences`, `:feature:*`, `:app` | file, network, I/O, platform | yes |

## Sealed ADTs

Follow `docs/codestyle.md` §2 for sealed type conventions: `sealed interface` with `data object` / `data class` branches, exhaustive `when`, three-site rule (variant declaration, classifier, parser/factory), one file per type.

See `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt` for the project's sealed error hierarchy: `DomainError` branches into `AuthError`, `PaymentLimitError`, `MediaError`, `DownloadError`, `QualityError`, `DecodeError`, `NetworkError`, and `LibraryError`.

## Pure Functions

Pure function rules follow `docs/codestyle.md` §4: parameters in, return value out, no mutable shared state, infectious side effects isolated at the shell boundary. Impure I/O goes in Android modules or behind port interfaces.

## Port / Adapter Pattern

Domain use cases depend on focused `suspend` capability ports, not on concrete implementations. Ports are `interface` definitions in `core/domain/src/main/kotlin/net/subsloth/core/domain/port/`. Adapters (implementations) live in the shell.

```kotlin
// core/domain/port/CatalogPort.kt (pure interface, no implementation details)
interface CatalogPort {
    suspend fun listCatalog(): Result<List<Media>>
    suspend fun getDetails(id: Media.MediaId): Result<MediaDetails>
}
```

Adapters implement these interfaces with actual I/O. Tests provide stub implementations directly.

DTOs generated from OpenAPI or written by hand stay inside `:core:network`. Mappers in `core/network/src/main/kotlin/net/subsloth/core/network/media/mapper/` translate DTOs to stable domain models. See `Mapper.kt` for the pattern: pure functions on a top-level `object Mapper` that accept DTOs and return either domain models or typed errors via `Result`.

## Error Handling

Follow `docs/codestyle.md` §7's three-tier strategy: `null` for expected absence, `require`/`check`/`error` for programmer mistakes, `Result<T>` for recoverable failures in pure code.

Use `Result<T>` in the functional core when callers should handle both success and failure paths. At the I/O boundary (shell port implementations), catch exceptions and convert them to `Result.failure(DomainResultException(...))` so domain code receives a typed `Result` rather than an unchecked exception. Within the shell's I/O code (network calls, file reads), use `try`/`catch` since this is where exceptions happen. Keep `throw` for programmer mistakes that should crash fast.

The `DomainError` sealed hierarchy at `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt` covers all recoverable domain failures. Use `Result.failure(DomainResultException(...))` in mappers to carry typed errors across the network-to-domain boundary.

## Policy Objects

Stateless `object` classes with pure functions. They encapsulate business rules with no side effects and no Android dependencies. Policies live in `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/`.

```kotlin
object SearchPolicy {
    fun matches(media: Media, query: String): Boolean { ... }
    fun filter(items: List<Media>, query: String): List<Media> { ... }
}
```

Existing policies: `SearchPolicy`, `QualityPolicy`, `DownloadPolicy`, `SubtitlePolicy`, `ResumePolicy`, `PlaybackSpeedPolicy`, `CompletionPolicy`, `NextEpisodePolicy`, `StorageCleanupPolicy`.

## Module Dependencies

Dependencies flow inward toward zero-dependency modules:

```text
:feature:* -> :core:network -> :core:domain -> :core:model
:feature:* -> :core:database -> :core:domain -> :core:model
:feature:* -> :core:preferences -> :core:model
:feature:* -> :core:media -> :core:model
```

- `:core:model` has no dependencies (pure Kotlin types, value classes, sealed ADTs).
- `:core:domain` depends on `:core:model` and defines port interfaces and policy objects.
- `:core:network` depends on `:core:domain` and `:core:model` and contains DTOs and mappers.
- `:feature:*` modules depend on core modules and implement the shell.
- No module within the Functional Core (`:core:model` and `:core:domain`) depends on the Android framework; this boundary is enforced by architecture tests. Shell modules (`:core:network`, `:core:database`, `:core:media`, `:core:preferences`, `:feature:*`, `:app`) do depend on Android by design.

Architecture tests in `:app` verify that core imports contain no Android shell packages, enforcing the boundary at build time.

## Banned Dependencies

The following libraries are excluded from the dependency graph by project policy (`openspec/specs/project/spec.md` section "Superseded Libraries Excluded"). Architecture tests verify their absence in CI.

| Library | Ban reason |
|---|---|
| **Arrow-kt** | The project uses Kotlin stdlib `Result<T>` and sealed ADTs instead of Arrow's `Either`, `Option`, or `IO` types. Adding Arrow would introduce a heavy FP dependency for patterns that Kotlin's type system already handles. |
| **Hilt / Dagger** | The project uses constructor injection without a DI framework. Modules create their own dependency graphs explicitly. A DI framework would add annotation processing, code generation, and indirection without enough benefit at this scale. |
| **Moshi / Gson** | The project uses kotlinx-serialization for JSON parsing. Moshi and Gson add competing annotation processors and runtime reflection that conflict with the compile-time-safe serialization approach. |
| **Navigation Compose** | Navigation is handled by a custom typed router built on Compose state. Navigation Compose would add fragment-based routing and XML nav graphs that are unnecessary for this app's navigation model. |
| **Kotest** | The project uses kotlin.test with JUnit 5 for testing. Kotest would add a competing test framework and assertion library on top of the existing toolchain. |
| **RxJava** | The project uses Kotlin coroutines and `Flow` for async work. RxJava would add an alternative concurrency model and pull in heavy reactive streams dependencies. |

## References

- `docs/codestyle.md`: definitive FC/IS rules, sealed types, pure functions, module dependencies, error handling, naming, Modern Kotlin FP techniques.
- `openspec/specs/architecture/spec.md`: architecture boundary requirements, typed error composition, capability ports, network isolation.
- `openspec/specs/project/spec.md`: project baseline, module layout, functional core isolation, banned dependencies.
- `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt`: sealed `DomainError` hierarchy.
- `core/domain/src/main/kotlin/net/subsloth/core/domain/port/`: port interfaces (`CatalogPort`, `LibraryPort`, `PlaybackPort`, etc.).
- `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/`: policy objects (`SearchPolicy`, `QualityPolicy`, `DownloadPolicy`, etc.).
- `core/network/src/main/kotlin/net/subsloth/core/network/media/mapper/Mapper.kt`: DTO-to-domain mapper pattern.
