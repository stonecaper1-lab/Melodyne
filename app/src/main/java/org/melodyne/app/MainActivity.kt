package org.melodyne.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.ui.App

class MainActivity : ComponentActivity() {
    private val openSignal = mutableIntStateOf(0)
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        // First frame is the dashboard (with a loading state), never a blank screen.
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) openSignal.intValue++
        setContent { App(ServiceLocator.player, openSignal.intValue) }
    }

    // singleTask: a tap on the notification while the app is alive arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openSignal.intValue++
    }

    companion object { const val EXTRA_OPEN_PLAYER = "open_player" }
}
