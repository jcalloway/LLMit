package com.usage.claudewidget.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.usage.claudewidget.R
import com.usage.claudewidget.data.ResetNotify
import com.usage.claudewidget.data.Storage
import com.usage.claudewidget.data.UsageSnapshot
import com.usage.claudewidget.data.Window
import kotlin.math.roundToInt

/**
 * Posts usage notifications: a near-limit heads-up when a window crosses [THRESHOLD_PCT]
 * (once per reset cycle), and an optional "limit reset" heads-up when a window rolls over.
 */
object UsageNotifier {

    private const val CHANNEL_ID = "usage_alerts"
    private const val RESET_CHANNEL_ID = "limit_resets"
    private const val DEFAULT_THRESHOLD_PCT = 90
    private const val CAPPED_PCT = 100       // reset "when capped" = window was actually maxed

    // A genuine reset moves resets_at forward by >= the window length (5h at minimum); poll
    // drift is sub-second, so any jump past this is unambiguously a reset, not jitter.
    private const val RESET_JUMP_MS = 2 * 60 * 60 * 1000L

    private const val WEEKLY_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

    private const val ID_5H = 1001
    private const val ID_7D = 1002
    private const val ID_FABLE = 1003
    private const val ID_RESET_5H = 2001
    private const val ID_RESET_7D = 2002
    private const val ID_RESET_FABLE = 2003
    private const val ID_UNEXPECTED_7D = 7001
    private const val ID_UNEXPECTED_FABLE = 7002
    private const val ID_WEEKLY_WARN_7D = 8001
    private const val ID_WEEKLY_WARN_FABLE = 8002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // createNotificationChannel is idempotent, so it's safe to (re)declare both every call.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RESET_CHANNEL_ID,
                context.getString(R.string.reset_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.reset_channel_desc) }
        )
    }

    private val SERVICE_NOTIFY_IDS = mapOf(
        "claude" to Triple(ID_5H, ID_7D, ID_FABLE),
        "openai" to Triple(3001, 3002, 3003),
        "cursor" to Triple(4001, 4002, 4003),
    )

    private val SERVICE_RESET_IDS = mapOf(
        "claude" to Triple(ID_RESET_5H, ID_RESET_7D, ID_RESET_FABLE),
        "openai" to Triple(5001, 5002, 5003),
        "cursor" to Triple(6001, 6002, 6003),
    )

    fun maybeNotifyForService(
        context: Context, storage: Storage, serviceId: String, snapshot: UsageSnapshot
    ) {
        val (id5h, id7d, idFable) = SERVICE_NOTIFY_IDS[serviceId] ?: return
        val (rid5h, rid7d, ridFable) = SERVICE_RESET_IDS[serviceId] ?: return
        val prefix = "${serviceId}_"

        if (storage.nearLimitAlertsEnabled) {
            checkWindow(context, storage, "${prefix}5h", id5h, R.string.notify_5h_title, snapshot.fiveHour, serviceId)
            checkWindow(context, storage, "${prefix}7d", id7d, R.string.notify_7d_title, snapshot.sevenDay, serviceId)
            snapshot.fableWeekly?.let {
                checkWindow(context, storage, "${prefix}fable", idFable, R.string.notify_fable_title, it, serviceId)
            }
        }

        checkReset(context, storage, "${prefix}5h", storage.reset5hMode, rid5h, R.string.reset_5h_title, snapshot.fiveHour, serviceId)
        checkReset(context, storage, "${prefix}7d", storage.resetWeeklyMode, rid7d, R.string.reset_7d_title, snapshot.sevenDay, serviceId)
        snapshot.fableWeekly?.let {
            checkReset(context, storage, "${prefix}fable", storage.resetWeeklyMode, ridFable, R.string.reset_fable_title, it, serviceId)
        }

        if (storage.weeklyWarningsEnabled) {
            checkWeeklyDepletion(context, storage, "${prefix}7d", id7d + 5000, "Weekly", snapshot.sevenDay, serviceId)
            snapshot.fableWeekly?.let {
                checkWeeklyDepletion(context, storage, "${prefix}fable", idFable + 5000, "Weekly · Fable", it, serviceId)
            }
        }
    }

    fun maybeNotify(context: Context, storage: Storage, snapshot: UsageSnapshot) {
        if (storage.nearLimitAlertsEnabled) {
            checkWindow(context, storage, "5h", ID_5H, R.string.notify_5h_title, snapshot.fiveHour)
            checkWindow(context, storage, "7d", ID_7D, R.string.notify_7d_title, snapshot.sevenDay)
            snapshot.fableWeekly?.let {
                checkWindow(context, storage, "fable", ID_FABLE, R.string.notify_fable_title, it)
            }
        }

        checkReset(context, storage, "5h", storage.reset5hMode, ID_RESET_5H, R.string.reset_5h_title, snapshot.fiveHour)
        checkReset(context, storage, "7d", storage.resetWeeklyMode, ID_RESET_7D, R.string.reset_7d_title, snapshot.sevenDay)
        snapshot.fableWeekly?.let {
            checkReset(context, storage, "fable", storage.resetWeeklyMode, ID_RESET_FABLE, R.string.reset_fable_title, it)
        }

        if (storage.unexpectedResetsEnabled) {
            checkUnexpectedReset(context, storage, "7d", ID_UNEXPECTED_7D, "Weekly", snapshot.sevenDay)
            snapshot.fableWeekly?.let {
                checkUnexpectedReset(context, storage, "fable", ID_UNEXPECTED_FABLE, "Weekly · Fable", it)
            }
        }

        if (storage.weeklyWarningsEnabled) {
            checkWeeklyDepletion(context, storage, "7d", ID_WEEKLY_WARN_7D, "Weekly", snapshot.sevenDay)
            snapshot.fableWeekly?.let {
                checkWeeklyDepletion(context, storage, "fable", ID_WEEKLY_WARN_FABLE, "Weekly · Fable", it)
            }
        }
    }

    private fun checkReset(
        context: Context,
        storage: Storage,
        key: String,
        mode: ResetNotify,
        notificationId: Int,
        titleRes: Int,
        window: Window,
        serviceId: String = "claude",
    ) {
        val prevSeen = storage.resetTrackSeen(key)
        val prevPct = storage.resetTrackPrevPct(key)
        val curReset = window.resetsAtEpochMs
        // Round (not truncate) so a "capped" window reporting e.g. 99.7 still counts as 100.
        val curPct = window.utilization.roundToInt()

        // Advance the tracker unconditionally (independent of mode/permission) so it always
        // reflects the latest cycle. Otherwise, enabling the feature later could compare against
        // a stale value and fire a spurious catch-up notification.
        storage.updateResetTrack(key, curReset, curPct)

        val didReset = prevSeen != 0L && curReset - prevSeen > RESET_JUMP_MS
        if (!didReset) return

        val shouldNotify = when (mode) {
            ResetNotify.OFF -> false
            ResetNotify.ALWAYS -> true
            ResetNotify.WHEN_CAPPED -> prevPct >= storage.resetThresholdPct
        }
        if (!shouldNotify) return

        post(context, RESET_CHANNEL_ID, notificationId,
            context.getString(titleRes), context.getString(R.string.reset_body), serviceId)
    }

    private fun checkWindow(
        context: Context,
        storage: Storage,
        key: String,
        notificationId: Int,
        titleRes: Int,
        window: Window,
        serviceId: String = "claude",
    ) {
        val pct = window.utilization.toInt()
        if (pct < storage.nearLimitThreshold) return
        if (storage.wasNotifiedFor(key, window.resetsAtEpochMs)) return

        val posted = post(context, CHANNEL_ID, notificationId,
            context.getString(titleRes, pct), context.getString(R.string.notify_body), serviceId)
        if (posted) storage.markNotified(key, window.resetsAtEpochMs)
    }

    private fun checkUnexpectedReset(
        context: Context,
        storage: Storage,
        key: String,
        notificationId: Int,
        label: String,
        window: Window,
        serviceId: String = "claude",
    ) {
        val prevSeen = storage.resetTrackSeen(key)
        val curReset = window.resetsAtEpochMs
        if (prevSeen == 0L) return
        val jump = curReset - prevSeen
        if (jump <= 0 || jump > RESET_JUMP_MS) return
        // resets_at moved forward but by less than 2h. That's an early refill, not a scheduled reset.
        if (storage.wasNotifiedFor("unexpected_$key", curReset)) return
        val posted = post(context, RESET_CHANNEL_ID, notificationId,
            "$label refilled early",
            "Your weekly limit was refilled before the scheduled reset.", serviceId)
        if (posted) storage.markNotified("unexpected_$key", curReset)
    }

    private fun checkWeeklyDepletion(
        context: Context,
        storage: Storage,
        key: String,
        notificationId: Int,
        label: String,
        window: Window,
        serviceId: String = "claude",
    ) {
        val pctUsed = window.utilization.toInt()
        val pctLeft = 100 - pctUsed
        if (pctLeft > storage.weeklyWarningRemaining) return
        val msUntilReset = window.resetsAtEpochMs - System.currentTimeMillis()
        if (msUntilReset <= 0) return
        val daysUntilReset = msUntilReset / (24 * 60 * 60 * 1000.0)
        if (daysUntilReset > storage.weeklyWarningTimingDays) return
        if (storage.wasNotifiedFor("depletion_$key", window.resetsAtEpochMs)) return
        val resetText = if (daysUntilReset >= 1) "${daysUntilReset.toInt()}d" else "<1d"
        val posted = post(context, CHANNEL_ID, notificationId,
            "$label is getting low",
            "${pctLeft}% left until $resetText reset.", serviceId)
        if (posted) storage.markNotified("depletion_$key", window.resetsAtEpochMs)
    }

    private fun iconForService(serviceId: String): Int = when (serviceId) {
        "openai" -> R.drawable.ic_openai
        "cursor" -> R.drawable.ic_cursor
        else -> R.drawable.ic_lobster
    }

    /** Posts one notification; returns false (without throwing) if notifications are disabled. */
    private fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        serviceId: String = "claude",
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            return false
        }

        val openUsage = android.app.PendingIntent.getActivity(
            context,
            notificationId,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/settings/usage"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconForService(serviceId))
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openUsage)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false // permission revoked between the check above and here
        }
    }
}
