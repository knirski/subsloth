plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
        }
    }
}
