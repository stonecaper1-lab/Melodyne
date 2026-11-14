package org.melodyne.app.ui.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.audio.PlayMode
import org.melodyne.app.audio.PlayModes
import org.melodyne.app.audio.PlaybackState
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Txt
import org.melodyne.app.ui.glass

/** Each mode has its own colour: neutral for Normal, gold for Audiophile, green for Frugal. */
private fun modeColor(m: PlayMode): Color = when (m) {
    PlayMode.NORMAL -> Color(0xFF9AA0A6)
    PlayMode.AUDIOPHILE -> Color(0xFFFFC857)
    PlayMode.FRUGAL -> Color(0xFF5BD18B)
}

/** A banner that slides in from the top: which mode is active, and what it is doing to the sound. Tap to dismiss. */
@Composable
fun ModeNotice(visible: Boolean, state: PlaybackState, eqShaping: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val mode = PlayModes.of(state)
    val c = modeColor(mode)
    AnimatedVisibility(
        visible, modifier,
        enter = slideInVertically(tween(280)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)),
    ) {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), alpha = 0.16f).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(PlayModes.title(mode), size = 14.sp, weight = FontWeight.Bold, color = c)
                Txt(PlayModes.detail(state, eqShaping), size = 12.sp, color = Amoled.Muted, maxLines = 3)
            }
        }
    }
}

/** A small always-visible indicator of the current mode. Tap it to show the banner again. */
@Composable
fun ModePill(state: PlaybackState, onClick: () -> Unit) {
    val mode = PlayModes.of(state)
    val c = modeColor(mode)
    Row(
        Modifier.glass(RoundedCornerShape(50), alpha = 0.08f).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
        Txt(
            PlayModes.label(mode) + if (mode == PlayMode.FRUGAL && state.frugalMode == 2) " · AUTO" else "",
            size = 10.sp, weight = FontWeight.Bold, color = c,
        )
    }
}
