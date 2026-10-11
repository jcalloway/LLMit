package com.goosexgoose.llmit.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.goosexgoose.llmit.data.FetchResult
import com.goosexgoose.llmit.data.ProviderRegistry
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.work.RefreshScheduler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val t0 = System.currentTimeMillis()
        val storage = Storage.get(context)
        val tStorage = System.currentTimeMillis()

        if (storage.refreshRequestedAt != 0L && t0 - storage.refreshRequestedAt < COOLDOWN_MS) {
            storage.tapDiag = "ignored (debounce: another tap in flight)"
            storage.tapStartedAt = t0
            storage.tapFinishedAt = t0
            return
        }

        storage.tapStartedAt = t0
        storage.tapFinishedAt = 0L
        storage.tapDiag = "running…"

        storage.refreshRequestedAt = t0
        updateAllWidgets(context)
        val tSpinner = System.currentTimeMillis()

        var outcome = "?"
        var tFetch = tSpinner
        var anySuccess = false
        try {
            ProviderRegistry.init()
            val parts = mutableListOf<String>()
            for (provider in ProviderRegistry.authenticated(context)) {
                val tag = try {
                    val r = provider.refresh(context)
                    if (r is FetchResult.Success) anySuccess = true
                    "${provider.serviceId}=${when (r) {
                        is FetchResult.Success -> "OK"
                        is FetchResult.NeedsLogin -> "NeedsLogin"
                        is FetchResult.Soft -> "Soft(${r.reason})"
                    }}"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "${provider.serviceId}=EX(${e.javaClass.simpleName})"
                }
                parts.add(tag)
            }
            outcome = parts.joinToString(", ").ifEmpty { "no providers" }
        } catch (e: CancellationException) {
            outcome = "cancelled"
            throw e
        } catch (e: Exception) {
            outcome = "EX(${e.javaClass.simpleName}: ${e.message})"
        } finally {
            withContext(NonCancellable) {
                tFetch = System.currentTimeMillis()
                storage.refreshRequestedAt = 0L
                updateAllWidgets(context)
                val tEnd = System.currentTimeMillis()
                storage.tapFinishedAt = tEnd
                storage.tapDiag = "$outcome | storage ${tStorage - t0}ms" +
                    " | spinner ${tSpinner - tStorage}ms" +
                    " | fetch ${tFetch - tSpinner}ms" +
                    " | render ${tEnd - tFetch}ms" +
                    " | total ${tEnd - t0}ms"
                if (anySuccess) RefreshScheduler.scheduleResetCheck(context)
            }
        }
    }

    private companion object {
        const val COOLDOWN_MS = 45_000L
    }
}
