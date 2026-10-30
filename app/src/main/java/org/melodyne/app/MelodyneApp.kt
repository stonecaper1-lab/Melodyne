package org.melodyne.app

import android.app.Application
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.util.CrashLog

class MelodyneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        CrashLog.install(ServiceLocator.prefs)
    }
}
