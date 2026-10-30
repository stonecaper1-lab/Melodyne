package org.melodyne.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.melodyne.app.BuildConfig
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.Field
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.LocalOpenEaster
import org.melodyne.app.ui.Panel
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.Txt

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val s by vm.ui.collectAsState()
    val playback = ServiceLocator.playback
    val pb by playback.state.collectAsState()
    val gain by ServiceLocator.normalizer.gainDb.collectAsState()
    val player by ServiceLocator.player.state.collectAsState()
    val update by ServiceLocator.updates.available.collectAsState()
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val openEaster = LocalOpenEaster.current

    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var host by remember(s.host) { mutableStateOf(s.host) }
    var lfKey by remember(s.lfKey) { mutableStateOf(s.lfKey) }
    var lfSecret by remember(s.lfSecret) { mutableStateOf(s.lfSecret) }
    var aiUrl by remember(s.ai) { mutableStateOf(s.ai.baseUrl) }
    var aiKey by remember(s.ai) { mutableStateOf(s.ai.apiKey) }
    var aiModel by remember(s.ai) { mutableStateOf(s.ai.model) }
    var updRepo by remember(s.updateRepo) { mutableStateOf(s.updateRepo) }
    var updToken by remember(s.updateToken) { mutableStateOf(s.updateToken) }
    var taps by remember { mutableStateOf(0) }
    val lastTap = remember { mutableLongStateOf(0L) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Txt("Settings", size = 28.sp, weight = FontWeight.ExtraBold)
        s.message?.let { Txt(it, color = LocalAccent.current, maxLines = 3, size = 14.sp, weight = FontWeight.SemiBold) }

        Panel("Playback · Audio Tuning") {
            ToggleRow(
                "Audiophile Mode", "Bit-transparent chain: EQ, normalisation and crossfade are bypassed. Also on the Home screen.",
                if (pb.audiophile) "On" else "Off", pb.audiophile,
            ) { playback.setAudiophile(!pb.audiophile) }
            ToggleRow(
                "Normalize volume",
                when {
                    pb.audiophile -> "Off in Audiophile Mode"
                    !pb.normalize -> "Off"
                    gain != null -> "Current gain: %+.1f dB".format(gain)
                    player.hasItem -> "Measuring this song…"
                    else -> "Measures each song as it starts"
                },
                if (pb.normalizeEffective) "On" else "Off", pb.normalizeEffective,
            ) { if (!pb.audiophile) playback.setNormalize(!pb.normalize) }
            Txt(
                "Prevents volume jumps between songs and reduces dynamic range slightly. Loudness is measured while a song plays " +
                    "(about the first 8 seconds, to −14 LUFS), then remembered for that song. Applied to the decoded audio only; nothing is re-encoded.",
                size = 12.sp, color = Amoled.Muted, maxLines = 6,
            )
            ToggleRow("Gapless", "Songs that declare their encoder delay and padding join without a gap, automatically.", "Always on", true) {}
            ToggleRow(
                "Crossfade",
                if (pb.audiophile) "Disabled in Audiophile Mode to preserve transparent chain."
                else "Needs a second decoder running at once, so it isn't bit-transparent. Arrives in the next build.",
                "Next build", false,
            ) {}
            Pill("Bluetooth volume: Developer options", filled = false) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            }
        }

        Panel("Last.fm") {
            if (s.lfUser == null) {
                Txt(
                    "1. Get a free API key from Last.fm (any app name works).\n2. Paste the key and secret here.\n3. Sign in with your Last.fm account.",
                    size = 13.sp, color = Amoled.Muted, maxLines = 6,
                )
                Pill("Get API key", filled = false) { uriHandler.openUri("https://www.last.fm/api/account/create") }
                if (s.lfBuiltIn && s.lfKey.isBlank()) Txt("This build already includes a key. You can skip to step 3.", size = 12.sp, color = Amoled.Muted)
                Field(lfKey, { lfKey = it }, "API key")
                Field(lfSecret, { lfSecret = it }, "Shared secret", password = true)
                Pill("Save key", filled = false) { vm.saveLastFmKeys(lfKey, lfSecret) }
            }
            if (s.lfConfigured) {
                if (s.lfUser != null) {
                    Chip("Signed in as ${s.lfUser}", true) {}
                    Pill("Sign out", filled = false, onClick = vm::logout)
                } else {
                    Field(user, { user = it }, "Last.fm username")
                    Field(pass, { pass = it }, "Password", password = true)
                    Pill(if (s.busy) "Signing in…" else "Sign in") { if (!s.busy) vm.login(user, pass) }
                    Txt("Your password goes to Last.fm once over HTTPS. Only the session key is kept.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
                }
            }
            Txt(s.lfStatus, size = 12.sp, color = LocalAccent.current, maxLines = 3)
            Txt(
                "Your key also powers moods, \"because you listened\" rows, trending charts and song tags. " +
                    "Each play sends Now Playing at the start and a scrobble after half the song or four minutes.",
                size = 12.sp, color = Amoled.Muted, maxLines = 5,
            )
        }

        Panel("Custom source (optional)") {
            Txt(
                "Melodyne has a built-in music source. If you run a compatible server of your own, put its https address here and it will be tried first.",
                size = 13.sp, color = Amoled.Muted, maxLines = 5,
            )
            Field(host, { host = it }, "https://your-instance.example/api", keyboard = KeyboardType.Uri)
            Pill("Save", filled = false) { vm.saveHost(host) }
        }

        Panel("AI playlist provider") {
            Txt(
                "Any OpenAI-compatible service. Your key stays on this phone; prompts go only to the provider you choose. " +
                    "Base URL examples: https://openrouter.ai/api/v1 or https://api.groq.com/openai/v1.",
                size = 13.sp, color = Amoled.Muted, maxLines = 6,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Get key: OpenRouter", false) { uriHandler.openUri("https://openrouter.ai/keys") }
                Chip("Groq", false) { uriHandler.openUri("https://console.groq.com/keys") }
                Chip("OpenAI", false) { uriHandler.openUri("https://platform.openai.com/api-keys") }
            }
            Field(aiUrl, { aiUrl = it }, "Base URL (https://…/v1)", keyboard = KeyboardType.Uri)
            Field(aiKey, { aiKey = it }, "API key", password = true)
            Field(aiModel, { aiModel = it }, "Model id from your provider")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Save provider") { vm.saveAi(aiUrl, aiKey, aiModel) }
                Pill(if (s.aiTesting) "Testing…" else "Test connection", filled = false) { if (!s.aiTesting) vm.testAi(aiUrl, aiKey, aiModel) }
            }
            s.aiTest?.let { t -> Txt(t, size = 13.sp, color = if (t.startsWith("Connected")) Amoled.Ok else Amoled.Err, maxLines = 4) }
        }

        Panel("Updates") {
            Txt(s.updateStatus, size = 13.sp, color = if (update != null) Amoled.Ok else Amoled.Muted, maxLines = 3)
            Pill("Check now") { vm.checkUpdates() }
            Txt(
                "Melodyne looks at the project's latest GitHub release when it opens (at most every 6 hours). Nothing installs by itself; " +
                    "Download opens the release in your browser. A private repository needs a read-only GitHub token.",
                size = 12.sp, color = Amoled.Muted, maxLines = 6,
            )
            Field(updRepo, { updRepo = it }, "owner/repository")
            Field(updToken, { updToken = it }, "GitHub token (only for a private repository)", password = true)
            Pill("Save", filled = false) { vm.saveUpdateSource(updRepo, updToken) }
        }

        s.crash?.let { trace ->
            Panel("Last crash") {
                Txt(trace.take(900), size = 11.sp, color = Amoled.Err, maxLines = 14)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Copy") { clipboard.setText(AnnotatedString(trace)) }
                    Pill("Clear", filled = false, onClick = vm::clearCrash)
                }
            }
        }

        Panel("About") {
            Txt(
                "Melodyne ${BuildConfig.VERSION_NAME}",
                Modifier.clickable {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap.longValue < 1500) taps + 1 else 1
                    lastTap.longValue = now
                    if (taps >= 5) { taps = 0; openEaster() }
                },
                size = 15.sp, weight = FontWeight.SemiBold,
            )
            Txt("Free software. No trackers, no ads, no analytics, no accounts.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, chipText: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, size = 15.sp, weight = FontWeight.SemiBold)
            Txt(subtitle, size = 12.sp, color = Amoled.Muted, maxLines = 4)
        }
        Chip(chipText, selected, onClick = onClick)
    }
}
