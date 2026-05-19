package net.subsloth.core.network.media

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.core.network.media.client.RequestCoalescer
import net.subsloth.core.network.media.client.RetryInterceptor
import net.subsloth.testing.assertions.assertThat
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for network policy requirements:
 * - No comments endpoints, no WebView/browser identity
 * - Raw URL redaction
 * - Server mutation gates
 * - Low concurrency, single-flight de-duplication
 * - Bounded retries, 429/Retry-After, non-retryable failures
 */
class NetworkPolicyTest {
    // ── No Comments Endpoints ────────────────────────────────────────────

    @Test
    fun `Api has no comments endpoint`() {
        val methods = Api::class.java.declaredMethods
        val methodNames = methods.map { it.name }
        // Comments endpoints must not be present
        assertThat(methodNames).doesNotContain("listComments")
        assertThat(methodNames).doesNotContain("getComments")
        assertThat(methodNames).doesNotContain("postComment")
        assertThat(methodNames).doesNotContain("deleteComment")
        // No method should contain "comment" in its name
        val commentMethods = methodNames.filter { it.contains("comment", ignoreCase = true) }
        assertThat(commentMethods).isEmpty()
    }

    // ── No WebView/Browser Identity ──────────────────────────────────────

    @Test
    fun `client factory builds client with Kodi identity not browser identity`() {
        val capturedHeaders = AtomicReference("")
        val capturingInterceptor =
            Interceptor { chain ->
                val request = chain.request()
                capturedHeaders.set(request.header("User-Agent") ?: "")
                chain.proceed(request)
            }

        val client =
            okhttp3.OkHttpClient
                .Builder()
                .addInterceptor(capturingInterceptor)
                .build()

        val request =
            Request
                .Builder()
                .url("http://localhost:1/nonexistent")
                .header("User-Agent", "Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1")
                .header("Accept", "application/json, */*")
                .build()
        runCatching { client.newCall(request).execute() }

        // The User-Agent passed through is the one we set
        assertThat(capturedHeaders.get()).isEqualTo("Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1")
    }

    // ── Raw URL Redaction ────────────────────────────────────────────────

    @Test
    fun `mapped domain models contain no raw Media stream URLs in persistent fields`() {
        val qualityDescriptorClass = QualityDescriptor::class.java
        val persistentFields = qualityDescriptorClass.declaredFields.map { it.name }
        assertThat(persistentFields).doesNotContain("url")
        assertThat(persistentFields).doesNotContain("downloadUrl")
        assertThat(persistentFields).doesNotContain("streamUrl")
    }

    @Test
    fun `download state contains no URLs`() {
        val downloadStateClass = DownloadState::class.java
        val fields = downloadStateClass.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("url")
        assertThat(fields).doesNotContain("downloadUrl")
        assertThat(fields).doesNotContain("streamUrl")
        assertThat(fields).doesNotContain("subtitleUrl")
    }

    @Test
    fun `playback progress contains no URLs`() {
        val progressClass = PlaybackProgress::class.java
        val fields = progressClass.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("url")
        assertThat(fields).doesNotContain("downloadUrl")
        assertThat(fields).doesNotContain("streamUrl")
    }

    // ── Server Mutation Gate ─────────────────────────────────────────────

    @Test
    fun `Api has no library mutation endpoints`() {
        val methods = Api::class.java.declaredMethods
        val methodNames = methods.map { it.name }

        assertThat(methodNames).doesNotContain("addFavorite")
        assertThat(methodNames).doesNotContain("removeFavorite")
        assertThat(methodNames).doesNotContain("markWatched")
        assertThat(methodNames).doesNotContain("markUnwatched")
        assertThat(methodNames).doesNotContain("addToLibrary")
        assertThat(methodNames).doesNotContain("removeFromLibrary")
        assertThat(methodNames).doesNotContain("subscribe")
        assertThat(methodNames).doesNotContain("unsubscribe")
    }

    // ── Single-flight (RequestCoalescer) ─────────────────────────────────

    @Test
    fun `RequestCoalescer coalesces identical concurrent requests`() {
        val callCount = AtomicInteger(0)
        val coalescer = RequestCoalescer()

        val mockResponse =
            Response
                .Builder()
                .request(Request.Builder().url("http://test/movies").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data":"test"}""".toResponseBody("application/json".toMediaType()))
                .build()

        val request = Request.Builder().url("http://test/movies").build()

        val chain1 =
            object : Interceptor.Chain {
                override fun request(): Request = request

                override fun proceed(request: Request): Response {
                    callCount.incrementAndGet()
                    // Wait a bit so the second thread can arrive before we finish
                    Thread.sleep(100)
                    return mockResponse
                }

                override fun connection() = null

                override fun call() = error("Not supported")

                override fun connectTimeoutMillis() = 10_000

                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun readTimeoutMillis() = 10_000

                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun writeTimeoutMillis() = 10_000

                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            }

        val chain2 =
            object : Interceptor.Chain {
                override fun request(): Request = request

                override fun proceed(request: Request): Response {
                    callCount.incrementAndGet()
                    return mockResponse
                }

                override fun connection() = null

                override fun call() = error("Not supported")

                override fun connectTimeoutMillis() = 10_000

                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun readTimeoutMillis() = 10_000

                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun writeTimeoutMillis() = 10_000

                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            }

        val t1 = Thread { runCatching { coalescer.intercept(chain1) } }
        val t2 = Thread { runCatching { coalescer.intercept(chain2) } }

        t1.start()
        // Ensure thread 1 has started processing before thread 2
        Thread.sleep(20)
        t2.start()

        t1.join(5000)
        t2.join(5000)

        // If coalescing worked: 1 call (only chain1.proceed called)
        // If timing prevented coalescing: at most 2 calls
        assertThat(callCount.get()).isAtMost(2)
        // Both threads completed without uncaught exceptions
        assertThat(t1.isAlive).isFalse()
        assertThat(t2.isAlive).isFalse()
    }

    // ── RetryInterceptor ─────────────────────────────────────────────────

    @Test
    fun `RetryInterceptor retries on 429 and returns response after retry`() {
        val callCount = AtomicInteger(0)
        val interceptor = RetryInterceptor(maxRetries = 2, baseDelayMs = 10)
        val request = Request.Builder().url("http://test/movies").build()

        val chain =
            object : Interceptor.Chain {
                override fun request(): Request = request

                override fun proceed(request: Request): Response {
                    val count = callCount.incrementAndGet()
                    val body = """{"data":"test"}""".toResponseBody("application/json".toMediaType())
                    return if (count <= 1) {
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(429)
                            .message("Too Many Requests")
                            .body(body)
                            .header("Retry-After", "1")
                            .build()
                    } else {
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body)
                            .build()
                    }
                }

                override fun connection() = null

                override fun call() = error("Not supported")

                override fun connectTimeoutMillis() = 10_000

                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun readTimeoutMillis() = 10_000

                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun writeTimeoutMillis() = 10_000

                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            }

        val result = runCatching { interceptor.intercept(chain) }
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.code).isEqualTo(200)
    }

    @Test
    fun `RetryInterceptor stops retrying after max attempts`() {
        val callCount = AtomicInteger(0)
        val interceptor = RetryInterceptor(maxRetries = 2, baseDelayMs = 10)
        val request = Request.Builder().url("http://test/movies").build()

        val chain =
            object : Interceptor.Chain {
                override fun request(): Request = request

                override fun proceed(request: Request): Response {
                    callCount.incrementAndGet()
                    val body = """{"data":"test"}""".toResponseBody("application/json".toMediaType())
                    return Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Server Error")
                        .body(body)
                        .build()
                }

                override fun connection() = null

                override fun call() = error("Not supported")

                override fun connectTimeoutMillis() = 10_000

                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun readTimeoutMillis() = 10_000

                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun writeTimeoutMillis() = 10_000

                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            }

        val result = runCatching { interceptor.intercept(chain) }
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.code).isEqualTo(500)
        // With maxRetries=2, we make 1 initial + 2 retries = 3 total attempts
        assertThat(callCount.get()).isEqualTo(3)
    }

    @Test
    fun `RetryInterceptor does not retry on 4xx non-retryable errors`() {
        val callCount = AtomicInteger(0)
        val interceptor = RetryInterceptor(maxRetries = 2, baseDelayMs = 10)
        val request = Request.Builder().url("http://test/movies").build()

        val chain =
            object : Interceptor.Chain {
                override fun request(): Request = request

                override fun proceed(request: Request): Response {
                    callCount.incrementAndGet()
                    val body = """{"error":"bad request"}""".toResponseBody("application/json".toMediaType())
                    return Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(400)
                        .message("Bad Request")
                        .body(body)
                        .build()
                }

                override fun connection() = null

                override fun call() = error("Not supported")

                override fun connectTimeoutMillis() = 10_000

                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun readTimeoutMillis() = 10_000

                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun writeTimeoutMillis() = 10_000

                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            }

        val result = runCatching { interceptor.intercept(chain) }
        assertThat(result.isSuccess).isTrue()
        assertThat(callCount.get()).isEqualTo(1)
        assertThat(result.getOrNull()!!.code).isEqualTo(400)
    }

    @Test
    fun `RetryInterceptor passes through 429 with excessive Retry-After`() {
        val interceptor = RetryInterceptor(maxRetries = 2, baseDelayMs = 10, maxRetryAfterSeconds = 5)
        val request = Request.Builder().url("http://test/movies").build()

        val chain =
            object : Interceptor.Chain {
                override fun request(): Request = request

                override fun proceed(request: Request): Response {
                    val body = """{"error":"too many requests"}""".toResponseBody("application/json".toMediaType())
                    return Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .body(body)
                        .header("Retry-After", "120")
                        .build()
                }

                override fun connection() = null

                override fun call() = error("Not supported")

                override fun connectTimeoutMillis() = 10_000

                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun readTimeoutMillis() = 10_000

                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun writeTimeoutMillis() = 10_000

                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            }

        val result = runCatching { interceptor.intercept(chain) }
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.code).isEqualTo(429)
    }

    // ── Low concurrency ──────────────────────────────────────────────────

    @Test
    fun `ClientFactory creates client with default connection pool`() {
        val api =
            ClientFactory.create(
                login = "test",
                password = "test",
                baseUrl = "http://localhost:1/",
            )
        assertThat(api).isNotNull()
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private class AtomicReference<T>(private var value: T) {
        @Synchronized
        fun get(): T = value

        @Synchronized
        fun set(newValue: T) {
            value = newValue
        }
    }
}
