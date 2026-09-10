package com.goosexgoose.llmit.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.goosexgoose.llmit.data.Storage
import com.goosexgoose.llmit.data.ThemeMode
import com.goosexgoose.llmit.data.WidgetWindowMode
import com.goosexgoose.llmit.widget.updateAllWidgets
import androidx.compose.material3.Switch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ServiceOption(val id: String, val label: String)

private val SERVICE_OPTIONS = listOf(
    ServiceOption("claude", "Claude"),
    ServiceOption("openai", "Codex"),
    ServiceOption("cursor", "Cursor"),
)

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val storage = Storage.get(this)
        val currentKey = storage.widgetBgColorKey(appWidgetId)
        val ctx = this

        val providerClass = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)?.provider?.className ?: ""
        val showWindowPicker = providerClass.endsWith("CombinedWidgetReceiver")
                || providerClass.endsWith("GaugeWidgetReceiver")
        val showServicePicker = providerClass.endsWith("DashboardWidgetReceiver")
                || providerClass.endsWith("ServiceWidgetReceiver")

        setContent {
            val dark = when (storage.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (dark) configDark else configLight) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    WidgetConfigScreen(
                        currentKey = currentKey,
                        showWindowPicker = showWindowPicker,
                        currentWindowMode = storage.widgetWindowMode(appWidgetId),
                        showServicePicker = showServicePicker,
                        currentServiceId = storage.widgetServiceId(appWidgetId),
                        currentOpacity = storage.widgetOpacityPercent(appWidgetId),
                        currentShowFable = storage.widgetShowFable(appWidgetId),
                    ) { key, windowMode, serviceId, opacity, showFable ->
                        storage.setWidgetBgColorKey(appWidgetId, key)
                        storage.setWidgetOpacityPercent(appWidgetId, opacity)
                        if (windowMode != null) storage.setWidgetWindowMode(appWidgetId, windowMode)
                        if (serviceId != null) storage.setWidgetServiceId(appWidgetId, serviceId)
                        if (showFable != null) storage.setWidgetShowFable(appWidgetId, showFable)
                        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(RESULT_OK, resultIntent)
                        CoroutineScope(Dispatchers.IO).launch { updateAllWidgets(ctx) }
                        finish()
                    }
                }
            }
        }
    }
}

internal val BG_COLOR_OPTIONS = listOf(
    BgColorOption("pure", "Pure", Color.White, Color.Black),
    BgColorOption("warm", "Warm", Color(0xFFFAF9F5), Color(0xFF1A2A38)),
    BgColorOption("claude", "Claude", Color(0xFFFEF0EA), Color(0xFF8B3A15)),
    BgColorOption("codex", "Codex", Color(0xFFEDF9F4), Color(0xFF1A3D2E)),
    BgColorOption("cursor", "Cursor", Color(0xFFF3EFFE), Color(0xFF3D2880)),
)

internal val BG_BRAND_ACCENTS = mapOf(
    "pure" to (Color(0xFF888888) to Color(0xFF888888)),
    "warm" to (Color(0xFFA09880) to Color(0xFF607088)),
    "claude" to (Color(0xFFD97757) to Color(0xFFD97757)),
    "codex" to (Color(0xFF10A37F) to Color(0xFF10A37F)),
    "cursor" to (Color(0xFF8B5CF6) to Color(0xFF8B5CF6)),
)

data class BgColorOption(val key: String, val label: String, val light: Color, val dark: Color)

@Composable
internal fun WidgetConfigScreen(
    currentKey: String,
    showWindowPicker: Boolean,
    currentWindowMode: WidgetWindowMode,
    showServicePicker: Boolean,
    currentServiceId: String,
    currentOpacity: Int,
    currentShowFable: Boolean = false,
    onConfirm: (colorKey: String, windowMode: WidgetWindowMode?, serviceId: String?, opacity: Int, showFable: Boolean?) -> Unit,
) {
    var selectedColor by remember { mutableStateOf(currentKey) }
    var selectedWindow by remember { mutableStateOf(currentWindowMode) }
    var selectedService by remember { mutableStateOf(currentServiceId) }
    var opacity by remember { mutableStateOf(currentOpacity.toFloat()) }
    var showFable by remember { mutableStateOf(currentShowFable) }
    val snappedOpacity = (opacity / 10f).let { kotlin.math.round(it) * 10 }.toInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (showServicePicker) {
            Text("Service", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SERVICE_OPTIONS.forEach { svc ->
                    ChipButton(
                        label = svc.label,
                        isSelected = selectedService == svc.id,
                        onClick = { selectedService = svc.id },
                    )
                }
            }
        }

        Text("Widget background", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BG_COLOR_OPTIONS.forEach { opt ->
                ColorSwatch(opt, selectedColor == opt.key) {
                    selectedColor = opt.key
                }
            }
        }

        Text("Opacity: $snappedOpacity%", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            valueRange = 0f..100f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )

        if (showWindowPicker) {
            Text("Time window", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                    WidgetWindowMode.WORST to "Both",
                    WidgetWindowMode.FIVE_HOUR to "5-Hour",
                    WidgetWindowMode.WEEKLY to "Weekly",
                ).forEach { (mode, label) ->
                    ChipButton(
                        label = label,
                        isSelected = selectedWindow == mode,
                        onClick = { selectedWindow = mode },
                    )
                }
            }
        }

        if (showServicePicker && selectedService == "claude") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Show Fable weekly", style = MaterialTheme.typography.titleMedium)
                Switch(checked = showFable, onCheckedChange = { showFable = it })
            }
        }

        Button(
            onClick = {
                onConfirm(
                    selectedColor,
                    if (showWindowPicker) selectedWindow else null,
                    if (showServicePicker) selectedService else null,
                    snappedOpacity,
                    if (showServicePicker && selectedService == "claude") showFable else null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Done") }
    }
}

@Composable
private fun ChipButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        label,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
               else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) accent else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isSelected) accent else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ColorSwatch(opt: BgColorOption, isSelected: Boolean, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val swatchColor = if (isDark) opt.dark else opt.light
    val accent = BG_BRAND_ACCENTS[opt.key]?.let { if (isDark) it.second else it.first } ?: Color.Gray
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .then(
                    if (isSelected) Modifier.border(3.dp, accent, CircleShape)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                .clickable(onClick = onClick),
        )
        Text(
            opt.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val configLight = lightColorScheme(
    background = Color(0xFFF8FAFC), surface = Color(0xFFFFFFFF), outline = Color(0xFFCBD5E1),
)
private val configDark = darkColorScheme(
    background = Color(0xFF0F172A), surface = Color(0xFF1E293B), outline = Color(0xFF475569),
)
