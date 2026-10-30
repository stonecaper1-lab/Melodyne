package org.melodyne.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.domain.Song

/** Provided by App: opens the "add to playlist" dialog for a song. Any song row can call it. */
val LocalAddToPlaylist = staticCompositionLocalOf<(Song) -> Unit> { {} }

/** Provided by App: opens the signal-chain sheet / the hidden screen. */
val LocalOpenChain = staticCompositionLocalOf<() -> Unit> { {} }
val LocalOpenEaster = staticCompositionLocalOf<() -> Unit> { {} }

/** Rounded section card with an accent title. */
@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Amoled.Surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Txt(title, size = 17.sp, weight = FontWeight.Bold, color = LocalAccent.current)
        content()
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Txt(
        text,
        modifier.clip(RoundedCornerShape(50))
            .background(if (selected) accent else Amoled.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        size = 13.sp, weight = FontWeight.SemiBold, color = if (selected) Color.Black else Amoled.OnBg,
    )
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = Amoled.OnBg, fontSize = 16.sp),
        cursorBrush = SolidColor(LocalAccent.current),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        modifier = modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.heightIn(min = 96.dp))
            .clip(RoundedCornerShape(14.dp)).background(Amoled.SurfaceHi).padding(16.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Txt(hint, color = Amoled.Muted, size = 16.sp, maxLines = 2)
                inner()
            }
        },
    )
}

/** Tap or drag, value 0..1. */
@Composable
fun AccentSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val cb by rememberUpdatedState(onChange)
    Box(
        modifier.fillMaxWidth().height(32.dp)
            .pointerInput(Unit) { detectTapGestures { o -> cb((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> cb((change.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = 4.dp.toPx()
            val y = size.height / 2 - t / 2
            val v = value.coerceIn(0f, 1f)
            drawRoundRect(Color(0xFF2A2A2A), Offset(0f, y), Size(size.width, t), CornerRadius(t / 2))
            drawRoundRect(accent, Offset(0f, y), Size(size.width * v, t), CornerRadius(t / 2))
            drawCircle(accent, 9.dp.toPx(), Offset(size.width * v, size.height / 2))
        }
    }
}
