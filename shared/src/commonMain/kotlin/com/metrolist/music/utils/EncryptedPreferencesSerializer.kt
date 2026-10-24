/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import com.metrolist.music.platform.SecretProtector
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

// DESKTOP: the settings file holds the YouTube cookie, Last.fm session, API keys and the proxy
// password. On Windows it lives under %APPDATA% and is readable by every process of the same user,
// so the whole file is wrapped with SecretProtector (DPAPI) while DataStore still works with plain
// Preferences. Backups keep the plain bytes so they stay portable to Android and other computers.
private val MAGIC = "MLDPAPI1".encodeToByteArray()

private fun ByteArray.hasMagic(): Boolean =
    size >= MAGIC.size && MAGIC.indices.all { this[it] == MAGIC[it] }

object EncryptedPreferencesSerializer : OkioSerializer<Preferences> {
    override val defaultValue: Preferences = emptyPreferences()

    override suspend fun readFrom(source: BufferedSource): Preferences {
        val bytes = source.readByteArray()
        val plain = if (bytes.hasMagic()) {
            val backend = SecretProtector.backend
                ?: throw CorruptionException("settings could not be decrypted", null)
            try {
                backend.unprotect(bytes.copyOfRange(MAGIC.size, bytes.size))
            } catch (e: Exception) {
                throw CorruptionException("settings could not be decrypted", e)
            }
        } else {
            // Plain file from an older build or from a restored Android/other-PC backup.
            bytes
        }
        return PreferencesSerializer.readFrom(Buffer().write(plain))
    }

    override suspend fun writeTo(t: Preferences, sink: BufferedSink) {
        val buffer = Buffer()
        PreferencesSerializer.writeTo(t, buffer)
        val bytes = buffer.readByteArray()
        val backend = SecretProtector.backend
        if (backend != null) {
            sink.write(MAGIC)
            sink.write(backend.protect(bytes))
        } else {
            sink.write(bytes)
        }
    }

    /** Returns the plain protobuf bytes of [fileBytes], decrypting them when the file is protected. */
    fun plainBytes(fileBytes: ByteArray): ByteArray {
        if (!fileBytes.hasMagic()) return fileBytes
        val backend = SecretProtector.backend
            ?: throw CorruptionException("settings could not be decrypted", null)
        return backend.unprotect(fileBytes.copyOfRange(MAGIC.size, fileBytes.size))
    }
}
