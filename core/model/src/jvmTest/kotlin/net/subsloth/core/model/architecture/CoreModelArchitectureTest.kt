package net.subsloth.core.model.architecture

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Architecture boundary tests ensuring [net.subsloth.core.model] remains free
 * of Android framework, Compose, network, persistence, and media dependencies.
 *
 * `:core:model` does not depend on a Compose runtime artifact. Compose
 * stability for its types is supplied to consuming UI modules via the
 * checked-in stability configuration file (`config/compose_stability.conf`),
 * not via a compile-time dependency here.
 */
class CoreModelArchitectureTest {
    @Test
    fun `model module has no Android framework imports`() {
        val violations =
            findForbiddenImports(
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
    fun `model module has no Compose imports`() {
        val violations = findForbiddenImports("androidx.compose", "androidx.tv")
        assertThat(violations).isEmpty()
    }

    @Test
    fun `model module has no network implementation imports`() {
        val violations =
            findForbiddenImports(
                "retrofit",
                "okhttp",
                "kotlinx.serialization",
                "net.subsloth.core.network",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `model module has no persistence imports`() {
        val violations =
            findForbiddenImports(
                "androidx.room",
                "androidx.datastore",
                "android.security",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `model module has no media player imports`() {
        val violations = findForbiddenImports("androidx.media3", "android.media", "io.github.kdroidfilter")
        assertThat(violations).isEmpty()
    }

    @Test
    fun `model module has no UI imports`() {
        val violations =
            findForbiddenImports(
                "androidx.activity",
                "androidx.fragment",
                "androidx.navigation",
            )
        assertThat(violations).isEmpty()
    }

    @Test
    fun `model module has no WorkManager imports`() {
        val violations = findForbiddenImports("androidx.work")
        assertThat(violations).isEmpty()
    }

    @Test
    fun `model module has no notification imports`() {
        val violations =
            findForbiddenImports(
                "android.app.Notification",
                "androidx.core.app.Notification",
                "androidx.media.app.Notification",
            )
        assertThat(violations).isEmpty()
    }

    private val allModelImports: List<String> by lazy {
        val baseDir =
            System.getProperty("user.dir")
                ?: error("user.dir system property is required")
        val modelSourceDir =
            Paths
                .get(baseDir, "src", "commonMain", "kotlin")
        if (!modelSourceDir.toFile().isDirectory) {
            error("Model source directory not found at $modelSourceDir")
        }
        val result = mutableListOf<String>()
        Files.walk(modelSourceDir).use { walkStream ->
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

    private fun findForbiddenImports(vararg forbiddenPrefixes: String): List<String> = allModelImports.filter { line ->
        val importTarget = line.removePrefix("import").trim()
        forbiddenPrefixes.any { prefix -> importTarget.startsWith(prefix) }
    }
}
