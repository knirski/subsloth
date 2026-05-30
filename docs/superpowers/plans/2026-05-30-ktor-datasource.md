# KtorHttpDataSource Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `media3-datasource-okhttp` with a custom Ktor-based `HttpDataSource` implementation, removing the only remaining OkHttp dependency.

**Architecture:** New Android-library module `core:datasource-ktor` bridges Ktor's `HttpClient` into Media3's `HttpDataSource` interface via `runBlocking`. A single adapter class and its factory provide the same contract as `OkHttpDataSource` but backed by the CIO engine. The factory is wired into `MediaPlaybackController` and the old dependency is removed from both `core:media` and `feature:player`.

**Tech Stack:** Kotlin, Ktor (CIO), AndroidX Media3, JUnit 5, Ktor MockEngine

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `core/datasource-ktor/build.gradle.kts` | Module definition with Ktor + Media3 deps |
| Create | `core/datasource-ktor/src/main/kotlin/net/subsloth/core/datasource/KtorHttpDataSource.kt` | `HttpDataSource` adapter + `Factory` |
| Create | `core/datasource-ktor/src/test/kotlin/net/subsloth/core/datasource/KtorHttpDataSourceTest.kt` | Unit tests with MockEngine |
| Modify | `settings.gradle.kts` | Add `:core:datasource-ktor` include |
| Modify | `core/media/build.gradle.kts` | Replace `media3-datasource-okhttp` with `:core:datasource-ktor` |
| Modify | `core/media/src/main/kotlin/net/subsloth/core/media/MediaPlaybackController.kt` | Wire `KtorHttpDataSource.Factory` into ExoPlayer |
| Modify | `feature/player/build.gradle.kts` | Remove `media3-datasource-okhttp` dep |

---

### Task 1: Create the `core:datasource-ktor` module

**Files:**
- Create: `core/datasource-ktor/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create module directory**

```bash
mkdir -p core/datasource-ktor/src/main/kotlin/net/subsloth/core/datasource
mkdir -p core/datasource-ktor/src/test/kotlin/net/subsloth/core/datasource
```

- [ ] **Step 2: Create `core/datasource-ktor/build.gradle.kts`**

```kotlin
plugins {
    id("subsloth.android.library")
}

android {
    namespace = "net.subsloth.core.datasource"
}

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.coroutines.test)
}
```

- [ ] **Step 3: Add module to `settings.gradle.kts`**

Add the line below before the closing brace of the `include(...)` block:

```kotlin
include(":core:datasource-ktor")
```

- [ ] **Step 4: Run a compile check to verify the module wires up**

```bash
./gradlew :core:datasource-ktor:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL (the module compiles with no source yet).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts core/datasource-ktor/build.gradle.kts
git commit -m "chore: add core:datasource-ktor module"
```

---

### Task 2: Implement `KtorHttpDataSource` and `Factory`

**Files:**
- Create: `core/datasource-ktor/src/main/kotlin/net/subsloth/core/datasource/KtorHttpDataSource.kt`

This file contains both the `KtorHttpDataSource` class and its `Factory` inner class.

- [ ] **Step 1: Write the full implementation**

```kotlin
package net.subsloth.core.datasource

import androidx.annotation.VisibleForTesting
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.net.URI

/**
 * A [HttpDataSource] backed by Ktor's [HttpClient].
 *
 * Provides the same contract as [OkHttpDataSource] but uses the CIO engine,
 * eliminating the dependency on OkHttp.
 */
class KtorHttpDataSource @VisibleForTesting internal constructor(
    private val client: HttpClient,
    private val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
) : HttpDataSource {

    private var response: HttpResponse? = null
    private var channel: ByteReadChannel? = null
    private var openedUri: URI? = null
    private var responseHeaders: Map<String, List<String>>? = null
    private var bytesRead: Long = 0
    private var bytesTotal: Long = C.LENGTH_UNSET

    override fun open(dataSpec: DataSpec): Long = runBlocking {
        val uri = dataSpec.uri
        openedUri = uri
        bytesRead = 0

        val httpResponse = client.prepareGet(uri.toString()).apply {
            // Custom headers from DataSpec
            for ((name, value) in dataSpec.httpRequestHeaders.entries) {
                header(name, value)
            }

            // Byte-range header
            if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET) {
                val rangeEnd = if (dataSpec.length != C.LENGTH_UNSET) {
                    dataSpec.position + dataSpec.length - 1
                } else {
                    ""
                }
                header(HttpHeaders.Range, "bytes=${dataSpec.position}-$rangeEnd")
            }

            // GZIP: omit Accept-Encoding when FLAG_ALLOW_GZIP is NOT set
            if (dataSpec.flags and DataSpec.FLAG_ALLOW_GZIP == 0) {
                header(HttpHeaders.AcceptEncoding, "identity")
            }

            // Cache control
            if (dataSpec.flags and DataSpec.FLAG_IGNORE_CACHE != 0) {
                header(HttpHeaders.CacheControl, "no-cache")
            }
        }.execute()

        val statusCode = httpResponse.status.value
        if (!httpResponse.status.isSuccess()) {
            val responseMessage = httpResponse.status.description
            val headers = httpResponse.headers.entries().associate { (k, v) ->
                k to v
            }
            throw InvalidResponseCodeException(
                statusCode,
                responseMessage,
                headers,
                dataSpec,
            )
        }

        response = httpResponse
        channel = httpResponse.bodyAsChannel()
        openedUri = URI.create(httpResponse.request.url.toString())
        responseHeaders = httpResponse.headers.entries().associate { (k, v) -> k to v }

        // Determine content length
        val contentLength = httpResponse.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            ?: C.LENGTH_UNSET
        bytesTotal = when {
            dataSpec.length != C.LENGTH_UNSET -> dataSpec.length
            contentLength != C.LENGTH_UNSET -> contentLength
            else -> C.LENGTH_UNSET
        }

        bytesTotal
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = runBlocking {
        val currentChannel = channel ?: throw HttpDataSource.HttpDataSourceException(
            "Connection closed",
            HttpDataSource.HttpDataSourceException.TYPE_CLOSE,
        )

        val bytesRead = currentChannel.readAvailable(buffer, offset, length)
        if (bytesRead == -1) return@runBlocking C.RESULT_END_OF_INPUT
        this@KtorHttpDataSource.bytesRead += bytesRead
        bytesRead
    }

    override fun getUri(): android.net.Uri? = openedUri?.let { android.net.Uri.parse(it.toString()) }

    override fun close() {
        channel = null
        response = null
        openedUri = null
        responseHeaders = null
    }

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders ?: emptyMap()

    class Factory(
        private val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
        private val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
    ) : HttpDataSource.Factory {

        private val client: HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = connectTimeoutMs
                requestTimeoutMillis = readTimeoutMs
            }
        }

        override fun createDataSource(): HttpDataSource =
            KtorHttpDataSource(client, connectTimeoutMs, readTimeoutMs)
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000L
        const val DEFAULT_READ_TIMEOUT_MS = 30_000L
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :core:datasource-ktor:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL (may need to add imports for `android.net.Uri` — see inline import).

- [ ] **Step 3: Commit**

```bash
git add core/datasource-ktor/src/main/kotlin/net/subsloth/core/datasource/KtorHttpDataSource.kt
git commit -m "feat: add KtorHttpDataSource and Factory"
```

---

### Task 3: Wire the factory into `MediaPlaybackController`

**Files:**
- Modify: `core/media/build.gradle.kts`
- Modify: `core/media/src/main/kotlin/net/subsloth/core/media/MediaPlaybackController.kt`

- [ ] **Step 1: Replace dependency in `core/media/build.gradle.kts`**

Remove the `media3-datasource-okhttp` line and add the new module:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(project(":core:datasource-ktor"))
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:assertions"))
}
```

- [ ] **Step 2: Wire the factory in `MediaPlaybackController.kt`**

Add the import at the top:

```kotlin
import net.subsloth.core.datasource.KtorHttpDataSource
```

Then modify the `buildPlayer()` method to inject the factory:

```kotlin
fun buildPlayer(): ExoPlayer {
    release()
    val dataSourceFactory = KtorHttpDataSource.Factory()
    val exoPlayer = ExoPlayer.Builder(application)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(application)
                .setDataSourceFactory(dataSourceFactory)
                .setLiveTargetOffsetMs(DEFAULT_LIVE_OFFSET.inWholeMilliseconds),
        )
        .build()
    player = exoPlayer
    attachErrorListener()
    return exoPlayer
}
```

- [ ] **Step 3: Compile `core:media` to verify**

```bash
./gradlew :core:media:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/media/build.gradle.kts core/media/src/main/kotlin/net/subsloth/core/media/MediaPlaybackController.kt
git commit -m "feat: wire KtorHttpDataSource into MediaPlaybackController"
```

---

### Task 4: Remove remaining OkHttp dependency from `feature:player`

**Files:**
- Modify: `feature/player/build.gradle.kts`

- [ ] **Step 1: Remove `media3-datasource-okhttp` from `feature/player/build.gradle.kts`**

Remove the line:
```kotlin
    implementation(libs.media3.datasource.okhttp)
```

The `feature:player` doesn't need the OkHttp datasource anymore — it already depends on `:core:media` which now provides the Ktor-backed player.

- [ ] **Step 2: Compile `feature:player` to verify**

```bash
./gradlew :feature:player:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Assemble the full app (confirms no OkHttp leaks)**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add feature/player/build.gradle.kts
git commit -m "chore: remove media3-datasource-okhttp from feature:player"
```

---

### Task 5: Add unit tests for `KtorHttpDataSource`

**Files:**
- Create: `core/datasource-ktor/src/test/kotlin/net/subsloth/core/datasource/KtorHttpDataSourceTest.kt`

Uses Ktor's `MockEngine` to simulate HTTP responses without a real server.

- [ ] **Step 1: Write the test class**

```kotlin
package net.subsloth.core.datasource

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI

class KtorHttpDataSourceTest {

    private fun mockClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "",
        contentLength: Long? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpClient {
        val responseHeaders = buildMap {
            putAll(headers)
            if (contentLength != null) {
                put(HttpHeaders.ContentLength, contentLength.toString())
            }
        }
        return HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = ByteReadChannel(body),
                        status = status,
                        headers = headersOf(responseHeaders.toList().flatMap { (k, v) ->
                            listOf(k, v)
                        }),
                    )
                }
            }
        }
    }

    @Test
    fun `open returns content length from header`() = runTest {
        val client = mockClient(body = "hello world", contentLength = 11)
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/file")).build()

        val length = dataSource.open(spec)

        assertEquals(11, length)
        dataSource.close()
    }

    @Test
    fun `open returns LENGTH_UNSET when no content length`() = runTest {
        val client = mockClient(body = "hello world")
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/file")).build()

        val length = dataSource.open(spec)

        assertEquals(C.LENGTH_UNSET, length)
        dataSource.close()
    }

    @Test
    fun `read returns content bytes`() = runTest {
        val client = mockClient(body = "ABCDEF", contentLength = 6)
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/file")).build()
        dataSource.open(spec)

        val buffer = ByteArray(6)
        val bytesRead = dataSource.read(buffer, 0, 6)

        assertEquals(6, bytesRead)
        assertEquals("ABCDEF", buffer.decodeToString())
        dataSource.close()
    }

    @Test
    fun `read returns RESULT_END_OF_INPUT after all data consumed`() = runTest {
        val client = mockClient(body = "AB", contentLength = 2)
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/file")).build()
        dataSource.open(spec)

        val buffer = ByteArray(4)
        dataSource.read(buffer, 0, 4) // consume "AB"
        val result = dataSource.read(buffer, 0, 4)

        assertEquals(C.RESULT_END_OF_INPUT.toLong(), result.toLong())
        dataSource.close()
    }

    @Test
    fun `open throws InvalidResponseCodeException on 404`() = runTest {
        val client = mockClient(status = HttpStatusCode.NotFound)
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/missing")).build()

        assertThrows(InvalidResponseCodeException::class.java) {
            dataSource.open(spec)
        }
    }

    @Test
    fun `open throws InvalidResponseCodeException on 401`() = runTest {
        val client = mockClient(status = HttpStatusCode.Unauthorized)
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/unauth")).build()

        assertThrows(InvalidResponseCodeException::class.java) {
            dataSource.open(spec)
        }
    }

    @Test
    fun `open throws InvalidResponseCodeException on 403`() = runTest {
        val client = mockClient(status = HttpStatusCode.Forbidden)
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/forbidden")).build()

        assertThrows(InvalidResponseCodeException::class.java) {
            dataSource.open(spec)
        }
    }

    @Test
    fun `getUri returns the effective URI`() = runTest {
        val client = mockClient(body = "ok")
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/file")).build()
        dataSource.open(spec)

        val uri = dataSource.uri
        assertNotNull(uri)
        assertEquals("https://example.com/file", uri.toString())
        dataSource.close()
    }

    @Test
    fun `getResponseHeaders returns response headers`() = runTest {
        val client = mockClient(
            body = "ok",
            headers = mapOf("X-Custom" to "value"),
        )
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder().setUri(URI.create("https://example.com/file")).build()
        dataSource.open(spec)

        val headers = dataSource.responseHeaders
        assertEquals(listOf("value"), headers["X-Custom"])
        dataSource.close()
    }

    @Test
    fun `getResponseHeaders returns empty map before open`() {
        val client = mockClient()
        val dataSource = KtorHttpDataSource(client)

        assertEquals(emptyMap(), dataSource.responseHeaders)
    }

    @Test
    fun `factory creates data source`() {
        val factory = KtorHttpDataSource.Factory()
        val dataSource = factory.createDataSource()

        assertNotNull(dataSource)
    }

    @Test
    fun `byte range sets Range header`() = runTest {
        val capturedHeaders = mutableMapOf<String, String>()
        val engine = MockEngine { request ->
            capturedHeaders["Range"] = request.headers[HttpHeaders.Range] ?: ""
            respond(
                content = ByteReadChannel("hello"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val client = HttpClient(engine)
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder()
            .setUri(URI.create("https://example.com/file"))
            .setPosition(10)
            .setLength(20)
            .build()

        dataSource.open(spec)

        assertEquals("bytes=10-29", capturedHeaders["Range"])
        dataSource.close()
    }

    @Test
    fun `gzip flag omits Accept-Encoding override`() = runTest {
        val capturedHeaders = mutableMapOf<String, String>()
        val engine = MockEngine { request ->
            capturedHeaders["Accept-Encoding"] = request.headers[HttpHeaders.AcceptEncoding] ?: ""
            respond(
                content = ByteReadChannel("data"),
                status = HttpStatusCode.OK,
            )
        }
        val client = HttpClient(engine)
        val dataSource = KtorHttpDataSource(client)
        val spec = DataSpec.Builder()
            .setUri(URI.create("https://example.com/file"))
            .setFlags(DataSpec.FLAG_ALLOW_GZIP)
            .build()

        dataSource.open(spec)

        // When FLAG_ALLOW_GZIP is set, we don't force "identity"
        assertEquals("", capturedHeaders["Accept-Encoding"] ?: "")
        dataSource.close()
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew :core:datasource-ktor:test
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add core/datasource-ktor/src/test/kotlin/net/subsloth/core/datasource/KtorHttpDataSourceTest.kt
git commit -m "test: add KtorHttpDataSource unit tests"
```

---

### Task 6: Verify no OkHttp remains

- [ ] **Step 1: Run full pre-commit checks**

```bash
./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :app:assembleDebug test
```

Expected: All pass.

- [ ] **Step 2: Confirm OkHttp is not in the dependency tree**

```bash
./gradlew app:dependencies --configuration debugRuntimeClasspath | grep okhttp || echo "No okhttp found"
```

Expected: `No okhttp found`

- [ ] **Step 3: Final commit**

```bash
git commit -m "chore: verify OkHttp fully removed"
```

---

## Post-Merge Validation

After this PR is merged, smoke-test playback on a real device with a streaming URL. The error handling (`PlayerViewModel.categorizeError`) should work unchanged since `InvalidResponseCodeException` is from Media3, not OkHttp.
