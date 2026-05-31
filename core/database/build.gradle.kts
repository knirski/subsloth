plugins {
    id("subsloth.kmp.library")
    alias(libs.plugins.room3)
    alias(libs.plugins.ksp)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.room3.runtime)
        }

        jvmMain.dependencies {
            implementation(libs.sqlite.bundled)
        }

        iosMain.dependencies {
            implementation(libs.sqlite.framework)
        }

        wasmJsMain.dependencies {
            implementation(libs.sqlite.web)
            implementation(libs.kotlinx.browser)
        }

        jvmTest.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

dependencies {
    kspJvm(libs.room3.compiler)
    kspWasmJs(libs.room3.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
