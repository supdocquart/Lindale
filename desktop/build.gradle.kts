plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.metrolist.desktop"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":innertube"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation(compose.foundation)
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
}


compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs("-Dmetrolist.logDir=" + rootProject.file("logs").absolutePath)
        // Keep the heap small and hand unused memory back to Windows instead of holding on to it.
        // Serial GC and the C1-only JIT carry far less native overhead than G1 + C2 for a UI-sized heap.
        jvmArgs(
            "-XX:+UseSerialGC",
            "-XX:TieredStopAtLevel=1",
            "-Xms64m",
            "-Xmx768m",
            "-XX:MinHeapFreeRatio=10",
            "-XX:MaxHeapFreeRatio=25",
            "-XX:+UseStringDeduplication",
            "-XX:ReservedCodeCacheSize=64m",
            // Diagnostic: lets MemoryMonitor report the JVM's own native memory. Remove after measuring.
            "-XX:NativeMemoryTracking=summary",
        )
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "MetrolistDesktop"
            packageVersion = "1.0.0"
            modules("jdk.httpserver")
        }
    }
}

