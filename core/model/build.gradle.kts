plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Compose runtime provides @Stable / @Immutable for all targets.
            // compileOnly keeps it off the runtime classpath of consumers.
            // api required for KMP native targets — compileOnly not supported there.
            api(libs.compose.runtime)
        }
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
        }
    }
}
