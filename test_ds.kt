package com.metrolist.music.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import com.metrolist.music.PlatformContext

val PlatformContext.dataStore: DataStore<Preferences>
    get() = dataStoreInstance ?: synchronized(dataStoreLock) {
        dataStoreInstance ?: PreferenceDataStoreFactory.createWithPath(
            produceFile = { "settings.preferences_pb".toPath() }
        ).also { dataStoreInstance = it }
    }
