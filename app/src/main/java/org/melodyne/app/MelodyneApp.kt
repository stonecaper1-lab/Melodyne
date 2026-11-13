package org.melodyne.app

import android.app.Application
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.util.CrashLog

class MelodyneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        CrashLog.install(ServiceLocator.prefs)
        runCatching { org.melodyne.app.data.update.UpdateScheduler.ensure(this) }
        runCatching { org.melodyne.app.data.local.CacheCleaner.sweepOnStart(this) }
        org.melodyne.app.player.FrugalMonitor.start(this)
    }
}
