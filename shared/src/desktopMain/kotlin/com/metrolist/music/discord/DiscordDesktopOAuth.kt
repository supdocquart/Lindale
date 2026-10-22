package com.metrolist.music.discord

import com.sun.net.httpserver.HttpServer
import timber.log.Timber
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Loopback HTTP receiver for the Discord OAuth redirect.
 *
 * Discord redirects to metrolistdiscord://oauth2/callback. On Windows that scheme is registered
 * under HKCU to a small PowerShell script which forwards the URL to a loopback server started
 * only for the duration of one authorization.
 */
object DiscordDesktopOAuth {
    private const val TAG = "DiscordSvc"
    private const val SCHEME = "metrolistdiscord"

    fun install() {
        DiscordOAuthCallback.platformReceiver = { start() }
    }

    private fun start(): AutoCloseable {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        val port = server.address.port
        server.createContext("/discord") { exchange ->
            val response = runCatching {
                val raw = exchange.requestURI.rawQuery.orEmpty().removePrefix("u=")
                val callbackUrl = URLDecoder.decode(raw, StandardCharsets.UTF_8)
                val code = param(callbackUrl, "code")
                val state = param(callbackUrl, "state")
                val error = param(callbackUrl, "error")
                Timber.tag(TAG).i(
                    "oauthCallback: received (code=%s, state=%s, error=%s)",
                    code != null, state != null, error,
                )
                if (code != null && state != null) {
                    DiscordOAuthCallback.complete(code, state)
                } else {
                    DiscordOAuthCallback.fail(error ?: "missing code")
                }
                "ok"
            }.getOrElse {
                Timber.tag(TAG).e(it, "oauthCallback: failed to handle")
                "error"
            }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.executor = null
        server.start()
        Timber.tag(TAG).i("oauthCallback: listening on 127.0.0.1:%d", port)

        runCatching { registerProtocol(port) }
            .onFailure { Timber.tag(TAG).e(it, "oauthCallback: protocol registration failed") }

        return AutoCloseable {
            server.stop(0)
            Timber.tag(TAG).i("oauthCallback: stopped")
        }
    }

    private fun param(url: String, name: String): String? =
        Regex("[?&]$name=([^&#]*)").find(url)?.groupValues?.get(1)
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            ?.takeIf { it.isNotEmpty() }

    private fun registerProtocol(port: Int) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
            Timber.tag(TAG).w("oauthCallback: %s:// registration is only implemented on Windows", SCHEME)
            return
        }
        val dir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Metrolist").apply { mkdirs() }

        val script = File(dir, "discord-callback.ps1")
        script.writeText(
            "param([string]\$u)\r\n" +
                "try {\r\n" +
                "  \$c = New-Object System.Net.WebClient\r\n" +
                "  \$c.Proxy = \$null\r\n" +
                "  \$c.DownloadString('http://127.0.0.1:$port/discord?u=' + [uri]::EscapeDataString(\$u)) | Out-Null\r\n" +
                "} catch {}\r\n",
            Charsets.UTF_8,
        )

        val command = "powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass " +
            "-File \"${script.absolutePath}\" \"%1\""
        fun esc(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        val root = "HKEY_CURRENT_USER\\Software\\Classes\\$SCHEME"
        val reg = buildString {
            append("Windows Registry Editor Version 5.00\r\n\r\n")
            append("[$root]\r\n")
            append("@=\"URL:Metrolist Discord\"\r\n")
            append("\"URL Protocol\"=\"\"\r\n\r\n")
            append("[$root\\shell\\open\\command]\r\n")
            append("@=\"${esc(command)}\"\r\n")
        }
        val regFile = File(dir, "discord-protocol.reg")
        regFile.outputStream().use {
            it.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            it.write(reg.toByteArray(Charsets.UTF_16LE))
        }

        val process = ProcessBuilder("reg", "import", regFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        val output = runCatching { process.inputStream.bufferedReader().readText().trim() }.getOrDefault("")
        if (!finished) process.destroy()
        Timber.tag(TAG).i(
            "oauthCallback: registered %s:// (exit=%s, output=%s)",
            SCHEME,
            if (finished) process.exitValue() else "timeout",
            output,
        )
    }
}
