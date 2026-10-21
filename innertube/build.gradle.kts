plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                if (providers.gradleProperty("useMavenLocalInnerTubeX").isPresent) {
                    api("com.github.MetrolistGroup:innertubex:${libs.versions.innertubex.get()}")
                } else {
                    api(libs.innertubex)
                }

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.encoding)
            }
        }
    }
}
