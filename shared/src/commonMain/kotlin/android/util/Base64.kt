package android.util

object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2
    const val URL_SAFE = 8

    fun encodeToString(input: ByteArray, flags: Int): String =
        java.util.Base64.getEncoder().encodeToString(input)

    fun decode(str: String, flags: Int): ByteArray =
        java.util.Base64.getDecoder().decode(str)
}
