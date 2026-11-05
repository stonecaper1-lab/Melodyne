package org.melodyne.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.melodyne.app.MainActivity
import org.melodyne.app.R
import org.melodyne.app.di.ServiceLocator
import java.io.File

data class WidgetSnapshot(val title: String, val artist: String, val quality: String, val playing: Boolean, val artUrl: String?)

/** Draws the Now Playing home-screen widget. The last state is remembered, so the widget is never blank after a restart. */
object WidgetUpdater {
    private var lastArtUrl: String? = null

    suspend fun update(ctx: Context, s: WidgetSnapshot) {
        if (s.artUrl != null && s.artUrl != lastArtUrl) {
            val bmp = runCatching { loadArt(ctx, s.artUrl) }.getOrNull()
            if (bmp != null) {
                runCatching { artFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                lastArtUrl = s.artUrl
            }
        }
        ServiceLocator.prefs.edit()
            .putString("w_title", s.title).putString("w_artist", s.artist).putString("w_quality", s.quality).putBoolean("w_playing", s.playing)
            .apply()
        render(ctx, s)
    }

    /** Called when the widget is first added or the system asks for a refresh. */
    fun renderLast(ctx: Context) {
        val p = ServiceLocator.prefs
        render(ctx, WidgetSnapshot(p.getString("w_title", "").orEmpty(), p.getString("w_artist", "").orEmpty(), p.getString("w_quality", "").orEmpty(), p.getBoolean("w_playing", false), null))
    }

    private fun artFile(ctx: Context) = File(ctx.cacheDir, "widget_art.png")

    private suspend fun loadArt(ctx: Context, url: String): Bitmap? {
        val req = ImageRequest.Builder(ctx).data(url).size(256).allowHardware(false).build()
        val raw = (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap() ?: return null
        val side = minOf(raw.width, raw.height)
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        val px = 256
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), 56f, 56f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(Bitmap.createScaledBitmap(square, px, px, true), 0f, 0f, paint)
        return out
    }

    private fun action(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, WidgetActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun render(ctx: Context, s: WidgetSnapshot) {
        val v = RemoteViews(ctx.packageName, R.layout.widget_now_playing)
        v.setTextViewText(R.id.w_title, s.title.ifBlank { "Melodyne" })
        v.setTextViewText(R.id.w_artist, if (s.title.isBlank()) "Nothing playing yet" else s.artist)
        v.setTextViewText(R.id.w_quality, if (s.title.isBlank()) "" else s.quality)
        v.setImageViewResource(R.id.w_play, if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        v.setInt(R.id.w_play, "setColorFilter", 0xFF1DE9B6.toInt())
        val art = artFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        if (art != null && s.title.isNotBlank()) v.setImageViewBitmap(R.id.w_art, art) else v.setImageViewResource(R.id.w_art, R.drawable.widget_art_placeholder)
        v.setOnClickPendingIntent(R.id.w_prev, action(ctx, WidgetActionReceiver.PREV, 1))
        v.setOnClickPendingIntent(R.id.w_play, action(ctx, WidgetActionReceiver.TOGGLE, 2))
        v.setOnClickPendingIntent(R.id.w_next, action(ctx, WidgetActionReceiver.NEXT, 3))
        val open = PendingIntent.getActivity(
            ctx, 4, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        v.setOnClickPendingIntent(R.id.w_art, open)
        v.setOnClickPendingIntent(R.id.w_title, open)
        v.setOnClickPendingIntent(R.id.w_artist, open)
        AppWidgetManager.getInstance(ctx).updateAppWidget(ComponentName(ctx, NowPlayingWidget::class.java), v)
    }
}
