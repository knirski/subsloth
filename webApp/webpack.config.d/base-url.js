// Injects the build-time API base URL as a runtime global that the
// Kotlin/wasm code reads via globalThis (see WebBaseUrl.kt). The wasm
// binary cannot be text-substituted, but the `@JsFun` bodies are emitted
// into the JS glue bundle, so DefinePlugin rewrites the global read there
// to the build-time literal. A plain assignment in this file would run in
// the Node-side webpack config process only and never reach the browser.
//
// CI sets process.env.SUBSLOTH_API_BASE_URL from the repository secret for
// production builds (empty → demo tier).
const webpack = require("webpack");

config.plugins.push(
    new webpack.DefinePlugin({
        "globalThis.SUBSLOTH_API_BASE_URL": JSON.stringify(
            process.env.SUBSLOTH_API_BASE_URL || "",
        ),
    }),
);
