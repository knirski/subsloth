package net.subsloth.core.network.media.client

import kotlinx.serialization.json.Json
import net.subsloth.core.network.media.api.Api
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Base64

object ClientFactory {
    private val DEFAULT_BASE_URL: String by lazy {
        System.getenv("SUBSLOTH_API_BASE_URL") ?: "http://localhost:8080/api/v2/"
    }

    /**
     * Shared [RequestCoalescer] so all API clients benefit from cross-client
     * single-flight de-duplication. Kept internal so tests can supply a
     * separate instance if needed.
     */
    private val requestCoalescer = RequestCoalescer()

    /**
     * Creates an [Api] instance configured with:
     * - Kodi-compatible request identity (User-Agent, Accept headers)
     * - Basic authentication via login/password
     * - [ResponseInterceptor] for unexpected redirect/HTML/non-JSON detection
     * - [RequestCoalescer] for single-flight de-duplication
     * - [RetryInterceptor] for bounded retries on 429/5xx responses
     * - Optional HTTP logging (headers only, with redacted auth headers)
     */
    fun create(
        login: String,
        password: String,
        baseUrl: String = DEFAULT_BASE_URL,
        enableHttpLogging: Boolean = false,
    ): Api {
        val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        val clientBuilder =
            OkHttpClient
                .Builder()
                .addInterceptor(kodiIdentity())
                .addInterceptor(basicAuth(login, password))
                .addInterceptor(ResponseInterceptor())
                .addInterceptor(requestCoalescer)
                .addInterceptor(RetryInterceptor())

        if (enableHttpLogging) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                    redactHeader("Set-Cookie")
                    redactHeader("Cookie")
                    redactHeader("Authorization")
                },
            )
        }

        return Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Api::class.java)
    }

    private fun kodiIdentity(): Interceptor = Interceptor { chain ->
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("User-Agent", "Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1")
                .header("Accept", "application/json, */*")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build(),
        )
    }

    private fun basicAuth(login: String, password: String): Interceptor = Interceptor { chain ->
        val encoded =
            Base64
                .getEncoder()
                .encodeToString("$login:$password".toByteArray(Charsets.UTF_8))
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("Authorization", "Basic $encoded")
                .build(),
        )
    }
}
