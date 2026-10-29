import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.metrolist.desktop"
version = "1.0-SNAPSHOT"

// DESKTOP: the custom title bar needs the JBR-only com.jetbrains.JBR API, so the desktop
// module compiles, runs and packages against JetBrains Runtime 21 instead of Temurin.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

val jbrLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.JETBRAINS)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":innertube"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation(compose.foundation)

    // DESKTOP: thin interface jar for com.jetbrains.JBR; the implementation ships inside the JBR.
    implementation(libs.jbr.api)
    // DESKTOP: Windows media overlay and keyboard media keys (SMTC). MIT.
    implementation("dev.nucleusframework:nucleus.media-control:2.5.18")

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
        // DESKTOP: Compose uses the Gradle JVM for run/package by default, so point it at the
        // JBR toolchain explicitly or the app would still start on Temurin.
        javaHome = jbrLauncher.get().metadata.installationPath.asFile.absolutePath
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
            // DESKTOP: AppCDS. The first run writes a class-data archive next to the app on exit, later
            // starts map it instead of loading/verifying thousands of classes again. A missing or stale
            // archive is ignored by the JVM. jpackage expands APPDIR; dev runs rewrite it below.
            "-XX:+AutoCreateSharedArchive",
            "-XX:SharedArchiveFile=${'$'}APPDIR/metrolist.jsa",
        )
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "Lindale"
            // Windows installers need a numeric version; the "-beta" label lives in BuildConfig.VERSION_NAME.
            packageVersion = "0.1.1"
            // Ship the whole runtime: a missing JDK module only shows up as a crash on the tester's PC.
            includeAllModules = true
            // paketle.bat copies VLC into appResources/windows/vlc so playback works without a VLC install.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "Lindale"
                shortcut = true
                // DESKTOP: fixed forever, so a new Lindale installer upgrades the old one instead of
                // installing a second copy. Never change it.
                upgradeUuid = "1ca9abb7-22db-403d-a97f-d99f01612e32"
                perUserInstall = true
                dirChooser = true
            }
        }
    }
}


// DESKTOP: jlink only writes JAVA_VERSION/MODULES to runtime/release, so record the toolchain
// vendor there as well; without this the packaged runtime is indistinguishable from Temurin.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    freeArgs.addAll("--release-info", "add:IMPLEMENTOR=${jbrLauncher.get().metadata.vendor}")
}

// DESKTOP: jlink copies only the JCEF DLLs into the app runtime, not Chromium's data files (icudtl.dat,
// *.pak, v8 snapshots, locales/) or its helper. Without them the embedded "Sign in with Google" browser
// dies at start-up. Copy every missing JCEF/Chromium file from the JBR into the runtime image.
val jcefFilePattern = Regex(
    "(?i).*(cef|helper|locales/|swiftshader|vulkan|chrome_elf|libegl|libglesv2|d3dcompiler|dxcompiler|dxil)" +
        ".*|.*\\.(pak|dat|bin)",
)

fun copyJcefFiles(runtimeDir: File, log: org.gradle.api.logging.Logger) {
    val jbrHome = jbrLauncher.get().metadata.installationPath.asFile
    var copied = 0
    for (top in listOf("bin", "lib")) {
        val src = File(jbrHome, top)
        if (!src.isDirectory) continue
        src.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(jbrHome).invariantSeparatorsPath
            if (rel.startsWith("lib/modules") || rel.contains("/jmods/") || !jcefFilePattern.matches(rel)) return@forEach
            val target = File(runtimeDir, rel)
            if (!target.exists()) {
                file.copyTo(target)
                copied++
            }
        }
    }
    log.lifecycle("JCEF: copied $copied missing file(s) into ${runtimeDir.name}")
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    doLast {
        val out = destinationDir.get().asFile
        // The runtime root is the folder whose bin/ holds jcef.dll (the output dir itself or one level down).
        val root = sequenceOf(out).plus(out.listFiles().orEmpty().asSequence())
            .firstOrNull { File(it, "bin/jcef.dll").isFile || File(it, "lib/libjcef.so").isFile }
        if (root != null) copyJcefFiles(root, logger) else logger.warn("JCEF: no jcef library in ${out}")
    }
}

// Dev runs (gradlew :desktop:run): logs in the repo + native memory tracking for MemoryMonitor. Not in packaged builds.
tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        systemProperty("metrolist.logDir", rootProject.file("logs").absolutePath)
        jvmArgs("-XX:NativeMemoryTracking=summary")
        // DESKTOP: there is no APPDIR in dev runs; keep the CDS archive in the build folder.
        val cdsFile = layout.buildDirectory.file("cds/run.jsa").get().asFile
        doFirst {
            cdsFile.parentFile.mkdirs()
            jvmArgs = jvmArgs.orEmpty().map {
                if (it.startsWith("-XX:SharedArchiveFile=")) "-XX:SharedArchiveFile=${cdsFile.absolutePath}" else it
            }
        }
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
    javaLauncher.set(jbrLauncher)
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
