## Why

The app needs a strongly typed functional core before Android shell behavior is implemented. This change defines the domain, policy, network, mapper, retry, and request-boundary rules that prevent API DTOs, Android framework objects, and raw URLs from leaking into app decisions.

## What Changes

- Add typed domain modeling and policy requirements with Kotlin `Result<T>` and sealed errors.
- Add network client and mapper boundary requirements.
- Add typed error handling for expected Media and local failures.
- Add low-concurrency, single-flight, bounded retry, and `429` handling expectations.
- Add safeguards that prevent raw stream, download, subtitle, artwork, auth, and credential data from being persisted or logged.

## Capabilities

### New Capabilities

- `architecture`: Functional Core / Imperative Shell, typed domain model, ports, network boundary, request policies, and mapper constraints.

### Modified Capabilities

- None.

## Impact

- Affects `:core:model`, `:core:domain`, `:core:network`, domain tests, mapper tests, and network request-policy tests.
- Depends on the API contract and fixtures created by `foundation-api-contract`.
