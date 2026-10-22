package com.metrolist.music.utils
import com.metrolist.music.platform.AppContext
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkConnectivityObserver(context: AppContext) {
    private val _networkStatus = MutableStateFlow(true)
    val networkStatus = _networkStatus.asStateFlow()

    fun start() {}
    fun stop() {}
    fun isConnected(): Boolean = true
    private fun isCurrentlyConnected(): Boolean = true
}
