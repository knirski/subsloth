pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        ivy("https://nodejs.org/dist") {
            name = "Node.js"
            patternLayout {
                artifact("v[revision]/[artifact]-v[revision]-linux-x64.tar.gz")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn"
            patternLayout {
                artifact("v[revision]/[artifact]-v[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.yarnpkg", "yarn")
            }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen"
            patternLayout {
                artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.github.webassembly", "binaryen")
            }
        }
    }
}

rootProject.name = "subsloth"

include(":androidApp")
include(":desktopApp")
include(":webApp")
include(":core:model")
include(":core:domain")
include(":core:network")
include(":core:database")
include(":core:preferences")
include(":core:media")
include(":core:ui")
include(":feature:auth")
include(":feature:catalog")
include(":feature:details")
include(":feature:player")
include(":feature:library")
include(":feature:settings")
include(":testing:api-contract")
include(":testing:assertions")
include(":testing:detekt-rules")
include(":testing:mock-api")
include(":testing:tv-focus-harness")
include(":benchmark")
