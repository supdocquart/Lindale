package com.metrolist.music.platform

/**
 * Encrypts small secrets (tokens) with a key owned by the operating system user,
 * so a copied settings file cannot be decrypted elsewhere. Installed by platform code.
 */
interface SecretProtector {
    fun protect(data: ByteArray): ByteArray

    fun unprotect(data: ByteArray): ByteArray

    companion object {
        @Volatile
        var backend: SecretProtector? = null
    }
}
