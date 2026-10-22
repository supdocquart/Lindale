package com.metrolist.music.platform

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Coil's JVM default sizes the in-memory image cache at a share of the maximum heap,
 * which on a desktop can reach hundreds of MB. Album art is cheap to re-decode from disk.
 */
internal object ImageLoaderSetup {
    private const val MEMORY_CACHE_BYTES = 64L * 1024 * 1024
    private const val DISK_CACHE_BYTES = 512L * 1024 * 1024

    fun install() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).strongReferencesEnabled(true).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(DesktopFiles.cacheDir, "images").toOkioPath())
                        .maxSizeBytes(DISK_CACHE_BYTES)
                        .build()
                }
                .build()
        }
    }

    /** Drops decoded images (e.g. while minimized); they reload from the disk cache. */
    fun trimMemory() {
        runCatching { SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache?.clear() }
    }
}
