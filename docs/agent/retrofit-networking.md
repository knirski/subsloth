# Retrofit Networking

Retrofit 3.0.0 + OkHttp 5.3.2 + kotlinx.serialization conventions for SubSloth. All networking is Imperative Shell. No Retrofit exceptions escape to domain layer.

## Retrofit Setup

Follow `docs/agent/fc-is-data-layer.md` for API patterns: `suspend` functions on `Api` interface, kotlinx.serialization converter (`@Serializable` DTOs with `@SerialName`). JSON config: `ignoreUnknownKeys = true`, `coerceInputValues = true`.

## OkHttp Interceptor Stack (order matters)

### 1. IdentityInterceptor
Injects Kodi-compatible headers: `User-Agent: Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1`, `Accept: application/json, */*`, `Accept-Language: en-US,en;q=0.5`.

### 2. BasicAuthInterceptor
Encodes `login:password` as Base64, injects `Basic` `Authorization` header on every request. No token refresh pattern yet.

### 3. ResponseInterceptor
Detects unexpected responses before DTO parsing. Throws `ResponseException` for: redirects (3xx), HTML responses, non-JSON Content-Type on 2xx, error responses pass through for Retrofit `HttpException`.

### 4. RequestCoalescer
Single-flight: `ConcurrentHashMap<String, InFlightEntry>` with `CountDownLatch`. Duplicate concurrent requests await the first and receive buffered response body. Responses over 10 MiB bypass coalescing. 30s timeout.

### 5. RetryInterceptor
Bounded retry: 429 (respects `Retry-After` up to 60s), 5xx (progressive backoff `baseDelayMs * (retryCount + 1)`, max 2 retries, base 500ms). 429 with `Retry-After` over 60s passes through immediately. IOExceptions also retried.

### 6. LoggingInterceptor
`HttpLoggingInterceptor` at `HEADERS` level (when `enableHttpLogging = true`). `Set-Cookie`, `Cookie`, `Authorization` redacted.

## Kodi Identity

Client impersonates Kodi 20.2 (Nexus) — required because upstream API only serves JSON to Kodi-compatible User-Agent. Both identity and basic auth interceptors are stateless functions added at client construction.

## Rate Limiting

429 with `Retry-After` ≤ 60s: retry after delay. > 60s: pass through immediately → `NetworkError.RateLimited(retryAfterSeconds)`.

## Single-Flight Coalescing

Cache key = full URL (method + path + query). Only deduplicates concurrent requests — not a persistent cache.

## Error Handling

- `ResponseInterceptor` → `ResponseException` (non-JSON/redirect/HTML)
- `RetryInterceptor` → `IOException` after retries exhausted
- `HttpException`/`IOException` propagate to caller (caught at mapper layer)
- `Mapper` → `Result.failure(DomainResultException(DecodeError.*))`
- No exception propagates past shell boundary. Callers always get domain types or `DomainError` via `Result`.

## Testing

- OkHttp `MockWebServer` for integration tests against full interceptor stack
- `FakeChain` for unit-testing individual interceptors
- WireMock for contract tests
- Mock `Api` interface for ViewModel unit tests
- See `NetworkPolicyTest` for patterns: `AtomicInteger` call counting, fake `Interceptor.Chain`

## References

- `docs/codestyle.md`: FC/IS architecture rules
- `docs/agent/fc-is-data-layer.md`: data layer conventions
- `ClientFactory.kt`: interceptor assembly and singleton setup
- `RequestCoalescer.kt`: single-flight implementation
- `ResponseInterceptor.kt`: response validation
- `RetryInterceptor.kt`: retry and rate-limit handling
- `Api.kt`: Retrofit API interface
- `Mapper.kt`: DTO-to-domain mapping
