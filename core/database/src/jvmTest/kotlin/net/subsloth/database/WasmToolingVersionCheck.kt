package net.subsloth.database

import org.junit.jupiter.api.Test

/**
 * Logs the wasm-opt version available at test time so version drift
 * between the Kotlin/Wasm plugin (binaryen 125) and the system-installed
 * binaryen (Nix 129) doesn't go unnoticed.
 */
class WasmToolingVersionCheck {
    @Test
    fun `wasm-opt version`() = println(
        "wasm-opt version: " + try {
            ProcessBuilder("wasm-opt", "--version")
                .redirectErrorStream(true).start().apply { waitFor() }
                .inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            "unavailable (${e.message})"
        },
    )
}
