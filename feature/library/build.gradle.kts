plugins {
    id("subsloth.android.feature")
}

android {
    namespace = "subsloth.feature.library"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))

    implementation(libs.work.runtime.ktx)
}
