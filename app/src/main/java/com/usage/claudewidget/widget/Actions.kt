package com.usage.claudewidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.usage.claudewidget.data.FetchResult
import com.usage.claudewidget.data.ProviderRegistry
import com.usage.claudewidget.data.Storage
import com.usage.claudewidget.work.RefreshScheduler

/**
 * Tap action when the widget is healthy: refresh immediately.
 *
 * The fetch runs DIRECTLY here (in the Glance action's own coroutine), not via WorkManager.
 * Diagnosis that led here: the in-app "Test fetch now" — which fetches directly — returns in
 * ~0.3s, yet the widget tap (which used to hand off to a one-time WorkManager RefreshWorker)
 * would sit stuck on its spinner. One-time WorkManager jobs are not "immediate": on many
 * devices they're throttled/delayed for a long time before they run, so the fetch was fine but
 * the *scheduling* stalled. Doing the fetch inline makes the tap instant again, matching the
 * in-app button. The periodic 15-min RefreshWorker still runs the background updates.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val t0 = System.currentTimeMillis()
        val storage = Storage.get(context)
        val tStorage = System.currentTimeMillis()

        // Debounce overlapping taps: a fresh tap while one is genuinely in flight is ignored.
        // Cleared to 0 in the finally below, so back-to-back taps after completion are allowed.
        if (storage.refreshRequestedAt != 0L && t0 - storage.refreshRequestedAt < COOLDOWN_MS) {
            storage.tapDiag = "ignored (debounce: another tap in flight)"
            storage.tapStartedAt = t0
            storage.tapFinishedAt = t0
            return
        }

        storage.tapStartedAt = t0
        storage.tapFinishedAt = 0L
        storage.tapDiag = "running…"

        // Flag the in-flight refresh so the widget shows a spinner right away.
        storage.refreshRequestedAt = t0
        updateAllWidgets(context)
        val tSpinner = System.currentTimeMillis()

        var outcome = "?"
        var tFetch = tSpinner
        try {
            ProviderRegistry.init()
            var anyOutcome = "?"
            for (provider in ProviderRegistry.authenticated(context)) {
                val r = provider.refresh(context)
                val tag = "${provider.serviceId}=${when (r) {
                    is FetchResult.Success -> "OK"
                    is FetchResult.NeedsLogin -> "NeedsLogin"
                    is FetchResult.Soft -> "Soft(${r.reason})"
                }}"
                anyOutcome = if (anyOutcome == "?") tag else "$anyOutcome, $tag"
            }
            outcome = anyOutcome
        } catch (e: Exception) {
            outcome = "EX(${e.javaClass.simpleName}: ${e.message})"
        } finally {
            tFetch = System.currentTimeMillis()
            // Always clear the spinner and render whatever we have, success or not.
            storage.refreshRequestedAt = 0L
            updateAllWidgets(context)
            val tEnd = System.currentTimeMillis()
            storage.tapFinishedAt = tEnd
            storage.tapDiag = "$outcome | storage ${tStorage - t0}ms" +
                " | spinner ${tSpinner - tStorage}ms" +
                " | fetch ${tFetch - tSpinner}ms" +
                " | render ${tEnd - tFetch}ms" +
                " | total ${tEnd - t0}ms"
            // A tap refreshed the snapshot, so re-arm the precise reset check off the new times.
            if (outcome == "OK") RefreshScheduler.scheduleResetCheck(context)
        }
    }

    private companion object {
        // Just a double-tap debounce now that the fetch is inline (~0.3s), not a guard against
        // WorkManager's REPLACE-cancellation (that path is no longer used for taps).
        const val COOLDOWN_MS = 5_000L
    }
}
