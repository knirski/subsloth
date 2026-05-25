plugins {
    id("subsloth.android.library")
}

android {
    namespace = "net.subsloth.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.annotation)
}
