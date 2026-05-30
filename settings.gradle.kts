pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "subsloth"

include(":app")
include(":core:model")
include(":core:domain")
include(":core:network")
include(":core:database")
include(":core:preferences")
include(":core:media")
include(":core:datasource-ktor")
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
include(":testing:screenshots")
include(":testing:tv-focus-harness")
