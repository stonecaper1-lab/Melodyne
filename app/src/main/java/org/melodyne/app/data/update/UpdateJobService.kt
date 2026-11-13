package org.melodyne.app.data.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.melodyne.app.di.ServiceLocator

/** Runs about every six hours, even when Vina is closed: looks for a newer release and notifies once per version. */
class UpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val updates = ServiceLocator.updates
        if (!updates.notifyEnabled || ServiceLocator.playback.state.value.frugal) return false
        running = scope.launch {
            try {
                val info = updates.newerRelease()
                val prefs = ServiceLocator.prefs
                if (info != null && prefs.getString("upd_notified", null) != info.version) {
                    UpdateNotifier.show(applicationContext, info)
                    prefs.edit().putString("upd_notified", info.version).apply()
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { running?.cancel(); return true }
}

object UpdateScheduler {
    private const val JOB_ID = 7001

    /** Idempotent: schedules the periodic check once (it survives reboots), or cancels it when notifications are switched off. */
    fun ensure(ctx: Context) {
        val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (!ServiceLocator.updates.notifyEnabled) { js.cancel(JOB_ID); return }
        if (js.getPendingJob(JOB_ID) != null) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJobService::class.java))
                .setPeriodic(6L * 3600 * 1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build(),
        )
    }
}
