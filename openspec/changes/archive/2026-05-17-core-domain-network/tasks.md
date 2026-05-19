## 1. Domain Tests

- [x] 1.1 Add tests for quality policy, subtitle policy, resume policy, next-episode policy, playback speed policy, search policy, and download decision policy.
- [x] 1.2 Add architecture-boundary tests that reject Android, network implementation, persistence, media, and UI imports from core domain modules.

## 2. Core Models

- [x] 2.1 Add immutable media, episode, season, subtitle, quality, progress, library, download, availability, and error models.
- [x] 2.2 Add value classes for Media identifiers, external IDs, language codes, resolution, account profile keys, and local media identifiers.

## 3. Domain Policies and Ports

- [x] 3.1 Implement pure policies for search, sort, filter, quality fallback, subtitle fallback, resume thresholds, completion, next episode, offline decisions, and storage cleanup.
- [x] 3.2 Define small `suspend` capability ports for catalog, library, credentials, downloads, playback, clock, connectivity, and storage.
- [x] 3.3 Add typed error composition with Kotlin `Result<T>`, sealed error hierarchies, and explicit validation accumulation where applicable.

## 4. Network Client and Mappers

- [x] 4.1 Add Retrofit/OkHttp API boundary or documented handwritten DTO fallback if OpenAPI generation is unsuitable.
- [x] 4.2 Add Kodi-style request metadata, auth, User-Agent, and redacting interceptors.
- [x] 4.3 Add unexpected redirect, HTML, and non-JSON response detection before DTO parsing.
- [x] 4.4 Add mappers from network DTOs into stable domain models.

## 5. Network Policy Verification

- [x] 5.1 Add tests for no comments endpoints, no WebView/browser identity, raw URL redaction, and server mutation gates.
- [x] 5.2 Add tests for low concurrency, single-flight de-duplication, bounded retries, `429`/`Retry-After`, and non-retryable failures.
- [x] 5.3 Run `./gradlew :core:model:test :core:domain:test :core:network:test`.
- [x] 5.4 Run `openspec validate core-domain-network --strict`.
