plugins {
    kotlin("jvm")
}

group = "subsloth.buildlogic"

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
}

kotlin {
    jvmToolchain(17)
}
