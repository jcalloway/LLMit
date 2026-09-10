package com.goosexgoose.llmit.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.goosexgoose.llmit.R
import com.goosexgoose.llmit.auth.LoginActivity
import com.goosexgoose.llmit.data.CursorProvider
import com.goosexgoose.llmit.data.FetchResult
import com.goosexgoose.llmit.data.OpenAiProvider
import com.goosexgoose.llmit.data.ProviderRegistry
import com.goosexgoose.llmit.data.ResetNotify
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.data.ThemeMode
import com.goosexgoose.llmit.data.UsageRepository
import com.goosexgoose.llmit.widget.updateAllWidgets
import com.goosexgoose.llmit.work.RefreshScheduler
import com.goosexgoose.llmit.work.UsageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val widgetUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

private fun pushWidgetUpdate(context: Context) {
    widgetUpdateScope.launch { updateAllWidgets(context.applicationContext) }
}

private const val BUILD_TAG = "llmit-3"

private val ClaudeBrand = Color(0xFFD97757)
private val CodexBrand = Color(0xFF10A37F)
private val CursorBrand = Color(0xFF8B5CF6)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color.White,
    secondary = Color(0xFF64748B),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    secondary = Color(0xFF94A3B8),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProviderRegistry.init()
        setContent {
            val storage = remember { Storage.get(this) }
            var themeMode by remember { mutableStateOf(storage.themeMode) }
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val colors = if (dark) DarkColors else LightColors
            MaterialTheme(colorScheme = colors) {
                var screen by remember { mutableStateOf("dashboard") }
                when (screen) {
                    "dashboard" -> DashboardScreen(
                        onSettingsClick = { screen = "settings" },
                        onRefreshed = {},
                    )
                    "settings" -> SettingsScreen(
                        onBack = { screen = "dashboard" },
                        onThemeChanged = { themeMode = it },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit, onThemeChanged: (ThemeMode) -> Unit = {}) {
    val context = LocalContext.current
    val storage = remember { Storage.get(context) }
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableStateOf(0) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val loginLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    val openaiLoginLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshKey++
        if (it.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                OpenAiProvider.refresh(context)
                updateAllWidgets(context)
            }
        }
    }

    val cursorLoginLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshKey++
        if (it.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                CursorProvider.refresh(context)
                updateAllWidgets(context)
            }
        }
    }

    var notificationsGranted by remember { mutableStateOf(notificationsEnabled(context)) }
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    var batteryOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }

    DisposableEffect(Unit) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = notificationsEnabled(context)
                batteryOptimized = isBatteryOptimized(context)
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // -- Accounts --
            SectionLabel("Accounts")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    val claudeLoggedIn = remember(refreshKey) { storage.isLoggedIn }
                    AccountRow(
                        iconRes = R.drawable.ic_lobster_color,
                        name = "Claude",
                        connected = claudeLoggedIn,
                        brandColor = ClaudeBrand,
                        onSignIn = { loginLauncher.launch(Intent(context, LoginActivity::class.java)) },
                        onSignOut = {
                            storage.clearClaudeCredentials()
                            refreshKey++
                            pushWidgetUpdate(context)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    val openaiLoggedIn = remember(refreshKey) { storage.isOpenaiLoggedIn }
                    AccountRow(
                        iconRes = R.drawable.ic_openai_color,
                        name = "Codex",
                        connected = openaiLoggedIn,
                        brandColor = CodexBrand,
                        onSignIn = { openaiLoginLauncher.launch(Intent(context, OpenAiLoginActivity::class.java)) },
                        onSignOut = {
                            OpenAiProvider.logout(context)
                            refreshKey++
                            pushWidgetUpdate(context)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    val cursorLoggedIn = remember(refreshKey) { storage.isCursorLoggedIn }
                    AccountRow(
                        iconRes = R.drawable.ic_cursor_color,
                        name = "Cursor",
                        connected = cursorLoggedIn,
                        brandColor = CursorBrand,
                        onSignIn = { cursorLoginLauncher.launch(Intent(context, CursorLoginActivity::class.java)) },
                        onSignOut = {
                            CursorProvider.logout(context)
                            refreshKey++
                            pushWidgetUpdate(context)
                        },
                    )
                }
            }

            // -- Appearance --
            SectionLabel("Appearance")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    var theme by remember { mutableStateOf(storage.themeMode) }
                    var showThemeDialog by remember { mutableStateOf(false) }
                    SettingRow(icon = Icons.Filled.DarkMode, label = "Theme") {
                        OutlinedButton(onClick = { showThemeDialog = true }) {
                            Text(when (theme) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            })
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                                modifier = Modifier.padding(start = 4.dp).size(18.dp))
                        }
                    }
                    if (showThemeDialog) {
                        PickerDialog(
                            title = "Theme",
                            options = ThemeMode.entries.toList(),
                            labels = listOf("System", "Light", "Dark"),
                            current = theme,
                            onPick = {
                                theme = it
                                storage.themeMode = it
                                onThemeChanged(it)
                                showThemeDialog = false
                            },
                            onDismiss = { showThemeDialog = false },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var bgOpacity by remember { mutableStateOf(storage.widgetBgOpacityPercent.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Opacity, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            "Background opacity: ${bgOpacity.roundToInt()}%",
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Slider(
                        value = bgOpacity,
                        onValueChange = { bgOpacity = it },
                        valueRange = 0f..100f,
                        onValueChangeFinished = {
                            storage.widgetBgOpacityPercent = bgOpacity.roundToInt()
                            pushWidgetUpdate(context)
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var bgColorKey by remember { mutableStateOf(storage.widgetBgColorKey) }
                    Text(
                        "Background color (all widgets)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        BG_COLOR_OPTIONS.forEach { opt ->
                            val swatchColor = if (isDark) opt.dark else opt.light
                            val accent = BG_BRAND_ACCENTS[opt.key]?.let {
                                if (isDark) it.second else it.first
                            } ?: Color.Gray
                            val selected = bgColorKey == opt.key
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .then(
                                            if (selected) Modifier.border(3.dp, accent, CircleShape)
                                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        )
                                        .clickable {
                                            bgColorKey = opt.key
                                            storage.widgetBgColorKey = opt.key
                                            pushWidgetUpdate(context)
                                        },
                                )
                                Text(
                                    opt.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var showUsed by remember { mutableStateOf(storage.showUsed) }
                    SettingRow(icon = Icons.Filled.DataUsage, label = if (showUsed) "Show % used" else "Show % left") {
                        Switch(
                            checked = showUsed,
                            onCheckedChange = {
                                showUsed = it
                                storage.showUsed = it
                                pushWidgetUpdate(context)
                            },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var showAbsolute by remember { mutableStateOf(storage.showAbsoluteResetTime) }
                    SettingRow(icon = Icons.Filled.Schedule, label = "Absolute reset time") {
                        Switch(
                            checked = showAbsolute,
                            onCheckedChange = {
                                showAbsolute = it
                                storage.showAbsoluteResetTime = it
                                pushWidgetUpdate(context)
                            },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var showFable by remember { mutableStateOf(storage.showFableWeekly) }
                    SettingRow(icon = Icons.Filled.AutoStories, label = "Fable weekly row") {
                        Switch(
                            checked = showFable,
                            onCheckedChange = {
                                showFable = it
                                storage.showFableWeekly = it
                                pushWidgetUpdate(context)
                            },
                        )
                    }
                }
            }

            // -- Notifications --
            SectionLabel("Notifications")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    var bgRefresh by remember { mutableStateOf(storage.backgroundRefreshEnabled) }
                    SettingRow(icon = Icons.Filled.Sync, label = "Background refresh") {
                        Switch(
                            checked = bgRefresh,
                            onCheckedChange = {
                                bgRefresh = it
                                storage.backgroundRefreshEnabled = it
                                RefreshScheduler.ensurePeriodic(context)
                                RefreshScheduler.scheduleResetCheck(context)
                            },
                        )
                    }
                    if (bgRefresh && batteryOptimized) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Battery optimization may delay refreshes",
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { requestBatteryExemption(context) }) { Text("Fix") }
                        }
                    }

                    if (!notificationsGranted) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingRow(icon = Icons.Filled.NotificationsActive, label = "Notifications") {
                            OutlinedButton(onClick = {
                                UsageNotifier.ensureChannel(context)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notificationsGranted = true
                                }
                            }) { Text("Enable") }
                        }
                    }

                    val alertsActive = notificationsGranted && bgRefresh

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var nearLimit by remember { mutableStateOf(storage.nearLimitAlertsEnabled) }
                    SettingRow(icon = Icons.Filled.Campaign, label = "Near-limit alerts") {
                        Switch(
                            checked = nearLimit,
                            enabled = alertsActive,
                            onCheckedChange = {
                                nearLimit = it
                                storage.nearLimitAlertsEnabled = it
                            },
                        )
                    }

                    if (nearLimit && alertsActive) {
                        var threshold by remember { mutableStateOf(storage.nearLimitThreshold.toFloat()) }
                        Row(
                            modifier = Modifier.padding(start = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Warn at ${threshold.roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = threshold,
                            onValueChange = { threshold = it },
                            valueRange = 50f..100f,
                            steps = 9,
                            modifier = Modifier.padding(start = 32.dp),
                            onValueChangeFinished = {
                                storage.nearLimitThreshold = threshold.roundToInt()
                            },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var reset5h by remember { mutableStateOf(storage.reset5hMode) }
                    var resetWeekly by remember { mutableStateOf(storage.resetWeeklyMode) }
                    var resetDialogFor by remember { mutableStateOf<String?>(null) }

                    SettingRow(icon = Icons.Filled.Timer, label = "Session reset alerts") {
                        OutlinedButton(enabled = alertsActive, onClick = { resetDialogFor = "5h" }) {
                            Text(reset5h.shortLabel())
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                                modifier = Modifier.padding(start = 4.dp).size(18.dp))
                        }
                    }
                    SettingRow(icon = Icons.Filled.DateRange, label = "Weekly reset alerts") {
                        OutlinedButton(enabled = alertsActive, onClick = { resetDialogFor = "weekly" }) {
                            Text(resetWeekly.shortLabel())
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                                modifier = Modifier.padding(start = 4.dp).size(18.dp))
                        }
                    }
                    when (resetDialogFor) {
                        "5h" -> ResetModeDialog(
                            title = "Session reset alerts",
                            capExplainer = "\"When capped\" fires only if you hit 100% of the session limit before it reset.",
                            current = reset5h,
                            onPick = {
                                reset5h = it
                                storage.reset5hMode = it
                                if (it != ResetNotify.OFF) UsageNotifier.ensureChannel(context)
                                RefreshScheduler.scheduleResetCheck(context)
                                resetDialogFor = null
                            },
                            onDismiss = { resetDialogFor = null },
                        )
                        "weekly" -> ResetModeDialog(
                            title = "Weekly reset alerts",
                            capExplainer = "Fires for all weekly limits across services. \"When capped\" fires only if you hit 100% before the reset.",
                            current = resetWeekly,
                            onPick = {
                                resetWeekly = it
                                storage.resetWeeklyMode = it
                                if (it != ResetNotify.OFF) UsageNotifier.ensureChannel(context)
                                RefreshScheduler.scheduleResetCheck(context)
                                resetDialogFor = null
                            },
                            onDismiss = { resetDialogFor = null },
                        )
                    }

                    if (reset5h != ResetNotify.OFF || resetWeekly != ResetNotify.OFF) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        var resetThreshold by remember { mutableStateOf(storage.resetThresholdPct) }
                        var showResetThresholdDialog by remember { mutableStateOf(false) }
                        SettingRow(icon = Icons.Filled.Campaign, label = "Notify after reset") {
                            OutlinedButton(enabled = alertsActive, onClick = { showResetThresholdDialog = true }) {
                                Text(if (resetThreshold >= 100) "When capped" else "If under ${100 - resetThreshold}% left")
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                                    modifier = Modifier.padding(start = 4.dp).size(18.dp))
                            }
                        }
                        Text(
                            "Only notify about resets when usage was high enough to matter.",
                            modifier = Modifier.padding(start = 32.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (showResetThresholdDialog) {
                            PickerDialog(
                                title = "Notify after reset",
                                options = listOf(100, 95, 90, 80),
                                labels = listOf("When capped (100%)", "If under 5% left", "If under 10% left", "If under 20% left"),
                                current = resetThreshold,
                                onPick = {
                                    resetThreshold = it
                                    storage.resetThresholdPct = it
                                    showResetThresholdDialog = false
                                },
                                onDismiss = { showResetThresholdDialog = false },
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var unexpectedResets by remember { mutableStateOf(storage.unexpectedResetsEnabled) }
                    SettingRow(icon = Icons.Filled.NewReleases, label = "Unexpected weekly resets") {
                        Switch(
                            checked = unexpectedResets,
                            enabled = alertsActive,
                            onCheckedChange = {
                                unexpectedResets = it
                                storage.unexpectedResetsEnabled = it
                                if (it) UsageNotifier.ensureChannel(context)
                            },
                        )
                    }
                    Text(
                        "Notify when a weekly limit refills before its scheduled reset.",
                        modifier = Modifier.padding(start = 32.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    var weeklyWarnings by remember { mutableStateOf(storage.weeklyWarningsEnabled) }
                    SettingRow(icon = Icons.Filled.TrendingDown, label = "Weekly depletion warnings") {
                        Switch(
                            checked = weeklyWarnings,
                            enabled = alertsActive,
                            onCheckedChange = {
                                weeklyWarnings = it
                                storage.weeklyWarningsEnabled = it
                                if (it) UsageNotifier.ensureChannel(context)
                            },
                        )
                    }
                    Text(
                        "Warn when a weekly limit is projected to run out before reset.",
                        modifier = Modifier.padding(start = 32.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (weeklyWarnings && alertsActive) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        var timingDays by remember { mutableStateOf(storage.weeklyWarningTimingDays) }
                        var showTimingDialog by remember { mutableStateOf(false) }
                        SettingRow(icon = Icons.Filled.Schedule, label = "Warning timing") {
                            OutlinedButton(onClick = { showTimingDialog = true }) {
                                Text("${timingDays}d before")
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                                    modifier = Modifier.padding(start = 4.dp).size(18.dp))
                            }
                        }
                        if (showTimingDialog) {
                            PickerDialog(
                                title = "Warning timing",
                                options = listOf(1, 2, 3, 5),
                                labels = listOf("1 day before", "2 days before", "3 days before", "5 days before"),
                                current = timingDays,
                                onPick = {
                                    timingDays = it
                                    storage.weeklyWarningTimingDays = it
                                    showTimingDialog = false
                                },
                                onDismiss = { showTimingDialog = false },
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        var remaining by remember { mutableStateOf(storage.weeklyWarningRemaining) }
                        var showRemainingDialog by remember { mutableStateOf(false) }
                        SettingRow(icon = Icons.Filled.Campaign, label = "Weekly remaining") {
                            OutlinedButton(onClick = { showRemainingDialog = true }) {
                                Text("${remaining}% left")
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                                    modifier = Modifier.padding(start = 4.dp).size(18.dp))
                            }
                        }
                        if (showRemainingDialog) {
                            PickerDialog(
                                title = "Warn when remaining at",
                                options = listOf(5, 10, 15, 20, 30, 50),
                                labels = listOf("5% left", "10% left", "15% left", "20% left", "30% left", "50% left"),
                                current = remaining,
                                onPick = {
                                    remaining = it
                                    storage.weeklyWarningRemaining = it
                                    showRemainingDialog = false
                                },
                                onDismiss = { showRemainingDialog = false },
                            )
                        }
                    }
                }
            }

            // -- Footer --
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    BUILD_TAG,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showDiagnostics = true }) {
                    Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Diagnostics", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            storage = storage,
            onDismiss = { showDiagnostics = false },
        )
    }
}

private fun ResetNotify.shortLabel(): String = when (this) {
    ResetNotify.OFF -> "Off"
    ResetNotify.WHEN_CAPPED -> "When capped"
    ResetNotify.ALWAYS -> "Every reset"
}

private fun ResetNotify.description(): String = when (this) {
    ResetNotify.OFF -> "Never notify on reset"
    ResetNotify.WHEN_CAPPED -> "Only if you'd hit 100% before it reset"
    ResetNotify.ALWAYS -> "Every time this limit resets"
}

@Composable
private fun ResetModeDialog(
    title: String,
    capExplainer: String,
    current: ResetNotify,
    onPick: (ResetNotify) -> Unit,
    onDismiss: () -> Unit,
) {
    val order = listOf(ResetNotify.WHEN_CAPPED, ResetNotify.ALWAYS, ResetNotify.OFF)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            Column {
                order.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onPick(mode) })
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(mode.shortLabel(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                mode.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    capExplainer,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<T>,
    labels: List<String>,
    current: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(opt) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = opt == current, onClick = { onPick(opt) })
                        Text(labels[i], modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}

@Composable
private fun AccountRow(
    iconRes: Int,
    name: String,
    connected: Boolean,
    brandColor: Color,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            name,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (connected) {
            Text(
                "Connected",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF22C55E),
                modifier = Modifier.padding(end = 12.dp),
            )
            TextButton(onClick = onSignOut) { Text("Sign out") }
        } else {
            Button(
                onClick = onSignIn,
                colors = ButtonDefaults.buttonColors(containerColor = brandColor),
            ) { Text("Sign in") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
    )
}

@Composable
private fun DiagnosticsDialog(
    storage: Storage,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debug by remember { mutableStateOf("") }

    var tapDiag by remember { mutableStateOf(describeTap(storage)) }
    LaunchedEffect(Unit) {
        while (true) {
            tapDiag = describeTap(storage)
            delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Diagnostics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            debug = "Fetching..."
                            val t0 = System.currentTimeMillis()
                            val r = UsageRepository(context).refresh()
                            val took = String.format(
                                java.util.Locale.US,
                                "%.1fs",
                                (System.currentTimeMillis() - t0) / 1000.0,
                            )
                            debug = when (r) {
                                is FetchResult.Success -> {
                                    updateAllWidgets(context)
                                    "OK in $took"
                                }
                                is FetchResult.NeedsLogin -> "NeedsLogin in $took"
                                is FetchResult.Soft -> "Soft(${r.reason}) in $took"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Test fetch now", modifier = Modifier.padding(start = 8.dp))
                }

                if (debug.isNotBlank()) {
                    Text(debug, style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(tapDiag, style = MaterialTheme.typography.bodySmall)
                Text("build: $BUILD_TAG", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}


@Composable
private fun SettingRow(icon: ImageVector, label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        trailing()
    }
}


private fun describeTap(s: Storage): String {
    val started = s.tapStartedAt
    if (started == 0L) return "Last widget tap: none recorded yet"
    val clock = java.text.SimpleDateFormat("h:mm:ss a", java.util.Locale.US)
        .format(java.util.Date(started))
    val finished = s.tapFinishedAt
    if (finished == 0L) {
        val secs = (System.currentTimeMillis() - started) / 1000
        return "Last widget tap: $clock — STILL RUNNING (${secs}s)"
    }
    return "Last widget tap: $clock\n${s.tapDiag ?: "?"}"
}

private fun notificationsEnabled(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun isBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestBatteryExemption(context: Context) {
    if (isBatteryOptimized(context)) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
