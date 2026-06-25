package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient
import net.subsloth.core.network.media.mock.createMockClient

actual fun createMockClient(
    login: String?,
    password: String?,
    baseUrl: String,
    enableHttpLogging: Boolean,
): HttpClient = createMockClient()
