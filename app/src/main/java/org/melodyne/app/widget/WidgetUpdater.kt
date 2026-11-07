package org.melodyne.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.melodyne.app.MainActivity
import org.melodyne.app.R
import org.melodyne.app.di.ServiceLocator
import java.io.File

data class WidgetSnapshot(
    val title: String, val artist: String, val quality: String, val playing: Boolean, val artUrl: String?,
    val positionMs: Long = 0L, val durationMs: Long = 0L,
)

object WidgetMath {
    /** 0..1000 for the progress bar. */
    fun progress(pos: Long, dur: Long): Int = if (dur <= 0) 0 else ((pos.coerceIn(0L, dur) * 1000L) / dur).toInt()
    fun clock(ms: Long): String { val s = (ms.coerceAtLeast(0) / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }
}

/**
 * Draws the Now Playing home-screen widget in three sizes (it switches as you resize it on Android 12+), with a progress
 * bar and an accent colour taken from the artwork. The last state is remembered, so it is never blank after a restart.
 */
object WidgetUpdater {
    private const val DEFAULT_ACCENT = 0xFF1DE9B6.toInt()
    private var lastArtUrl: String? = null

    suspend fun update(ctx: Context, s: WidgetSnapshot) {
        if (s.artUrl != null && s.artUrl != lastArtUrl) {
            val art = runCatching { loadArt(ctx, s.artUrl) }.getOrNull()
            if (art != null) {
                runCatching { artFile(ctx).outputStream().use { art.first.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                ServiceLocator.prefs.edit().putInt("w_accent", art.second).apply()
                lastArtUrl = s.artUrl
            }
        }
        ServiceLocator.prefs.edit()
            .putString("w_title", s.title).putString("w_artist", s.artist).putString("w_quality", s.quality).putBoolean("w_playing", s.playing)
            .putLong("w_pos", s.positionMs).putLong("w_dur", s.durationMs).apply()
        render(ctx, s)
    }

    /** Called when the widget is first added or the system asks for a refresh. */
    fun renderLast(ctx: Context) {
        val p = ServiceLocator.prefs
        render(ctx, WidgetSnapshot(
            p.getString("w_title", "").orEmpty(), p.getString("w_artist", "").orEmpty(), p.getString("w_quality", "").orEmpty(),
            p.getBoolean("w_playing", false), null, p.getLong("w_pos", 0L), p.getLong("w_dur", 0L),
        ))
    }

    private fun artFile(ctx: Context) = File(ctx.cacheDir, "widget_art.png")

    private suspend fun loadArt(ctx: Context, url: String): Pair<Bitmap, Int>? {
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
        return out to accentOf(raw)
    }

    /** The artwork's vibrant colour, brightened until it reads on black (the same rule the app uses). */
    private fun accentOf(bmp: Bitmap): Int {
        val p = Palette.from(bmp).maximumColorCount(12).generate()
        var c = p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(DEFAULT_ACCENT)))
        var i = 0
        while (ColorUtils.calculateLuminance(c) < 0.3 && i++ < 6) c = ColorUtils.blendARGB(c, Color.WHITE, 0.18f)
        return c
    }

    private fun action(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, WidgetActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun open(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 4, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** One layout, filled in. Views a layout doesn't have are simply skipped by RemoteViews. */
    private fun build(ctx: Context, s: WidgetSnapshot, layout: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val idle = s.title.isBlank()
        val accent = ServiceLocator.prefs.getInt("w_accent", DEFAULT_ACCENT)
        v.setTextViewText(R.id.w_title, if (idle) "Melodyne" else s.title)
        v.setTextViewText(R.id.w_artist, if (idle) "Nothing playing yet" else s.artist)
        v.setTextViewText(R.id.w_quality, if (idle) "" else s.quality)
        v.setTextViewText(R.id.w_time, if (idle || s.durationMs <= 0) "" else WidgetMath.clock(s.positionMs) + " / " + WidgetMath.clock(s.durationMs))
        v.setProgressBar(R.id.w_progress, 1000, if (idle) 0 else WidgetMath.progress(s.positionMs, s.durationMs), false)
        if (Build.VERSION.SDK_INT >= 31) v.setColorStateList(R.id.w_progress, "setProgressTintList", ColorStateList.valueOf(accent))
        v.setImageViewResource(R.id.w_play, if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        v.setInt(R.id.w_play, "setColorFilter", accent)
        val art = artFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        if (art != null && !idle) v.setImageViewBitmap(R.id.w_art, art) else v.setImageViewResource(R.id.w_art, R.drawable.widget_art_placeholder)
        v.setOnClickPendingIntent(R.id.w_prev, action(ctx, WidgetActionReceiver.PREV, 1))
        v.setOnClickPendingIntent(R.id.w_next, action(ctx, WidgetActionReceiver.NEXT, 3))
        // With nothing loaded there is nothing to toggle, so Play opens the app instead.
        v.setOnClickPendingIntent(R.id.w_play, if (idle) open(ctx) else action(ctx, WidgetActionReceiver.TOGGLE, 2))
        val openApp = open(ctx)
        v.setOnClickPendingIntent(R.id.w_art, openApp)
        v.setOnClickPendingIntent(R.id.w_title, openApp)
        v.setOnClickPendingIntent(R.id.w_artist, openApp)
        return v
    }

    private fun render(ctx: Context, s: WidgetSnapshot) {
        val medium = build(ctx, s, R.layout.widget_now_playing)
        val views = if (Build.VERSION.SDK_INT >= 31) {
            RemoteViews(
                mapOf(
                    SizeF(180f, 50f) to build(ctx, s, R.layout.widget_now_playing_small),
                    SizeF(250f, 110f) to medium,
                    SizeF(250f, 220f) to build(ctx, s, R.layout.widget_now_playing_large),
                ),
            )
        } else medium
        AppWidgetManager.getInstance(ctx).updateAppWidget(ComponentName(ctx, NowPlayingWidget::class.java), views)
    }
}
