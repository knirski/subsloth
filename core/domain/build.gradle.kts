plugins {
    id("subsloth.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.datetime)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:assertions"))
    testImplementation(libs.coroutines.test)
}
