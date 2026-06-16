plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.gradle)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.power.assert)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

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

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.navigation3.ui.kmp)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.savedstate)
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

tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    if (desktopLibPath != null) {
        environment("LD_LIBRARY_PATH", desktopLibPath)
    }
}

spotless {
    kotlin {
        target("src/*/kotlin/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                ),
            )
        toggleOffOn()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    config.setFrom(file("${rootProject.layout.projectDirectory}/config/detekt.yml"))
    basePath = rootProject.layout.projectDirectory.asFile
    baseline.set(file("${rootProject.layout.projectDirectory}/config/detekt-baseline.xml"))
}

dependencies {
    detektPlugins(project(":testing:detekt-rules"))
    detektPlugins(libs.compose.rules.detekt)
}
