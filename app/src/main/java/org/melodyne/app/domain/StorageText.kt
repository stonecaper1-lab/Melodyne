package org.melodyne.app.domain

/** Small pure helpers for the Storage settings. */
object StorageText {
    private val apk = Regex("v([0-9]+(?:\\.[0-9]+)*)\\.apk$", RegexOption.IGNORE_CASE)

    /** "Vina-v0.9.1.apk" -> "0.9.1"; anything else -> null. */
    fun apkVersion(name: String): String? = apk.find(name)?.groupValues?.get(1)

    fun formatBytes(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> "%.0f KB".format(n / 1024.0)
        else -> "%.1f MB".format(n / 1048576.0)
    }
}
