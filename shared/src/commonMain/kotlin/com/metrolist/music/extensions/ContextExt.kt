package com.metrolist.music.extensions
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.PlatformContext
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.runBlocking

fun PlatformContext.isSyncEnabled(): Boolean {
    return runBlocking {
        dataStore.get(YtmSyncKey, true) && isUserLoggedIn()
    }
}

fun PlatformContext.isUserLoggedIn(): Boolean {
    return runBlocking {
        val cookie = dataStore.get(InnerTubeCookieKey) ?: ""
        "SAPISID" in parseCookieString(cookie) && isInternetConnected()
    }
}

fun PlatformContext.isInternetConnected(): Boolean = true
