# architecture Specification (delta)

## MODIFIED Requirements

### Requirement: Functional Core Boundary
The system SHALL keep domain models and pure decisions in Kotlin core modules with no Android framework, Room, DataStore, Retrofit, OkHttp, Media3, WorkManager, filesystem, or notification dependencies. `:core:model` and `:core:domain` SHALL NOT depend on a Compose runtime artifact; any Compose stability annotation needed by consuming UI modules SHALL be supplied through a Compose stability configuration file consumed by those modules, not through a compile-time dependency in `:core:model` or `:core:domain`.

#### Scenario: Core imports are checked
- **WHEN** architecture tests inspect `:core:model` and `:core:domain`
- **THEN** Android shell, network implementation, and Compose runtime packages are absent from those modules across every source set (`commonMain`, `androidMain`, `jvmMain`, `wasmJsMain`), not only `commonMain`

## ADDED Requirements

### Requirement: Transport-Only Network Module
`:core:network` SHALL depend only on `:core:model` and `:core:domain`. It SHALL NOT depend on `:core:database` or `:core:preferences`. Repository and orchestration classes that combine HTTP transport with persistence or preferences SHALL live in `:core:data`, which depends on `:core:network`, `:core:database`, and `:core:preferences` and implements the domain ports those repositories fulfill.

#### Scenario: Network module has no persistence dependency
- **WHEN** the dependency graph of `:core:network` is resolved
- **THEN** no configuration includes `:core:database` or `:core:preferences`

#### Scenario: A repository combines transport and persistence
- **WHEN** a class needs to coordinate an API call with a Room cache or DataStore preference
- **THEN** it is implemented in `:core:data`, not `:core:network`

### Requirement: Feature Adapter Isolation
`:feature:*` modules SHALL depend only on `:core:model`, `:core:domain` (for domain ports), and shared UI modules (`:core:ui`, `:core:media`). They SHALL NOT depend on `:core:network`, `:core:database`, `:core:preferences`, or `:core:data`. Concrete adapter instances for transport, persistence, and preferences are constructed only at each platform's composition root and injected into feature ViewModels through domain port constructor parameters. `:core:media` is a shared playback/UI-bridging module, not a concrete IO adapter, and remains a permitted direct dependency for feature modules that need it (e.g. `:feature:player`); its own use of `:core:database`/`:core:preferences` is unchanged by this requirement.

#### Scenario: A feature module's dependency graph is resolved
- **WHEN** the dependency graph of any `:feature:*` module is resolved
- **THEN** no configuration includes `:core:network`, `:core:database`, `:core:preferences`, or `:core:data`

#### Scenario: A ViewModel needs catalog data
- **WHEN** a feature ViewModel is constructed
- **THEN** it receives a domain port (e.g. `CatalogPort`) as a constructor parameter, and the concrete implementation is supplied by the platform composition root, not imported directly by the feature module

### Requirement: UI Error Mapping Ownership
Functions that translate a `DomainError` or `Throwable` into a user-displayable message or UI-facing error type SHALL live in a UI-facing module (`:core:ui` or a `:feature:*` module), not in `:core:network`.

#### Scenario: A feature needs to display an error
- **WHEN** a feature ViewModel maps a `DomainError` to a UI-facing message
- **THEN** the mapping function it calls is defined in `:core:ui` or the feature module itself, not imported from `:core:network`

### Requirement: Executable Dependency Graph Invariants
The allowed module dependency graph SHALL be enforced by a test that inspects the resolved Gradle dependency graph or configuration classpath for every `:core:*` and `:feature:*` module across all of that module's source sets. Source-file import-statement scanning limited to a single source set (e.g. `commonMain` only) SHALL NOT be the sole enforcement mechanism for a module-boundary rule.

#### Scenario: A forbidden edge is introduced
- **WHEN** a `:feature:*` module's `build.gradle.kts` adds a dependency on `:core:network`, `:core:database`, `:core:preferences`, or `:core:data`, or `:core:network` adds a dependency on `:core:database` or `:core:preferences`
- **THEN** the dependency-graph invariant test fails

#### Scenario: A violation exists only in a non-commonMain source set
- **WHEN** a forbidden import appears in an `androidMain`, `jvmMain`, or `wasmJsMain` source set rather than `commonMain`
- **THEN** the dependency-graph invariant test still detects the violation, because it inspects the resolved dependency graph rather than scanning only `commonMain` source files

### Requirement: Composition Root Documentation
Each platform's composition root — the class or function responsible for constructing concrete network, persistence, preferences, and platform adapters and injecting them into feature ViewModels — SHALL be documented, including which platforms currently lack a production composition root and what non-production default they fall back to.

#### Scenario: A developer looks for composition-root ownership
- **WHEN** a developer wants to know where Android, Desktop, or Web construct their real adapters
- **THEN** a checked-in doc names the responsible class per platform and states explicitly whether that platform's composition root is production-ready or falls back to a non-production default
