package org.melodyne.app.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import org.melodyne.app.di.ServiceLocator
import java.io.File

/**
 * Downloads a release with Android's own DownloadManager (it shows progress, survives the app closing, and follows
 * GitHub's redirects) and hands the finished file to the system installer. If a release has no APK attached, the
 * notification opens the release page instead.
 */
object UpdateInstaller {
    private const val APK = "application/vnd.android.package-archive"

    fun canAutoDownload(info: UpdateInfo): Boolean = info.apkUrl != null

    private fun fileFor(ctx: Context, version: String): File? =
        ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "Vina-v$version.apk") }

    fun download(ctx: Context, info: UpdateInfo): Boolean {
        val url = info.apkUrl ?: return false
        if (!canAutoDownload(info)) return false
        val file = fileFor(ctx, info.version) ?: return false
        return runCatching {
            file.delete()
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Vina v${info.version}")
                .setDescription("Downloading the update")
                .setMimeType(APK)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(file))
            val id = dm.enqueue(request)
            ServiceLocator.prefs.edit().putLong("upd_dl_id", id).putString("upd_dl_ver", info.version).apply()
            true
        }.getOrDefault(false)
    }

    /** The intent that opens Android's installer on the downloaded file. */
    fun installIntent(ctx: Context, version: String): Intent? {
        val file = fileFor(ctx, version)?.takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun onDownloadComplete(ctx: Context, id: Long) {
        val prefs = ServiceLocator.prefs
        if (id != prefs.getLong("upd_dl_id", -1L)) return
        val version = prefs.getString("upd_dl_ver", null) ?: return
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val ok = runCatching {
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
            }
        }.getOrDefault(false)
        UpdateNotifier.showReady(ctx, version, ok)
    }
}
