package com.goosexgoose.llmit.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.goosexgoose.llmit.R
import com.goosexgoose.llmit.data.ResetNotify
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.data.UsageSnapshot
import com.goosexgoose.llmit.data.Window
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
    private const val EARLY_MARGIN_MS = 60 * 60 * 1000L

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
            checkWindow(context, storage, "${prefix}5h", id5h, notify5hTitleRes(serviceId), snapshot.fiveHour, serviceId)
            checkWindow(context, storage, "${prefix}7d", id7d, notify7dTitleRes(serviceId), snapshot.sevenDay, serviceId)
            snapshot.fableWeekly?.let {
                checkWindow(context, storage, "${prefix}fable", idFable, notifyFableTitleRes(), it, serviceId)
            }
        }

        // Before checkReset, which advances the tracker this compares against. An early refill
        // gets its own notification, so the plain "limit reset" one is suppressed for it.
        val early7d = storage.unexpectedResetsEnabled &&
            checkUnexpectedReset(context, storage, "${prefix}7d", id7d + 10000, weeklyLabel(serviceId), snapshot.sevenDay, serviceId)
        val earlyFable = storage.unexpectedResetsEnabled && snapshot.fableWeekly?.let {
            checkUnexpectedReset(context, storage, "${prefix}fable", idFable + 10000, fableLabel(), it, serviceId)
        } == true

        checkReset(context, storage, "${prefix}5h", storage.reset5hMode, rid5h, reset5hTitleRes(serviceId), resetBody5hRes(serviceId), snapshot.fiveHour, serviceId)
        checkReset(context, storage, "${prefix}7d", storage.resetWeeklyMode, rid7d, reset7dTitleRes(serviceId), resetBody7dRes(serviceId), snapshot.sevenDay, serviceId, suppressPost = early7d)
        snapshot.fableWeekly?.let {
            checkReset(context, storage, "${prefix}fable", storage.resetWeeklyMode, ridFable, resetFableTitleRes(), R.string.reset_body_claude_fable, it, serviceId, suppressPost = earlyFable)
        }

        if (storage.weeklyWarningsEnabled) {
            // +20000: +5000 collided with Cursor's reset IDs (1002+5000 = 6002).
            checkWeeklyDepletion(context, storage, "${prefix}7d", id7d + 20000, weeklyLabel(serviceId), snapshot.sevenDay, serviceId)
            snapshot.fableWeekly?.let {
                checkWeeklyDepletion(context, storage, "${prefix}fable", idFable + 20000, fableLabel(), it, serviceId)
            }
        }
    }

    /**
     * The tracked cycle ended: either resets_at jumped past poll jitter, or the window went idle
     * (Codex reports no reset time at 0% until first use). Needs real usage in the old cycle.
     */
    private fun isNewCycle(prevSeen: Long, prevPct: Int, window: Window): Boolean {
        if (prevSeen <= 0L || prevPct <= 0) return false
        val cur = window.resetsAtEpochMs
        return if (cur > 0L) cur - prevSeen > RESET_JUMP_MS else window.utilization == 0f
    }

    private fun checkReset(
        context: Context,
        storage: Storage,
        key: String,
        mode: ResetNotify,
        notificationId: Int,
        titleRes: Int,
        bodyRes: Int,
        window: Window,
        serviceId: String = "claude",
        suppressPost: Boolean = false,
    ) {
        val prevSeen = storage.resetTrackSeen(key)
        val prevPct = storage.resetTrackPrevPct(key)
        val curReset = window.resetsAtEpochMs
        // Round (not truncate) so a "capped" window reporting e.g. 99.7 still counts as 100.
        val curPct = window.utilization.roundToInt()
        // Window missing (-1), or usage with no reset time: unknown, keep the last real cycle.
        if (window.utilization < 0f || (curReset <= 0L && curPct > 0)) return

        val didReset = isNewCycle(prevSeen, prevPct, window)

        // Advance the tracker unconditionally (independent of mode/permission) so it always
        // reflects the latest cycle. Otherwise, enabling the feature later could compare against
        // a stale value and fire a spurious catch-up notification. Idle windows record (0, 0).
        storage.updateResetTrack(key, curReset.coerceAtLeast(0L), curPct)

        if (!didReset || suppressPost) return

        val shouldNotify = when (mode) {
            ResetNotify.OFF -> false
            ResetNotify.ALWAYS -> true
            ResetNotify.WHEN_CAPPED -> prevPct >= storage.resetThresholdPct
        }
        if (!shouldNotify) return

        post(context, RESET_CHANNEL_ID, notificationId,
            context.getString(titleRes), context.getString(bodyRes), serviceId)
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
        // Dedupe is keyed on the reset time; without one every poll would re-alert.
        if (window.resetsAtEpochMs <= 0L) return
        if (storage.wasNotifiedFor(key, window.resetsAtEpochMs)) return

        val posted = post(context, CHANNEL_ID, notificationId,
            context.getString(titleRes, pct), context.getString(bodyResForService(serviceId)), serviceId)
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
    ): Boolean {
        val prevSeen = storage.resetTrackSeen(key)
        val prevPct = storage.resetTrackPrevPct(key)
        if (window.utilization < 0f) return false
        // A new cycle while the old one still had over an hour to run, and usage dropped.
        if (!isNewCycle(prevSeen, prevPct, window)) return false
        if (prevSeen - System.currentTimeMillis() < EARLY_MARGIN_MS) return false
        if (window.utilization.roundToInt() >= prevPct) return false
        val dedupe = window.resetsAtEpochMs.takeIf { it > 0L } ?: prevSeen
        if (storage.wasNotifiedFor("unexpected_$key", dedupe)) return true
        val posted = post(context, RESET_CHANNEL_ID, notificationId,
            "$label refilled early",
            "Your weekly limit was refilled before the scheduled reset.", serviceId)
        if (posted) storage.markNotified("unexpected_$key", dedupe)
        return true
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
        if (window.utilization < 0f) return
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

    private fun bodyResForService(serviceId: String): Int = when (serviceId) {
        "openai" -> R.string.notify_body_openai
        "cursor" -> R.string.notify_body_cursor
        else -> R.string.notify_body_claude
    }

    private fun resetBody5hRes(serviceId: String): Int = when (serviceId) {
        "openai" -> R.string.reset_body_openai_5h
        "cursor" -> R.string.reset_body_cursor_5h
        else -> R.string.reset_body_claude_5h
    }

    private fun resetBody7dRes(serviceId: String): Int = when (serviceId) {
        "openai" -> R.string.reset_body_openai_7d
        "cursor" -> R.string.reset_body_cursor_7d
        else -> R.string.reset_body_claude_7d
    }

    private fun notify5hTitleRes(serviceId: String): Int = when (serviceId) {
        "openai" -> R.string.notify_openai_5h_title
        "cursor" -> R.string.notify_cursor_5h_title
        else -> R.string.notify_claude_5h_title
    }

    private fun notify7dTitleRes(serviceId: String): Int = when (serviceId) {
        "openai" -> R.string.notify_openai_7d_title
        "cursor" -> R.string.notify_cursor_7d_title
        else -> R.string.notify_claude_7d_title
    }

    private fun notifyFableTitleRes(): Int = R.string.notify_claude_fable_title

    private fun reset5hTitleRes(serviceId: String): Int = when (serviceId) {
        "openai" -> R.string.reset_openai_5h_title
        "cursor" -> R.string.reset_cursor_5h_title
        else -> R.string.reset_claude_5h_title
    }

    private fun reset7dTitleRes(serviceId: String): Int = when (serviceId) {
        "openai" -> R.string.reset_openai_7d_title
        "cursor" -> R.string.reset_cursor_7d_title
        else -> R.string.reset_claude_7d_title
    }

    private fun resetFableTitleRes(): Int = R.string.reset_claude_fable_title

    private fun weeklyLabel(serviceId: String): String = when (serviceId) {
        "openai" -> "Codex Weekly"
        "cursor" -> "Cursor Weekly"
        else -> "Claude Weekly"
    }

    private fun fableLabel(): String = "Claude Fable"

    private fun usageUrlForService(serviceId: String): String = when (serviceId) {
        "openai" -> "https://chatgpt.com/#settings/Usage"
        "cursor" -> "https://www.cursor.com/settings"
        else -> "https://claude.ai/settings/usage"
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
            Intent(Intent.ACTION_VIEW, Uri.parse(usageUrlForService(serviceId)))
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
