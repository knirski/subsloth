# Ktor Networking

Ktor 3.5 + kotlinx.serialization conventions for SubSloth. All networking is Imperative Shell. No Ktor exceptions escape to domain layer.

## Ktor Setup

`HttpClient` built by `ClientFactory.create()` at `core/network/.../media/client/ClientFactory.kt`. Uses Ktor CIO engine (Android/JVM) and MockEngine (wasmJs). JSON config: `ignoreUnknownKeys = true`, `coerceInputValues = true`.

## Ktor Plugin Stack (order matters)

### 1. defaultRequest
Injects Kodi-compatible headers: `User-Agent: Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1`, `Accept: application/json, */*`, `Accept-Language: en-US,en;q=0.5`. Sets base URL from `ClientConfig.baseUrl` (default `http://localhost:8080/api/v2/`).

### 2. ContentNegotiation
`kotlinx.serialization` JSON converter. Configures `ignoreUnknownKeys = true`, `coerceInputValues = true`.

### 3. HttpTimeout
30s request/socket timeout, 10s connect timeout.

### 4. Auth (basic)
Ktor `BasicAuth` provider that sends `login:password` encoded as Base64 on every request (`sendWithoutRequest = true`). No token refresh pattern yet.

### 5. HttpRequestRetry
Bounded retry: 429 (respects `Retry-After` up to 60s), 5xx (exponential backoff, max 2 retries, base 500ms). 429 with `Retry-After` over 60s passes through immediately.

### 6. Logging
`LogLevel.HEADERS` (when `enableHttpLogging = true`). Uses `InterceptorLogger` Kermit bridge. `Authorization` header redacted.

### 7. ResponseValidationPlugin (custom)
Custom `createClientPlugin` that hooks `onResponse`. Detects: redirects (3xx) → `ResponseValidationException(UnexpectedResponse)`, HTML responses → `ResponseValidationException(UnexpectedResponse)`, non-JSON Content-Type on 2xx → `ResponseValidationException(UnexpectedResponse)`.

## Kodi Identity

Client impersonates Kodi 20.2 (Nexus) — required because upstream API only serves JSON to Kodi-compatible User-Agent. Both identity and basic auth are configured at client construction via `defaultRequest` headers and Ktor's `Auth` plugin.

## Rate Limiting

429 with `Retry-After` ≤ 60s: retry after delay via `HttpRequestRetry`. > 60s: pass through immediately → `NetworkError.RateLimited(retryAfterSeconds)`.

## Error Handling

- `ResponseValidationPlugin` → `ResponseValidationException` (non-JSON/redirect/HTML)
- `HttpRequestRetry` → throws after retries exhausted
- Exceptions propagate to caller (caught at mapper layer)
- `Mapper` → `Result.failure(DomainResultException(DecodeError.*))`
- No exception propagates past shell boundary. Callers always get domain types or `DomainError` via `Result`.

## Testing

- Ktor `MockEngine` for mock HTTP responses
- WireMock for contract tests
- Mock `Api` class for ViewModel unit tests
- See `NetworkPolicyTest` for patterns

## References

- `docs/codestyle.md`: FC/IS architecture rules
- `docs/agent/fc-is-data-layer.md`: data layer conventions
- `ClientFactory.kt`: plugin assembly and HttpClient creation
- `ResponseValidationPlugin.kt`: response validation
- `Api.kt`: suspending endpoint methods
- `Mapper.kt`: DTO-to-domain mapping
