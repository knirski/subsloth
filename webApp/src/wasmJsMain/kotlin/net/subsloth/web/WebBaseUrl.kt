@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.web

/**
 * The build-injected API base URL for the production web tier, or the
 * empty string when absent (demo tier).
 *
 * `webApp/webpack.config.d/base-url.js` rewrites the
 * `globalThis.SUBSLOTH_API_BASE_URL` read in the JS glue to the
 * build-time value (the wasm binary itself cannot be text-substituted);
 * CI sets the env from the repository secret for production builds.
 */
@JsFun("() => globalThis.SUBSLOTH_API_BASE_URL || ''")
internal external fun subslothApiBaseUrlEnv(): String
