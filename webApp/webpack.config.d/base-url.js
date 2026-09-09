// Injects the build-time API base URL as a runtime global that the
// Kotlin/wasm code reads via globalThis (see WebBaseUrl.kt). The wasm
// binary cannot be text-substituted, so the value travels through a
// runtime global; CI sets process.env.SUBSLOTH_API_BASE_URL from the
// repository secret for production builds (empty → demo tier).
globalThis.SUBSLOTH_API_BASE_URL = process.env.SUBSLOTH_API_BASE_URL || "";
