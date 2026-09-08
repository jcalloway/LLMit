package com.usage.claudewidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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

internal fun gaugeColorInt(pct: Int): Int = when {
    pct >= 85 -> 0xFFD1453B.toInt()
    pct >= 60 -> 0xFFE0A000.toInt()
    else      -> 0xFF4C9A6A.toInt()
}

internal fun gaugeColor(pct: Int): ColorProvider = when {
    pct >= 85 -> ColorProvider(R.color.meter_danger)
    pct >= 60 -> ColorProvider(R.color.meter_warn)
    else -> ColorProvider(R.color.meter_good)
}

private const val TRACK_ALPHA = 80
private const val ARC_SPAN = 270f
private const val ARC_START = 135f

internal fun drawArcBitmap(fillPct: Int, colorPct: Int, sizePx: Int = 160): Bitmap {
    val strokePx = (sizePx * 0.0875f).coerceAtLeast(8f)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val inset = strokePx / 2f
    val oval = RectF(inset, inset, sizePx - inset, sizePx - inset)
    val color = gaugeColorInt(colorPct)
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = strokePx; strokeCap = Paint.Cap.ROUND
        this.color = (color and 0x00FFFFFF) or (TRACK_ALPHA shl 24)
    }
    canvas.drawArc(oval, ARC_START, ARC_SPAN, false, trackPaint)
    val clamped = fillPct.coerceIn(0, 100)
    if (clamped > 0) {
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = strokePx; strokeCap = Paint.Cap.ROUND
            this.color = color
        }
        canvas.drawArc(oval, ARC_START, (clamped / 100f) * ARC_SPAN, false, arcPaint)
    }
    return bmp
}

private fun drawFigure8Bitmap(
    topFillPct: Int, topColorPct: Int,
    botFillPct: Int, botColorPct: Int,
    topLabel: String = "5h", botLabel: String = "1w",
    circlePx: Int = 220,
): Bitmap {
    val gapPx = -(circlePx * 0.30f).toInt()
    val strokePx = circlePx * 0.0727f
    val w = circlePx
    val h = circlePx * 2 + gapPx
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val inset = strokePx / 2f

    val topOval = RectF(inset, inset, w - inset, circlePx.toFloat() - inset)
    val botY = (circlePx + gapPx).toFloat()
    val botOval = RectF(inset, botY + inset, w - inset, botY + circlePx - inset)

    val topColor = gaugeColorInt(topColorPct)
    val topTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = strokePx; strokeCap = Paint.Cap.ROUND
        color = (topColor and 0x00FFFFFF) or (TRACK_ALPHA shl 24)
    }
    canvas.drawArc(topOval, ARC_START, ARC_SPAN, false, topTrack)
    val topClamped = topFillPct.coerceIn(0, 100)
    if (topClamped > 0) {
        val topFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = strokePx; strokeCap = Paint.Cap.ROUND
            color = topColor
        }
        canvas.drawArc(topOval, ARC_START, (topClamped / 100f) * ARC_SPAN, false, topFill)
    }

    val botColor = gaugeColorInt(botColorPct)
    val botTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = strokePx; strokeCap = Paint.Cap.ROUND
        color = (botColor and 0x00FFFFFF) or (TRACK_ALPHA shl 24)
    }
    canvas.drawArc(botOval, 315f, ARC_SPAN, false, botTrack)
    val botClamped = botFillPct.coerceIn(0, 100)
    if (botClamped > 0) {
        val botFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = strokePx; strokeCap = Paint.Cap.ROUND
            color = botColor
        }
        canvas.drawArc(botOval, 225f, -(botClamped / 100f) * ARC_SPAN, false, botFillPaint)
    }

    val pctTextSize = circlePx * 0.20f
    val labelTextSize = circlePx * 0.12f
    val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = pctTextSize; typeface = Typeface.DEFAULT_BOLD
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = labelTextSize; typeface = Typeface.DEFAULT_BOLD
    }

    val topCy = circlePx / 2f
    val botCy = botY + circlePx / 2f

    pctPaint.color = gaugeColorInt(topColorPct)
    canvas.drawText("$topFillPct%", w / 2f, topCy + pctTextSize * 0.35f, pctPaint)
    labelPaint.color = (gaugeColorInt(topColorPct) and 0x00FFFFFF) or (0xCC shl 24)
    canvas.drawText(topLabel, w / 2f, topCy - pctTextSize * 0.55f, labelPaint)

    pctPaint.color = gaugeColorInt(botColorPct)
    canvas.drawText("$botFillPct%", w / 2f, botCy + pctTextSize * 0.35f, pctPaint)
    labelPaint.color = (gaugeColorInt(botColorPct) and 0x00FFFFFF) or (0xCC shl 24)
    canvas.drawText(botLabel, w / 2f, botCy - pctTextSize * 0.55f, labelPaint)

    return bmp
}

@Composable
fun GaugeWidgetContent(state: CombinedWidgetState) {
    val size = LocalSize.current
    val compact = size.width < 130.dp
    val isFigure8 = state.windowLabel.isEmpty()
    val serviceCount = state.services.size.coerceAtLeast(1)

    val labelHeight = if (state.windowLabel.isNotEmpty()) 18.dp else 0.dp
    val pad = 2.dp
    val maxByWidth = (size.width - pad * 2) / serviceCount
    val maxByHeight = size.height - pad * 2 - labelHeight
    val gaugeSize = minOf(maxByWidth, maxByHeight).coerceIn(48.dp, 300.dp)
    val scale = (gaugeSize.value / 80f).coerceIn(0.7f, 2.5f)
    val availWidthPerService = (size.width - pad * 2) / serviceCount
    val f8MaxByWidth = availWidthPerService / 0.59f
    val f8Height = minOf(size.height - pad * 2, f8MaxByWidth).coerceIn(80.dp, 400.dp)
    val f8Width = (f8Height * 0.59f)
    val arcSizePx = (gaugeSize.value * 3f).toInt().coerceIn(120, 900)
    val f8CirclePx = (f8Height.value * 0.95f).toInt().coerceIn(160, 800)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgFor(state.bgOpacityPercent, state.bgColorKey))
            .cornerRadius(20.dp)
            .padding(pad),
        contentAlignment = Alignment.Center,
    ) {
        if (state.services.isEmpty()) {
            Text("No services", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        } else if (compact) {
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.services.forEachIndexed { i, svc ->
                    if (i > 0) Spacer(GlanceModifier.height(2.dp))
                    CompactGaugeRow(svc, state.showUsed)
                }
            }
        } else if (isFigure8) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.services.forEachIndexed { i, svc ->
                    if (i > 0) Spacer(GlanceModifier.width(2.dp))
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Figure8GaugeCell(svc, state.showUsed, f8Width, f8Height, f8CirclePx)
                    }
                }
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.services.forEachIndexed { i, svc ->
                    if (i > 0) Spacer(GlanceModifier.width(4.dp))
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularGaugeCell(svc, state.showUsed, state.windowLabel, gaugeSize, arcSizePx, scale)
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure8GaugeCell(svc: CombinedServiceState, showUsed: Boolean, cellWidth: androidx.compose.ui.unit.Dp, cellHeight: androidx.compose.ui.unit.Dp, circlePx: Int) {
    val pct5 = svc.fiveHourPct.coerceIn(0, 100)
    val pct7 = svc.weeklyPct.coerceIn(0, 100)
    val disp5 = if (showUsed) pct5 else (100 - pct5).coerceIn(0, 100)
    val disp7 = if (showUsed) pct7 else (100 - pct7).coerceIn(0, 100)
    val iconRes = serviceIconRes(svc.serviceId)
    val logoSize = (cellWidth.value * 0.30f).coerceAtLeast(14f).dp

    Box(
        modifier = GlanceModifier.width(cellWidth).height(cellHeight),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(drawFigure8Bitmap(disp5, pct5, disp7, pct7, svc.primaryLabel, svc.secondaryLabel, circlePx)),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        if (iconRes != null) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(logoSize),
            )
        }
    }
}

@Composable
private fun CircularGaugeCell(svc: CombinedServiceState, showUsed: Boolean, windowLabel: String, gaugeSize: androidx.compose.ui.unit.Dp, arcSizePx: Int, scale: Float) {
    val pct = svc.worstPct.coerceIn(0, 100)
    val displayPct = if (showUsed) pct else (100 - pct).coerceIn(0, 100)
    val iconRes = serviceIconRes(svc.serviceId)
    val logoSize = (gaugeSize.value * 0.30f).coerceAtLeast(16f).dp
    val fontSize = (gaugeSize.value * 0.18f).coerceAtLeast(10f).sp
    val labelFontSize = (gaugeSize.value * 0.14f).coerceAtLeast(11f).sp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.size(gaugeSize),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(drawArcBitmap(displayPct, pct, arcSizePx)),
                contentDescription = null,
                modifier = GlanceModifier.size(gaugeSize),
                contentScale = ContentScale.Fit,
            )
            if (iconRes != null) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(logoSize).padding(bottom = 4.dp),
                )
            }
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    "$displayPct%",
                    style = TextStyle(
                        color = gaugeColor(pct),
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier.padding(bottom = 6.dp),
                )
            }
        }
        if (windowLabel.isNotEmpty()) {
            Text(
                windowLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelFontSize,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun CompactGaugeRow(svc: CombinedServiceState, showUsed: Boolean = false) {
    val displayPct = if (showUsed) svc.worstPct else (100 - svc.worstPct).coerceIn(0, 100)
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
