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
import com.goosexgoose.llmit.data.AuthState
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.data.WidgetWindowMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    private fun stateFlow(context: Context, awId: Int): Flow<CombinedWidgetState> = callbackFlow<Unit> {
        val storage = Storage.get(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        storage.registerSnapshotListener(listener)
        trySend(Unit)
        launch { while (true) { delay(60_000L); trySend(Unit) } }
        awaitClose { storage.unregisterSnapshotListener(listener) }
    }.debounce(100).conflate().map { readState(context, awId) }

    private fun readState(context: Context, awId: Int): CombinedWidgetState {
        val s = Storage.get(context)
        val windowMode = s.widgetWindowMode(awId)
        return buildCombinedState(context, s, awId, windowMode, s.showAbsoluteResetTime)
    }
}

/**
 * Shared by Bars and Gauge. Per service: a window the plan doesn't have (-1, e.g. Codex Pro Lite's
 * missing 5H) is never picked; an expired sign-in shows "Sign in" instead of frozen numbers; and
 * staleness is judged per service, so one fresh fetch can't hide another service's stale data.
 */
internal fun buildCombinedState(
    context: Context, s: Storage, awId: Int, windowMode: WidgetWindowMode, absolute: Boolean,
): CombinedWidgetState {
    val now = System.currentTimeMillis()
    val services = mutableListOf<CombinedServiceState>()
    var anyStale = false

    fun add(id: String, name: String, snap: Storage.ServiceSnapshotData, expired: Boolean,
            primary: String = "5h", secondary: String = "1w") {
        val has5 = snap.fiveHourUtil >= 0f
        val has7 = snap.sevenDayUtil >= 0f
        val pct5 = snap.fiveHourUtil.coerceAtLeast(0f).roundToInt()
        val pct7 = snap.sevenDayUtil.coerceAtLeast(0f).roundToInt()
        val useShort = when {
            !has7 -> true
            !has5 -> false
            else -> windowMode == WidgetWindowMode.FIVE_HOUR ||
                (windowMode == WidgetWindowMode.WORST && pct5 >= pct7)
        }
        val pct = if (useShort) pct5 else pct7
        val reset = if (useShort) snap.fiveHourReset else snap.sevenDayReset
        anyStale = anyStale || expired || TimeFmt.isStale(snap.fetchedAt, now)
        services.add(CombinedServiceState(id, name, pct,
            if (expired) "Sign in" else formatReset(context, reset, now, absolute, useShort),
            fiveHourPct = pct5, weeklyPct = pct7, hasFiveHour = has5, expired = expired,
            primaryLabel = primary, secondaryLabel = secondary))
    }

    // Each of these decrypts EncryptedSharedPreferences; read once per render, not twice.
    val claudeIn = s.isLoggedIn
    val openaiIn = s.isOpenaiLoggedIn
    val cursorIn = s.isCursorLoggedIn

    if (claudeIn && s.hasSnapshot) {
        add("claude", "Claude", Storage.ServiceSnapshotData(
            fiveHourUtil = s.fiveHourUtil, fiveHourReset = s.fiveHourReset,
            sevenDayUtil = s.sevenDayUtil, sevenDayReset = s.sevenDayReset,
            fableWeeklyUtil = s.fableWeeklyUtil, fableWeeklyReset = s.fableWeeklyReset,
            fetchedAt = s.fetchedAt,
        ), s.authState == AuthState.NEEDS_LOGIN)
    }
    if (openaiIn) {
        val snap = s.loadServiceSnapshot("openai")
        if (snap.hasData) add("openai", "Codex", snap, s.serviceAuthState("openai") == AuthState.NEEDS_LOGIN)
    }
    if (cursorIn) {
        val snap = s.loadServiceSnapshot("cursor")
        if (snap.hasData) add("cursor", "Cursor", snap, s.serviceAuthState("cursor") == AuthState.NEEDS_LOGIN,
            primary = "Auto", secondary = "API")
    }

    val anyLoggedIn = claudeIn || openaiIn || cursorIn
    val allExpired = services.isNotEmpty() && services.all { it.expired }

    return CombinedWidgetState(
        services = services,
        bgOpacityPercent = s.widgetOpacityPercent(awId),
        bgColorKey = s.widgetBgColorKey(awId),
        showUsed = s.showUsed,
        windowLabel = windowModeLabel(windowMode),
        refreshing = s.refreshRequestedAt > 0L && now - s.refreshRequestedAt < 60_000L,
        stale = anyStale,
        needsLogin = !anyLoggedIn || allExpired,
    )
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
