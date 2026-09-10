package com.goosexgoose.llmit.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.data.WidgetWindowMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.roundToInt

class CombinedWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val awId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initial = readState(context, awId)
        provideContent {
            val state by remember { stateFlow(context, awId) }.collectAsState(initial = initial)
            GlanceTheme { CombinedWidgetContent(state) }
        }
    }

    private fun stateFlow(context: Context, awId: Int): Flow<CombinedWidgetState> = callbackFlow {
        val storage = Storage.get(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readState(context, awId))
        }
        storage.registerSnapshotListener(listener)
        trySend(readState(context, awId))
        awaitClose { storage.unregisterSnapshotListener(listener) }
    }.conflate()

    private fun readState(context: Context, awId: Int): CombinedWidgetState {
        val s = Storage.get(context)
        val now = System.currentTimeMillis()
        val windowMode = s.widgetWindowMode(awId)
        val absolute = s.showAbsoluteResetTime
        val services = mutableListOf<CombinedServiceState>()

        if (s.isLoggedIn && s.hasSnapshot) {
            val pct5 = s.fiveHourUtil.coerceAtLeast(0f).roundToInt()
            val pct7 = s.sevenDayUtil.coerceAtLeast(0f).roundToInt()
            val (pct, reset) = pickWindow(windowMode, pct5, s.fiveHourReset, pct7, s.sevenDayReset)
            val isShort = windowMode == WidgetWindowMode.FIVE_HOUR ||
                (windowMode == WidgetWindowMode.WORST && pct5 >= pct7)
            services.add(CombinedServiceState("claude", "Claude", pct,
                formatReset(context, reset, now, absolute, isShort)))
        }

        if (s.isOpenaiLoggedIn) {
            val snap = s.loadServiceSnapshot("openai")
            if (snap.hasData) {
                val pct5 = snap.fiveHourUtil.coerceAtLeast(0f).roundToInt()
                val pct7 = snap.sevenDayUtil.coerceAtLeast(0f).roundToInt()
                val (pct, reset) = pickWindow(windowMode, pct5, snap.fiveHourReset, pct7, snap.sevenDayReset)
                val isShort = windowMode == WidgetWindowMode.FIVE_HOUR ||
                    (windowMode == WidgetWindowMode.WORST && pct5 >= pct7)
                services.add(CombinedServiceState("openai", "Codex", pct,
                    formatReset(context, reset, now, absolute, isShort)))
            }
        }

        if (s.isCursorLoggedIn) {
            val snap = s.loadServiceSnapshot("cursor")
            if (snap.hasData) {
                val pct5 = snap.fiveHourUtil.coerceAtLeast(0f).roundToInt()
                val pct7 = snap.sevenDayUtil.coerceAtLeast(0f).roundToInt()
                val (pct, reset) = pickWindow(windowMode, pct5, snap.fiveHourReset, pct7, snap.sevenDayReset)
                val isShort = windowMode == WidgetWindowMode.FIVE_HOUR ||
                    (windowMode == WidgetWindowMode.WORST && pct5 >= pct7)
                services.add(CombinedServiceState("cursor", "Cursor", pct,
                    formatReset(context, reset, now, absolute, isShort),
                    primaryLabel = "Auto", secondaryLabel = "API"))
            }
        }

        return CombinedWidgetState(
            services = services,
            bgOpacityPercent = s.widgetOpacityPercent(awId),
            bgColorKey = s.widgetBgColorKey(awId),
            showUsed = s.showUsed,
            windowLabel = windowModeLabel(windowMode),
        )
    }
}

internal fun windowModeLabel(mode: WidgetWindowMode): String = when (mode) {
    WidgetWindowMode.FIVE_HOUR -> "5-hour"
    WidgetWindowMode.WEEKLY -> "Weekly"
    WidgetWindowMode.WORST -> ""
}

internal fun pickWindow(
    mode: WidgetWindowMode,
    pct5: Int, reset5: Long,
    pct7: Int, reset7: Long,
): Pair<Int, Long> = when (mode) {
    WidgetWindowMode.FIVE_HOUR -> pct5 to reset5
    WidgetWindowMode.WEEKLY -> pct7 to reset7
    WidgetWindowMode.WORST -> if (pct5 >= pct7) pct5 to reset5 else pct7 to reset7
}

internal fun formatReset(context: Context, reset: Long, now: Long, absolute: Boolean, isShortWindow: Boolean): String {
    if (reset <= 0L) return "-"
    if (!absolute) return TimeFmt.resetsIn(reset, now)
    return if (isShortWindow) TimeFmt.clockTime(context, reset)
    else TimeFmt.dateAndTime(context, reset)
}
