@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.web

/**
 * The build-injected API base URL for the production web tier, or the
 * empty string when absent (demo tier).
 *
 * `webApp/webpack.config.d/base-url.js` assigns
 * `globalThis.SUBSLOTH_API_BASE_URL = process.env.SUBSLOTH_API_BASE_URL`
 * at bundle startup; CI sets the env from the repository secret for
 * production builds. The wasm binary cannot be text-substituted, so the
 * value travels through a runtime global instead.
 */
@JsFun("() => globalThis.SUBSLOTH_API_BASE_URL || ''")
internal external fun subslothApiBaseUrlEnv(): String
