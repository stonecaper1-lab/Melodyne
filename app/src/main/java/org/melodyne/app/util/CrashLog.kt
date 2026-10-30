package org.melodyne.app.util

import android.content.SharedPreferences
import android.util.Log
import java.util.Date

/** Local-only crash recorder: the trace stays on the phone and is shown in Settings. No network, no SDK. */
object CrashLog {
    private const val KEY = "last_crash"

    fun install(prefs: SharedPreferences) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                prefs.edit().putString(KEY, "${Date()} · thread ${t.name}\n" + Log.getStackTraceString(e).take(3500)).commit()
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun last(prefs: SharedPreferences): String? = prefs.getString(KEY, null)
    fun clear(prefs: SharedPreferences) { prefs.edit().remove(KEY).apply() }
}
