package net.subsloth.buildlogic.architecture

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Enforces the "Feature Adapter Isolation" and "Transport-Only Network Module"
 * requirements from the `enforce-architecture-boundaries` change by inspecting the
 * *resolved* Gradle dependency graph rather than scanning source imports.
 *
 * Source-import scanning (like the old `CoreModelArchitectureTest`) only ever sees
 * `commonMain`, so a violation introduced only in `androidMain`/`jvmMain`/`wasmJsMain`
 * (or arriving transitively through a dependency that itself has no offending import)
 * would slip through undetected. Running the real `dependencies` task via Gradle
 * TestKit and inspecting its output instead catches a forbidden module no matter which
 * source set or transitive path it comes in through, because Gradle has already fully
 * resolved the classpath by the time this text is produced.
 *
 * Each `:feature:*` module and `:core:network` is checked against both
 * `jvmCompileClasspath` and `wasmJsCompileClasspath` (the two resolvable
 * configurations that print full `project ':...'` paths for project dependencies;
 * `commonMainImplementation` is declared but not directly resolvable).
 *
 * One `GradleRunner` build is run per (module, configuration) pair — deliberately
 * *not* batched into fewer invocations. Empirically, passing several
 * `:x:dependencies` task paths together with a single `--configuration` argument
 * only filters the *first* task's report; every subsequent `dependencies` task in
 * the same invocation ignores the filter and prints its full, unfiltered report
 * (all configurations). That would make forbidden-string matching produce false
 * positives (matching sections that were never restricted to the configuration
 * under test). Running exactly one module+configuration per build avoids this
 * entirely and was verified to filter correctly in isolation.
 */
class DependencyGraphInvariantTest {

    private data class ModuleRule(val modulePath: String, val forbidden: List<String>)

    private val rules = listOf(
        ModuleRule(":feature:auth", FEATURE_FORBIDDEN_MODULES),
        ModuleRule(":feature:catalog", FEATURE_FORBIDDEN_MODULES),
        ModuleRule(":feature:details", FEATURE_FORBIDDEN_MODULES),
        ModuleRule(":feature:player", FEATURE_FORBIDDEN_MODULES),
        ModuleRule(":feature:library", FEATURE_FORBIDDEN_MODULES),
        ModuleRule(":feature:settings", FEATURE_FORBIDDEN_MODULES),
        ModuleRule(":core:network", NETWORK_FORBIDDEN_MODULES),
    )

    @TestFactory
    fun `resolved dependency graph has no forbidden module edges`(): List<DynamicTest> =
        rules.flatMap { rule ->
            CONFIGURATIONS.map { configuration ->
                DynamicTest.dynamicTest("${rule.modulePath} / $configuration") {
                    val output = runDependenciesTask(rule.modulePath, configuration)
                    rule.forbidden.forEach { forbiddenModule ->
                        val needle = "project '$forbiddenModule'"
                        assertFalse(
                            output.contains(needle),
                            "Module ${rule.modulePath} resolves forbidden dependency " +
                                "'$forbiddenModule' on configuration '$configuration'. " +
                                "Feature/core modules must not depend on concrete adapter " +
                                "modules (:core:network, :core:database, :core:preferences, " +
                                ":core:data) — see the \"Feature Adapter Isolation\" and " +
                                "\"Transport-Only Network Module\" requirements.\n" +
                                "Full dependency report:\n$output",
                        )
                    }
                }
            }
        }

    /**
     * Runs `<modulePath>:dependencies --configuration <configuration>` for a single
     * module via Gradle TestKit and returns the captured build output.
     *
     * Without `withTestKitDir`, GradleRunner defaults to an isolated working
     * directory under this test task's own temp folder (`build/tmp/test/work/
     * .gradle-test-kit`), which has its own separate dependency cache — it does
     * NOT reuse `GRADLE_USER_HOME` automatically, and that isolated directory does
     * not survive between CI runs on ephemeral runners. `withTestKitDir` is pointed
     * explicitly at `gradle.gradleUserHomeDir` (the *outer* build's actual resolved
     * Gradle user home, wired in via the `subsloth.gradleUserHome` system property)
     * so these nested builds share the same, already-warm dependency cache that
     * other Gradle steps in the same CI job already populated.
     */
    private fun runDependenciesTask(modulePath: String, configuration: String): String {
        val rootDir = File(System.getProperty("subsloth.rootDir"))
        val gradleUserHome = File(System.getProperty("subsloth.gradleUserHome"))
        val result = GradleRunner.create()
            .withProjectDir(rootDir)
            .withTestKitDir(gradleUserHome)
            .withArguments("$modulePath:dependencies", "--configuration", configuration)
            .build()
        return result.output
    }

    private companion object {
        val CONFIGURATIONS = listOf("jvmCompileClasspath", "wasmJsCompileClasspath")

        val FEATURE_FORBIDDEN_MODULES = listOf(
            ":core:network",
            ":core:database",
            ":core:preferences",
            ":core:data",
        )

        val NETWORK_FORBIDDEN_MODULES = listOf(
            ":core:database",
            ":core:preferences",
        )
    }
}
