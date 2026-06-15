plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:domain"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.datetime)
        }
    }
}
