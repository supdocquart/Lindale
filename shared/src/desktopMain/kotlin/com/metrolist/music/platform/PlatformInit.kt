package com.metrolist.music.platform

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.lastfm.LastFM
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.ContentLanguageKey
import com.metrolist.music.constants.CountryCodeToName
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.LanguageCodeToName
import com.metrolist.music.constants.LastFMSessionKey
import com.metrolist.music.constants.ProxyEnabledKey
import com.metrolist.music.constants.ProxyPasswordKey
import com.metrolist.music.constants.ProxyTypeKey
import com.metrolist.music.constants.ProxyUrlKey
import com.metrolist.music.constants.ProxyUsernameKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.constants.UseLoginForBrowse
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.extensions.toInetSocketAddress
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.InnerTubeXPlayer
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Credentials
import org.koin.core.context.startKoin
import timber.log.Timber
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.Locale

private val platformScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Volatile
private var initialized = false

fun initDesktopPlatform() {
    if (initialized) return
    initialized = true

    com.metrolist.music.discord.DiscordDesktopOAuth.install()
    com.metrolist.music.ui.theme.DesktopPaletteReader.install()

    try {
        startKoin {
            modules(platformModule)
        }
    } catch (_: Exception) {
        // Ignored if already initialized
    }

    val context = Context()
    runCatching { ArtistNameAliases.initialize() }
    InnerTubeXPlayer.initialize(context)

    platformScope.launch {
        runCatching { initializeDesktopSettings(context) }
            .onFailure { Timber.e(it, "Failed to initialize desktop settings") }

        observeDesktopSettingsChanges(context)

        launch(Dispatchers.IO) {
            delay(1500)
            var waitedMs = 0
            while (YouTube.visitorData == null && waitedMs < 10_000) {
                delay(500)
                waitedMs += 500
            }
            runCatching { InnerTubeXPlayer.prewarm() }
        }
    }
}

private suspend fun initializeDesktopSettings(context: Context) {
    val settings = context.dataStore.data.first()
    val locale = Locale.getDefault()
    val languageTag = locale.language

    YouTube.locale = YouTubeLocale(
        gl = settings[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
            ?: locale.country.takeIf { it in CountryCodeToName }
            ?: "US",
        hl = settings[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
            ?: locale.language.takeIf { it in LanguageCodeToName }
            ?: languageTag.takeIf { it in LanguageCodeToName }
            ?: "en",
    )

    if (settings[ProxyEnabledKey] == true) {
        val username = settings[ProxyUsernameKey].orEmpty()
        val password = settings[ProxyPasswordKey].orEmpty()
        val type = settings[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP)

        if (username.isNotEmpty() || password.isNotEmpty()) {
            if (type == Proxy.Type.HTTP) {
                YouTube.proxyAuth = Credentials.basic(username, password)
            } else {
                Authenticator.setDefault(
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(username, password.toCharArray())
                    },
                )
            }
        }
        runCatching {
            settings[ProxyUrlKey]?.let {
                YouTube.proxy = Proxy(type, it.toInetSocketAddress())
            }
        }
    }

    YouTube.useLoginForBrowse = settings[UseLoginForBrowse] ?: true
}

private fun observeDesktopSettingsChanges(context: Context) {
    platformScope.launch(Dispatchers.IO) {
        context.dataStore.data
            .map { it[VisitorDataKey] }
            .distinctUntilChanged()
            .collect { visitorData ->
                YouTube.visitorData = visitorData?.takeIf { it != "null" }
                    ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                        context.safeDataStoreEdit { settings ->
                            settings[VisitorDataKey] = newVisitorData
                        }
                    }
            }
    }

    platformScope.launch(Dispatchers.IO) {
        context.dataStore.data
            .map { it[DataSyncIdKey] }
            .distinctUntilChanged()
            .collect { dataSyncId ->
                YouTube.dataSyncId = dataSyncId?.let {
                    it.takeIf { !it.contains("||") }
                        ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                        ?: it.substringAfter("||")
                }
            }
    }

    platformScope.launch(Dispatchers.IO) {
        context.dataStore.data
            .map { it[InnerTubeAuthUserKey] ?: "0" }
            .distinctUntilChanged()
            .collect { authUser ->
                YouTube.authUser = authUser
            }
    }

    platformScope.launch(Dispatchers.IO) {
        context.dataStore.data
            .map { it[InnerTubeCookieKey] }
            .distinctUntilChanged()
            .collect { cookie ->
                runCatching { YouTube.cookie = cookie }
            }
    }

    platformScope.launch(Dispatchers.IO) {
        context.dataStore.data
            .map { it[LastFMSessionKey] }
            .distinctUntilChanged()
            .collect { session ->
                runCatching { LastFM.sessionKey = session }
            }
    }

    platformScope.launch(Dispatchers.IO) {
        context.dataStore.data
            .map { Triple(it[ContentCountryKey], it[ContentLanguageKey], it[AppLanguageKey]) }
            .distinctUntilChanged()
            .collect { (contentCountry, contentLanguage, appLanguage) ->
                val systemLocale = Locale.getDefault()
                val effectiveAppLocale = appLanguage
                    ?.takeUnless { it == SYSTEM_DEFAULT }
                    ?.let { Locale.forLanguageTag(it) }
                    ?: systemLocale

                YouTube.locale = YouTubeLocale(
                    gl = contentCountry?.takeIf { it != SYSTEM_DEFAULT }
                        ?: effectiveAppLocale.country.takeIf { it in CountryCodeToName }
                        ?: systemLocale.country.takeIf { it in CountryCodeToName }
                        ?: "US",
                    hl = contentLanguage?.takeIf { it != SYSTEM_DEFAULT }
                        ?: effectiveAppLocale.toLanguageTag().takeIf { it in LanguageCodeToName }
                        ?: effectiveAppLocale.language.takeIf { it in LanguageCodeToName }
                        ?: "en",
                )
            }
    }
}
