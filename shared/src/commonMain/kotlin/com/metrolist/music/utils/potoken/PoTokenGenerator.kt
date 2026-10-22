package com.metrolist.music.utils.potoken
import com.metrolist.music.platform.AppContext
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


class PoTokenGenerator(context: AppContext) {
    suspend fun getPoToken(): String? = null
    suspend fun getWebClientPoToken(videoId: String, visitorData: String): PoTokenResult? = null
    fun close() {}
}
