package org.melodyne.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.Field
import org.melodyne.app.ui.Panel
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.SongRow
import org.melodyne.app.ui.Txt

private val IDEAS = listOf(
    "Late night drive", "Gym power hour", "Rainy day Hindi", "Sufi evening", "Focus and study",
    "Punjabi party", "Retro Kishore Kumar", "Heartbreak playlist", "Road trip singalong",
)

@Composable
fun AiScreen(conn: PlayerConnection, onOpenSettings: () -> Unit) {
    val vm: AiViewModel = viewModel(factory = AiViewModel.Factory)
    val s by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val pad = Modifier.padding(horizontal = 20.dp)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(pad, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt("AI Mix", size = 28.sp, weight = FontWeight.ExtraBold)
                Txt("Describe a mood, moment or taste. Get a playlist you can play right now.", size = 13.sp, color = Amoled.Muted, maxLines = 3)
            }
        }
        if (!s.configured) item {
            Panel("Connect an AI provider", pad) {
                Txt(
                    "AI Mix uses your own key with any OpenAI-compatible service (OpenRouter, Groq, …). It's stored on this phone only. " +
                        "Quick mix below works without any key.",
                    size = 13.sp, color = Amoled.Muted, maxLines = 6,
                )
                Pill("Open Settings", onClick = onOpenSettings)
            }
        }
        item { Field(s.prompt, vm::onPrompt, "e.g. rainy night drive, 90s Hindi melodies", pad, singleLine = false) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IDEAS) { idea -> Chip(idea, s.prompt == idea) { vm.onPrompt(idea) } }
            }
        }
        item {
            Row(pad.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Songs", size = 13.sp, color = Amoled.Muted)
                listOf(10, 20, 30).forEach { n -> Chip("$n", s.count == n) { vm.setCount(n) } }
                Chip("Use my recent plays", s.useRecents) { vm.toggleRecents() }
            }
        }
        item {
            Row(pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(if (s.busy) "Working…" else "Generate with AI", filled = s.configured) { if (!s.busy) vm.generate() }
                Pill("Quick mix", filled = false) { if (!s.busy) vm.quickMix() }
            }
        }
        if (s.useRecents) item {
            Txt("Your last few played songs will be sent to your AI provider as taste hints.", pad, size = 11.sp, color = Amoled.Muted, maxLines = 2)
        }
        s.status?.let { msg -> item { Txt(msg, pad, size = 13.sp, color = Amoled.Muted) } }
        s.error?.let { msg -> item { Txt(msg, pad, size = 13.sp, color = Amoled.Err, maxLines = 6) } }

        if (s.result.isNotEmpty()) {
            item {
                Panel(s.resultName, pad) {
                    Txt("${s.result.size} songs", size = 13.sp, color = Amoled.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Play all") { conn.play(s.result, 0) }
                        Pill("Save", filled = false, onClick = vm::save)
                    }
                }
            }
            itemsIndexed(s.result) { i, song -> SongRow(song) { conn.play(s.result, i) } }
        }
    }
}
