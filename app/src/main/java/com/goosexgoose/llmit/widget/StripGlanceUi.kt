package com.goosexgoose.llmit.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goosexgoose.llmit.R
import com.goosexgoose.llmit.ui.MainActivity

@Composable
fun StripWidgetContent(state: WidgetState) {
    val size = widgetSize()
    val scale = (size.height.value / 90f).coerceIn(0.8f, 2f)
    val context = LocalContext.current
    val tap = if (state.needsLogin) {
        actionStartActivity(Intent(context, MainActivity::class.java))
    } else {
        actionRunCallback<RefreshAction>()
    }
    val gap = (12 * scale).coerceIn(8f, 24f).dp
    val cols = if (state.showFableWeekly) 3 else 2
    val colW = (size.width.value - 2 * (12 * scale).coerceIn(10f, 20f) - (cols - 1) * gap.value) / cols
    val cellScale = minOf(scale, colW / 90f).coerceAtLeast(0.8f)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgFor(state.bgOpacityPercent, state.bgColorKey))
            .cornerRadius(20.dp)
            .padding(horizontal = (12 * scale).coerceIn(10f, 20f).dp, vertical = (8 * scale).coerceIn(6f, 14f).dp)
            .clickable(tap),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            state.needsLogin -> Text(
                context.getString(R.string.tap_to_sign_in),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
            )
            !state.hasData -> Text("…", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
            else -> Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconRes = serviceIconRes(state.serviceId)
                    if (iconRes != null) {
                        Image(
                            provider = ImageProvider(iconRes),
                            contentDescription = null,
                            modifier = GlanceModifier.size((16 * scale).coerceIn(14f, 28f).dp),
                        )
                        Spacer(GlanceModifier.width((6 * scale).coerceIn(4f, 10f).dp))
                    }
                    Text(
                        state.serviceLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = (15 * scale).coerceIn(13f, 26f).sp,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height((6 * scale).coerceIn(4f, 12f).dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    StripCell(GlanceModifier.defaultWeight(), state.primaryLabel, state.fiveHourPct, state.fiveHourResetLabel, state.showUsed, cellScale)
                    Spacer(GlanceModifier.width(gap))
                    StripCell(GlanceModifier.defaultWeight(), state.secondaryLabel, state.sevenDayPct, state.sevenDayResetLabel, state.showUsed, cellScale)
                    if (state.showFableWeekly) {
                        Spacer(GlanceModifier.width(gap))
                        StripCell(GlanceModifier.defaultWeight(), "Fable", state.fableWeeklyPct, state.fableWeeklyResetLabel, state.showUsed, cellScale)
                    }
                }
            }
        }
        when {
            state.refreshing -> RefreshingIndicator()
            state.hasData && state.stale -> StaleDot()
        }    }
}

@Composable
private fun StripCell(modifier: GlanceModifier, label: String, pct: Int, reset: String, showUsed: Boolean, scale: Float) {
    val display = if (showUsed) pct else (100 - pct).coerceIn(0, 100)
    val font = (12 * scale).coerceIn(11f, 20f).sp
    val barH = (5 * scale).coerceIn(4f, 9f).dp
    Column(modifier = modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = font), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "$display%",
                style = TextStyle(color = gaugeColor(pct), fontWeight = FontWeight.Bold, fontSize = font),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height((3 * scale).coerceIn(2f, 6f).dp))
        LinearProgressIndicator(
            progress = pct.coerceIn(0, 100) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(barH).cornerRadius((barH.value / 2).dp),
            color = gaugeColor(pct),
            backgroundColor = ColorProvider(R.color.bar_track),
        )
        Spacer(GlanceModifier.height((3 * scale).coerceIn(2f, 6f).dp))
        Text(
            reset,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = (10 * scale).coerceIn(9f, 16f).sp),
            maxLines = 1,
        )
    }
}
