package org.melodyne.app.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.melodyne.app.R

object UpdateNotifier {
    private const val CHANNEL = "updates"
    const val ID_UPDATE = 4101
    const val ID_READY = 4102
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when a new version of Melodyne is available"
        })
    }

    /** "Melodyne vX is ready". Tapping it starts the download (or opens the release page when a direct download isn't possible). */
    fun show(ctx: Context, info: UpdateInfo) {
        ensureChannel(ctx)
        val auto = UpdateInstaller.canAutoDownload(info)
        val tap = if (auto) {
            PendingIntent.getBroadcast(
                ctx, 11,
                Intent(ctx, UpdateActionReceiver::class.java).setAction(UpdateActionReceiver.DOWNLOAD)
                    .putExtra("version", info.version).putExtra("apk", info.apkUrl).putExtra("page", info.pageUrl),
                FLAGS,
            )
        } else {
            PendingIntent.getActivity(ctx, 11, Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS)
        }
        val notes = info.notes.lines().filter { it.isNotBlank() }.take(5).joinToString("\n")
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_melodyne)
            .setContentTitle("Melodyne v${info.version} is ready")
            .setContentText(if (auto) "Tap to download the update" else "Tap to open the release")
            .setStyle(NotificationCompat.BigTextStyle().bigText((if (auto) "Tap to download it; I'll ask you to install when it's ready.\n\n" else "") + notes))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_UPDATE, n) }          // fails quietly if notifications are switched off
    }

    /** After the download: "Tap to install" (or a failure notice). */
    fun showReady(ctx: Context, version: String, ok: Boolean) {
        ensureChannel(ctx)
        val install = if (ok) UpdateInstaller.installIntent(ctx, version) else null
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_melodyne)
            .setAutoCancel(true)
        if (install != null) {
            builder.setContentTitle("Melodyne v$version downloaded").setContentText("Tap to install")
                .setContentIntent(PendingIntent.getActivity(ctx, 12, install, FLAGS))
        } else {
            builder.setContentTitle("The update didn't download").setContentText("Open Melodyne > Settings > Updates to try again")
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_READY, builder.build()) }
    }
}
