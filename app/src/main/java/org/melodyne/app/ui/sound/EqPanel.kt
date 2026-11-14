package org.melodyne.app.ui.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.audio.CUSTOM_ID
import org.melodyne.app.audio.EQ_BANDS
import org.melodyne.app.audio.EQ_MAX_DB
import org.melodyne.app.audio.EqImport
import org.melodyne.app.audio.EqMath
import org.melodyne.app.audio.EqPresets
import org.melodyne.app.audio.USER_PREFIX
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.Field
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.Txt

/**
 * Ten-band graphic equaliser, drawn as a response curve you drag, with presets of your own, and headphone-correction
 * import from AutoEQ profiles. Used inside Now Playing.
 */
@Composable
fun EqPanel(modifier: Modifier = Modifier) {
    val eq = ServiceLocator.eq
    val st by eq.state.collectAsState()
    val gains = st.gains
    val pb by ServiceLocator.playback.state.collectAsState()
    val ps by ServiceLocator.player.state.collectAsState()
    val headroom = remember(gains) { EqMath.headroomForTargets(gains) }
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var note by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    val isUser = st.presetId.startsWith(USER_PREFIX)

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt("Equalizer", size = 20.sp, weight = FontWeight.ExtraBold)
                Txt(
                    when {
                        pb.audiophile -> "Bypassed while Audiophile Mode is on."
                        pb.frugal -> "Bypassed while Frugal mode is on."
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

        EqGraph(gains, st.enabled && !pb.audiophile && !pb.frugal, ps.isPlaying, onBand = { i, db -> eq.setBand(i, db) })

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EqPresets.all) { p -> Chip(p.name, st.presetId == p.id) { eq.setPreset(p.id) } }
            items(st.users) { u -> Chip(u.name, st.presetId == u.id) { eq.setPreset(u.id) } }
            item { Chip("Custom", st.presetId == CUSTOM_ID) { eq.setPreset(CUSTOM_ID) } }
        }
        Txt(
            when {
                st.presetId == CUSTOM_ID -> "Your own curve. Drag any point on the graph."
                isUser -> "Your saved curve. Dragging a point makes a copy you can save again."
                else -> EqPresets.byId(st.presetId).blurb
            },
            size = 13.sp, color = Amoled.Muted, maxLines = 2,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Reset", filled = false) { eq.setBands(List(EQ_BANDS.size) { 0f }); note = null }
            Pill(if (naming) "Cancel" else "Save as…", filled = false) { naming = !naming }
            if (isUser) Pill("Delete", filled = false) { eq.deleteUser(st.presetId) }
        }
        if (naming) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Field(nameText, { nameText = it }, "Name this curve") }
                Pill("Save") { eq.saveUser(nameText); naming = false; nameText = "" }
            }
        }

        Txt("Headphone correction", Modifier.padding(top = 4.dp), size = 14.sp, weight = FontWeight.SemiBold, color = LocalAccent.current)
        Txt(
            "Copy an AutoEQ profile (the text of a ParametricEQ or GraphicEQ file), tap Paste profile, and the curve is set to match it. " +
                "Ten fixed bands follow a profile's broad shape; very narrow notches between bands are only approximated.",
            size = 12.sp, color = Amoled.Muted, maxLines = 6,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Paste profile") {
                val result = EqImport.parse(clipboard.getText()?.text.orEmpty())
                note = if (result == null) {
                    "No AutoEQ profile found on the clipboard. Copy the text of a ParametricEQ or GraphicEQ file first."
                } else {
                    eq.setBands(result.gains)
                    "Imported ${result.count} ${result.kind} onto ${EQ_BANDS.size} bands." +
                        (if (result.skipped > 0) " ${result.skipped} unsupported filters were skipped." else "") +
                        (if (result.clipped) " Some bands were limited to ±${EQ_MAX_DB.toInt()} dB." else "") +
                        " A Preamp line is ignored; automatic headroom covers it."
                }
            }
            Pill("Find profiles", filled = false) { uriHandler.openUri("https://autoeq.app") }
        }
        note?.let { Txt(it, size = 12.sp, color = LocalAccent.current, maxLines = 6) }
    }
}
