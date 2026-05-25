# SubSloth Best Practices

## Port / Adapter

Define I/O boundary interfaces as ports in `core/domain/.../port/`. Implementations (adapters) live in shell modules (`:core:network`, `:core:database`, etc.).

## Error Handling

Use `Result<T>` for recoverable failures in pure code. Use `try`/`catch` only around I/O in shell code. Never `throw` for recoverable failures. Domain errors must be typed entries in the `DomainError` sealed hierarchy.

## Sealed Types

Use `sealed interface` with `data object` (singleton) or `data class` (value). One file per sealed type.

## Collections

Use `kotlinx.collections.immutable` (`ImmutableList`, `ImmutableSet`) for all public collection-typed properties, especially in Compose UI state classes.

## DateTime

Use `kotlinx.datetime` types (`Instant`, `LocalDateTime`, `Duration`). Never `java.time` or `java.util.Date`.

## Compose

Annotate UI state classes with `@Immutable` or `@Stable`. Follow unidirectional data flow.

## Code Style

Prefer `when` over `if`-`else` for sealed types. Prefer collection pipelines (`mapNotNull`, `filter`, `groupBy`) over mutable loops. Prefer extension functions for domain-specific behavior. Prefer local functions to avoid namespace pollution.
