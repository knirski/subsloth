package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient

actual fun createMockClient(login: String, password: String, baseUrl: String, enableHttpLogging: Boolean): HttpClient =
    error("Mock client is not available on JVM target")
