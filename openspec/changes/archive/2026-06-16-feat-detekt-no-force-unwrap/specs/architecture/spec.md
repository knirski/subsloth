# Architecture Specification (delta)

## What Changes

- A new detekt rule `subsloth.NoForceUnwrap` is added to the
  project's custom rule set. It reports every `!!` (force-unwrap)
  used in production source sets; test source sets are excluded.
- The existing `SearchViewModel.ensureCatalogLoaded` `!!` is
  replaced with `requireNotNull` so the new rule reports zero
  findings in current production code.

## ADDED Requirements

### Requirement: forbid force-unwrap in production code
Production code SHALL NOT use the Kotlin force-unwrap operator
`!!`. Every nullable expression SHALL be handled at the type level
— by modelling the null case as a sealed variant, by lifting the
call into a typed `Outcome<T>` / `Result<T>` / sealed `DomainError`,
or by an explicit `requireNotNull(x) { "..." }` / `checkNotNull(x) { "..." }`
with a descriptive message.

Test source sets (any path matching `**/src/<X>Test/`) MAY use `!!`
to assert preconditions.

#### Scenario: detekt reports !! in production
- **WHEN** a `!!` postfix expression appears in `src/main/`,
  `src/commonMain/`, `src/jvmMain/`, `src/androidMain/`, or any
  other production source set
- **THEN** detekt reports it under the `subsloth.NoForceUnwrap` rule
- **AND** CI fails on the finding

#### Scenario: detekt does not report !! in tests
- **WHEN** a `!!` postfix expression appears in any `*Test`
  source set
- **THEN** detekt does not report it
