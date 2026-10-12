package com.goosexgoose.llmit.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.data.WidgetWindowMode
import com.goosexgoose.llmit.work.RefreshScheduler
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class GaugeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val awId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initial = readState(context, awId)
        provideContent {
            val state by remember { stateFlow(context, awId) }.collectAsState(initial = initial)
            GlanceTheme { GaugeWidgetContent(state) }
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
        return buildCombinedState(context, s, awId, s.widgetWindowMode(awId), absolute = false)
    }
}

class GaugeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GaugeWidget()

    override fun onEnabled(context: android.content.Context) {
        super.onEnabled(context)
        RefreshScheduler.ensurePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }

    override fun onUpdate(context: android.content.Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshScheduler.ensurePeriodic(context)
    }

    override fun onAppWidgetOptionsChanged(context: android.content.Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Storage.get(context).nudge()
    }
}
