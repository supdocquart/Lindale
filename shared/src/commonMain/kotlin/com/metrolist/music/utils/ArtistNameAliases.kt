package com.metrolist.music.utils
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

expect object ArtistNameAliasesStorage {
    fun load(): Map<String, String>
    fun save(aliases: Map<String, String>)
    fun clear()
}

object ArtistNameAliases {
    const val BACKUP_FILENAME = "artist_name_aliases.json"

    private val _aliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val aliases = _aliases.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        _aliases.value = ArtistNameAliasesStorage.load()
        initialized = true
    }

    fun set(
        artistId: String,
        channelId: String?,
        originalName: String,
        name: String?,
    ) {
        initialize()
        val currentAliases = _aliases.value
        val aliasKeys =
            buildSet {
                add(artistId)
                channelId?.let(::add)
                add(nameKey(originalName))
                currentAliases.filterValues { it == originalName }.keys.forEach(::add)
            }
        
        _aliases.update { aliases ->
            val newAliases = aliases.toMutableMap()
            aliasKeys.forEach { if (name != null) newAliases[it] = name }
            ArtistNameAliasesStorage.save(newAliases)
            newAliases
        }
    }

    fun remove(
        artistId: String,
        channelId: String?,
        originalName: String,
    ) {
        initialize()
        val currentAliases = _aliases.value
        val aliasKeys =
            buildSet {
                add(artistId)
                channelId?.let(::add)
                add(nameKey(originalName))
                currentAliases.filterValues { it == originalName }.keys.forEach(::add)
            }
        
        _aliases.update { aliases ->
            val newAliases = aliases.toMutableMap()
            aliasKeys.forEach { newAliases.remove(it) }
            ArtistNameAliasesStorage.save(newAliases)
            newAliases
        }
    }

    fun clear() {
        initialize()
        ArtistNameAliasesStorage.clear()
        _aliases.value = emptyMap()
    }

    fun resolve(
        aliases: Map<String, String>,
        id: String?,
        name: String?,
    ): String {
        return id?.let { aliases[it] } ?: aliases[nameKey(name ?: "")] ?: name ?: ""
    }

    fun resolve(
        id: String?,
        name: String?,
    ): String {
        initialize()
        return resolve(_aliases.value, id, name)
    }

    fun serialize(): String = kotlinx.serialization.json.Json.encodeToString(_aliases.value)

    fun deserialize(serializedAliases: String): Map<String, String> =
        kotlinx.serialization.json.Json.decodeFromString(serializedAliases)

    fun restore(context: Any?, restoredAliases: Map<String, String>) {
        initialize()
        _aliases.value = restoredAliases
        ArtistNameAliasesStorage.save(restoredAliases)
    }

    private fun nameKey(name: String) = name.lowercase()
}

