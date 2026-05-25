plugins {
    id("subsloth.android.feature")
}

android {
    namespace = "net.subsloth.feature.auth"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))

    implementation(libs.androidx.activity.compose)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:assertions"))
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
}
