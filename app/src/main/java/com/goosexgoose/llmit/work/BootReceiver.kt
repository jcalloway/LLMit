package com.goosexgoose.llmit.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Exact alarms do not survive a reboot, so re-arm the reset check (and reconcile the periodic
 * refresh) once the device finishes booting. Reset times persist in encrypted storage, so
 * scheduleResetCheck can compute the next window immediately; if they're stale, it no-ops and the
 * next periodic poll re-arms.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RefreshScheduler.ensurePeriodic(context)
            RefreshScheduler.scheduleResetCheck(context)
        }
    }
}
