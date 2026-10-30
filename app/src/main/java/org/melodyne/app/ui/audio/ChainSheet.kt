package org.melodyne.app.ui.audio

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.melodyne.app.audio.CUSTOM_ID
import org.melodyne.app.audio.EqPresets
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.player.Chain
import org.melodyne.app.player.OutputInspector
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Panel
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.Txt

/** The honest signal chain: what happens to the audio from the file to your ears, and where it stops being clean. */
@Composable
fun ChainSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ps by ServiceLocator.player.state.collectAsState()
    val pb by ServiceLocator.playback.state.collectAsState()
    val eq by ServiceLocator.eq.state.collectAsState()
    var chain by remember { mutableStateOf<Chain?>(null) }
    BackHandler(onBack = onClose)
    LaunchedEffect(ps.quality, pb, eq) {
        while (true) {
            val name = if (eq.presetId == CUSTOM_ID) "Custom" else EqPresets.byId(eq.presetId).name
            chain = OutputInspector.chain(ctx, ps.quality, pb, eq, name)
            delay(2000)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Amoled.Surface)
                .clickable(enabled = false) {}.navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Txt("Signal chain", size = 20.sp, weight = FontWeight.ExtraBold)
            chain?.let { c ->
                Txt(c.verdict, size = 14.sp, weight = FontWeight.Bold, color = if (c.clean) Amoled.Ok else Amoled.Warn, maxLines = 3)
                Panel {
                    c.lines.forEach { l ->
                        Row(verticalAlignment = Alignment.Top) {
                            Txt(l.stage, Modifier.width(96.dp), size = 11.sp, weight = FontWeight.Bold, color = Amoled.Muted)
                            Txt(l.value, Modifier.weight(1f), size = 13.sp, maxLines = 4)
                        }
                    }
                }
            }
            Txt("Why no bit-perfect? The source itself is lossy AAC, so the best we can do is avoid ADDING more loss.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
            Txt("Why does the OS path matter for AAC? It stops Android quietly resampling 44.1 kHz to 48 kHz on the way out.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
            Txt("AAC 320 is transparent for most listeners, as long as it isn't re-processed.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
            Txt(
                "Bluetooth: your phone re-encodes everything it sends to headphones, and apps can't prevent that. Turning off " +
                    "“Disable absolute volume” in Android's Developer options keeps the phone's volume control from squeezing dynamic range.",
                size = 12.sp, color = Amoled.Muted, maxLines = 6,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Done", onClick = onClose)
                Pill("Developer options", filled = false) {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                }
            }
        }
    }
}
