plugins {
    id("subsloth.android.feature")
}

android {
    namespace = "subsloth.feature.settings"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:assertions"))
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
}
