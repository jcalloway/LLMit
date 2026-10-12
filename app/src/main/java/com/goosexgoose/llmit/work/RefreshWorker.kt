package com.goosexgoose.llmit.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goosexgoose.llmit.data.FetchResult
import com.goosexgoose.llmit.data.ProviderRegistry
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.widget.updateAllWidgets

/** Fetches usage in the background and pushes the result into the widget. */
class RefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ProviderRegistry.init()
        var anySuccess = false
        var anySoft = false

        for (provider in ProviderRegistry.authenticated(applicationContext)) {
            when (val result = provider.refresh(applicationContext)) {
                is FetchResult.Success -> {
                    anySuccess = true
                    try {
                        UsageNotifier.ensureChannel(applicationContext)
                        UsageNotifier.maybeNotifyForService(
                            applicationContext, Storage.get(applicationContext),
                            provider.serviceId, result.snapshot
                        )
                    } catch (_: Exception) { }
                }
                is FetchResult.NeedsLogin -> { }
                is FetchResult.Soft -> { anySoft = true }
            }
        }

        Storage.get(applicationContext).refreshRequestedAt = 0L
        updateAllWidgets(applicationContext)
        if (anySuccess) RefreshScheduler.scheduleResetCheck(applicationContext)

        return when {
            // Retry only when nothing came through (offline): a retry re-fetches every provider, so
            // one persistently blocked provider would otherwise quadruple each 15-minute cycle.
            anySoft && !anySuccess && runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
