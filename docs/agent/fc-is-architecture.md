# FC/IS Architecture

Functional Core / Imperative Shell conventions for subsloth.

## Layer Boundaries

| Layer | Modules | Side effects | Android deps |
|---|---|---|---|
| **Core** | `:core:model`, `:core:domain` | none | none (JVM-only) |
| **Shell** | `:core:network`, `:core:database`, `:core:media`, `:core:preferences`, `:feature:*`, `:app` | file, network, I/O, platform | yes |

Pure code goes in core. Side-effectful code goes in shell. Calling a function twice with same args = same result? Core. Reads a file, writes to network, prints to stdout? Shell.

## Sealed ADTs

`sealed interface` with `data object` / `data class` branches, exhaustive `when`, three-site rule (variant declaration, classifier, parser/factory), one file per type. See `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt`.

## Pure Functions

Parameters in, return value out. No mutable shared state. Side effects isolated at shell boundary. Impure I/O goes in Android modules or behind port interfaces.

## Port / Adapter Pattern

Domain depends on `suspend` port interfaces, never on concrete implementations. Ports are `interface` definitions in `core/domain/src/main/kotlin/net/subsloth/core/domain/port/`. Adapters live in the shell. Tests provide stub implementations directly.

DTOs stay in `:core:network`. Mappers translate DTOs to domain models via pure functions on top-level `object Mapper`. See `Mapper.kt`.

## Error Handling

Three-tier: `null` for expected absence, `require`/`check`/`error` for programmer mistakes, `Result<T>` for recoverable failures in pure code. At I/O boundary, catch exceptions and convert to `Result.failure(DomainResultException(...))`. `DomainError` sealed hierarchy covers all recoverable failures. See `DomainError.kt`.

## Policy Objects

Stateless `object` classes with pure functions encapsulating business rules. Live in `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/`. Existing: `SearchPolicy`, `QualityPolicy`, `DownloadPolicy`, `SubtitlePolicy`, `ResumePolicy`, `PlaybackSpeedPolicy`, `CompletionPolicy`, `NextEpisodePolicy`, `StorageCleanupPolicy`.

## Module Dependencies

```
:feature:* → :core:network → :core:domain → :core:model
:feature:* → :core:database → :core:domain → :core:model
:feature:* → :core:preferences → :core:model
```

`:core:model` has no dependencies. `:core:domain` depends on `:core:model`. No core module depends on Android framework. Enforced by architecture tests.

## Banned Dependencies

| Library | Reason |
|---|---|
| **Arrow-kt** | Project uses stdlib `Result<T>` and sealed ADTs |
| **Hilt / Dagger** | Constructor injection without DI framework |
| **Moshi / Gson** | Project uses kotlinx-serialization |
| **Navigation Compose** | Custom typed router built on Compose state |
| **Kotest** | kotlin.test + JUnit 5 |
| **RxJava** | Kotlin coroutines + Flow |

## References

- `docs/codestyle.md`: definitive FC/IS rules
- `openspec/specs/architecture/spec.md`: architecture boundary requirements
- `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt`: sealed error hierarchy
- `core/domain/src/main/kotlin/net/subsloth/core/domain/port/`: port interfaces
- `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/`: policy objects
- `core/network/src/main/kotlin/net/subsloth/core/network/media/mapper/Mapper.kt`: DTO-to-domain mapper pattern
