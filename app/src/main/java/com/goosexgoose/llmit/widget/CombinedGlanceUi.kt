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
import com.goosexgoose.llmit.data.WidgetWindowMode

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
)

private fun colorForPct(pct: Int): ColorProvider = when {
    pct >= 85 -> ColorProvider(R.color.meter_danger)
    pct >= 60 -> ColorProvider(R.color.meter_warn)
    else -> ColorProvider(R.color.meter_good)
}

@Composable
fun CombinedWidgetContent(state: CombinedWidgetState) {
    val size = LocalSize.current
    val compact = size.width < 130.dp
    val scale = (size.height.value / 140f).coerceIn(0.7f, 2.5f)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgFor(state.bgOpacityPercent, state.bgColorKey))
            .cornerRadius(20.dp)
            .padding(
                horizontal = if (compact) 8.dp else (12 * scale.coerceAtMost(1.5f)).dp,
                vertical = if (compact) 6.dp else (8 * scale.coerceAtMost(1.5f)).dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (state.services.isEmpty()) {
            Text("No services", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                state.services.forEachIndexed { i, svc ->
                    if (i > 0) Spacer(GlanceModifier.height(if (compact) (4 * scale).dp else (8 * scale).dp))
                    if (compact) {
                        CompactServiceRow(svc, state.showUsed, scale)
                    } else {
                        FullServiceRow(svc, state.showUsed, scale)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullServiceRow(svc: CombinedServiceState, showUsed: Boolean = false, scale: Float = 1f) {
    val displayPct = if (showUsed) svc.worstPct else (100 - svc.worstPct).coerceIn(0, 100)
    val iconSize = (14 * scale).coerceAtLeast(14f).dp
    val barHeight = (6 * scale).coerceAtLeast(6f).dp
    val fontSize = (14 * scale).coerceAtLeast(14f).sp
    val smallFontSize = (12 * scale).coerceAtLeast(12f).sp

    Column(modifier = GlanceModifier.fillMaxWidth()) {
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
                Spacer(GlanceModifier.width((4 * scale).coerceAtLeast(4f).dp))
            }
            Text(
                svc.label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                ),
                modifier = GlanceModifier.width((56 * scale).coerceAtLeast(56f).dp),
            )
            Spacer(GlanceModifier.width((8 * scale).coerceAtLeast(8f).dp))
            Text(
                "$displayPct%",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                svc.resetLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = smallFontSize,
                ),
            )
        }
        Spacer(GlanceModifier.height((4 * scale).coerceAtLeast(4f).dp))
        LinearProgressIndicator(
            progress = (svc.worstPct.coerceIn(0, 100)) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(barHeight).cornerRadius((barHeight.value / 2).dp),
            color = colorForPct(svc.worstPct),
            backgroundColor = ColorProvider(R.color.bar_track),
        )
    }
}

@Composable
private fun CompactServiceRow(svc: CombinedServiceState, showUsed: Boolean = false, scale: Float = 1f) {
    val displayPct = if (showUsed) svc.worstPct else (100 - svc.worstPct).coerceIn(0, 100)
    val iconSize = (12 * scale).coerceAtLeast(12f).dp

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
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(
            svc.label,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            "$displayPct%",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold),
        )
    }
}
