plugins {
    id("subsloth.android.application.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.compose.screenshot)
}

val appVersionName: String by lazy {
    val tag: String =
        try {
            providers
                .exec {
                    workingDir = rootProject.projectDir
                    commandLine("git", "describe", "--tags", "--abbrev=0", "--match=v*")
                    isIgnoreExitValue = true
                }.standardOutput.asText
                .get()
                .trim()
                .removePrefix("v")
        } catch (_: Exception) {
            ""
        }
    if (tag.isBlank()) "0.0.1" else tag
}

val appVersionCode: Int by lazy {
    // Strip SemVer pre-release suffix (e.g. "1.0.0-rc.1" -> "1.0.0") to get
    // a purely numeric version for the integer code.  The pre-release label
    // is preserved in versionName.
    val numeric = appVersionName.substringBefore("-").substringBefore("+")
    require(numeric.matches(Regex("""\d+\.\d+\.\d+"""))) {
        "Version '$appVersionName' must be SemVer 'major.minor.patch' (got '$numeric')"
    }
    val parts = numeric.split(".")
    val major = parts[0].toInt()
    val minor = parts[1].toInt()
    val patch = parts[2].toInt()
    // Max safe: 999.999.999
    require(major < 1000) { "Major version $major would overflow versionCode" }
    require(minor < 1000) { "Minor version $minor would overflow versionCode" }
    require(patch < 1000) { "Patch version $patch would overflow versionCode" }
    major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "net.subsloth"

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "net.subsloth"
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                output.outputFileName.set(
                    "subsloth-$appVersionName-${variant.buildType}.apk",
                )
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:media"))

    // Room and SQLite — needed for SubSlothDatabase access
    implementation(libs.room3.runtime)
    implementation(libs.sqlite.bundled)

    // Ktor — needed for ClientFactory and Api access
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(project(":feature:auth"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:details"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))
    implementation(project(":feature:settings"))

    // ── AndroidX Jetpack Compose — platform-specific dependencies ──────────
    //
    // This module (the Android application shell) uses the official AndroidX
    // Jetpack Compose BOM and artifacts rather than the compose-multiplatform
    // variants used by :feature:* and :core:ui modules.  The reasons:
    //
    // 1. Android TV requires `androidx.tv:tv-foundation` and
    //    `androidx.tv:tv-material` which are AndroidX-only — mixing BOM
    //    variants in the same dependency graph is cleanest when the shell
    //    owns the BOM.
    //
    // 2. Adaptive layout (`material3.adaptive.*`) and other Android-only
    //    APIs (Activity, WindowInsets, system bars) are only available
    //    through AndroidX artifacts.
    //
    // Shared feature modules use org.jetbrains.compose.* (multiplatform) so
    // they compile for all targets (JVM desktop, Wasm web, and iOS when
    // re-enabled).  The Android shell retains AndroidX for full fidelity to
    // platform capabilities.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.datastore.preferences)

    implementation(libs.kermit)

    implementation(libs.profileinstaller)

    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:tv-focus-harness"))

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(platform(libs.androidx.compose.bom))
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.kotlinx.collections.immutable)
}
