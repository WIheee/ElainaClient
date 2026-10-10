@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenLocal()
        mavenCentral()
        maven("https://repo.opencollab.dev/maven-snapshots")
        maven("https://repo.opencollab.dev/maven-releases")

    }
}

rootProject.name = "ElainaClient"
include(":app")
include(":relay")
include(
    ":relay:Network:codec-query",
    ":relay:Network:codec-rcon",
    ":relay:Network:transport-raknet",
)
// relay/Protocol/{adventure,bedrock-codec,bedrock-connection,common} is kept in the tree as the
// vendored/newest Bedrock Protocol source, but is NOT built - :relay pulls the Bedrock
// protocol/codecs (and netty-transport-raknet) from Maven instead
// (org.cloudburstmc.protocol:*:3.0.0.Beta13-SNAPSHOT). See relay/build.gradle.kts.
