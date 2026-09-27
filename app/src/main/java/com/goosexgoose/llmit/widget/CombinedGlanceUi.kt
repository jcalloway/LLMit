package com.goosexgoose.llmit.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
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

data class CombinedServiceState(
    val serviceId: String,
    val label: String,
    val worstPct: Int,
    val resetLabel: String,
    val fiveHourPct: Int = 0,
    val weeklyPct: Int = 0,
    val primaryLabel: String = "5h",
    val secondaryLabel: String = "1w",
)

data class CombinedWidgetState(
    val services: List<CombinedServiceState>,
    val bgOpacityPercent: Int = 100,
    val bgColorKey: String = "pure",
    val showUsed: Boolean = false,
    val windowLabel: String = "",
    val refreshing: Boolean = false,
    val stale: Boolean = false,
)

private fun colorForPct(pct: Int): ColorProvider = when {
    pct >= 85 -> ColorProvider(R.color.meter_danger)
    pct >= 60 -> ColorProvider(R.color.meter_warn)
    else -> ColorProvider(R.color.meter_good)
}

@Composable
fun CombinedWidgetContent(state: CombinedWidgetState) {
    val size = widgetSize()
    val compact = size.width < 130.dp
    val serviceCount = state.services.size.coerceAtLeast(1)
    val scale = (size.height.value / (serviceCount * 38f)).coerceIn(0.7f, 2.5f)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgFor(state.bgOpacityPercent, state.bgColorKey))
            .cornerRadius(20.dp)
            .padding(
                horizontal = if (compact) 8.dp else (12 * scale).coerceIn(10f, 24f).dp,
                vertical = if (compact) 6.dp else (6 * scale).coerceIn(4f, 12f).dp,
            )
            .clickable(actionRunCallback<RefreshAction>()),
        contentAlignment = Alignment.Center,
    ) {
        if (state.services.isEmpty()) {
            Text("No services", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                if (state.windowLabel.isNotEmpty()) {
                    Text(
                        state.windowLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = (11 * scale).coerceIn(9f, 28f).sp,
                        ),
                    )
                    Spacer(GlanceModifier.height((4 * scale).dp))
                }
                state.services.forEachIndexed { i, svc ->
                    if (i > 0) Spacer(GlanceModifier.height(if (compact) (4 * scale).dp else (8 * scale).coerceIn(4f, 16f).dp))
                    if (compact) {
                        CompactServiceRow(svc, state.showUsed, scale)
                    } else {
                        FullServiceRow(svc, state.showUsed, scale)
                    }
                }
            }
        }
        when {
            state.refreshing -> RefreshingIndicator()
            state.stale -> StaleDot()
        }
        DebugSizeTag()
    }
}

@Composable
private fun FullServiceRow(svc: CombinedServiceState, showUsed: Boolean = false, scale: Float = 1f) {
    val displayPct = if (showUsed) svc.worstPct else (100 - svc.worstPct).coerceIn(0, 100)
    val iconSize = (14 * scale).coerceIn(10f, 35f).dp
    val barHeight = (6 * scale).coerceIn(4f, 12f).dp
    val fontSize = (14 * scale).coerceIn(11f, 35f).sp
    val smallFontSize = (12 * scale).coerceIn(10f, 30f).sp
    val iconGap = (4 * scale).coerceIn(3f, 10f).dp
    val iconRes = serviceIconRes(svc.serviceId)
    val barIndent = if (iconRes != null) iconSize + iconGap else 0.dp

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconRes != null) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(iconSize),
                )
                Spacer(GlanceModifier.width(iconGap))
            }
            Text(
                svc.label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                ),
                maxLines = 1,
                modifier = GlanceModifier.width((56 * scale).coerceIn(40f, 140f).dp),
            )
            Spacer(GlanceModifier.width((8 * scale).coerceIn(5f, 20f).dp))
            Text(
                "$displayPct%",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                svc.resetLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = smallFontSize,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height((3 * scale).coerceIn(2f, 6f).dp))
        Box(modifier = GlanceModifier.fillMaxWidth().padding(start = barIndent)) {
            LinearProgressIndicator(
                progress = (svc.worstPct.coerceIn(0, 100)) / 100f,
                modifier = GlanceModifier.fillMaxWidth().height(barHeight).cornerRadius((barHeight.value / 2).dp),
                color = colorForPct(svc.worstPct),
                backgroundColor = ColorProvider(R.color.bar_track),
            )
        }
    }
}

@Composable
private fun CompactServiceRow(svc: CombinedServiceState, showUsed: Boolean = false, scale: Float = 1f) {
    val displayPct = if (showUsed) svc.worstPct else (100 - svc.worstPct).coerceIn(0, 100)
    val iconSize = (12 * scale).coerceIn(9f, 30f).dp
    val fontSize = (12 * scale).coerceIn(10f, 24f).sp

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconRes = serviceIconRes(svc.serviceId)
        if (iconRes != null) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
            )
            Spacer(GlanceModifier.width((4 * scale).coerceIn(2f, 8f).dp))
        }
        Text(
            svc.label,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = fontSize),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            "$displayPct%",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = fontSize),
            maxLines = 1,
        )
    }
}
