package com.metrolist.music.discord
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import java.util.Base64
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import java.util.UUID

object DiscordSuperProperties {
    private const val TAG = "DiscordSvc"
    private const val CLIENT_VERSION = "314.13 - Stable"
    private const val CLIENT_BUILD_NUMBER = 314013
    private const val RELEASE_CHANNEL = "stable"

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) discord/1.0.9187 Chrome/134.0.6998.205 Electron/35.3.0 Safari/537.36"

    private val osVersion: String = System.getProperty("os.version").orEmpty()
    private val osArch: String = System.getProperty("os.arch").orEmpty()

    @Volatile
    private var _superProperties: JSONObject? = null

    private fun buildSuperProperties(): JSONObject {
        val vendorId = DiscordTokenStore.getDeviceVendorId()
            ?: UUID.randomUUID().toString()
        val clientUuid = DiscordTokenStore.getClientUuid()
            ?: UUID.randomUUID().toString()

        Timber.tag(TAG).d(
            "superProperties: building (os=%s, arch=%s, vendorId=%s)",
            osVersion, osArch, vendorId.take(8),
        )

        return JSONObject().apply {
            put("os", "Windows")
            put("browser", "Discord Client")
            put("device", "")
            put("system_locale", Locale.getDefault().toString())
            put("client_version", CLIENT_VERSION)
            put("release_channel", RELEASE_CHANNEL)
            put("device_vendor_id", vendorId)
            put("client_uuid", clientUuid)
            put("client_launch_id", UUID.randomUUID().toString())
            put("os_version", osVersion)
            put("os_arch", osArch)
            put("client_build_number", CLIENT_BUILD_NUMBER)
            put("client_event_source", JSONObject.NULL)
            put("design_id", 0)
        }
    }

    val superProperties: JSONObject
        get() {
            if (_superProperties == null) {
                _superProperties = buildSuperProperties()
            }
            return _superProperties!!
        }

    @Volatile
    private var _base64: String? = null

    val base64: String
        get() {
            if (_base64 == null) {
                _base64 = Base64.getEncoder().encodeToString(
                    superProperties.toString().toByteArray(),
                )
                Timber.tag(TAG).d("superProperties: base64 encoded (length=%d)", _base64!!.length)
            }
            return _base64!!
        }

    fun reset() {
        _superProperties = null
        _base64 = null
    }
}
