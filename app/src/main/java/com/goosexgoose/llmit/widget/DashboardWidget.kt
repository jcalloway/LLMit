package com.goosexgoose.llmit.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.goosexgoose.llmit.R
import com.goosexgoose.llmit.data.AuthState
import com.goosexgoose.llmit.data.Storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.roundToInt

class DashboardWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val awId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initial = readState(context, awId)
        provideContent {
            val state by remember { stateFlow(context, awId) }.collectAsState(initial = initial)
            GlanceTheme { DashboardWidgetContent(state) }
        }
    }

    private fun stateFlow(context: Context, awId: Int): Flow<WidgetState> = callbackFlow {
        val storage = Storage.get(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readState(context, awId))
        }
        storage.registerSnapshotListener(listener)
        trySend(readState(context, awId))
        awaitClose { storage.unregisterSnapshotListener(listener) }
    }.conflate()

    private fun readState(context: Context, awId: Int): WidgetState {
        val s = Storage.get(context)
        val now = System.currentTimeMillis()
        val absolute = s.showAbsoluteResetTime
        val serviceId = s.widgetServiceId(awId)

        val (label, needsLogin, snap) = when (serviceId) {
            "openai" -> Triple("Codex", !s.isOpenaiLoggedIn, s.loadServiceSnapshot("openai"))
            "cursor" -> Triple("Cursor", !s.isCursorLoggedIn, s.loadServiceSnapshot("cursor"))
            else -> Triple("Claude", !s.isLoggedIn || s.authState == AuthState.NEEDS_LOGIN,
                Storage.ServiceSnapshotData(
                    fiveHourUtil = s.fiveHourUtil, fiveHourReset = s.fiveHourReset,
                    sevenDayUtil = s.sevenDayUtil, sevenDayReset = s.sevenDayReset,
                    fableWeeklyUtil = s.fableWeeklyUtil, fableWeeklyReset = s.fableWeeklyReset,
                    fetchedAt = s.fetchedAt,
                ))
        }

        val fiveHourLabel = if (snap.fiveHourReset <= 0L) "-" else if (absolute) {
            context.getString(R.string.resets_at, TimeFmt.clockTime(context, snap.fiveHourReset))
        } else {
            context.getString(R.string.resets_in, TimeFmt.resetsIn(snap.fiveHourReset, now))
        }
        val sevenDayLabel = if (snap.sevenDayReset <= 0L) "-" else if (absolute) {
            context.getString(R.string.resets_at, TimeFmt.dateAndTime(context, snap.sevenDayReset))
        } else {
            context.getString(R.string.resets_in, TimeFmt.resetsIn(snap.sevenDayReset, now))
        }

        return WidgetState(
            serviceId = serviceId,
            serviceLabel = label,
            needsLogin = needsLogin,
            hasData = snap.hasData,
            fiveHourPct = snap.fiveHourUtil.coerceAtLeast(0f).roundToInt(),
            fiveHourResetLabel = fiveHourLabel,
            sevenDayPct = snap.sevenDayUtil.coerceAtLeast(0f).roundToInt(),
            sevenDayResetLabel = sevenDayLabel,
            stale = TimeFmt.isStale(snap.fetchedAt, now),
            refreshing = s.refreshRequestedAt > 0L && now - s.refreshRequestedAt < 60_000L,
            bgOpacityPercent = s.widgetOpacityPercent(awId),
            bgColorKey = s.widgetBgColorKey(awId),
            showUsed = s.showUsed,
            primaryLabel = if (serviceId == "cursor") "Auto" else "5H",
            secondaryLabel = if (serviceId == "cursor") "API" else "1W",
            primaryResetPrefix = if (serviceId == "cursor") "Billing reset" else "5h reset",
            secondaryResetPrefix = if (serviceId == "cursor") "Billing" else "Weekly",
        )
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DashboardWidget()
}
