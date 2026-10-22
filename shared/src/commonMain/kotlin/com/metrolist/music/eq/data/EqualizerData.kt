package com.metrolist.music.eq.data
import com.metrolist.music.platform.AppContext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Serializable
enum class FilterType {
    PK, LSC, HSC, LPQ, HPQ
}

@Serializable
data class ParametricEQBand(
    val filterType: FilterType = FilterType.PK,
    val frequency: Double = 1000.0,
    val gain: Double = 0.0,
    val q: Double = 1.0,
    val enabled: Boolean = true,
)

@Serializable
data class ParametricEQ(
    val bands: List<ParametricEQBand> = emptyList(),
    val preamp: Double = 0.0,
)

@Serializable
data class SavedEQProfile(
    val id: String = "",
    val name: String = "",
    val deviceModel: String? = null,
    val source: String? = null,
    val rig: String? = null,
    val bands: List<ParametricEQBand> = emptyList(),
    val preamp: Double = 0.0,
    val isCustom: Boolean = false,
    val isActive: Boolean = false,
)

@Serializable
private data class EqStoreFile(
    val profiles: List<SavedEQProfile> = emptyList(),
    val activeProfileId: String? = null,
)

/** Process-wide EQ profile store persisted to eq_profiles.json in the app data folder. */
internal object EqStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file: File by lazy { File(AppContext().filesDir, "eq_profiles.json") }
    private val lock = Any()

    val profiles = MutableStateFlow<List<SavedEQProfile>>(emptyList())
    val activeProfile = MutableStateFlow<SavedEQProfile?>(null)

    init {
        runCatching {
            if (file.exists()) {
                val data = json.decodeFromString(EqStoreFile.serializer(), file.readText())
                profiles.value = data.profiles
                activeProfile.value = data.profiles.find { it.id == data.activeProfileId }
            }
        }.onFailure { timber.log.Timber.e(it, "EqStore: load failed") }
    }

    fun update(newProfiles: List<SavedEQProfile>, activeId: String?) = synchronized(lock) {
        profiles.value = newProfiles
        activeProfile.value = newProfiles.find { it.id == activeId }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(EqStoreFile.serializer(), EqStoreFile(newProfiles, activeProfile.value?.id)))
        }.onFailure { timber.log.Timber.e(it, "EqStore: save failed") }
    }
}

class EQProfileRepository {
    val profiles: StateFlow<List<SavedEQProfile>> = EqStore.profiles.asStateFlow()
    val activeProfile: StateFlow<SavedEQProfile?> = EqStore.activeProfile.asStateFlow()

    fun getSortedProfiles(): List<SavedEQProfile> = EqStore.profiles.value.sortedWith(
        compareBy({ it.isCustom }, { it.name })
    )

    suspend fun saveProfiles(newProfiles: List<SavedEQProfile>) {
        val ids = newProfiles.map { it.id }.toSet()
        EqStore.update(EqStore.profiles.value.filter { it.id !in ids } + newProfiles, EqStore.activeProfile.value?.id)
    }

    suspend fun importCustomProfile(name: String, parametricEQ: ParametricEQ?) {
        if (parametricEQ == null) return
        val profile = SavedEQProfile(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            bands = parametricEQ.bands,
            preamp = parametricEQ.preamp,
            isCustom = true
        )
        EqStore.update(EqStore.profiles.value + profile, EqStore.activeProfile.value?.id)
    }

    suspend fun setActiveProfile(profileId: String?) {
        EqStore.update(EqStore.profiles.value, profileId)
    }

    suspend fun deleteProfile(profileId: String) {
        val activeId = EqStore.activeProfile.value?.id?.takeIf { it != profileId }
        EqStore.update(EqStore.profiles.value.filter { it.id != profileId }, activeId)
    }
}

object ParametricEQParser {
    fun parseText(content: String): ParametricEQ? {
        val bands = mutableListOf<ParametricEQBand>()
        var preamp = 0.0
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Preamp:", ignoreCase = true)) {
                preamp = trimmed.substringAfter(":").trim().removeSuffix("dB").trim().toDoubleOrNull() ?: 0.0
            } else if (trimmed.startsWith("Filter", ignoreCase = true)) {
                // e.g. Filter 1: ON PK Fc 1000 Hz Gain 2.0 dB Q 1.0
                val enabled = !trimmed.contains("OFF", ignoreCase = true)
                val type = when {
                    trimmed.contains("LSC", ignoreCase = true) -> FilterType.LSC
                    trimmed.contains("HSC", ignoreCase = true) -> FilterType.HSC
                    trimmed.contains("LPQ", ignoreCase = true) -> FilterType.LPQ
                    trimmed.contains("HPQ", ignoreCase = true) -> FilterType.HPQ
                    else -> FilterType.PK
                }
                val fc = Regex("""Fc\s+([\d.]+)""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1000.0
                val gain = Regex("""Gain\s+([-]?[\d.]+)""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val q = Regex("""Q\s+([\d.]+)""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                bands.add(ParametricEQBand(type, fc, gain, q, enabled))
            }
        }
        return ParametricEQ(bands, preamp)
    }

    fun validate(eq: ParametricEQ?): List<String> {
        if (eq == null) return listOf("Empty or unparseable EQ data")
        if (eq.bands.isEmpty()) return listOf("No filter bands found")
        return emptyList()
    }
}

/**
 * Headphone presets from the AutoEq project. The model index (results/INDEX.md) is cached in the
 * app data folder and each profile's ParametricEQ.txt is downloaded on demand.
 */
class GitHubAutoEqSearch(context: Any? = null) {
    data class AutoEqEntry(
        val label: String = "",
        val source: String = "",
        val rig: String = "",
        val form: String = "",
        val path: String = "",
    )

    private val indexFile = File(AppContext().filesDir, "autoeq/INDEX.md")

    fun isDatabaseCached(): Boolean = indexFile.isFile && indexFile.length() > 0

    suspend fun buildIndex(): Boolean = withContext(Dispatchers.IO) {
        val stale = !isDatabaseCached() || System.currentTimeMillis() - indexFile.lastModified() > 7L * 24 * 3600 * 1000
        if (stale) {
            val ok = runCatching {
                val text = httpGet("$RESULTS_URL/INDEX.md")
                indexFile.parentFile?.mkdirs()
                indexFile.writeText(text)
            }.onFailure { timber.log.Timber.e(it, "AutoEq: index download failed") }.isSuccess
            if (!ok && !isDatabaseCached()) return@withContext false
        }
        entries = parseIndex(indexFile.readText())
        entries.isNotEmpty()
    }

    suspend fun downloadDatabase(progress: (Float) -> Unit = {}): Boolean {
        progress(0f)
        return buildIndex().also { progress(1f) }
    }

    suspend fun search(query: String): List<AutoEqEntry> = ensureEntries().filter { it.label.contains(query, ignoreCase = true) }

    suspend fun searchModels(query: String): Map<String, List<AutoEqEntry>> {
        val q = query.trim()
        return ensureEntries()
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
            .groupBy { it.label }
            .toList()
            .take(if (q.isEmpty()) 200 else 500)
            .toMap()
    }

    suspend fun getVariantsForModel(modelName: String): List<AutoEqEntry> = ensureEntries().filter { it.label == modelName }

    suspend fun loadEQ(entry: AutoEqEntry): ParametricEQ? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = entry.path.removePrefix("./").trimEnd('/')
            val folderName = URLDecoder.decode(dir.substringAfterLast('/'), StandardCharsets.UTF_8)
            val fileName = URLEncoder.encode("$folderName ParametricEQ.txt", StandardCharsets.UTF_8).replace("+", "%20")
            ParametricEQParser.parseText(httpGet("$RESULTS_URL/$dir/$fileName"))
        }.onFailure { timber.log.Timber.e(it, "AutoEq: profile download failed for %s", entry.label) }.getOrNull()
    }

    private suspend fun ensureEntries(): List<AutoEqEntry> {
        if (entries.isEmpty()) buildIndex()
        return entries
    }

    private fun parseIndex(markdown: String): List<AutoEqEntry> =
        markdown.lineSequence().mapNotNull { line ->
            val m = INDEX_LINE.find(line.trim()) ?: return@mapNotNull null
            val label = m.groupValues[1]
            val path = m.groupValues[2]
            val segments = path.removePrefix("./").split('/').map { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            val source = m.groupValues[3].ifBlank { segments.getOrNull(0).orEmpty() }
            val rigAndForm = segments.getOrNull(1).orEmpty()
            val form = FORMS.firstOrNull { rigAndForm.contains(it, ignoreCase = true) } ?: rigAndForm
            val rig = m.groupValues[4].ifBlank { rigAndForm.replace(form, "", ignoreCase = true).trim().ifEmpty { rigAndForm } }
            AutoEqEntry(label = label, source = source, rig = rig, form = form, path = path)
        }.toList()

    private fun httpGet(url: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Metrolist-Desktop")
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode} for $url" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val RESULTS_URL = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
        val INDEX_LINE = Regex("""^- \[(.+?)]\((\./.+?)\)(?:\s+by\s+(.+?))?(?:\s+on\s+(.+?))?\s*$""")
        val FORMS = listOf("over-ear", "in-ear", "earbud")

        @Volatile
        var entries: List<AutoEqEntry> = emptyList()
    }
}
