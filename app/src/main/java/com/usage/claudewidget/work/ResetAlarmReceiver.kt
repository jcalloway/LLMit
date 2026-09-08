package com.usage.claudewidget.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.usage.claudewidget.data.FetchResult
import com.usage.claudewidget.data.ProviderRegistry
import com.usage.claudewidget.data.Storage
import com.usage.claudewidget.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires from an exact AlarmManager alarm timed to a window's reset (see
 * [RefreshScheduler.scheduleResetCheck]). Unlike the WorkManager one-shot it replaced, an exact
 * `setExactAndAllowWhileIdle` alarm lands to-the-minute even in Doze, so the "limit available
 * again" notification is no longer batched a WorkManager cycle late.
 *
 * The fetch runs DIRECTLY here under goAsync() rather than handing off to WorkManager — the whole
 * point is to avoid WorkManager's scheduling latency, and the exact alarm grants a short network
 * allowlist window that comfortably covers a ~0.6s fetch. If a full Cloudflare re-solve ever
 * overruns the goAsync budget, the 15-minute periodic poll remains the backstop.
 */
class ResetAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                ProviderRegistry.init()
                var anySuccess = false
                for (provider in ProviderRegistry.authenticated(appContext)) {
                    val result = provider.refresh(appContext)
                    if (result is FetchResult.Success) {
                        anySuccess = true
                        try {
                            UsageNotifier.ensureChannel(appContext)
                            UsageNotifier.maybeNotifyForService(
                                appContext, Storage.get(appContext),
                                provider.serviceId, result.snapshot
                            )
                        } catch (_: Exception) { }
                    }
                }
                Storage.get(appContext).refreshRequestedAt = 0L
                updateAllWidgets(appContext)
                if (anySuccess) RefreshScheduler.scheduleResetCheck(appContext)
            } catch (_: Exception) { } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.usage.claudewidget.RESET_CHECK"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
