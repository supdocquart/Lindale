package com.metrolist.music.platform

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ExplicitDuplicates
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.lastfm.LastFM
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.ContentLanguageKey
import com.metrolist.music.constants.CountryCodeToName
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.HideExplicitDuplicatesKey
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
import com.metrolist.music.utils.UiTrace
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

// DESKTOP: B7 - the player JS prewarm costs 3-4 s of CPU, so it must not run while the first
// screen is being drawn. These control how long we wait for the first [UI:NAV] signal.
private const val FIRST_SCREEN_WAIT_MS = 20_000L
private const val PREWARM_SETTLE_MS = 1_000L

@Volatile
private var initialized = false

/** Releases caches while the window is minimized. */
fun trimDesktopMemory() {
    ImageLoaderSetup.trimMemory()
    System.gc()
    Timber.i("memory trimmed: %s", MemoryMonitor.snapshot())
}

fun initDesktopPlatform() {
    if (initialized) return
    initialized = true

    WindowsDpapi.install()
    ImageLoaderSetup.install()
    MemoryMonitor.start()
    com.metrolist.music.discord.DiscordDesktopOAuth.install()
    com.metrolist.music.ui.theme.DesktopPaletteReader.install()
    WindowsScreenWake.install()

    try {
        startKoin {
            modules(platformModule)
        }
    } catch (_: Exception) {
        // Ignored if already initialized
    }

    val context = AppContext()
    runCatching { ArtistNameAliases.initialize() }
    InnerTubeXPlayer.initialize(context)

    platformScope.launch {
        runCatching { initializeDesktopSettings(context) }
            .onFailure { Timber.e(it, "Failed to initialize desktop settings") }

        observeDesktopSettingsChanges(context)

        // DESKTOP: B7 - the prewarm downloads the ~2.6 MB player script and evaluates it (3-4 s CPU).
        // It used to start 1.5 s after init and competed with the Compose/DB/network work that draws
        // the first screen. It now waits for the first navigation event (the first frame signal we
        // have) and then runs on a low-priority IO thread. Playback does not depend on it: if the
        // user presses play first, the normal extraction path loads exactly what it needs.
        launch(Dispatchers.IO) {
            val waitStartedAt = System.currentTimeMillis()
            while (UiTrace.currentRoute == "?" &&
                System.currentTimeMillis() - waitStartedAt < FIRST_SCREEN_WAIT_MS
            ) {
                delay(250)
            }
            Timber.d(
                "prewarm: first screen %s after %dms",
                if (UiTrace.currentRoute == "?") "not detected" else "shown",
                System.currentTimeMillis() - waitStartedAt,
            )
            delay(PREWARM_SETTLE_MS)

            var waitedMs = 0
            while (YouTube.visitorData == null && waitedMs < 10_000) {
                delay(500)
                waitedMs += 500
            }

            val startedAt = System.currentTimeMillis()
            val thread = Thread.currentThread()
            val previousPriority = thread.priority
            runCatching { thread.priority = Thread.MIN_PRIORITY }
            try {
                runCatching { InnerTubeXPlayer.prewarm() }
                    .onFailure { Timber.w(it, "prewarm failed") }
            } finally {
                runCatching { thread.priority = previousPriority }
            }
            Timber.d("prewarm: finished after first screen [elapsedMs=%d]", System.currentTimeMillis() - startedAt)
        }
    }
}

private suspend fun initializeDesktopSettings(context: AppContext) {
    val settings = context.dataStore.data.first()
    // DESKTOP: the duplicate filter ignores its ~30 call sites; seed the global flag from the
    // startup snapshot before the first list is built.
    ExplicitDuplicates.enabled = settings[HideExplicitDuplicatesKey] ?: false
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

private fun observeDesktopSettingsChanges(context: AppContext) {
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

    platformScope.launch(Dispatchers.IO) {
        context.dataStore.data
            .map { it[HideExplicitDuplicatesKey] ?: false }
            .distinctUntilChanged()
            .collect { enabled -> ExplicitDuplicates.enabled = enabled }
    }
}
