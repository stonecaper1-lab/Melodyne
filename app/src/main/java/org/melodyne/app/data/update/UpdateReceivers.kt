package org.melodyne.app.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/** The notification tap: start the download. */
class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DOWNLOAD) return
        val info = UpdateInfo(
            intent.getStringExtra("version").orEmpty(), "", intent.getStringExtra("page").orEmpty(), intent.getStringExtra("apk"),
        )
        NotificationManagerCompat.from(context).cancel(UpdateNotifier.ID_UPDATE)
        val started = UpdateInstaller.download(context, info)
        Toast.makeText(
            context, if (started) "Downloading Vina v${info.version}…" else "Couldn't start the download. Open Settings > Updates.", Toast.LENGTH_LONG,
        ).show()
    }

    companion object { const val DOWNLOAD = "org.melodyne.app.update.DOWNLOAD" }
}

/** The system tells us when a download finished. */
class DownloadDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        UpdateInstaller.onDownloadComplete(context, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))
    }
}
