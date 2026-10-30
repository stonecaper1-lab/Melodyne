package org.melodyne.app.ui.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.audio.CUSTOM_ID
import org.melodyne.app.audio.EQ_LABELS
import org.melodyne.app.audio.EQ_MAX_DB
import org.melodyne.app.audio.EqMath
import org.melodyne.app.audio.EqPresets
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.Txt
import kotlin.math.abs
import kotlin.math.roundToInt

/** Seven-band equalizer: presets on top, one draggable slider per band. Used inside Now Playing. */
@Composable
fun EqPanel(modifier: Modifier = Modifier) {
    val eq = ServiceLocator.eq
    val st by eq.state.collectAsState()
    val gains = st.gains
    val pb by ServiceLocator.playback.state.collectAsState()
    val headroom = remember(gains) { EqMath.headroomDb(gains) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt("Equalizer", size = 20.sp, weight = FontWeight.ExtraBold)
                Txt(
                    when {
                        pb.audiophile -> "Bypassed while Audiophile Mode is on."
                        !st.enabled -> "Off. Audio passes through untouched."
                        !st.shaping -> "Flat. Audio passes through untouched."
                        headroom > 0.05 -> "Auto headroom −%.1f dB keeps boosts clean.".format(headroom)
                        else -> "Cuts only, so no headroom is needed."
                    },
                    size = 12.sp, color = Amoled.Muted, maxLines = 2,
                )
            }
            Chip(if (st.enabled) "On" else "Off", st.enabled) { eq.setEnabled(!st.enabled) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EqPresets.all) { p -> Chip(p.name, st.presetId == p.id) { eq.setPreset(p.id) } }
            item { Chip("Custom", st.presetId == CUSTOM_ID) { eq.setPreset(CUSTOM_ID) } }
        }
        Txt(
            if (st.presetId == CUSTOM_ID) "Your own curve. Drag any band." else EqPresets.byId(st.presetId).blurb,
            size = 13.sp, color = Amoled.Muted, maxLines = 2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            gains.forEachIndexed { i, g -> BandSlider(EQ_LABELS[i], g, st.enabled) { eq.setBand(i, it) } }
        }
    }
}

@Composable
private fun BandSlider(label: String, db: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val accent = if (enabled) LocalAccent.current else Amoled.Muted
    val cb by rememberUpdatedState(onChange)
    Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(dbText(db), size = 11.sp, color = if (abs(db) < 0.05f) Amoled.Muted else accent)
        Box(
            Modifier.height(150.dp).width(40.dp)
                .pointerInput(Unit) { detectTapGestures { o -> cb(yToDb(o.y, size.height)) } }
                .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> cb(yToDb(change.position.y, size.height)) } },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val mid = size.height / 2
                val y = mid - (db / EQ_MAX_DB) * mid
                val t = 3.dp.toPx()
                drawLine(Color(0xFF2A2A2A), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = t, cap = StrokeCap.Round)
                drawLine(Color(0xFF3A3A3A), Offset(cx - 9.dp.toPx(), mid), Offset(cx + 9.dp.toPx(), mid), strokeWidth = 1.dp.toPx())
                drawLine(accent, Offset(cx, mid), Offset(cx, y), strokeWidth = t, cap = StrokeCap.Round)
                drawCircle(accent, 8.dp.toPx(), Offset(cx, y))
            }
        }
        Txt(label, size = 11.sp, color = Amoled.Muted)
    }
}

/** Tap/drag position to dB, in 0.5 dB steps. */
private fun yToDb(y: Float, h: Int): Float {
    val raw = ((h / 2f) - y) / (h / 2f) * EQ_MAX_DB
    return (raw.coerceIn(-EQ_MAX_DB, EQ_MAX_DB) * 2f).roundToInt() / 2f
}

private fun dbText(db: Float): String = when {
    abs(db) < 0.05f -> "0"
    db % 1f == 0f -> "%+d".format(db.toInt())
    else -> "%+.1f".format(db)
}
