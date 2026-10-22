package com.metrolist.music.viewmodels
import com.metrolist.music.platform.AppContext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AccountSettingsViewModel(val context: AppContext) : ViewModel() {
    val isLoggedIn = MutableStateFlow(false)
    val loginProgress = MutableStateFlow(false)

    fun updateCookie(cookie: String) {
        YouTube.cookie = cookie.takeIf { it.isNotBlank() }
    }

    fun updateProxy(proxy: String) {}
    fun updateLanguage(lang: String) {}
    fun updateLocation(loc: String) {}
    fun updateContentMode(mode: String) {}
    fun updateUiType(type: String) {}

    fun logout() {
        viewModelScope.launch {
            logoutKeepData(context)
        }
    }

    fun restartApp() {}

    suspend fun logoutAndClearLibraryData(context: Any?) {
        withContext(Dispatchers.IO) {
            YouTube.cookie = null
            YouTube.dataSyncId = null
            YouTube.authUser = "0"
            this@AccountSettingsViewModel.context.safeDataStoreEdit { settings ->
                settings.remove(InnerTubeCookieKey)
                settings.remove(DataSyncIdKey)
                settings.remove(InnerTubeAuthUserKey)
                settings.remove(AccountNameKey)
                settings.remove(AccountEmailKey)
                settings.remove(AccountChannelHandleKey)
            }
        }
    }

    suspend fun logoutKeepData(context: Any?, onInnerTubeCookieChange: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            YouTube.cookie = null
            YouTube.dataSyncId = null
            YouTube.authUser = "0"
            this@AccountSettingsViewModel.context.safeDataStoreEdit { settings ->
                settings.remove(InnerTubeCookieKey)
                settings.remove(DataSyncIdKey)
                settings.remove(InnerTubeAuthUserKey)
                settings.remove(AccountNameKey)
                settings.remove(AccountEmailKey)
                settings.remove(AccountChannelHandleKey)
            }
        }
        onInnerTubeCookieChange("")
    }

    fun saveTokenAndRestart(
        context: Any?,
        cookie: String,
        visitorData: String? = null,
        dataSyncId: String? = null,
        authUser: String? = null,
        accountName: String? = null,
        accountEmail: String? = null,
        accountChannelHandle: String? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanCookie = cookie.trim()
                val cleanDataSyncId = dataSyncId?.trim()?.substringBefore("||").orEmpty()
                val cleanAuthUser = authUser?.trim()?.ifBlank { "0" } ?: "0"

                YouTube.cookie = cleanCookie.takeIf { it.isNotBlank() }
                if (!visitorData.isNullOrBlank()) {
                    YouTube.visitorData = visitorData.trim()
                } else if (YouTube.visitorData.isNullOrBlank()) {
                    YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() }?.let {
                        YouTube.visitorData = it
                    }
                }
                YouTube.dataSyncId = cleanDataSyncId.takeIf { it.isNotBlank() }
                YouTube.authUser = cleanAuthUser

                val accountInfo = runCatching { YouTube.accountInfo().getOrNull() }.getOrNull()
                val resolvedName = accountName?.takeIf { it.isNotBlank() } ?: accountInfo?.name.orEmpty()
                val resolvedEmail = accountEmail?.takeIf { it.isNotBlank() } ?: accountInfo?.email.orEmpty()
                val resolvedHandle = accountChannelHandle?.takeIf { it.isNotBlank() } ?: accountInfo?.channelHandle.orEmpty()
                val resolvedVisitorData = YouTube.visitorData.orEmpty()

                this@AccountSettingsViewModel.context.safeDataStoreEdit { settings ->
                    settings[InnerTubeCookieKey] = cleanCookie
                    if (resolvedVisitorData.isNotBlank()) {
                        settings[VisitorDataKey] = resolvedVisitorData
                    }
                    settings[DataSyncIdKey] = cleanDataSyncId
                    settings[InnerTubeAuthUserKey] = cleanAuthUser
                    settings[AccountNameKey] = resolvedName
                    settings[AccountEmailKey] = resolvedEmail
                    settings[AccountChannelHandleKey] = resolvedHandle
                }
                isLoggedIn.value = cleanCookie.isNotBlank()
            } catch (e: Exception) {
                Timber.e(e, "Failed to save token in AccountSettingsViewModel")
            }
        }
    }
}
