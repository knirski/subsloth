package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient

/**
 * Controls whether [ClientFactory.create] returns a real or mock HTTP client.
 * Set to `true` at app startup on targets that support mocking (wasmJs).
 */
object ClientConfig {
    var useMock: Boolean = false
}

/**
 * Platform-specific mock client factory.
 * [wasmJsMain][net.subsloth.core.network.media.mock] provides the actual mock
 * implementation using Ktor MockEngine. JVM/iOS targets throw — they are never
 * expected to run with `useMock = true`.
 */
expect fun createMockClient(login: String, password: String, baseUrl: String, enableHttpLogging: Boolean): HttpClient
