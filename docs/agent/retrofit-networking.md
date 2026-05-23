# Retrofit Networking: Agent Instructions

Networking conventions for the SubSloth Android app. All networking is part of the Imperative Shell (see `docs/codestyle.md`). The stack uses Retrofit 3.0.0 + OkHttp 5.3.2 + kotlinx.serialization. Network calls return domain types wrapped in `Result<T>` at the mapper boundary. No Retrofit exceptions escape to the domain layer.

---

## Retrofit 3.0.0 Setup

Follow `docs/agent/fc-is-data-layer.md` for Retrofit API patterns: `suspend` functions on `Api` interface, kotlinx.serialization converter, `@Serializable` DTOs with `@SerialName`. The concrete builder configuration in `ClientFactory.create()`:

```kotlin
val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(sharedOkHttpClient)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(Api::class.java)
```

---

## OkHttp 5.3.2 Interceptor Stack

Interceptors are added in this exact order in `ClientFactory.create()`:

### 1. IdentityInterceptor
Injects Kodi-compatible headers: `User-Agent: Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1`, `Accept: application/json, */*`, and `Accept-Language: en-US,en;q=0.5`. Implemented as a private `kodiIdentity()` function in `ClientFactory.kt`.

### 2. BasicAuthInterceptor
Encodes `login:password` as Base64 and injects a `Basic` `Authorization` header on every request. Implemented as a private `basicAuth()` function in `ClientFactory.kt`. No token refresh pattern exists yet all requests carry credentials directly.

### 3. ResponseInterceptor
Detects unexpected response types before DTO parsing. Throws `ResponseException` (carrying `NetworkError.UnexpectedResponse`) for:
- Redirects (3xx)
- HTML responses (`text/html` Content-Type)
- Successful responses (2xx) with non-JSON Content-Type (unless `*/*`)
- Error responses (4xx, 5xx) pass through so Retrofit can surface them as `HttpException`.

### 4. RequestCoalescer
Single-flight pattern implemented as a thread-safe OkHttp interceptor. Uses `ConcurrentHashMap<String, InFlightEntry>` with `CountDownLatch` synchronisation. When two identical requests (same URL + query params) arrive concurrently, the second caller awaits the first and receives a buffered copy of the response body. Large responses (over 10 MiB) bypass coalescing to avoid OOM. Timeout defaults to 30 seconds.

### 5. RetryInterceptor
Applies bounded retry for retryable responses:
- **429** (Too Many Requests), respects `Retry-After` header up to 60 seconds.
- **5xx** (server errors), retries with progressive backoff: `baseDelayMs * (retryCount + 1)`.
- Max 2 retries (`MAX_RETRIES = 2`), base delay 500 ms.
- 429 with `Retry-After` over 60 seconds passes through immediately so the caller surfaces a typed `NetworkError.RateLimited` without added delay.
- IOExceptions (connection drop, DNS failure) are also retried up to the max.

### 6. LoggingInterceptor
`HttpLoggingInterceptor` at `HEADERS` level, added only when `enableHttpLogging = true`. `Set-Cookie`, `Cookie`, and `Authorization` headers are redacted.

---

## Kodi Identity Pattern

The client impersonates a Kodi 20.2 (Nexus) media client. This is required because the upstream API only serves JSON responses when it sees a Kodi-compatible User-Agent. The `User-Agent` string is `"Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1"`. Both the identity interceptor and the basic auth interceptor are stateless functions added at client construction time. Credentials (`login`, `password`) are passed to `ClientFactory.create()`.

---

## kotlinx.serialization

Same conventions as `docs/agent/fc-is-data-layer.md`: `@Serializable` DTOs, `@SerialName` for snake_case mapping, `Json { ignoreUnknownKeys = true; coerceInputValues = true }`. The `@file:Suppress("TooManyFunctions")` rule applies to `Mapper.kt`.

---

## Rate Limiting

The server may return HTTP 429 with a `Retry-After` header. The `RetryInterceptor` handles this:
- If `Retry-After` is <= 60 seconds, the request is retried after that delay.
- If `Retry-After` exceeds 60 seconds, the 429 passes through immediately.
- The domain layer maps this to `NetworkError.RateLimited(retryAfterSeconds)`.
- Backoff uses `baseDelayMs * attempt` (no explicit jitter yet; waiters sleep via `Thread.sleep`).

---

## Single-Flight Coalescing

`RequestCoalescer` ensures only one in-flight request for a given URL at a time. The cache key is the full URL string (method + path + query params). When a duplicate arrives:
1. It checks the `ConcurrentHashMap` for an existing entry.
2. If none exists, it inserts a new `InFlightEntry` and proceeds with the network call.
3. If one exists, it calls `InFlightEntry.await()` and receives a buffered copy of the response body.
4. After completion, the entry is removed from the map.

This is purely a concurrent-request deduplication mechanism. It is not a response cache and does not persist responses across time.

---

## Error Handling

- `ResponseInterceptor` throws `ResponseException` carrying `NetworkError.UnexpectedResponse` for non-JSON/redirect/HTML responses.
- `RetryInterceptor` throws `IOException` after exhausting retries.
- HttpException and IOException are NOT caught inside the interceptor stack. They propagate to the caller.
- The `Mapper` catches mapping failures and returns `Result.failure(DomainResultException(DecodeError.*))`.
- The `Api` interface itself returns bare DTO types (not `Result`). The `Result<T>` wrapping happens in the mapper layer, not in Retrofit.
- No exception propagates past the shell boundary. Callers always get either domain types or typed `DomainError` variants via `Result`.

---

## Testing with MockWebServer

- Use OkHttp `MockWebServer` for integration tests against the full interceptor stack.
- Use `FakeChain` (as seen in `ResponseInterceptorTest`) for unit-testing individual interceptors in isolation.
- WireMock is available for contract tests that need request matching and stub verification.
- Mock the `Api` interface for ViewModel unit tests (domain-layer tests should not depend on network DTOs).
- See `NetworkPolicyTest` for patterns: `AtomicInteger` call counting, fake `Interceptor.Chain` implementations, and thread coordination with `Thread.sleep`.

---

## References

- `docs/codestyle.md`: FC/IS architecture rules
- `docs/agent/fc-is-data-layer.md`: data layer conventions
- `core/network/src/main/kotlin/net/subsloth/core/network/media/client/ClientFactory.kt`: interceptor assembly and singleton setup
- `core/network/src/main/kotlin/net/subsloth/core/network/media/client/RequestCoalescer.kt`: single-flight implementation
- `core/network/src/main/kotlin/net/subsloth/core/network/media/client/ResponseInterceptor.kt`: response validation
- `core/network/src/main/kotlin/net/subsloth/core/network/media/client/RetryInterceptor.kt`: retry and rate-limit handling
- `core/network/src/main/kotlin/net/subsloth/core/network/media/api/Api.kt`: Retrofit API interface
- `core/network/src/main/kotlin/net/subsloth/core/network/media/mapper/Mapper.kt`: DTO-to-domain mapping
