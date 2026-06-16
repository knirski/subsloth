# Architecture Specification (delta)

## What Changes

- `EnqueueOutcome`, `TransferPreference`, `SubtitleEnqueueOutcome`,
  `DownloadFailureReason`, `DownloadCommandOutcome` become
  `enum class` instead of `sealed interface` with `data object` branches.
- All five are in `:core:model` or `:core:domain`. Variants and
  reference syntax are unchanged.

## ADDED Requirements

### Requirement: prefer `enum class` for data-only variant sets
The system MUST express variant sets with no per-variant state (e.g. `Queued`, `Applied`, `WifiOnly`) as `enum class`, not as `sealed interface` with `data object` branches. Rationale: `enum class` exposes `entries` and `values()` for iteration and exhaustiveness checks; the compiler enforces the variant set; it removes the detekt-flagged `'is' over enum entry` antipattern; and it is uniform with `QualityDescriptor` and other existing enums in the project.

#### Scenario: data-only sealed type is expressed as `enum class`
- **WHEN** a variant set with no per-variant state is added to `:core:model` or `:core:domain`
- **THEN** it SHALL be declared as `enum class`
- **AND** SHALL use `Foo.Bar` reference syntax at call sites
- **AND** exhaustive `when` SHALL match without `is` keywords
