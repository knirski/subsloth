plugins {
    id("subsloth.android.library")
}

android {
    namespace = "net.subsloth.core.ui"
}

dependencies {
    implementation(project(":core:model"))
}
