// SPDX-License-Identifier: Apache-2.0
//
// Cross-Origin headers required for OPFS-backed SQLite persistence.
// Without these, sqlite3.oo1.OpfsDb in the SQLite web worker falls
// back to in-memory storage and data is lost on page reload.
//
// This applies to the webpack-dev-server (development mode).
// Production deployments must set these headers at the reverse proxy /
// CDN level.
if (config.devServer) {
    config.devServer.headers = {
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
    };
}
