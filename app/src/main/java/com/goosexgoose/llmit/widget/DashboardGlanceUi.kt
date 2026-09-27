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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goosexgoose.llmit.R

@Composable
fun DashboardWidgetContent(state: WidgetState) {
    val size = widgetSize()
    val scale = (size.height.value / 120f).coerceIn(0.8f, 2.5f)
    val gaugeSize = (size.height - (36 * scale).dp).coerceIn(48.dp, 200.dp)
    val arcPx = (gaugeSize.value * 3f).toInt().coerceIn(120, 900)
    val pct5 = state.fiveHourPct.coerceIn(0, 100)
    val pct7 = state.sevenDayPct.coerceIn(0, 100)
    val disp5 = if (state.showUsed) pct5 else (100 - pct5).coerceIn(0, 100)
    val disp7 = if (state.showUsed) pct7 else (100 - pct7).coerceIn(0, 100)
    val iconRes = serviceIconRes(state.serviceId)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgFor(state.bgOpacityPercent, state.bgColorKey))
            .cornerRadius(20.dp)
            .padding(horizontal = (10 * scale).coerceIn(8f, 16f).dp, vertical = (6 * scale).coerceIn(4f, 10f).dp)
            .clickable(actionRunCallback<RefreshAction>()),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = GlanceModifier.size(gaugeSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(drawArcBitmap(disp5, pct5, arcPx)),
                        contentDescription = null,
                        modifier = GlanceModifier.size(gaugeSize),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$disp5",
                            style = TextStyle(
                                color = gaugeColor(pct5),
                                fontWeight = FontWeight.Bold,
                                fontSize = (gaugeSize.value * 0.28f).coerceIn(11f, 56f).sp,
                                textAlign = TextAlign.Center,
                            ),
                        )
                        Text(
                            "%",
                            style = TextStyle(
                                color = gaugeColor(pct5),
                                fontSize = (gaugeSize.value * 0.14f).coerceIn(10f, 28f).sp,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
                Text(
                    state.primaryLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = (10 * scale).coerceIn(9f, 16f).sp,
                    ),
                    maxLines = 1,
                )
            }

            Spacer(GlanceModifier.width((6 * scale).coerceIn(4f, 10f).dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconRes != null) {
                        Image(
                            provider = ImageProvider(iconRes),
                            contentDescription = null,
                            modifier = GlanceModifier.size((18 * scale).coerceIn(16f, 32f).dp),
                        )
                        Spacer(GlanceModifier.width((6 * scale).coerceIn(4f, 10f).dp))
                    }
                    Text(
                        state.serviceLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = (14 * scale).coerceIn(11f, 24f).sp,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height((4 * scale).coerceIn(2f, 8f).dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.secondaryLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = (11 * scale).coerceIn(10f, 18f).sp,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        "$disp7%",
                        style = TextStyle(
                            color = gaugeColor(pct7),
                            fontWeight = FontWeight.Bold,
                            fontSize = (14 * scale).coerceIn(12f, 22f).sp,
                        ),
                    )
                }
                Spacer(GlanceModifier.height((2 * scale).coerceIn(1f, 4f).dp))
                LinearProgressIndicator(
                    progress = pct7 / 100f,
                    modifier = GlanceModifier.fillMaxWidth().height((6 * scale).coerceIn(5f, 10f).dp).cornerRadius((3 * scale).dp),
                    color = gaugeColor(pct7),
                    backgroundColor = ColorProvider(R.color.bar_track),
                )
                Spacer(GlanceModifier.height((4 * scale).coerceIn(2f, 8f).dp))
                Text(
                    "${state.primaryResetPrefix}: ${state.fiveHourResetLabel.removePrefix("Resets in ")}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = (10 * scale).coerceIn(9f, 16f).sp,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height((2 * scale).coerceIn(1f, 4f).dp))
                Text(
                    "${state.secondaryResetPrefix} reset: ${state.sevenDayResetLabel.removePrefix("Resets in ")}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = (10 * scale).coerceIn(9f, 16f).sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        when {
            state.refreshing -> RefreshingIndicator()
            state.hasData && state.stale -> StaleDot()
        }
        DebugSizeTag()
    }
}
