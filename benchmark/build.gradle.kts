plugins {
    id("com.android.test")
}

android {
    namespace = "net.subsloth.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37

        // Suppress expected benchmark errors for local/emulator runs.
        // On CI a physical device with a release build is used for accurate
        // measurements; the suppressions allow non-representative runs to
        // at least exercise the benchmark code paths for smoke-testing.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "EMULATOR,DEBUGGABLE"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true

    kotlin {
        jvmToolchain(17)
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = false
    }

    // Points to the app module under test
    targetProjectPath = ":androidApp"
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
