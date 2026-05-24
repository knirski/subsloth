package net.subsloth.core.domain.architecture

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Architecture boundary tests ensuring [net.subsloth.core.domain] and
 * [net.subsloth.core.model] remain free of Android framework, network,
 * persistence, media, and UI dependencies.
 */
class DomainArchitectureTest {
    @Test
    fun `domain module has no Android framework imports`() {
        val violations =
            findImportsInDomainSource(
                "android.app",
                "android.content",
                "android.os",
                "android.view",
                "android.widget",
                "android.graphics",
                "android.net",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `domain module has no Compose imports`() {
        val violations =
            findImportsInDomainSource(
                "androidx.compose",
                "androidx.tv",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `domain module has no network implementation imports`() {
        val violations =
            findImportsInDomainSource(
                "retrofit",
                "okhttp",
                "kotlinx.serialization",
                "net.subsloth.core.network",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `domain module has no persistence imports`() {
        val violations =
            findImportsInDomainSource(
                "androidx.room",
                "androidx.datastore",
                "android.security",
                "java.security.spec",
                "javax.crypto",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `domain module has no media player imports`() {
        val violations =
            findImportsInDomainSource(
                "androidx.media3",
                "android.media",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `domain module has no UI imports`() {
        val violations =
            findImportsInDomainSource(
                "androidx.activity",
                "androidx.fragment",
                "androidx.navigation",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `domain module has no WorkManager imports`() {
        val violations =
            findImportsInDomainSource(
                "androidx.work",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `domain module has no filesystem imports`() {
        val violations =
            findImportsInDomainSource(
                "java.io",
                "java.nio.file",
            )
        assertThat(violations).isEmpty()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * All import lines found in `:core:domain` source files, cached after
     * the first scan so that every test method reuses the result instead of
     * re-walking the source tree.
     */
    private val allDomainImports: List<String> by lazy {
        val baseDir =
            System.getProperty("user.dir")
                ?: error("user.dir system property is required")
        val domainSourceDir =
            Paths
                .get(baseDir, "src", "main", "kotlin")

        if (!domainSourceDir.toFile().isDirectory) {
            error("Domain source directory not found at $domainSourceDir")
        }

        val result = mutableListOf<String>()
        Files.walk(domainSourceDir).use { walkStream ->
            walkStream
                .filter { it.toString().endsWith(".kt") }
                .forEach { file: Path ->
                    Files.lines(file).use { lines ->
                        lines
                            .filter { it.trimStart().startsWith("import ") }
                            .map { it.trim() }
                            .forEach { result.add(it) }
                    }
                }
        }
        result
    }

    /**
     * Returns any import lines from [allDomainImports] that reference
     * the given [forbiddenPrefixes].
     */
    private fun findImportsInDomainSource(vararg forbiddenPrefixes: String): List<String> =
        allDomainImports.filter { line ->
            forbiddenPrefixes.any { prefix ->
                line.removePrefix("import").trim().startsWith(prefix)
            }
        }
}
