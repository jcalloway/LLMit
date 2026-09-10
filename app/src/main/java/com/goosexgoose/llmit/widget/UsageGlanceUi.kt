package com.goosexgoose.llmit.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.CircularProgressIndicator
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
import androidx.glance.LocalContext
import android.content.Intent
import com.goosexgoose.llmit.R
import com.goosexgoose.llmit.ui.MainActivity
import kotlin.math.roundToInt

data class WidgetState(
    val serviceId: String = "",
    val serviceLabel: String = "",
    val needsLogin: Boolean,
    val hasData: Boolean,
    val fiveHourPct: Int,
    val fiveHourResetLabel: String,
    val sevenDayPct: Int,
    val sevenDayResetLabel: String,
    val showFableWeekly: Boolean = false,
    val fableWeeklyPct: Int = 0,
    val fableWeeklyResetLabel: String = "",
    val stale: Boolean,
    val refreshing: Boolean,
    val bgOpacityPercent: Int = 100,
    val bgColorKey: String = "pure",
    val showUsed: Boolean = false,
    val primaryLabel: String = "5H",
    val secondaryLabel: String = "1W",
    val primaryResetPrefix: String = "5h reset",
    val secondaryResetPrefix: String = "Weekly reset",
)

private val COMPACT_MAX_WIDTH = 130.dp

private val accent = ColorProvider(R.color.accent)

internal fun serviceIconRes(serviceId: String): Int? = when (serviceId) {
    "claude" -> R.drawable.ic_lobster_color
    "openai" -> R.drawable.ic_openai_color
    "cursor" -> R.drawable.ic_cursor_color
    else -> null
}

private val BG_RES = mapOf(
    "warm" to intArrayOf(
        R.color.widget_bg_00, R.color.widget_bg_10, R.color.widget_bg_20,
        R.color.widget_bg_30, R.color.widget_bg_40, R.color.widget_bg_50,
        R.color.widget_bg_60, R.color.widget_bg_70, R.color.widget_bg_80,
        R.color.widget_bg_90, R.color.widget_bg_100,
    ),
    "pure" to intArrayOf(
        R.color.widget_bg_pure_00, R.color.widget_bg_pure_10, R.color.widget_bg_pure_20,
        R.color.widget_bg_pure_30, R.color.widget_bg_pure_40, R.color.widget_bg_pure_50,
        R.color.widget_bg_pure_60, R.color.widget_bg_pure_70, R.color.widget_bg_pure_80,
        R.color.widget_bg_pure_90, R.color.widget_bg_pure_100,
    ),
    "claude" to intArrayOf(
        R.color.widget_bg_claude_00, R.color.widget_bg_claude_10, R.color.widget_bg_claude_20,
        R.color.widget_bg_claude_30, R.color.widget_bg_claude_40, R.color.widget_bg_claude_50,
        R.color.widget_bg_claude_60, R.color.widget_bg_claude_70, R.color.widget_bg_claude_80,
        R.color.widget_bg_claude_90, R.color.widget_bg_claude_100,
    ),
    "codex" to intArrayOf(
        R.color.widget_bg_codex_00, R.color.widget_bg_codex_10, R.color.widget_bg_codex_20,
        R.color.widget_bg_codex_30, R.color.widget_bg_codex_40, R.color.widget_bg_codex_50,
        R.color.widget_bg_codex_60, R.color.widget_bg_codex_70, R.color.widget_bg_codex_80,
        R.color.widget_bg_codex_90, R.color.widget_bg_codex_100,
    ),
    "cursor" to intArrayOf(
        R.color.widget_bg_cursor_00, R.color.widget_bg_cursor_10, R.color.widget_bg_cursor_20,
        R.color.widget_bg_cursor_30, R.color.widget_bg_cursor_40, R.color.widget_bg_cursor_50,
        R.color.widget_bg_cursor_60, R.color.widget_bg_cursor_70, R.color.widget_bg_cursor_80,
        R.color.widget_bg_cursor_90, R.color.widget_bg_cursor_100,
    ),
)

internal fun widgetBgFor(percent: Int, colorKey: String = "pure"): ColorProvider {
    val idx = (percent.coerceIn(0, 100) / 10.0).roundToInt().coerceIn(0, 10)
    val arr = BG_RES[colorKey] ?: BG_RES["pure"]!!
    return ColorProvider(arr[idx])
}
private fun barTrack() = ColorProvider(R.color.bar_track)

private fun colorForPct(pct: Int): ColorProvider = when {
    pct >= 85 -> ColorProvider(R.color.meter_danger)
    pct >= 60 -> ColorProvider(R.color.meter_warn)
    else -> ColorProvider(R.color.meter_good)
}

@Composable
fun UsageWidgetContent(state: WidgetState) {
    val size = LocalSize.current
    val compact = size.width < COMPACT_MAX_WIDTH
    val context = LocalContext.current

    val tap = if (state.needsLogin) {
        actionStartActivity(Intent(context, MainActivity::class.java))
    } else {
        actionRunCallback<RefreshAction>()
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgFor(state.bgOpacityPercent, state.bgColorKey))
            .cornerRadius(20.dp)
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 6.dp else 8.dp,
            )
            .clickable(tap),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.needsLogin -> SignInPrompt(compact)
            !state.hasData -> Loading(compact)
            compact -> CompactLayout(state)
            else -> FullLayout(state)
        }
        when {
            state.refreshing -> RefreshingIndicator()
            state.hasData && state.stale -> StaleDot()
        }
    }
}

@Composable
private fun FullLayout(s: WidgetState) {
    val size = LocalSize.current
    val scale = (size.height.value / 140f).coerceIn(0.8f, 2.5f)
    val iconSize = (16 * scale).coerceAtLeast(16f).dp
    val barHeight = (6 * scale).coerceAtLeast(6f).dp
    val fontSize = (14 * scale).coerceAtLeast(14f).sp
    val smallFontSize = (12 * scale).coerceAtLeast(12f).sp
    val rowSpacing = (10 * scale).coerceAtLeast(10f).dp

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (s.serviceLabel.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconRes = serviceIconRes(s.serviceId)
                if (iconRes != null) {
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = null,
                        modifier = GlanceModifier.size(iconSize),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(
                    s.serviceLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                    ),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
        }
        MeterRow(s.primaryLabel, s.fiveHourPct, s.fiveHourResetLabel, s.showUsed, barHeight, fontSize, smallFontSize)
        Spacer(GlanceModifier.height(rowSpacing))
        MeterRow(s.secondaryLabel, s.sevenDayPct, s.sevenDayResetLabel, s.showUsed, barHeight, fontSize, smallFontSize)
        if (s.showFableWeekly) {
            Spacer(GlanceModifier.height(rowSpacing))
            MeterRow("Fable", s.fableWeeklyPct, s.fableWeeklyResetLabel, s.showUsed, barHeight, fontSize, smallFontSize)
        }
    }
}

@Composable
private fun MeterRow(
    label: String, pct: Int, resetLabel: String, showUsed: Boolean = false,
    barHeight: androidx.compose.ui.unit.Dp = 6.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    smallFontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
) {
    val displayPct = if (showUsed) pct else (100 - pct).coerceIn(0, 100)
    val labelWidth = (fontSize.value * 3.4f).dp
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.width(labelWidth)) {
                Text(
                    label,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                    ),
                )
            }
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
                resetLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = smallFontSize,
                ),
            )
        }
        Spacer(GlanceModifier.height((barHeight.value * 0.67f).dp))
        LinearProgressIndicator(
            progress = (pct.coerceIn(0, 100)) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(barHeight).cornerRadius((barHeight.value / 2).dp),
            color = colorForPct(pct),
            backgroundColor = barTrack(),
        )
    }
}

@Composable
private fun CompactLayout(s: WidgetState) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Lobster(16.dp)
        Spacer(GlanceModifier.height(6.dp))
        CompactMeter(s.primaryLabel, s.fiveHourPct, s.showUsed)
        Spacer(GlanceModifier.height(6.dp))
        CompactMeter(s.secondaryLabel, s.sevenDayPct, s.showUsed)
        if (s.showFableWeekly) {
            Spacer(GlanceModifier.height(6.dp))
            CompactMeter("Fable", s.fableWeeklyPct, s.showUsed)
        }
    }
}

@Composable
private fun CompactMeter(label: String, pct: Int, showUsed: Boolean = false) {
    val displayPct = if (showUsed) pct else (100 - pct).coerceIn(0, 100)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                label,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "$displayPct%",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        Bar(pct)
    }
}

@Composable
private fun Bar(pct: Int) {
    LinearProgressIndicator(
        progress = (pct.coerceIn(0, 100)) / 100f,
        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
        color = colorForPct(pct),
        backgroundColor = barTrack(),
    )
}

@Composable
private fun Lobster(s: androidx.compose.ui.unit.Dp) {
    Image(
        provider = ImageProvider(R.drawable.ic_lobster_color),
        contentDescription = LocalContext.current.getString(R.string.mascot_desc),
        modifier = GlanceModifier.size(s),
    )
}

@Composable
private fun SignInPrompt(compact: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Lobster(if (compact) 18.dp else 24.dp)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            LocalContext.current.getString(
                if (compact) R.string.sign_in_short else R.string.tap_to_sign_in
            ),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun Loading(compact: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("…", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun RefreshingIndicator() {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(2.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        CircularProgressIndicator(
            modifier = GlanceModifier.size(12.dp),
            color = accent,
        )
    }
}

@Composable
private fun StaleDot() {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(2.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .cornerRadius(4.dp)
                .background(ColorProvider(R.color.stale)),
        ) {}
    }
}
