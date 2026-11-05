package org.melodyne.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.melodyne.app.di.ServiceLocator

/** The widget's three buttons. They reach the same player the app uses, even if the app itself isn't open. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player = ServiceLocator.player
        when (intent.action) {
            TOGGLE -> player.command { if (it.isPlaying) it.pause() else it.play() }
            NEXT -> player.command { it.seekToNextMediaItem() }
            PREV -> player.command { if (it.currentPosition > 3_000) it.seekTo(0) else it.seekToPreviousMediaItem() }
        }
    }

    companion object {
        const val TOGGLE = "org.melodyne.app.widget.TOGGLE"
        const val NEXT = "org.melodyne.app.widget.NEXT"
        const val PREV = "org.melodyne.app.widget.PREV"
    }
}
