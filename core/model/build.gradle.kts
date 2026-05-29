plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform runtime provides @Stable / @Immutable for all targets.
            // api required for KMP native targets — compileOnly not supported on non-JVM.
            api(libs.compose.runtime)
        }
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
        }
    }
}
