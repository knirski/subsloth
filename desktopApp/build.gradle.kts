plugins {
    id("subsloth.jvm.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.gradle)
}

// jvmToolchain(17), allWarningsAsErrors, JUnit Platform, spotless, detekt,
// and power-assert are configured by the subsloth.jvm.library convention plugin above.

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.components.resources)

    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:preferences"))
    implementation(project(":core:ui"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:details"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:library"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.navigation3.ui.kmp)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.savedstate)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.compose.multiplatform.ui.test.junit4)
    testImplementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.compose.media.player)
    testImplementation(kotlin("test"))
}

// Compose compiler — share the project-wide stability configuration so that
// types like kotlin.time.Instant and kotlin.time.Duration are recognised as
// stable during strong-skipping-mode analysis.
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/compose_stability.conf"),
    )
}

// Forward LD_LIBRARY_PATH from the shell to the forked desktop app JVM.
// The Nix shell sets ORG_GRADLE_PROJECT_desktopLibPath in the environment;
// gradlew forwards ORG_GRADLE_PROJECT_* vars to the daemon as project
// properties regardless of when the daemon was started.  Without this,
// the daemon would need a restart after entering the nix-shell.
val desktopLibPath = providers.gradleProperty("desktopLibPath").orNull

compose.desktop {
    application {
        mainClass = "net.subsloth.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "SubSloth"
            packageVersion = "1.0.0"
            description = "SubSloth Media Browser"
            vendor = "SubSloth"

            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

// Hot Reload is enabled by default in Compose Multiplatform 1.12.0 for
// desktop.  When running `./gradlew :desktopApp:run`, the app will
// automatically reload Composable code changes without restarting.
// Uncomment to tune:
// compose.hotReload {
//     enabled.set(true)
// }

tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    if (desktopLibPath != null) {
        environment("LD_LIBRARY_PATH", desktopLibPath)
    }
}
