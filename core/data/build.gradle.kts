plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(project(":core:database"))
            implementation(project(":core:preferences"))
        }
    }
}
