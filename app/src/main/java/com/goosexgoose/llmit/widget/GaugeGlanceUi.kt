package com.goosexgoose.llmit.widget

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
import com.goosexgoose.llmit.R

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

private fun drawConcentricBitmap(
    innerFillPct: Int, innerColorPct: Int,
    outerFillPct: Int, outerColorPct: Int,
    innerLabel: String = "5h", outerLabel: String = "1w",
    sizePx: Int = 220,
): Bitmap {
    val outerStroke = sizePx * 0.09f
    val innerStroke = sizePx * 0.09f
    val gap = sizePx * 0.04f
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val outerInset = outerStroke / 2f
    val outerOval = RectF(outerInset, outerInset, sizePx - outerInset, sizePx - outerInset)
    val outerColor = gaugeColorInt(outerColorPct)
    canvas.drawArc(outerOval, ARC_START, ARC_SPAN, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = outerStroke; strokeCap = Paint.Cap.ROUND
        color = (outerColor and 0x00FFFFFF) or (TRACK_ALPHA shl 24)
    })
    val outerClamped = outerFillPct.coerceIn(0, 100)
    if (outerClamped > 0) {
        canvas.drawArc(outerOval, ARC_START, (outerClamped / 100f) * ARC_SPAN, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = outerStroke; strokeCap = Paint.Cap.ROUND
                color = outerColor
            })
    }

    val innerInset = outerStroke + gap + innerStroke / 2f
    val innerOval = RectF(innerInset, innerInset, sizePx - innerInset, sizePx - innerInset)
    val innerColor = gaugeColorInt(innerColorPct)
    canvas.drawArc(innerOval, ARC_START, ARC_SPAN, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = innerStroke; strokeCap = Paint.Cap.ROUND
        color = (innerColor and 0x00FFFFFF) or (TRACK_ALPHA shl 24)
    })
    val innerClamped = innerFillPct.coerceIn(0, 100)
    if (innerClamped > 0) {
        canvas.drawArc(innerOval, ARC_START, (innerClamped / 100f) * ARC_SPAN, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = innerStroke; strokeCap = Paint.Cap.ROUND
                color = innerColor
            })
    }

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val outerRadius = (sizePx - outerStroke) / 2f
    val innerRadius = sizePx / 2f - innerInset

    val labelSize = sizePx * 0.11f
    val innerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = labelSize
        typeface = Typeface.DEFAULT_BOLD; color = innerColor
    }
    val outerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = labelSize
        typeface = Typeface.DEFAULT_BOLD; color = outerColor
    }

    val fm = innerTextPaint.fontMetrics
    val textCenterOffset = -(fm.ascent + fm.descent) / 2f
    val gapEndY = 0.74f
    canvas.drawText("$innerLabel $innerFillPct%", cx, cy + innerRadius * gapEndY + textCenterOffset, innerTextPaint)
    canvas.drawText("$outerLabel $outerFillPct%", cx, cy + outerRadius * (gapEndY + 0.03f) + textCenterOffset, outerTextPaint)

    return bmp
}

@Composable
fun GaugeWidgetContent(state: CombinedWidgetState) {
    val size = LocalSize.current
    val compact = size.width < 130.dp
    val isConcentric = state.windowLabel.isEmpty()
    val serviceCount = state.services.size.coerceAtLeast(1)

    val labelHeight = if (state.windowLabel.isNotEmpty()) 18.dp else 0.dp
    val pad = 4.dp
    val maxByWidth = (size.width - pad * 2) / serviceCount
    val maxByHeight = size.height - pad * 2 - labelHeight
    val gaugeSize = minOf(maxByWidth, maxByHeight).coerceIn(48.dp, 300.dp)
    val scale = (gaugeSize.value / 80f).coerceIn(0.7f, 2.5f)
    val concentricSize = minOf(maxByWidth, size.height - pad * 2).coerceIn(80.dp, 300.dp)
    val arcSizePx = (gaugeSize.value * 3f).toInt().coerceIn(120, 900)
    val concentricPx = (concentricSize.value * 3f).toInt().coerceIn(160, 900)

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
        } else if (isConcentric) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.services.forEachIndexed { i, svc ->
                    if (i > 0) Spacer(GlanceModifier.width(4.dp))
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ConcentricGaugeCell(svc, state.showUsed, concentricSize, concentricPx)
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
private fun ConcentricGaugeCell(svc: CombinedServiceState, showUsed: Boolean, cellSize: androidx.compose.ui.unit.Dp, sizePx: Int) {
    val pct5 = svc.fiveHourPct.coerceIn(0, 100)
    val pct7 = svc.weeklyPct.coerceIn(0, 100)
    val disp5 = if (showUsed) pct5 else (100 - pct5).coerceIn(0, 100)
    val disp7 = if (showUsed) pct7 else (100 - pct7).coerceIn(0, 100)
    val iconRes = serviceIconRes(svc.serviceId)
    val logoSize = (cellSize.value * 0.30f).coerceAtLeast(18f).dp

    Box(
        modifier = GlanceModifier.size(cellSize),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(drawConcentricBitmap(disp5, pct5, disp7, pct7, svc.primaryLabel, svc.secondaryLabel, sizePx)),
            contentDescription = null,
            modifier = GlanceModifier.size(cellSize),
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
