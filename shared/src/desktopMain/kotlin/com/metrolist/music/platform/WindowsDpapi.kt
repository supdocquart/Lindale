package com.metrolist.music.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString

/** Windows DPAPI (CryptProtectData), scoped to the current Windows user account. */
internal object WindowsDpapi : SecretProtector {
    @Structure.FieldOrder("cbData", "pbData")
    class DataBlob() : Structure() {
        @JvmField var cbData: Int = 0

        @JvmField var pbData: Pointer? = null

        constructor(bytes: ByteArray) : this() {
            if (bytes.isNotEmpty()) {
                pbData = Memory(bytes.size.toLong()).also { it.write(0, bytes, 0, bytes.size) }
            }
            cbData = bytes.size
        }

        fun bytes(): ByteArray = pbData?.getByteArray(0, cbData) ?: ByteArray(0)
    }

    @Suppress("FunctionName")
    private interface Crypt32 : Library {
        fun CryptProtectData(
            dataIn: DataBlob,
            description: WString?,
            entropy: DataBlob?,
            reserved: Pointer?,
            prompt: Pointer?,
            flags: Int,
            dataOut: DataBlob,
        ): Boolean

        fun CryptUnprotectData(
            dataIn: DataBlob,
            description: Pointer?,
            entropy: DataBlob?,
            reserved: Pointer?,
            prompt: Pointer?,
            flags: Int,
            dataOut: DataBlob,
        ): Boolean
    }

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun LocalFree(memory: Pointer?): Pointer?
    }

    private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

    // Binds the ciphertext to this app so other DPAPI consumers of the same user cannot reuse it.
    private val entropy = "Metrolist.Desktop.Secrets".toByteArray()

    private val crypt32 by lazy { Native.load("Crypt32", Crypt32::class.java) }
    private val kernel32 by lazy { Native.load("Kernel32", Kernel32::class.java) }

    fun install() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        runCatching { crypt32; kernel32 }.onSuccess { SecretProtector.backend = this }
    }

    override fun protect(data: ByteArray): ByteArray {
        val out = DataBlob()
        check(crypt32.CryptProtectData(DataBlob(data), null, DataBlob(entropy), null, null, CRYPTPROTECT_UI_FORBIDDEN, out)) {
            "CryptProtectData failed"
        }
        return out.consume()
    }

    override fun unprotect(data: ByteArray): ByteArray {
        val out = DataBlob()
        check(crypt32.CryptUnprotectData(DataBlob(data), null, DataBlob(entropy), null, null, CRYPTPROTECT_UI_FORBIDDEN, out)) {
            "CryptUnprotectData failed"
        }
        return out.consume()
    }

    private fun DataBlob.consume(): ByteArray {
        read()
        return try {
            bytes()
        } finally {
            kernel32.LocalFree(pbData)
        }
    }
}
