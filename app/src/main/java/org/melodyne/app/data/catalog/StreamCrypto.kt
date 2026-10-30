package org.melodyne.app.data.catalog

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Stream URLs arrive DES-encrypted; the catalogue's own web player decrypts them the same way. */
object StreamCrypto {
    private val key = SecretKeySpec("38346591".toByteArray(Charsets.US_ASCII), "DES")

    fun decrypt(encrypted: String): String? = runCatching {
        val c = Cipher.getInstance("DES/ECB/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, key)
        String(c.doFinal(Base64.getMimeDecoder().decode(encrypted)), Charsets.UTF_8).trim()
    }.getOrNull()

    /** Turns the 96 kbps URL into the requested bitrate variant and forces https. */
    fun withBitrate(url: String, kbps: Int): String =
        url.replace(Regex("_\\d+\\.mp4"), "_$kbps.mp4").replace("http://", "https://")
}
