/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils
import com.metrolist.music.platform.AppContext
import com.metrolist.music.platform.LocalAppContext
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.metrolist.music.utils.LocalPlatformContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import com.metrolist.music.extensions.toEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.properties.ReadOnlyProperty
import timber.log.Timber



@Volatile private var dataStoreInstance: DataStore<Preferences>? = null
private val dataStoreLock = Any()

val AppContext.dataStore: DataStore<Preferences>
    get() {
        dataStoreInstance?.let { return it }
        synchronized(dataStoreLock) {
            dataStoreInstance?.let { return it }
            // DESKTOP: Android's location (<data>/datastore/settings.preferences_pb), which backup/restore use.
            // Early desktop builds kept the file directly in the data folder; move it once.
            val legacyFile = this.filesDir.resolve("settings.preferences_pb")
            val targetFile = this.filesDir.resolve("datastore").resolve("settings.preferences_pb")
            // If the move fails, keep using the old file rather than starting with empty settings.
            val settingsFile =
                if (legacyFile.exists() && !targetFile.exists() && !moveLegacySettings(legacyFile, targetFile)) legacyFile
                else targetFile
            return androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath(
                produceFile = { settingsFile.absolutePath.toPath() }
            ).also { dataStoreInstance = it }
        }
    }

private fun moveLegacySettings(legacy: java.io.File, target: java.io.File): Boolean {
    target.parentFile?.mkdirs()
    val ok = legacy.renameTo(target)
    if (ok) Timber.i("settings: moved to datastore folder")
    else Timber.e("settings: could not move legacy settings file; keeping the old location")
    return ok
}

suspend fun AppContext.safeDataStoreEdit(
    transform: suspend (MutablePreferences) -> Unit,
): Boolean {
    return try {
        dataStore.edit(transform)
        true
    } catch (e: Exception) {
        timber.log.Timber.e(e, "DataStore edit failed")
        false
    }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    runBlocking(Dispatchers.IO) {
        data.first()[key]
    }

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    runBlocking(Dispatchers.IO) {
        data.first()[key] ?: defaultValue
    }

fun <T> preference(
    context: AppContext,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: AppContext,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = com.metrolist.music.platform.LocalAppContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.safeDataStoreEdit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = com.metrolist.music.platform.LocalAppContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.safeDataStoreEdit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}


