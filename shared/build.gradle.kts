plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp")
    id("org.jetbrains.compose")
    alias(libs.plugins.compose.compiler)
    id("androidx.room") version "2.8.5"
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
                implementation(libs.room.runtime)
                implementation("androidx.sqlite:sqlite-bundled:2.5.0-alpha01")
                // coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation(project(":innertube"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
                implementation(compose.ui)

                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.viewmodel.compose)
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")
                implementation(libs.datastore)
                implementation(libs.coil)
                implementation(libs.coil.network.ktor)
                implementation(libs.materialKolor)
                implementation(libs.compose.reorderable)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.encoding)
                implementation(libs.ktor.serialization.json)
                implementation(libs.protobuf.javalite)
                implementation(libs.protobuf.kotlin.lite)
                implementation(libs.kuromoji.ipadic)
                implementation(libs.tinypinyin)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)
                implementation("uk.co.caprica:vlcj:4.8.3")
                implementation(libs.json)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspCommonMainMetadata", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}









