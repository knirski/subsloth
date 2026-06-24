plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
        }

        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.coroutines.test)
            implementation(libs.junit.jupiter.params)
        }
    }
}
