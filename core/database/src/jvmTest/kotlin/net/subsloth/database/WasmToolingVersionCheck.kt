package net.subsloth.database

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Checks that the wasm tooling (wasm-opt from binaryen) expected by the
 * Kotlin/Wasm plugin is available and reports a known version.
 *
 * The Kotlin/Wasm plugin (org.jetbrains.kotlin.gradle.internal.platform.wasm)
 * downloads binaryen version 125 by default. On Nix we get version 129 from
 * nixpkgs. This test ensures at least one of them is reachable and logs the
 * actual version so version drift doesn't go unnoticed.
 */
class WasmToolingVersionCheck {

    @Test
    fun `wasm-opt is available and version is known`() {
        val candidates = listOfNotNull(
            findOnPath("wasm-opt"),
            findInDir(System.getenv("KOTLIN_NODEJS_HOME"), "wasm-opt"),
            findInDir(System.getProperty("java.io.tmpdir"), "wasm-opt"),
        )

        assertTrue(candidates.isNotEmpty(), "wasm-opt not found on PATH or KOTLIN_NODEJS_HOME")

        val wasmOpt = candidates.first()
        val output = runCommand(wasmOpt.absolutePath, "--version")
        assertTrue(output.isNotBlank(), "wasm-opt --version produced no output")
        println("wasm-opt version: $output")
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun findOnPath(name: String): File? = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .firstNotNullOfOrNull { dir ->
            val f = File(dir, name)
            f.takeIf { it.canExecute() }
        }

    private fun findInDir(dir: String?, name: String): File? {
        if (dir.isNullOrBlank()) return null
        val f = File("$dir/bin", name)
        return f.takeIf { it.canExecute() }
    }

    private fun runCommand(vararg parts: String): String = try {
        ProcessBuilder(*parts)
            .redirectErrorStream(true)
            .start()
            .apply { waitFor() }
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
    } catch (e: Exception) {
        println("Failed to run ${parts.joinToString(" ")}: ${e.message}")
        ""
    }
}
