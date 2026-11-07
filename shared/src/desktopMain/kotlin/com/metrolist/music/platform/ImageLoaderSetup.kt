package com.metrolist.music.platform

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import com.metrolist.music.constants.MaxImageCacheSizeKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Coil's JVM default sizes the in-memory image cache at a share of the maximum heap,
 * which on a desktop can reach hundreds of MB. Album art is cheap to re-decode from disk.
 */
internal object ImageLoaderSetup {
    private const val MEMORY_CACHE_BYTES = 64L * 1024 * 1024
    private const val DEFAULT_DISK_CACHE_MB = 512

    fun install() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).strongReferencesEnabled(true).build() }
                .apply {
                    // Storage > image cache size (MB, read at start-up like upstream; 0 turns the cache off).
                    val sizeMb = runCatching { AppContext.Default.dataStore.get(MaxImageCacheSizeKey, DEFAULT_DISK_CACHE_MB) }
                        .getOrDefault(DEFAULT_DISK_CACHE_MB)
                    if (sizeMb <= 0) {
                        diskCachePolicy(CachePolicy.DISABLED)
                    } else {
                        diskCache {
                            DiskCache.Builder()
                                .directory(File(DesktopFiles.cacheDir, "images").toOkioPath())
                                .maxSizeBytes(sizeMb * 1024L * 1024L)
                                .build()
                        }
                    }
                }
                .build()
        }
    }

    /** Drops decoded images (e.g. while minimized); they reload from the disk cache. */
    fun trimMemory() {
        runCatching { SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache?.clear() }
    }
}
