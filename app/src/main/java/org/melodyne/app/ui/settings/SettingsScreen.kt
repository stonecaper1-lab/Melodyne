package org.melodyne.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.melodyne.app.data.local.CacheCleaner
import org.melodyne.app.domain.StorageText
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
import kotlin.math.roundToInt
import org.melodyne.app.BuildConfig
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Frugal
import org.melodyne.app.ui.AccentSlider
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.Field
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.LocalOpenEaster
import org.melodyne.app.ui.LocalOpenTour
import org.melodyne.app.ui.skins.Skins
import org.melodyne.app.ui.Panel
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.Txt

@OptIn(ExperimentalLayoutApi::class)
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
    val openTour = LocalOpenTour.current

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
            ToggleRow(
                "Gentle normalisation", "Only turns loud songs down, never boosts quiet ones, so dynamics stay intact.",
                if (pb.normGentle) "On" else "Off", pb.normGentle,
            ) { playback.setNormGentle(!pb.normGentle) }
            ToggleRow(
                "Gapless",
                if (pb.crossfadeEffective) "Paused while Crossfade is on." else "Songs that declare their encoder delay and padding join without a gap, automatically.",
                if (pb.crossfadeEffective) "Paused" else "Always on", !pb.crossfadeEffective,
            ) {}
            ToggleRow(
                "Crossfade",
                when {
                    pb.audiophile -> "Disabled in Audiophile Mode to preserve transparent chain."
                    pb.crossfade -> "Fades ${pb.crossfadeSec} s between songs. Crossfade decodes two AAC streams at once + DSP mix - not bit-transparent."
                    else -> "Blends the end of one song into the next."
                },
                if (pb.crossfadeEffective) "On" else "Off", pb.crossfadeEffective,
            ) { if (!pb.audiophile) playback.setCrossfade(!pb.crossfade) }
            if (pb.crossfade && !pb.audiophile) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Txt("${pb.crossfadeSec} s", Modifier.width(40.dp), size = 14.sp, weight = FontWeight.SemiBold)
                    AccentSlider((pb.crossfadeSec - 1) / 11f, { v -> playback.setCrossfadeSec(1 + (v * 11).roundToInt()) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Equal-power", pb.equalPower) { playback.setEqualPower(true) }
                    Chip("Linear", !pb.equalPower) { playback.setEqualPower(false) }
                }
                Txt("The fade starts at a song's natural end. Skipping cuts cleanly. Not used on the last song or in repeat-one.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
            }
            ToggleRow(
                "Infinite play", "When the queue is nearly done, similar songs are added automatically so the music never stops.",
                if (pb.infinite) "On" else "Off", pb.infinite,
            ) { playback.setInfinite(!pb.infinite) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Profile", size = 13.sp, color = Amoled.Muted)
                Chip("Normal", !pb.audiophile && !pb.crossfade) { playback.preset("normal") }
                Chip("Audiophile", pb.audiophile) { playback.preset("audiophile") }
                Chip("Party", !pb.audiophile && pb.crossfade) { playback.preset("party") }
            }
            Pill("Bluetooth volume: Developer options", filled = false) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            }
        }

        Panel("Frugal mode") {
            Txt(
                "Saves mobile data and battery at the cost of sound quality: lower-bitrate streams, small artwork, a smaller buffer, and no EQ, " +
                    "normalisation, crossfade, skins, glow or Last.fm lookups.",
                size = 13.sp, color = Amoled.Muted, maxLines = 6,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Off", pb.frugalMode == 0) { playback.setFrugalMode(0) }
                Chip("On", pb.frugalMode == 1) { playback.setFrugalMode(1) }
                Chip("Auto", pb.frugalMode == 2) { playback.setFrugalMode(2) }
            }
            if (pb.frugalMode == 2) Txt(
                "Auto turns Frugal on while you're on mobile data or Battery Saver is on. Right now: " +
                    (if (pb.frugal) "on (${if (pb.batterySaver) "Battery Saver" else "mobile data"})" else "off (Wi-Fi, no Battery Saver)") + ".",
                size = 12.sp, color = LocalAccent.current, maxLines = 3,
            )
            if (pb.frugalMode != 0) {
                Txt("Stream quality while frugal", size = 14.sp, weight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(48, 96, 160).forEach { k -> Chip("$k kbps", pb.frugalKbps == k) { playback.setFrugalKbps(k) } }
                }
                Txt(
                    "About ${"%.2f".format(Frugal.mbPerMinute(pb.frugalKbps))} MB per minute, against ${"%.1f".format(Frugal.mbPerMinute(320))} MB at 320 kbps. " +
                        "The smaller buffer takes effect the next time the app starts. Frugal and Audiophile Mode switch each other off.",
                    size = 12.sp, color = Amoled.Muted, maxLines = 5,
                )
            }
        }

        Panel("Appearance") {
            ToggleRow(
                "Liquid glow", "Soft colour drifting behind the glass, and the blurred artwork on Now Playing. Off gives pure AMOLED black.",
                if (pb.glow) "On" else "Off", pb.glow,
            ) { playback.setGlow(!pb.glow) }
            Txt("Player skin", size = 15.sp, weight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skins.ALL.forEach { sk -> Chip(sk.name, pb.skin == sk.id) { playback.setSkin(sk.id) } }
            }
            Txt(Skins.byId(pb.skin).blurb, size = 12.sp, color = Amoled.Muted, maxLines = 4)
            Pill("Add the Now Playing widget", filled = false) {
                runCatching {
                    val mgr = ctx.getSystemService(android.appwidget.AppWidgetManager::class.java)
                    if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(android.content.ComponentName(ctx, org.melodyne.app.widget.NowPlayingWidget::class.java), null, null)
                }
            }
            Txt("Or long-press your home screen, choose Widgets, then Vina.", size = 12.sp, color = Amoled.Muted, maxLines = 2)
            Pill("Replay the welcome tour", filled = false) { openTour() }
        }

        Panel("Car & Bluetooth") {
            Txt(
                "If music sounds flatter in the car than a file played directly, the usual causes are the Bluetooth codec, the source quality, " +
                    "and the car's own volume stage. These help with the parts the app controls.",
                size = 13.sp, color = Amoled.Muted, maxLines = 6,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Txt("Width +${(pb.width * 100).roundToInt()}%", Modifier.width(92.dp), size = 13.sp, weight = FontWeight.SemiBold)
                AccentSlider(pb.width / 0.5f, { v -> playback.setWidth(v * 0.5f) }, Modifier.weight(1f))
            }
            Txt("Stereo width widens the image without changing the tone. It is bypassed in Audiophile Mode.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
            Pill("Apply car profile") { playback.setWidth(0.25f); ServiceLocator.eq.setPreset("car"); playback.setNormGentle(true) }
            Txt(
                "Bluetooth: pick the best codec your car supports (AAC, aptX or LDAC) in Developer options. Keep Bluetooth absolute volume on and set " +
                    "the phone's volume high, so the car's dial does the attenuating.",
                size = 12.sp, color = Amoled.Muted, maxLines = 6,
            )
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
                "Vina has a built-in music source. If you run a compatible server of your own, put its https address here and it will be tried first.",
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

        Panel("Storage") {
            var clearOnExit by remember { mutableStateOf(CacheCleaner.enabled()) }
            var cacheBytes by remember { mutableLongStateOf(-1L) }
            val storageScope = rememberCoroutineScope()
            LaunchedEffect(Unit) { cacheBytes = withContext(Dispatchers.IO) { CacheCleaner.sizeBytes(ctx) } }
            ToggleRow(
                "Clear cache when Vina closes",
                "Artwork, buffered audio and Last.fm lookups are deleted when you close the app, so Vina keeps nothing but your library and settings. It never clears while music is playing.",
                if (clearOnExit) "On" else "Off", clearOnExit,
            ) { clearOnExit = !clearOnExit; CacheCleaner.setEnabled(clearOnExit) }
            Txt(
                if (cacheBytes < 0) "Cache: measuring..." else "Cache right now: " + StorageText.formatBytes(cacheBytes),
                size = 13.sp, color = Amoled.Muted,
            )
            Pill("Clear now") {
                storageScope.launch {
                    val freed = withContext(Dispatchers.IO) { CacheCleaner.clear(ctx) }
                    cacheBytes = withContext(Dispatchers.IO) { CacheCleaner.sizeBytes(ctx) }
                    Toast.makeText(ctx, "Freed " + StorageText.formatBytes(freed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        Panel("Updates") {
            Txt(s.updateStatus, size = 13.sp, color = if (update != null) Amoled.Ok else Amoled.Muted, maxLines = 3)
            var notifyOn by remember { mutableStateOf(ServiceLocator.updates.notifyEnabled) }
            ToggleRow(
                "Update notifications", "A check every few hours, even when Vina is closed. Tap the notification and the download starts by itself.",
                if (notifyOn) "On" else "Off", notifyOn,
            ) {
                notifyOn = !notifyOn
                ServiceLocator.updates.notifyEnabled = notifyOn
                runCatching { org.melodyne.app.data.update.UpdateScheduler.ensure(ctx) }
            }
            Pill("Check now") { vm.checkUpdates() }
            Txt(
                "Vina looks at the project's latest GitHub release when it opens (at most every 6 hours). Nothing installs by itself; " +
                    "A new version downloads inside Vina and Android then asks you to install it (the first time you will be asked to allow installs from Vina). A private repository needs a read-only GitHub token and opens the release page instead.",
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
                "Vina ${BuildConfig.VERSION_NAME}",
                Modifier.clickable {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap.longValue < 1500) taps + 1 else 1
                    lastTap.longValue = now
                    if (taps >= 5) { taps = 0; openEaster() }
                },
                size = 15.sp, weight = FontWeight.SemiBold,
            )
            Txt("Free software. No trackers, no ads, no analytics, no accounts.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
            Txt("Stonecaper loves 1509", size = 13.sp, weight = FontWeight.SemiBold, color = LocalAccent.current)
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
