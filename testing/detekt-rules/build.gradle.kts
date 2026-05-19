plugins {
    kotlin("jvm")
}

group = "net.subsloth.buildlogic"

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
}

kotlin {
    jvmToolchain(17)
}
