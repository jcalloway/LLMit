package com.goosexgoose.llmit.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.goosexgoose.llmit.R
import com.goosexgoose.llmit.data.ProviderRegistry
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.widget.TimeFmt
import com.goosexgoose.llmit.widget.updateAllWidgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val BarBlue = Color(0xFF3B82F6)
private val BarRed = Color(0xFFEF4444)
private val BarTrack = Color(0xFFE5E7EB)
private val BarTrackDark = Color(0xFF374151)

private val BrandTint = mapOf(
    "claude" to Color(0xFFD97706),
    "openai" to Color(0xFF10A37F),
    "cursor" to Color(0xFF8B5CF6),
)

// Icon + title colors per theme (contrast against 25% brand-tinted cards)
private val BrandTitleLight = mapOf(
    "claude" to Color(0xFFC2600A),
    "openai" to Color(0xFF0D8A6A),
    "cursor" to Color(0xFF7044E0),
)
private val BrandTitleDark = mapOf(
    "claude" to Color(0xFFFBBF24),
    "openai" to Color(0xFF34D399),
    "cursor" to Color(0xFFC4B5FD),
)

private data class UsageRow(
    val label: String,
    val pctUsed: Int,
    val resetMs: Long,
)

private data class ServiceDashData(
    val serviceId: String,
    val label: String,
    val iconRes: Int,
    val loggedIn: Boolean,
    val rows: List<UsageRow>,
    val fetchedAt: Long,
    val planTier: String? = null,
    val billingCycleEnd: Long = 0L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit,
    onRefreshed: () -> Unit,
) {
    val context = LocalContext.current
    val storage = remember { Storage.get(context) }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !refreshing) {
                refreshing = true
                scope.launch {
                    for (p in ProviderRegistry.authenticated(context)) p.refresh(context)
                    updateAllWidgets(context)
                    refreshing = false
                    tick = System.currentTimeMillis()
                    onRefreshed()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showUsed = remember(tick) { storage.showUsed }
    val services = remember(tick, refreshing) { buildServiceList(storage) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "LLMit",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!refreshing) {
                                refreshing = true
                                scope.launch {
                                    for (p in ProviderRegistry.authenticated(context)) p.refresh(context)
                                    updateAllWidgets(context)
                                    refreshing = false
                                    tick = System.currentTimeMillis()
                                    onRefreshed()
                                }
                            }
                        },
                    ) {
                        val spin = rememberInfiniteTransition(label = "spin")
                        val angle by spin.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                tween(800, easing = LinearEasing),
                                RepeatMode.Restart,
                            ),
                            label = "refreshSpin",
                        )
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            modifier = if (refreshing) Modifier.rotate(angle) else Modifier,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                ,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val loggedInServices = services.filter { it.loggedIn }

            loggedInServices.forEach { svc ->
                ServiceCard(svc, tick, showUsed, modifier = Modifier.weight(1f))
            }

            val latestFetch = services.maxOfOrNull { it.fetchedAt } ?: 0L
            if (latestFetch > 0L) {
                Text(
                    "Updated ${agoText(latestFetch, tick)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                )
            }

        }
    }
}

@Composable
private fun ServiceCard(svc: ServiceDashData, tick: Long, showUsed: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                BrandTint[svc.serviceId] ?: MaterialTheme.colorScheme.surfaceVariant,
                0.25f,
            ),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val surfaceLum = MaterialTheme.colorScheme.surface.luminance()
                val titleColors = if (surfaceLum < 0.5f) BrandTitleDark else BrandTitleLight
                val titleColor = titleColors[svc.serviceId]
                    ?: MaterialTheme.colorScheme.onSurfaceVariant
                val iconTint = if (svc.serviceId != "claude") {
                    androidx.compose.ui.graphics.ColorFilter.tint(titleColor)
                } else null
                Image(
                    painter = painterResource(svc.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = iconTint,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    svc.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                if (svc.planTier != null) {
                    Spacer(Modifier.weight(1f))
                    val isDark = surfaceLum < 0.5f
                    val badgeBg = if (isDark) {
                        titleColor.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    }
                    val badgeBorder = titleColor.copy(alpha = if (isDark) 0.5f else 0.6f)
                    Text(
                        svc.planTier,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        modifier = Modifier
                            .border(1.5f.dp, badgeBorder, RoundedCornerShape(12.dp))
                            .background(badgeBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            svc.rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                UsageRowContent(row, tick, showUsed)
            }

            if (svc.billingCycleEnd > 0L) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Text(
                    "Renews ${renewsText(svc.billingCycleEnd, tick)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UsageRowContent(row: UsageRow, tick: Long, showUsed: Boolean) {
    val pctLeft = (100 - row.pctUsed).coerceIn(0, 100)
    val isLow = pctLeft <= 15
    val barColor = if (isLow) BarRed else BarBlue
    val textColor = if (isLow) BarRed else MaterialTheme.colorScheme.onSurface
    val displayText = if (showUsed) "${row.pctUsed}% used" else "$pctLeft% left"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            displayText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }

    Spacer(Modifier.height(6.dp))

    val progress by animateFloatAsState(
        targetValue = row.pctUsed.coerceIn(0, 100) / 100f,
        label = "progress",
    )

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = barColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
    )

    if (row.resetMs > 0L) {
        Spacer(Modifier.height(4.dp))
        val resetText = TimeFmt.resetsIn(row.resetMs, tick)
        Text(
            "↻ Resets in $resetText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildServiceList(storage: Storage): List<ServiceDashData> {
    val now = System.currentTimeMillis()
    return listOf(
        buildClaudeData(storage, now),
        buildOtherServiceData(storage, "openai", "Codex", R.drawable.ic_openai_color),
        buildOtherServiceData(storage, "cursor", "Cursor", R.drawable.ic_cursor_color),
    )
}

private fun buildClaudeData(storage: Storage, now: Long): ServiceDashData {
    val rows = mutableListOf<UsageRow>()
    if (storage.hasSnapshot) {
        rows += UsageRow("5-hour session", storage.fiveHourUtil.roundToInt(), storage.fiveHourReset)
        rows += UsageRow("Weekly", storage.sevenDayUtil.roundToInt(), storage.sevenDayReset)
        if (storage.fableWeeklyUtil >= 0f) {
            rows += UsageRow("Weekly · Fable", storage.fableWeeklyUtil.roundToInt(), storage.fableWeeklyReset)
        }
    }
    return ServiceDashData(
        serviceId = "claude",
        label = "Claude Code",
        iconRes = R.drawable.ic_lobster_color,
        loggedIn = storage.isLoggedIn,
        rows = rows,
        fetchedAt = storage.fetchedAt,
        planTier = storage.claudeTier,
    )
}

private fun buildOtherServiceData(
    storage: Storage,
    serviceId: String,
    label: String,
    iconRes: Int,
): ServiceDashData {
    val loggedIn = when (serviceId) {
        "openai" -> storage.isOpenaiLoggedIn
        "cursor" -> storage.isCursorLoggedIn
        else -> false
    }
    val snap = storage.loadServiceSnapshot(serviceId)
    val rows = mutableListOf<UsageRow>()
    if (snap.hasData) {
        if (serviceId == "cursor") {
            if (snap.fiveHourUtil >= 0f) rows += UsageRow("Auto usage", snap.fiveHourUtil.roundToInt(), snap.fiveHourReset)
            if (snap.sevenDayUtil >= 0f) rows += UsageRow("API usage", snap.sevenDayUtil.roundToInt(), snap.sevenDayReset)
        } else if (serviceId == "openai") {
            val hasSecondary = snap.sevenDayUtil >= 0f
            if (snap.fiveHourUtil >= 0f) {
                val lbl = if (hasSecondary) "Session" else "Total usage"
                rows += UsageRow(lbl, snap.fiveHourUtil.roundToInt(), snap.fiveHourReset)
            }
            if (hasSecondary) rows += UsageRow("Weekly", snap.sevenDayUtil.roundToInt(), snap.sevenDayReset)
        } else {
            if (snap.fiveHourUtil >= 0f) rows += UsageRow("Usage", snap.fiveHourUtil.roundToInt(), snap.fiveHourReset)
            if (snap.sevenDayUtil >= 0f) rows += UsageRow("Weekly", snap.sevenDayUtil.roundToInt(), snap.sevenDayReset)
        }
    }
    return ServiceDashData(
        serviceId = serviceId,
        label = label,
        iconRes = iconRes,
        loggedIn = loggedIn,
        rows = rows,
        fetchedAt = snap.fetchedAt,
        planTier = if (snap.planTier == "prolite") "Pro Lite" else snap.planTier?.replaceFirstChar { it.uppercase() },
        billingCycleEnd = snap.billingCycleEnd,
    )
}

private fun renewsText(epochMs: Long, now: Long): String {
    if (epochMs <= 0L) return ""
    val s = (epochMs - now) / 1000
    if (s <= 0) return "now"
    val d = s / 86400
    if (d > 0) return "in ${d}d"
    val h = s / 3600
    if (h > 0) return "in ${h}h"
    return "in <1h"
}

private fun agoText(epochMs: Long, now: Long): String {
    if (epochMs <= 0L) return "never"
    val s = (now - epochMs) / 1000
    if (s < 60) return "just now"
    val m = s / 60
    if (m < 60) return "${m}m ago"
    val h = m / 60
    val rm = m % 60
    if (h < 24) return if (rm > 0) "${h}h ${rm}m ago" else "${h}h ago"
    val d = h / 24
    return "${d}d ago"
}
