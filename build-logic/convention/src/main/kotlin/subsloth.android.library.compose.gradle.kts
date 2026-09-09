plugins {
    id("subsloth.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        layout.settingsDirectory.file("config/compose_stability.conf"),
    )
}
