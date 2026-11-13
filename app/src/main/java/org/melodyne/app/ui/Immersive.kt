package org.melodyne.app.ui

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full screen while [enabled]: the status and navigation bars are hidden (a swipe from the edge shows them briefly) and
 * the content may extend into a camera notch. Everything is put back when this leaves the screen.
 */
@Composable
fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        var previousCutout = 0
        if (Build.VERSION.SDK_INT >= 28) {
            val attrs = window.attributes
            previousCutout = attrs.layoutInDisplayCutoutMode
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= 28) {
                val attrs = window.attributes
                attrs.layoutInDisplayCutoutMode = previousCutout
                window.attributes = attrs
            }
        }
    }
}
