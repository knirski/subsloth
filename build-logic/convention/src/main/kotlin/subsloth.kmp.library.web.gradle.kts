plugins {
    id("subsloth.kmp.library")
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "subsloth.js"
            }
        }
    }
}
