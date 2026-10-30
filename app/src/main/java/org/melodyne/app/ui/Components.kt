package org.melodyne.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.melodyne.app.domain.Song

/** True-AMOLED palette. Background is exactly #000000 so OLED pixels are fully off. */
object Amoled {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0C0C0C)
    val SurfaceHi = Color(0xFF181818)
    val OnBg = Color(0xFFF2F2F2)
    val Muted = Color(0xFF8C8C8C)
    val Accent = Color(0xFF1DE9B6)
    val Ok = Color(0xFF4CAF50)
    val Warn = Color(0xFFFFB300)
    val Err = Color(0xFFFF6B6B)
}

/** Accent derived from the current album art (see rememberAccent in App.kt). */
val LocalAccent = compositionLocalOf { Amoled.Accent }

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = Amoled.OnBg,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) = BasicText(
    text = text,
    modifier = modifier,
    style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align, lineHeight = lineHeight),
    overflow = TextOverflow.Ellipsis,
    maxLines = maxLines,
)

enum class G { Play, Pause, Next, Prev, Down }

/** Transport icons drawn on a Canvas: no icon library, no bitmaps. */
@Composable
fun Glyph(kind: G, color: Color, boxSize: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(boxSize)) {
        val w = this.size.width
        val h = this.size.height
        when (kind) {
            G.Play -> drawPath(Path().apply {
                moveTo(w * .30f, h * .18f); lineTo(w * .82f, h * .50f); lineTo(w * .30f, h * .82f); close()
            }, color)
            G.Pause -> {
                drawRoundRect(color, Offset(w * .24f, h * .18f), Size(w * .18f, h * .64f), CornerRadius(w * .04f))
                drawRoundRect(color, Offset(w * .58f, h * .18f), Size(w * .18f, h * .64f), CornerRadius(w * .04f))
            }
            G.Next -> {
                drawPath(Path().apply {
                    moveTo(w * .18f, h * .20f); lineTo(w * .66f, h * .50f); lineTo(w * .18f, h * .80f); close()
                }, color)
                drawRect(color, Offset(w * .72f, h * .20f), Size(w * .10f, h * .60f))
            }
            G.Prev -> {
                drawPath(Path().apply {
                    moveTo(w * .82f, h * .20f); lineTo(w * .34f, h * .50f); lineTo(w * .82f, h * .80f); close()
                }, color)
                drawRect(color, Offset(w * .18f, h * .20f), Size(w * .10f, h * .60f))
            }
            G.Down -> drawPath(
                Path().apply { moveTo(w * .22f, h * .38f); lineTo(w * .50f, h * .66f); lineTo(w * .78f, h * .38f) },
                color, style = Stroke(width = w * .09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
fun IconBtn(kind: G, tint: Color, modifier: Modifier = Modifier, glyphSize: Dp = 26.dp, onClick: () -> Unit) {
    Box(modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(kind, tint, glyphSize)
    }
}

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(radius)).background(Amoled.SurfaceHi),
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (filled) accent else Amoled.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, color = if (filled) Color.Black else Amoled.OnBg, weight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(text: String) =
    Txt(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp), size = 18.sp, weight = FontWeight.Bold)

@Composable
fun SongRow(song: Song, onClick: () -> Unit) {
    val addToPlaylist = LocalAddToPlaylist.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song.thumbUrl, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(song.title, size = 16.sp, weight = FontWeight.Medium)
            Txt(song.artist, size = 13.sp, color = Amoled.Muted)
        }
        Txt(fmtSec(song.durationSec), size = 12.sp, color = Amoled.Muted)
        AddButton { addToPlaylist(song) }
    }
}

/** A plain "+" that opens the add-to-playlist dialog. */
@Composable
fun AddButton(onClick: () -> Unit) {
    Txt("+", Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), size = 26.sp, color = LocalAccent.current)
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(16.dp)).background(Amoled.Surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Txt(message, color = Amoled.Err, maxLines = 10, size = 13.sp)
        Pill("Retry", onClick = onRetry)
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.size(h))

fun fmtSec(s: Int) = "%d:%02d".format(s / 60, s % 60)
fun fmtMs(ms: Long) = fmtSec((ms / 1000).toInt().coerceAtLeast(0))
