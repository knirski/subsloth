package net.subsloth.buildlogic.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-import boundary guards for `:core:network` and the `:feature:*`
 * modules, complementing [DependencyGraphInvariantTest]'s resolved-graph
 * checks with explicit `commonMain` import rules.
 *
 * Scanning only `commonMain` is deliberate: platform source sets may
 * legitimately import Android/Apple/JS APIs, while `commonMain` compiles
 * for every target and must stay framework-free.
 */
class SourceImportBoundaryTest {
    private val rootDir = File(
        System.getProperty("subsloth.rootDir")
            ?: error("System property 'subsloth.rootDir' is not set — run this test via Gradle."),
    )

    @Test
    fun `core network commonMain has no framework, UI, media player, or adapter imports`() {
        val violations = importsIn(File(rootDir, "core/network/src/commonMain/kotlin"))
            .filter { importLine -> CORE_NETWORK_FORBIDDEN_IMPORTS.any(importLine::startsWith) }

        assertTrue(
            violations.isEmpty(),
            "Forbidden imports in :core:network commonMain:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `feature commonMain has no sibling feature or adapter module imports`() {
        val featureDirs = File(rootDir, "feature").listFiles().orEmpty().filter { it.isDirectory }
        val violations = featureDirs.flatMap { feature ->
            val siblingPrefixes = featureDirs
                .filter { it.name != feature.name }
                .map { "net.subsloth.${it.name}." }
            importsIn(File(feature, "src/commonMain/kotlin"))
                .filter { importLine -> (FEATURE_FORBIDDEN_IMPORTS + siblingPrefixes).any(importLine::startsWith) }
                .map { "${feature.name}: $it" }
        }

        assertTrue(
            violations.isEmpty(),
            "Forbidden imports in feature commonMain:\n${violations.joinToString("\n")}",
        )
    }

    private fun importsIn(sourceDir: File): List<String> {
        require(sourceDir.isDirectory) { "Source directory not found: $sourceDir" }
        return sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import").trim() }
            }
            .toList()
    }

    private companion object {
        val CORE_NETWORK_FORBIDDEN_IMPORTS = listOf(
            "android.",
            "androidx.",
            "io.github.kdroidfilter.",
            "net.subsloth.core.data",
            "net.subsloth.database",
            "net.subsloth.preferences",
            "net.subsloth.catalog",
            "net.subsloth.details",
            "net.subsloth.player",
            "net.subsloth.library",
            "net.subsloth.settings",
            "net.subsloth.auth",
        )

        val FEATURE_FORBIDDEN_IMPORTS = listOf(
            "net.subsloth.core.network",
            "net.subsloth.core.data",
            "net.subsloth.database",
            "net.subsloth.preferences",
        )
    }
}
