@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val useMavenLocalInnerTubeX = providers.gradleProperty("useMavenLocalInnerTubeX").isPresent

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        exclusiveContent {
            forRepository {
                if (useMavenLocalInnerTubeX) mavenLocal() else maven("https://jitpack.io")
            }
            filter {
                if (useMavenLocalInnerTubeX) {
                    includeModule("com.github.MetrolistGroup", "innertubex")
                    includeModule("com.github.MetrolistGroup", "innertubex-desktop")
                } else {
                    includeGroup("com.github.MetrolistGroup.innertubex")
                }
            }
        }
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "Metrolist"
include(":innertube")
include(":desktop")

include(":shared")
