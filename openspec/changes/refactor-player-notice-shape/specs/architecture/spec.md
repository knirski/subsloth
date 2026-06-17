# Architecture Specification (delta)

## What Changes

- `PlayerUiState.Notice` becomes a sealed interface with a
  nested sealed `Notice.Localized` and `Notice.Raw(message: String)`.
  `Notice.Localized` has three named variants: `NoSubtitles`,
  `SubtitleIn(language: String)`, `QualityReduced(quality: String)`.
  The previous `data class Notice(message, resKey?, formatArg?)`
  with three optional fields and string-keyed variants is removed.

## ADDED Requirements

### Requirement: polymorphic notifications use sealed types, not optional fields
The system MUST model polymorphic notification shapes (e.g. a
notice that is "resource-bound" vs "raw") as a sealed interface
with a small set of named variants. Modeling them as a `data class`
with N optional fields (where each field's presence depends on
the others), or as a `String resKey` discriminator that is
matched in a `when` at the consumer site, is forbidden.

#### Scenario: a Localized notice has a typed variant
- **WHEN** a notice is bound to a string resource
- **THEN** it is one of the named variants of
  `Notice.Localized` (e.g. `NoSubtitles`, `SubtitleIn`,
  `QualityReduced`)
- **AND** the consumer site uses an exhaustive `is` check;
  adding a new variant is a compile error in the screen
  until a matching branch is added

#### Scenario: a Raw notice has a non-empty message
- **WHEN** a notice is already resolved
- **THEN** it is `Notice.Raw(message)` and the message is
  non-empty

