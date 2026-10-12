package com.goosexgoose.llmit.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.goosexgoose.llmit.data.Const
import com.goosexgoose.llmit.data.ProviderRegistry
import com.goosexgoose.llmit.data.ResetNotify
import com.goosexgoose.llmit.data.Storage
import java.util.concurrent.TimeUnit

object RefreshScheduler {

    private val networkConstraint =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Reconciles the periodic 15-minute refresh with the user's setting: schedules it when
     * background refresh is on (idempotent KEEP), cancels it when off. Self-gating so every
     * caller (login, widget add/update, the settings toggle) respects the preference without
     * each having to check it.
     */
    fun ensurePeriodic(context: Context) {
        ProviderRegistry.init()
        if (!Storage.get(context).backgroundRefreshEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(Const.WORK_NAME)
            return
        }
        val work = PeriodicWorkRequestBuilder<RefreshWorker>(
            Const.REFRESH_INTERVAL_MIN, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Const.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work
        )
    }

    // Fire ~1 min past the reset so the server has definitely rolled the window over before we
    // fetch (resets_at is microsecond-precise; give clock skew and server timing a margin).
    private const val RESET_CHECK_BUFFER_MS = 60_000L

    /**
     * Arms an exact AlarmManager alarm to fire right at the next upcoming window reset (for windows
     * with reset alerts enabled), so the "limit available again" notification lands to-the-minute
     * instead of up to a full 15-minute poll cycle late. Re-armed after every fetch (and on boot),
     * and the 15-minute periodic refresh remains a backstop, so a missed exact fire self-heals.
     *
     * Exact alarms replaced a WorkManager one-shot here: WorkManager delayed jobs batch by a
     * minute or two in Doze, which left the reset alert ~12 minutes late. `setExactAndAllowWhileIdle`
     * fires on time through Doze.
     */
    fun scheduleResetCheck(context: Context) {
        val storage = Storage.get(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = resetAlarmPendingIntent(context)

        // Clear any leftover WorkManager one-shot from a pre-alarm build so it can't double-fire.
        WorkManager.getInstance(context).cancelUniqueWork(Const.RESET_CHECK_WORK)

        if (!storage.backgroundRefreshEnabled) {
            am.cancel(pi)
            return
        }

        val now = System.currentTimeMillis()
        val resetTimes = buildList {
            if (storage.isLoggedIn) {
                if (storage.reset5hMode != ResetNotify.OFF) add(storage.fiveHourReset)
                if (storage.resetWeeklyMode != ResetNotify.OFF) {
                    add(storage.sevenDayReset)
                    if (storage.fableWeeklyUtil >= 0f) add(storage.fableWeeklyReset)
                }
            }
            val others = listOfNotNull(
                "openai".takeIf { storage.isOpenaiLoggedIn },
                "cursor".takeIf { storage.isCursorLoggedIn },
            )
            for (id in others) {
                val snap = storage.loadServiceSnapshot(id)
                if (storage.reset5hMode != ResetNotify.OFF && snap.fiveHourUtil >= 0f) add(snap.fiveHourReset)
                if (storage.resetWeeklyMode != ResetNotify.OFF && snap.sevenDayUtil >= 0f) add(snap.sevenDayReset)
            }
        }
        val next = resetTimes.filter { it > now }.minOrNull()
        if (next == null) {
            am.cancel(pi)
            return
        }

        val triggerAt = next + RESET_CHECK_BUFFER_MS
        // SCHEDULE_EXACT_ALARM: default-granted on 31-32, user-grantable on 33+.
        // Falls back to inexact while-idle if revoked; periodic poll is the backstop.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun resetAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ResetAlarmReceiver::class.java)
            .setAction(ResetAlarmReceiver.ACTION)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Manual tap-to-refresh: run once now, ahead of the periodic schedule. */
    fun refreshNow(context: Context) {
        val work = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${Const.WORK_NAME}-now", ExistingWorkPolicy.REPLACE, work
        )
    }
}
