package com.metrolist.music.utils.potoken
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.PlatformContext

class PoTokenGenerator(context: PlatformContext) {
    suspend fun getPoToken(): String? = null
    suspend fun getWebClientPoToken(videoId: String, visitorData: String): PoTokenResult? = null
    fun close() {}
}
