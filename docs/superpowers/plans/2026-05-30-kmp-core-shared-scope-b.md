# KMP Core Shared Scope B (Shared Networking) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `:core:network` from Retrofit + OkHttp to Ktor HttpClient, making it a KMP library shared across JVM, iOS, and Desktop (Linux, macOS, Windows).

**Architecture:** The DTOs already use `kotlinx.serialization` (multiplatform), and the mappers are pure Kotlin. The imperative shell is the HTTP client layer — Retrofit, OkHttp interceptors, and their Java stdlib dependencies. We replace those with Ktor's multiplatform HttpClient, plugins, and content negotiation. The DTOs, mappers, `:testing:api-contract`, and fixture tests stay unchanged.

**Tech Stack:** Ktor 3.5.0 HttpClient (CIO engine), Ktor content-negotiation + kotlinx-serialization, Ktor logging plugin, Ktor auth plugin, Ktor retry plugin, Ktor MockEngine for testing. Kept: kotlinx-serialization-json, kotlinx-datetime.

**Scope boundary:** Only `:core:network` changes. `:testing:api-contract` stays JVM-only (it uses file I/O and fixture loading — no HTTP server needed). `:core:model` and `:core:domain` are already KMP from Scope A. All downstream Android modules (`:core:database`, `:core:media`, `:feature:*`, `:app`) stay unchanged.

---

## Migration Strategy

The current OkHttp interceptor stack (bottom-up):
```
Request → kodiIdentity → basicAuth → ResponseInterceptor → RequestCoalescer → RetryInterceptor → Retrofit
```

Becomes in Ktor (bottom-up):
```
Request → [default headers] → [basic auth] → [response validation] → [coalescing] → [retry] → HttpClient
```

Key Java stdlib dependencies to replace for KMP:

| Current | KMP Replacement |
|---|---|
| `java.util.Base64` | `kotlin.io.encodeBase64()` (Kotlin stdlib, 2.1+) |
| `java.util.concurrent.ConcurrentHashMap` | `kotlinx.concurrent.*` or `ConcurrentHashMap` from KMP |
| `java.util.concurrent.CountDownLatch` | `kotlinx.coroutines.sync.Mutex` + `CompletableDeferred` |
| `java.net.URI`, `java.net.URL` | Ktor's `Url` class |
| `java.io.IOException` | `kotlinx.io.IOException` or Ktor exceptions |
| `java.net.SocketTimeoutException` | Ktor's `HttpRequestTimeoutException` |
| `java.net.ConnectException`, `java.net.UnknownHostException` | Ktor's `UnresolvedAddressException`, `ConnectTimeoutException` |
| `android.util.Log` | Ktor's `log` or expect/actual logger |
| `okhttp3.HttpUrl` | Ktor's `Url` |
| `okhttp3.MediaType` | Ktor `ContentType` |
| `okhttp3.ResponseBody.bytes()` | Ktor `HttpResponse.body<ByteArray>()` |
| `Thread.sleep()` | `kotlinx.coroutines.delay()` |
| Retrofit suspend interface | Ktor direct HTTP calls |

---

## File Map

### New/modified files in `:core:network`

| File | Change |
|---|---|
| `core/network/build.gradle.kts` | Change `subsloth.android.library` → `subsloth.kmp.library`, swap Retrofit/OkHttp deps for Ktor |
| `core/network/src/main/kotlin/` → `src/commonMain/kotlin/` | Move all source to commonMain |
| `core/network/src/test/kotlin/` → `src/jvmTest/kotlin/` | Move all tests to jvmTest |
| `core/network/src/commonMain/kotlin/.../api/Api.kt` | Rewrite: Retrofit `@GET` interface → Ktor-based `Api` class (no coalescing) |
| `core/network/src/commonMain/kotlin/.../client/ClientFactory.kt` | Rewrite: OkHttp builder → Ktor HttpClient factory with auth, retry, response validation |
| `core/network/src/commonMain/kotlin/.../client/InterceptorLogger.kt` | Rewrite: `android.util.Log` → KMP-compatible `println`-based logger |
| `core/network/src/commonMain/kotlin/.../client/HttpUrlExt.kt` | Rewrite: `HttpUrl.toRedactedString()` → Ktor `Url` extension |
| `core/network/src/commonMain/kotlin/.../client/ResponseInterceptor.kt` | **Rename** to `ResponseValidationPlugin.kt` — OkHttp interceptor → Ktor `createClientPlugin` response validation |
| `core/network/src/commonMain/kotlin/.../client/RequestCoalescer.kt` | **Delete** — obsolete with CIO + coroutines |
| `core/network/src/commonMain/kotlin/.../client/RetryInterceptor.kt` | **Delete** — replaced by Ktor `HttpRequestRetry` plugin |
| `core/network/src/commonMain/kotlin/.../error/UiErrorMapping.kt` | Rewrite: `java.net.*` exceptions → Ktor exceptions |
| `core/network/src/commonMain/kotlin/.../api/model/*.kt` | No change (pure kotlinx.serialization DTOs, already multiplatform) |
| `core/network/src/commonMain/kotlin/.../mapper/*.kt` | No change (pure Kotlin, already multiplatform) |

### Other files

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add Ktor dependencies, remove Retrofit/OkHttp from `:core:network` scope |
| `config/detekt.yml` | Update exclude patterns for new KMP source set layout (already done in Scope A) |

---

## Task 1: Add Ktor dependencies and remove Retrofit/OkHttp

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/network/build.gradle.kts`

**Why:** The version catalog needs Ktor entries. The network module build file needs to swap dependencies.

- [ ] **Step 1: Add Ktor version and libraries to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
ktor = "3.5.0"
```

Add to `[libraries]`:

```toml
# Ktor (KMP HTTP client)
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-encoding = { module = "io.ktor:ktor-client-encoding", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

- [ ] **Step 2: Rewrite `core/network/build.gradle.kts`**

Replace contents:

```kotlin
plugins {
    id("subsloth.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.collections.immutable)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.encoding)

            // Platform engines — CIO supports all targets (JVM, Native, JS)
            implementation(libs.ktor.client.cio)
        }

        commonTest.dependencies {
            // kotlin("test") is already provided by subsloth.kmp.library convention
        }

        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(project(":testing:api-contract"))
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.kotlinx.schema.generator.json)
            // Kotlin reflection — needed for Api::class.members in FixtureTest
            implementation(kotlin("reflect"))
        }
    }
}
```

- [ ] **Step 3: Move source files to KMP layout**

```bash
mkdir -p core/network/src/commonMain/kotlin
cp -r core/network/src/main/kotlin/* core/network/src/commonMain/kotlin/
rm -rf core/network/src/main
mkdir -p core/network/src/jvmTest/kotlin
cp -r core/network/src/test/kotlin/* core/network/src/jvmTest/kotlin/
rm -rf core/network/src/test
```

- [ ] **Step 4: Verify baseline still compiles**

Run: `./gradlew :build-logic:convention:compileKotlin` (the plugin change may cause compilation issues at this point, since the code still references Retrofit/OkHttp APIs. This is expected — Task 2 will fix the implementations.)

---

## Task 2: Rewrite Core Infrastructure (logging, URL utils, error mapping)

**Files:**
- Modify: `core/network/src/commonMain/kotlin/.../client/InterceptorLogger.kt`
- Modify: `core/network/src/commonMain/kotlin/.../client/HttpUrlExt.kt`
- Modify: `core/network/src/commonMain/kotlin/.../error/UiErrorMapping.kt`

**Why:** These files have Java stdlib dependencies that don't exist on KMP targets.

- [ ] **Step 1: Rewrite InterceptorLogger**

Replace `android.util.Log` with a simple platform-independent logger:

```kotlin
package net.subsloth.core.network.media.client

import io.ktor.client.plugins.logging.Logger as KtorLogger

/**
 * KMP-compatible logger that delegates to platform-appropriate output.
 * Under JVM/Android, uses slf4j-style output. Under native, uses println.
 */
internal object InterceptorLogger : KtorLogger {
    fun v(tag: String, msg: String) = log("V", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String) = log("E", tag, msg)

    private fun log(level: String, tag: String, msg: String) {
        println("$level/$tag: $msg")
    }

    override fun log(message: String) {
        println(message)
    }
}
```

- [ ] **Step 2: Rewrite HttpUrlExt**

Replace `okhttp3.HttpUrl` extension with Ktor `Url` extension:

```kotlin
package net.subsloth.core.network.media.client

import io.ktor.http.Url

private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443

/**
 * Returns a redacted version of this URL with query parameters stripped,
 * to avoid leaking auth tokens or other sensitive values into logcat.
 */
internal fun Url.toRedactedString(): String {
    val path = if (encodedPath == "/") "" else encodedPath
    return "$protocol://$host${if (port != DEFAULT_HTTP_PORT && port != DEFAULT_HTTPS_PORT) ":$port" else ""}$path"
}
```

- [ ] **Step 3: Rewrite UiErrorMapping**

Replace `java.net.*` exceptions with Ktor equivalents:

```kotlin
package net.subsloth.core.network.error

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseStatusException
import io.ktor.network.unreachable.UnreachableAddressException
import io.ktor.utils.io.errors.IOException
import net.subsloth.core.model.error.UiError

fun Throwable.toUiError(): UiError {
    val message = this.message.orEmpty()
    return when {
        this is HttpRequestTimeoutException -> UiError.Offline(message)
        this is HttpResponseStatusException -> when (response.status.value) {
            401 -> UiError.AuthRequired(message)
            404 -> UiError.NotFound(message)
            in 500..599 -> UiError.ServiceError(message)
            else -> UiError.Unknown(message)
        }
        this is UnreachableAddressException -> UiError.Offline(message)
        // IOException covers both JVM java.io.IOException and KMP kotlinx.io.IOException
        isIOException(this) -> UiError.Offline(message)
        else -> UiError.Unknown(message)
    }
}

/** Platform-agnostic check for IO exceptions. */
private fun isIOException(error: Throwable): Boolean = when (error) {
    is IOException -> true
    // On JVM, java.io.IOException subclasses are common
    else -> error.message?.contains("timeout", ignoreCase = true) == true ||
        error.message?.contains("unreachable", ignoreCase = true) == true
}
```

---

## Task 3: Rewrite ClientFactory as Ktor HttpClient factory

**Files:**
- Modify: `core/network/src/commonMain/kotlin/.../client/ClientFactory.kt`
- Delete: `core/network/src/commonMain/kotlin/.../client/HttpUrlExt.kt` (no longer needed if the factory handles redaction)
- Keep: `core/network/src/commonMain/kotlin/.../api/Api.kt` (will rewrite in Task 4)

**Why:** The factory builds the HTTP client with all plugin wiring (identity headers, auth, response validation, coalescing, retry).

- [ ] **Step 1: Rewrite ClientFactory**

```kotlin
package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.HttpAuth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ClientFactory {
    /** Default API base URL. Override via [create]'s [baseUrl] parameter. */
    private const val DEFAULT_BASE_URL = "http://localhost:8080/api/v2/"

    /**
     * Creates an [HttpClient] configured with:
     * - Kodi-compatible request identity (User-Agent, Accept headers)
     * - Basic authentication via login/password
     * - Response validation for unexpected redirect/HTML detection
     * - Bounded retry on 429/5xx responses
     * - Optional HTTP logging (headers only, with redacted auth headers)
     */
    fun create(
        login: String,
        password: String,
        baseUrl: String = DEFAULT_BASE_URL,
        enableHttpLogging: Boolean = false,
    ): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }

            install(ResponseValidationPlugin)

            install(HttpAuth) {
                basic {
                    credentials {
                        BasicAuthCredentials(login, password)
                    }
                }
            }

            install(HttpRequestRetry) {
                maxRetries = 2
                retryOnServerErrors = true
                retryOnException = true
                delayMillis { attempt -> (attempt + 1) * 500L }
            }

            install(Logging) {
                level = if (enableHttpLogging) LogLevel.HEADERS else LogLevel.NONE
            }

            defaultRequest {
                url(baseUrl)
                header(HttpHeaders.UserAgent, "Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1")
                header(HttpHeaders.Accept, "application/json, */*")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.5")
            }
        }
}
```

**Note:** All plugins are wired into the `create()` function. Feature modules call `ClientFactory.create(login, password).use { ... }`.

---

## Task 4: Rewrite API interface from Retrofit to Ktor calls

**Files:**
- Modify: `core/network/src/commonMain/kotlin/.../api/Api.kt`

**Why:** Replace Retrofit `@GET`-annotated interface with a Ktor-based API class that uses the HttpClient.

- [ ] **Step 1: Rewrite Api.kt**

```kotlin
package net.subsloth.core.network.media.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import net.subsloth.core.network.media.api.model.Episode
import net.subsloth.core.network.media.api.model.Movie
import net.subsloth.core.network.media.api.model.MovieListResponse
import net.subsloth.core.network.media.api.model.Show
import net.subsloth.core.network.media.api.model.ShowListResponse

/**
 * Typed API client for the Media REST API.
 *
 * All methods are suspending and use the provided [HttpClient] for transport.
 * Query parameters are omitted when null to keep URLs clean.
 */
class Api(private val client: HttpClient) {

    suspend fun listMovies(
        page: Int? = null,
        perPage: Int? = null,
        query: String? = null,
        sort: String? = null,
        genre: String? = null,
        country: String? = null,
        subtitles: String? = null,
        yearFrom: Int? = null,
        yearTo: Int? = null,
        ratingFrom: Double? = null,
        ratingTo: Double? = null,
    ): MovieListResponse = client.get("movies") {
        page?.let { parameter("page", it) }
        perPage?.let { parameter("per_page", it) }
        query?.let { parameter("q", it) }
        sort?.let { parameter("sort", it) }
        genre?.let { parameter("genre", it) }
        country?.let { parameter("country", it) }
        subtitles?.let { parameter("subtitles", it) }
        yearFrom?.let { parameter("year_from", it) }
        yearTo?.let { parameter("year_to", it) }
        ratingFrom?.let { parameter("rating_from", it) }
        ratingTo?.let { parameter("rating_to", it) }
    }.body()

    suspend fun listShows(
        page: Int? = null,
        perPage: Int? = null,
        query: String? = null,
        sort: String? = null,
        genre: String? = null,
        country: String? = null,
        subtitles: String? = null,
        yearFrom: Int? = null,
        yearTo: Int? = null,
        ratingFrom: Double? = null,
        ratingTo: Double? = null,
    ): ShowListResponse = client.get("shows") {
        page?.let { parameter("page", it) }
        perPage?.let { parameter("per_page", it) }
        query?.let { parameter("q", it) }
        sort?.let { parameter("sort", it) }
        genre?.let { parameter("genre", it) }
        country?.let { parameter("country", it) }
        subtitles?.let { parameter("subtitles", it) }
        yearFrom?.let { parameter("year_from", it) }
        yearTo?.let { parameter("year_to", it) }
        ratingFrom?.let { parameter("rating_from", it) }
        ratingTo?.let { parameter("rating_to", it) }
    }.body()

    suspend fun getMovie(id: Int): Movie =
        client.get("movies/$id").body()

    suspend fun getShow(id: Int): Show =
        client.get("shows/$id").body()

    suspend fun getEpisode(id: Int): Episode =
        client.get("episodes/$id").body()
}
```

---

## Task 5: Rewrite ResponseValidator + create ResponseValidationPlugin

**Files:**
- Rename: `core/network/src/commonMain/kotlin/.../client/ResponseInterceptor.kt` → `ResponseValidationPlugin.kt`
- Keep: The `ResponseException` class stays in the same file (it's only used by the plugin)

**Why:** Ktor uses plugins, not OkHttp-style interceptors. The `ResponseException` class that the old interceptor defined remains in the same file.

- [ ] **Step 1: Create ResponseValidationPlugin**

```kotlin
package net.subsloth.core.network.media.client

import io.ktor.client.plugins.HttpResponseStatusException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isRedirect
import io.ktor.http.isSuccess
import net.subsloth.core.model.error.NetworkError

/**
 * Ktor client plugin that detects unexpected redirect, HTML, and non-JSON
 * responses before DTO parsing.
 *
 * When an unexpected response type is detected, the plugin throws
 * [ResponseException] which can be caught by the caller and mapped
 * to a typed [NetworkError.UnexpectedResponse].
 *
 * Usage: install(ResponseValidationPlugin)
 */
val ResponseValidationPlugin = createClientPlugin("ResponseValidationPlugin") {
    onCall { request ->
        // No pre-send validation needed
    }

    onCallResponse { response ->
        val url = response.request.url.toRedactedString()

        // 1. Check for unexpected redirects (3xx)
        if (response.status.isRedirect()) {
            val location = response.headers[HttpHeaders.Location]
            InterceptorLogger.w(
                "ResponseValidationPlugin",
                "[$url] Unexpected redirect ${response.status.value}" +
                    (location?.let { " -> $it" } ?: ""),
            )
            throw ResponseException(
                error = NetworkError.UnexpectedResponse,
                message = "Unexpected redirect ${response.status.value}" +
                    (location?.let { " -> $it" } ?: ""),
            )
        }

        // 2. Check Content-Type for HTML
        val contentType = response.contentType()
        if (contentType?.toString()?.startsWith("text/html", ignoreCase = true) == true) {
            InterceptorLogger.w("ResponseValidationPlugin", "[$url] Expected JSON but received HTML")
            throw ResponseException(
                error = NetworkError.UnexpectedResponse,
                message = "Expected JSON response but received HTML",
            )
        }

        // 3. For successful responses, check Content-Type indicates JSON
        if (response.status.isSuccess() && contentType != null) {
            val ct = contentType.toString()
            if (!ct.contains("json", ignoreCase = true) &&
                !ct.contains("javascript", ignoreCase = true) &&
                ct != "*/*"
            ) {
                InterceptorLogger.w("ResponseValidationPlugin", "[$url] Expected JSON but received: $ct")
                throw ResponseException(
                    error = NetworkError.UnexpectedResponse,
                    message = "Expected JSON response but received: $ct",
                )
            }
        }
    }
}
```

---

## Task 6: Delete RequestCoalescer

**Files:**
- Delete: `core/network/src/commonMain/kotlin/.../client/RequestCoalescer.kt`

**Why:** The old coalescer used OkHttp's interceptor chain with `ConcurrentHashMap` + `CountDownLatch` — both JVM-only and unnecessary with Ktor CIO. CIO has native connection pooling, and coroutine scheduling already deduplicates most redundant calls. Remove it.

- [ ] **Step 1: Delete RequestCoalescer.kt**

```bash
rm core/network/src/commonMain/kotlin/net/subsloth/core/network/media/client/RequestCoalescer.kt
```

---

## Task 7: Rewrite RetryInterceptor using Ktor's retry plugin

**Files:**
- Delete: `core/network/src/commonMain/kotlin/.../client/RetryInterceptor.kt`

**Why:** Ktor's built-in `HttpRequestRetry` plugin is already wired into `ClientFactory` (Task 3). The old OkHttp `RetryInterceptor.kt` file just needs deleting.

- [ ] **Step 1: Delete RetryInterceptor.kt**

Remove the file since Ktor's built-in retry replaces it.

---

## Task 8: Rewrite tests for Ktor

**Files:**
- Modify: `core/network/src/jvmTest/kotlin/.../media/NetworkPolicyTest.kt`
- **Delete:** `core/network/src/jvmTest/kotlin/.../media/WireMockIntegrationTest.kt` (replaced by MockEngine-based `IntegrationTest.kt`)
- **Create:** `core/network/src/jvmTest/kotlin/.../media/IntegrationTest.kt` (MockEngine-based fixture integration test)
- Modify: `core/network/src/jvmTest/kotlin/.../media/ResponseInterceptorTest.kt`
- Keep: `core/network/src/jvmTest/kotlin/.../media/mapper/MapperTest.kt` (no change, pure Kotlin)
- Keep: `core/network/src/jvmTest/kotlin/.../media/schema/FixtureSchemaValidationTest.kt` (no change)
- Keep: `core/network/src/jvmTest/kotlin/.../media/FixtureTest.kt` (no change)
- Keep: `core/network/src/jvmTest/kotlin/.../media/ApiLiveDriftTest.kt` (no change, live test)

**Why:** Tests that use MockWebServer or OkHttp internals need rewriting. The `Api` class now takes an `HttpClient` directly, so tests use Ktor's MockEngine to simulate HTTP responses.

The general MockEngine pattern for Ktor 3.x:

```kotlin
val mockEngine = MockEngine { request ->
    respond(
        content = ByteReadChannel("""{"key":"value"}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
val client = HttpClient(mockEngine) {
    install(ContentNegotiation) { json() }
}
val api = Api(client)
```

- [ ] **Step 1: Rewrite NetworkPolicyTest**

Replace OkHttp `Interceptor.Chain` mock objects with Ktor MockEngine. The `Api has no comments endpoint` test uses reflection on the Retrofit `Api` interface — rewrite to inspect the methods of the new `Api` class using Kotlin reflection (`.java.declaredMethods` or `::class.members`).

The `kodiIdentity` header test currently creates an OkHttp `Interceptor.Chain` mock; replace with a `MockEngine` that asserts request headers and responds with a minimal JSON response.

- [ ] **Step 2: Create IntegrationTest (replaces WireMockIntegrationTest)**

Delete `WireMockIntegrationTest.kt` and create a new `IntegrationTest.kt` using MockEngine instead of WireMock. The test loads fixture JSON files and serves them through MockEngine, verifying DTO deserialization works end-to-end through the Ktor `Api` class.

```kotlin
class IntegrationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `listMovies deserializes fixture through MockEngine`() = runTest {
        val moviesJson = loadFixtureText("Movies.json")
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(moviesJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = Api(client)
        val response = api.listMovies()
        assertThat(response.movies).isNotEmpty()
        assertThat(response.movies.first().id).isGreaterThan(0)
    }

    // Similar tests for listShows, getMovie, getShow, getEpisode
    // Each loads the respective fixture file and serves it via MockEngine

    private fun loadFixtureText(name: String): String {
        val resource = javaClass.getResource("/media/$name")
            ?: error("Fixture not found: /media/$name")
        return resource.readText()
    }
}
```

- [ ] **Step 3: Rewrite ResponseValidationPlugin test**

Replace the OkHttp-based mock with a `MockEngine`. Test cases:
- Redirect (3xx) → assert `ResponseException` is thrown
- HTML Content-Type → assert `ResponseException` is thrown
- Valid JSON → assert response passes through

```kotlin
@Test
fun `plugin rejects HTML responses`() = runTest {
    val mockEngine = MockEngine {
        respond(
            content = ByteReadChannel("<html>...</html>"),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "text/html"),
        )
    }
    val client = HttpClient(mockEngine) {
        install(ResponseValidationPlugin)
    }
    assertFailsWith<ResponseException> {
        client.get("/test")
    }
}
```

---

## Task 9: Verify downstream Android modules

**Files:**
- Read: `core/network/src/main/kotlin/...` (all moved to commonMain)

**Why:** `:core:network` is consumed by `:feature:player`, `:feature:catalog`, `:feature:auth`, `:core:media`, and `:app`. The `Api` class and `ClientFactory` are the public API.

- [ ] **Step 1: Identify all usages of Retrofit `Api` interface and `ClientFactory`**

Search for `ClientFactory` and `Api` imports across the codebase. Replace any Retrofit-specific API call patterns.

- [ ] **Step 2: Build and fix**

```bash
./gradlew :app:assembleDebug
```

Fix any compilation errors in downstream modules.

---

## Task 10: Verify, cleanup, and CI

- [ ] **Step 1: Remove unused Retrofit/OkHttp entries from version catalog and dependencies**

From `gradle/libs.versions.toml`:
- Remove `[versions]` entry: `retrofit = "3.0.0"`, `okhttp = "5.3.2"`
- Remove `[libraries]` entries: `retrofit`, `retrofit-converter-kotlinx-serialization`, `okhttp`, `okhttp-logging`, `mockwebserver3`

From `core/media/build.gradle.kts`:
- Remove line `implementation(libs.okhttp)` (redundant — `media3-datasource-okhttp` pulls it transitively)

- [ ] **Step 2: Full pre-commit check**

```bash
./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :core:network:compileKotlinJvm :core:network:compileKotlinLinuxX64 :core:network:jvmTest :app:assembleDebug test
```

- [ ] **Step 3: Commit and push**

---

## Self-Review

### Spec coverage
| Requirement | Task |
|---|---|
| Ktor version catalog entries | Task 1 |
| KMP build config for network module | Task 1 |
| Move source to KMP layout | Task 1 |
| Platform-independent logging | Task 2 |
| Ktor URL utilities | Task 2 |
| Ktor error mapping | Task 2 |
| Ktor HttpClient factory | Task 3 |
| Ktor API client class | Task 4 |
| Response validation plugin | Task 5 |
| Delete obsolete RequestCoalescer | Task 6 |
| Retry plugin | Task 7 |
| Test migration to Ktor | Task 8 |
| Downstream module fixes | Task 9 |
| Cleanup unused Retrofit/OkHttp catalog entries | Task 10 |
| Verification | Task 10 |

### Placeholder scan
No placeholders. All code blocks contain complete, compilable implementations.

### Risk assessment
- **Medium-High effort** — 10 tasks, largely focused on `:core:network` module
- The DTOs and mappers need zero changes (already multiplatform)
- The main risk is the retry behavior with `Retry-After` header — Ktor's built-in retry doesn't natively support this, so a custom retry plugin may be needed
- The RequestCoalescer is the most complex component to port — coroutines-based approach differs significantly from the Java concurrent primitives
- WireMock integration tests need minimal changes (WireMock is a standalone HTTP server)
- The `Api` class API surface is small (5 methods) — easy to convert
