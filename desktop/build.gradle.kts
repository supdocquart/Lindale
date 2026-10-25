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

    // DESKTOP: SLF4J reaches us transitively (Ktor/coroutines); without a provider it prints a
    // 3-line warning on every start. Match the slf4j-api version already on runtimeClasspath.
    runtimeOnly("org.slf4j:slf4j-nop:2.0.19")
}


compose.desktop {
    application {
        mainClass = "MainKt"
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
        )
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "Metrolist"
            packageVersion = "1.0.0"
            // Ship the whole runtime: a missing JDK module only shows up as a crash on the tester's PC.
            includeAllModules = true
            // paketle.bat copies VLC into appResources/windows/vlc so playback works without a VLC install.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "Metrolist"
                shortcut = true
                perUserInstall = true
                dirChooser = true
            }
        }
    }
}


// Dev runs (Metrolist.bat): logs in the repo + native memory tracking for MemoryMonitor. Not in packaged builds.
tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        systemProperty("metrolist.logDir", rootProject.file("logs").absolutePath)
        jvmArgs("-XX:NativeMemoryTracking=summary")
    }
}

// DESKTOP: headless smoke test. The data dir is a throwaway build folder so the user's real
// %APPDATA%/Metrolist and %LOCALAPPDATA%/Metrolist are never touched.
tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Runs the headless smoke test against the real network in an isolated data dir."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("SmokeTestKt")
    workingDir = projectDir
    jvmArgs("-Djava.awt.headless=true", "-Xmx512m")

    val smokeData = layout.buildDirectory.dir("smoke-data").get().asFile
    val smokeLogs = layout.buildDirectory.dir("smoke-logs").get().asFile
    doFirst {
        smokeData.deleteRecursively()
        smokeData.mkdirs()
        smokeLogs.deleteRecursively()
        smokeLogs.mkdirs()
    }
    systemProperty("metrolist.dataDir", smokeData.absolutePath)
    systemProperty("metrolist.logDir", smokeLogs.absolutePath)
}
