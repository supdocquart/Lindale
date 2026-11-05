package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    // DESKTOP: Lindale's own releases, not upstream's Android ones.
    private const val GITHUB_API_BASE = com.metrolist.music.platform.AppBrand.REPO_API

    /** "v0.2.0-beta" vs "0.1.1": numbers first; with equal numbers a final release beats a "-beta". */
    fun compareVersions(v1: String, v2: String): Int {
        fun numbers(v: String) = v.trim().removePrefix("v").substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }
        val v1Parts = numbers(v1)
        val v2Parts = numbers(v2)
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        val pre1 = v1.contains('-')
        val pre2 = v2.contains('-')
        return when {
            pre1 == pre2 -> 0
            pre1 -> -1
            else -> 1
        }
    }

    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    private fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            
            // DESKTOP: Windows installers (.exe preferred) plus the upstream APK names below.
            if (!name.endsWith(".apk") && !name.endsWith(".exe") && !name.endsWith(".msi")) continue
            
            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.getLong("size")
            
            val (arch, variant) = when {
                name.endsWith(".exe") -> "universal" to "foss"
                name.endsWith(".msi") -> "universal" to "msi"
                name == "Metrolist.apk" -> "universal" to "apk"
                name == "Metrolist-with-Google-Cast.apk" -> "universal" to "gms"
                name.startsWith("app-") && name.endsWith("-release.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-release.apk")
                    arch to "foss"
                }
                name.startsWith("app-") && name.endsWith("-with-Google-Cast.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-with-Google-Cast.apk")
                    arch to "gms"
                }
                else -> null to null
            }
            
            if (arch != null && variant != null) {
                assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
            }
        }
        
        return assets
    }

    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }
                
                // DESKTOP: /releases/latest skips pre-releases, and every Lindale release so far is one,
                // so take the newest published release from the list instead.
                val response = client.get("$GITHUB_API_BASE/releases?per_page=10")
                    .bodyAsText()
                val list = JSONArray(response)
                val json = (0 until list.length()).map { list.getJSONObject(it) }
                    .filter { !it.optBoolean("draft") }
                    .maxWithOrNull { a, b -> compareVersions(a.getString("tag_name"), b.getString("tag_name")) }
                    ?: error("no releases")

                val releaseInfo = ReleaseInfo(
                    tagName = json.getString("tag_name"),
                    versionName = json.getString("tag_name").removePrefix("v"),
                    description = json.optString("body"),
                    releaseDate = json.optString("published_at"),
                    assets = parseAssets(json.getJSONArray("assets"))
                )

                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }
        }

    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }
                
                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true
                
                while (hasMore && page <= 10) {
                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
                        .bodyAsText()
                    val json = JSONArray(response)
                    
                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }
                    
                    for (i in 0 until json.length()) {
                        val releaseObj = json.getJSONObject(i)
                        releases.add(ReleaseInfo(
                            tagName = releaseObj.getString("tag_name"),
                            versionName = releaseObj.getString("name"),
                            description = releaseObj.getString("body"),
                            releaseDate = releaseObj.getString("published_at"),
                            assets = parseAssets(releaseObj.getJSONArray("assets"))
                        ))
                    }
                    
                    page++
                }
                
                cachedAllReleases = releases
                releases
            }
        }

    /** DESKTOP: the release page, where Windows and Linux users each pick their download. */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? =
        "${com.metrolist.music.platform.AppBrand.REPO_URL}/releases/tag/${releaseInfo.tagName}"

    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        cachedReleaseInfo!!.versionName
                    )
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }
                
                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        releaseInfo.versionName
                    )
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }
        }

    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo

    /** DESKTOP: newest release found by the start-up check (the running version when there is none). */
    val latestVersion = kotlinx.coroutines.flow.MutableStateFlow(BuildConfig.BASE_VERSION_NAME)
}
