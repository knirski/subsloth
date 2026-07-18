plugins {
    id("com.android.test")
}

android {
    namespace = "net.subsloth.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

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
