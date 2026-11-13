package org.melodyne.app.player

import android.app.Application
import android.net.ConnectivityManager
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.melodyne.app.di.ServiceLocator

/** Feeds Frugal mode's "Auto" setting: is the phone on mobile data, or is Battery Saver on? Checked twice a minute. */
object FrugalMonitor {
    private var started = false

    fun start(app: Application) {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val pm = app.getSystemService(PowerManager::class.java)
            while (true) {
                val metered = runCatching { cm.isActiveNetworkMetered }.getOrDefault(false)
                val saver = runCatching { pm.isPowerSaveMode }.getOrDefault(false)
                ServiceLocator.playback.setEnv(metered, saver)
                delay(30_000)
            }
        }
    }
}
