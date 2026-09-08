package com.usage.claudewidget.widget

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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.usage.claudewidget.R

@Composable
fun DashboardWidgetContent(state: WidgetState) {
    val size = LocalSize.current
    val gaugeSize = (size.height - 8.dp).coerceIn(48.dp, 120.dp)
    val arcPx = (gaugeSize.value * 3f).toInt().coerceIn(120, 600)
    val pct5 = state.fiveHourPct.coerceIn(0, 100)
    val pct7 = state.sevenDayPct.coerceIn(0, 100)
    val disp5 = if (state.showUsed) pct5 else (100 - pct5).coerceIn(0, 100)
    val disp7 = if (state.showUsed) pct7 else (100 - pct7).coerceIn(0, 100)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgFor(state.bgOpacityPercent, state.bgColorKey))
            .cornerRadius(20.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
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
                                fontSize = (gaugeSize.value * 0.28f).sp,
                                textAlign = TextAlign.Center,
                            ),
                        )
                        Text(
                            "%",
                            style = TextStyle(
                                color = gaugeColor(pct5),
                                fontSize = (gaugeSize.value * 0.14f).sp,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
                Text(
                    "${state.primaryLabel} Session",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                )
            }

            Spacer(GlanceModifier.width(6.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconRes = serviceIconRes(state.serviceId)
                    if (iconRes != null) {
                        Image(
                            provider = ImageProvider(iconRes),
                            contentDescription = null,
                            modifier = GlanceModifier.size(16.dp),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    Text(
                        state.serviceLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    state.secondaryLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                LinearProgressIndicator(
                    progress = pct7 / 100f,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                    color = gaugeColor(pct7),
                    backgroundColor = ColorProvider(R.color.bar_track),
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "${state.primaryResetPrefix}: ${state.fiveHourResetLabel.removePrefix("Resets in ")}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    "${state.secondaryResetPrefix} reset: ${state.sevenDayResetLabel.removePrefix("Resets in ")}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }

            Spacer(GlanceModifier.width(8.dp))

            Text(
                "$disp7%",
                style = TextStyle(
                    color = gaugeColor(pct7),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.End,
                ),
            )
        }
    }
}
