package com.goosexgoose.llmit.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Two-tier storage:
 *  - [secure]   EncryptedSharedPreferences for credentials (sessionKey, cf_clearance, UA, orgId).
 *  - [snapshot] Plain prefs for the non-secret usage snapshot the widget renders on every frame.
 */
class Storage private constructor(
    private val secure: SharedPreferences,
    private val snapshot: SharedPreferences,
) {
    // ---- credentials (encrypted) ----
    var sessionKey: String?
        get() = secure.getString("sessionKey", null)
        set(v) { secure.edit().putString("sessionKey", v).commit() }

    var cfClearance: String?
        get() = secure.getString("cf_clearance", null)
        set(v) = secure.edit().putString("cf_clearance", v).apply()

    var userAgent: String?
        get() = secure.getString("user_agent", null)
        set(v) = secure.edit().putString("user_agent", v).apply()

    var orgId: String?
        get() = secure.getString("org_id", null)
        set(v) = secure.edit().putString("org_id", v).apply()

    val isLoggedIn: Boolean get() = !sessionKey.isNullOrBlank()

    fun clearCredentials() {
        secure.edit().clear().apply()
    }

    fun clearClaudeCredentials() {
        secure.edit()
            .remove("sessionKey")
            .remove("cf_clearance")
            .remove("user_agent")
            .remove("org_id")
            .apply()
    }

    // ---- OpenAI credentials ----
    var openaiAccessToken: String?
        get() = secure.getString("openai_access_token", null)
        set(v) { secure.edit().putString("openai_access_token", v).commit() }

    var openaiRefreshToken: String?
        get() = secure.getString("openai_refresh_token", null)
        set(v) = secure.edit().putString("openai_refresh_token", v).apply()

    var openaiAccountId: String?
        get() = secure.getString("openai_account_id", null)
        set(v) = secure.edit().putString("openai_account_id", v).apply()

    val isOpenaiLoggedIn: Boolean get() = !openaiAccessToken.isNullOrBlank()

    fun clearOpenaiCredentials() {
        secure.edit()
            .remove("openai_access_token")
            .remove("openai_refresh_token")
            .remove("openai_account_id")
            .apply()
    }

    // ---- Cursor credentials ----
    var cursorAccessToken: String?
        get() = secure.getString("cursor_access_token", null)
        set(v) { secure.edit().putString("cursor_access_token", v).commit() }

    var cursorRefreshToken: String?
        get() = secure.getString("cursor_refresh_token", null)
        set(v) = secure.edit().putString("cursor_refresh_token", v).apply()

    var cursorUserId: String?
        get() = secure.getString("cursor_user_id", null)
        set(v) = secure.edit().putString("cursor_user_id", v).apply()

    val isCursorLoggedIn: Boolean get() = !cursorAccessToken.isNullOrBlank()

    fun clearCursorCredentials() {
        secure.edit()
            .remove("cursor_access_token")
            .remove("cursor_refresh_token")
            .remove("cursor_user_id")
            .apply()
    }

    // ---- usage snapshot (plain, read by the widget) ----
    var fiveHourUtil: Float
        get() = snapshot.getFloat("fh_util", -1f)
        set(v) = snapshot.edit().putFloat("fh_util", v).apply()

    var fiveHourReset: Long
        get() = snapshot.getLong("fh_reset", 0L)
        set(v) = snapshot.edit().putLong("fh_reset", v).apply()

    var sevenDayUtil: Float
        get() = snapshot.getFloat("wk_util", -1f)
        set(v) = snapshot.edit().putFloat("wk_util", v).apply()

    var sevenDayReset: Long
        get() = snapshot.getLong("wk_reset", 0L)
        set(v) = snapshot.edit().putLong("wk_reset", v).apply()

    /** -1 when the account has no Fable-scoped weekly cap. */
    var fableWeeklyUtil: Float
        get() = snapshot.getFloat("fable_wk_util", -1f)
        set(v) = snapshot.edit().putFloat("fable_wk_util", v).apply()

    var fableWeeklyReset: Long
        get() = snapshot.getLong("fable_wk_reset", 0L)
        set(v) = snapshot.edit().putLong("fable_wk_reset", v).apply()

    var fetchedAt: Long
        get() = snapshot.getLong("fetched_at", 0L)
        set(v) = snapshot.edit().putLong("fetched_at", v).apply()

    var claudeTier: String?
        get() = snapshot.getString("claude_tier", null)
        set(v) = snapshot.edit().putString("claude_tier", v).apply()

    /** Millis when the user tapped refresh; 0 when no refresh is in flight. */
    var refreshRequestedAt: Long
        get() = snapshot.getLong("refresh_requested_at", 0L)
        set(v) = snapshot.edit().putLong("refresh_requested_at", v).apply()

    /** AuthState ordinal; widget shows "Tap to sign in" when NEEDS_LOGIN. */
    var authState: AuthState
        get() = AuthState.entries.getOrElse(snapshot.getInt("auth_state", 0)) { AuthState.OK }
        set(v) = snapshot.edit().putInt("auth_state", v.ordinal).apply()

    /** User-controlled widget background opacity, 0 (fully transparent) to 100 (opaque). */
    var widgetBgOpacityPercent: Int
        get() = snapshot.getInt("widget_bg_opacity", 80)
        set(v) = snapshot.edit().putInt("widget_bg_opacity", v.coerceIn(0, 100)).apply()

    /** Global default bg color key: pure, warm, claude, codex, cursor. */
    var widgetBgColorKey: String
        get() = snapshot.getString("widget_bg_color", "pure") ?: "pure"
        set(v) = snapshot.edit().putString("widget_bg_color", v).apply()

    fun widgetBgColorKey(appWidgetId: Int): String =
        snapshot.getString("widget_bg_color_$appWidgetId", null) ?: widgetBgColorKey

    fun setWidgetBgColorKey(appWidgetId: Int, key: String) =
        snapshot.edit().putString("widget_bg_color_$appWidgetId", key).apply()

    fun widgetWindowMode(appWidgetId: Int): WidgetWindowMode {
        val ord = snapshot.getInt("widget_window_$appWidgetId", -1)
        return if (ord in WidgetWindowMode.entries.indices) WidgetWindowMode.entries[ord]
        else WidgetWindowMode.WORST
    }

    fun setWidgetWindowMode(appWidgetId: Int, mode: WidgetWindowMode) =
        snapshot.edit().putInt("widget_window_$appWidgetId", mode.ordinal).apply()

    fun widgetServiceId(appWidgetId: Int): String =
        snapshot.getString("widget_service_$appWidgetId", null) ?: "claude"

    fun setWidgetServiceId(appWidgetId: Int, serviceId: String) =
        snapshot.edit().putString("widget_service_$appWidgetId", serviceId).apply()

    fun widgetOpacityPercent(appWidgetId: Int): Int =
        snapshot.getInt("widget_opacity_$appWidgetId", -1).let {
            if (it < 0) widgetBgOpacityPercent else it
        }

    fun setWidgetOpacityPercent(appWidgetId: Int, pct: Int) =
        snapshot.edit().putInt("widget_opacity_$appWidgetId", pct.coerceIn(0, 100)).apply()

    fun widgetShowFable(appWidgetId: Int): Boolean =
        snapshot.getBoolean("widget_fable_$appWidgetId", showFableWeekly)

    fun setWidgetShowFable(appWidgetId: Int, v: Boolean) =
        snapshot.edit().putBoolean("widget_fable_$appWidgetId", v).apply()

    /** false = "resets in Xh Ym" countdown (default); true = absolute clock time / date. */
    var showAbsoluteResetTime: Boolean
        get() = snapshot.getBoolean("show_absolute_reset_time", false)
        set(v) = snapshot.edit().putBoolean("show_absolute_reset_time", v).apply()

    /** Light/Dark/System theme for the in-app dashboard. Default SYSTEM. */
    var themeMode: ThemeMode
        get() = ThemeMode.entries.getOrElse(snapshot.getInt("theme_mode", 0)) { ThemeMode.SYSTEM }
        set(v) = snapshot.edit().putInt("theme_mode", v.ordinal).apply()

    /** Which usage window the combined/gauge widgets show: worst of both, 5-hour only, or weekly only. */
    var widgetWindowMode: WidgetWindowMode
        get() = WidgetWindowMode.entries.getOrElse(snapshot.getInt("widget_window_mode", 0)) { WidgetWindowMode.WORST }
        set(v) = snapshot.edit().putInt("widget_window_mode", v.ordinal).apply()

    /** true = show "X% used", false = show "X% left" (default). Affects dashboard + widget. */
    var showUsed: Boolean
        get() = snapshot.getBoolean("show_used", false)
        set(v) = snapshot.edit().putBoolean("show_used", v).apply()

    /** Opt-in third row in the widget for the Fable weekly cap; off by default to stay compact. */
    var showFableWeekly: Boolean
        get() = snapshot.getBoolean("show_fable_weekly", false)
        set(v) = snapshot.edit().putBoolean("show_fable_weekly", v).apply()

    /** On/off for the periodic 15-minute background refresh. Default on. When off, the widget
     *  only updates on a manual tap and NO alerts fire (they piggyback on the background fetch). */
    var backgroundRefreshEnabled: Boolean
        get() = snapshot.getBoolean("background_refresh", true)
        set(v) = snapshot.edit().putBoolean("background_refresh", v).apply()

    /** In-app on/off for the near-limit (>=90%) notifications. Default on to preserve the prior
     *  behavior, where they fired whenever notification permission was granted. */
    var nearLimitAlertsEnabled: Boolean
        get() = snapshot.getBoolean("near_limit_alerts", true)
        set(v) = snapshot.edit().putBoolean("near_limit_alerts", v).apply()

    var nearLimitThreshold: Int
        get() = snapshot.getInt("near_limit_threshold", 90)
        set(v) = snapshot.edit().putInt("near_limit_threshold", v.coerceIn(50, 100)).apply()

    // When to fire a "limit reset" notification, controlled separately for the 5-hour and the
    // weekly windows (weekly covers both the 7-day and the Fable weekly cap). Both default to
    // WHEN_CAPPED, still gated by the OS notification permission so nothing fires until enabled.
    var reset5hMode: ResetNotify
        get() = readReset("reset_5h_mode")
        set(v) = snapshot.edit().putInt("reset_5h_mode", v.ordinal).apply()

    var resetWeeklyMode: ResetNotify
        get() = readReset("reset_weekly_mode")
        set(v) = snapshot.edit().putInt("reset_weekly_mode", v.ordinal).apply()

    /** Notify when a weekly limit refills before its scheduled reset. Default on. */
    var unexpectedResetsEnabled: Boolean
        get() = snapshot.getBoolean("unexpected_resets", true)
        set(v) = snapshot.edit().putBoolean("unexpected_resets", v).apply()

    /** Predictive weekly depletion warnings. Off by default (Pro feature in Limits). */
    var weeklyWarningsEnabled: Boolean
        get() = snapshot.getBoolean("weekly_warnings", false)
        set(v) = snapshot.edit().putBoolean("weekly_warnings", v).apply()

    /** How many days before reset to warn if weekly is projected to run out. Default 2. */
    var weeklyWarningTimingDays: Int
        get() = snapshot.getInt("weekly_warning_days", 2)
        set(v) = snapshot.edit().putInt("weekly_warning_days", v.coerceIn(1, 5)).apply()

    /** Warn only if remaining is at or below this %. Default 20. */
    var weeklyWarningRemaining: Int
        get() = snapshot.getInt("weekly_warning_remaining", 20)
        set(v) = snapshot.edit().putInt("weekly_warning_remaining", v.coerceIn(5, 50)).apply()

    /** For reset notifications: only fire if usage was at or above this % when it reset.
     *  100 = our old WHEN_CAPPED behavior. 0 = fire always. Default 100. */
    var resetThresholdPct: Int
        get() = snapshot.getInt("reset_threshold_pct", 100)
        set(v) = snapshot.edit().putInt("reset_threshold_pct", v.coerceIn(0, 100)).apply()

    private fun readReset(key: String): ResetNotify =
        ResetNotify.entries.getOrElse(snapshot.getInt(key, ResetNotify.WHEN_CAPPED.ordinal)) {
            ResetNotify.WHEN_CAPPED
        }

    // ---- reset detection -------------------------------------------------------------------
    // A window "resets" when its resets_at jumps forward by roughly the window length (>= 5h);
    // sub-second drift between polls is far below that, so a large forward jump is an
    // unambiguous reset signal. We track the last-seen resets_at and the utilization observed
    // just before it (utilization only climbs within a window, so the last pre-reset reading is
    // effectively that window's peak — used by the WHEN_CAPPED mode). These keys are separate
    // from saveSnapshot's, so they still hold the previous cycle's values when detection runs.

    /** Last resets_at we recorded for [key]; 0 if never seen. */
    fun resetTrackSeen(key: String): Long = snapshot.getLong("reset_seen_$key", 0L)

    /** Utilization recorded alongside [resetTrackSeen]; the pre-reset peak of that window. */
    fun resetTrackPrevPct(key: String): Int = snapshot.getInt("reset_prevpct_$key", 0)

    fun updateResetTrack(key: String, resetsAtEpochMs: Long, pct: Int) {
        snapshot.edit()
            .putLong("reset_seen_$key", resetsAtEpochMs)
            .putInt("reset_prevpct_$key", pct)
            .apply()
    }

    // ---- widget-tap diagnostics ----
    // The in-app "Test fetch now" button is instant while a widget tap is slow, so the
    // difference between those two paths is the bug. These record what actually happens
    // inside RefreshAction so the app can display it, instead of us inferring from symptoms.

    /** Wall clock when RefreshAction.onAction was last entered; 0 = never tapped. */
    var tapStartedAt: Long
        get() = snapshot.getLong("tap_started_at", 0L)
        set(v) = snapshot.edit().putLong("tap_started_at", v).apply()

    /** Wall clock when onAction last ran to completion; 0 while one is in flight (or was killed). */
    var tapFinishedAt: Long
        get() = snapshot.getLong("tap_finished_at", 0L)
        set(v) = snapshot.edit().putLong("tap_finished_at", v).apply()

    /** Per-stage timing breakdown of the last widget tap. */
    var tapDiag: String?
        get() = snapshot.getString("tap_diag", null)
        set(v) = snapshot.edit().putString("tap_diag", v).apply()

    /**
     * Near-limit-notification dedup: has [key] (e.g. "5h", "7d", "fable") already been notified
     * for the window that resets at [resetEpochMs]? Keyed by reset time (not a simple boolean)
     * so a genuinely new cycle can notify again without a separate "seen" flag to clear on reset.
     *
     * Uses a tolerance rather than exact equality: the API can reissue a window's resets_at
     * with sub-second drift between polls (observed directly on /usage — microsecond-precision
     * timestamps that aren't perfectly stable call to call), especially once a window is pinned
     * at its cap and the rate limiter keeps re-evaluating it. An exact-match check treated each
     * such drift as a "new" window and re-fired the notification on every single refresh.
     */
    fun wasNotifiedFor(key: String, resetEpochMs: Long): Boolean {
        val last = snapshot.getLong("notified_$key", 0L)
        if (last == 0L) return false
        return kotlin.math.abs(resetEpochMs - last) < NOTIFY_DEDUP_TOLERANCE_MS
    }

    fun markNotified(key: String, resetEpochMs: Long) {
        snapshot.edit().putLong("notified_$key", resetEpochMs).apply()
    }

    // The widget composition observes these prefs so it recomposes on every write. Required
    // because Glance's update()/updateAll() do NOT restart provideGlance() while a session is
    // already running — without an observable source the widget would keep rendering the
    // snapshot it was first composed with.
    fun registerSnapshotListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        snapshot.registerOnSharedPreferenceChangeListener(l)
    }

    fun unregisterSnapshotListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        snapshot.unregisterOnSharedPreferenceChangeListener(l)
    }

    fun nudge() { snapshot.edit().putLong("_nudge", System.currentTimeMillis()).apply() }

    val hasSnapshot: Boolean get() = fiveHourUtil >= 0f

    fun saveSnapshot(s: UsageSnapshot) {
        snapshot.edit()
            .putFloat("fh_util", s.fiveHour.utilization)
            .putLong("fh_reset", s.fiveHour.resetsAtEpochMs)
            .putFloat("wk_util", s.sevenDay.utilization)
            .putLong("wk_reset", s.sevenDay.resetsAtEpochMs)
            .putFloat("fable_wk_util", s.fableWeekly?.utilization ?: -1f)
            .putLong("fable_wk_reset", s.fableWeekly?.resetsAtEpochMs ?: 0L)
            .putLong("fetched_at", s.fetchedAtEpochMs)
            .apply()
    }

    fun saveServiceSnapshot(serviceId: String, s: UsageSnapshot) {
        snapshot.edit()
            .putFloat("${serviceId}_fh_util", s.fiveHour.utilization)
            .putLong("${serviceId}_fh_reset", s.fiveHour.resetsAtEpochMs)
            .putFloat("${serviceId}_wk_util", s.sevenDay.utilization)
            .putLong("${serviceId}_wk_reset", s.sevenDay.resetsAtEpochMs)
            .putFloat("${serviceId}_fable_wk_util", s.fableWeekly?.utilization ?: -1f)
            .putLong("${serviceId}_fable_wk_reset", s.fableWeekly?.resetsAtEpochMs ?: 0L)
            .putLong("${serviceId}_fetched_at", s.fetchedAtEpochMs)
            .putString("${serviceId}_tier", s.planTier)
            .putLong("${serviceId}_billing_cycle_end", s.billingCycleEndMs)
            .apply()
    }

    fun loadServiceSnapshot(serviceId: String): ServiceSnapshotData {
        return ServiceSnapshotData(
            fiveHourUtil = snapshot.getFloat("${serviceId}_fh_util", -1f),
            fiveHourReset = snapshot.getLong("${serviceId}_fh_reset", 0L),
            sevenDayUtil = snapshot.getFloat("${serviceId}_wk_util", -1f),
            sevenDayReset = snapshot.getLong("${serviceId}_wk_reset", 0L),
            fableWeeklyUtil = snapshot.getFloat("${serviceId}_fable_wk_util", -1f),
            fableWeeklyReset = snapshot.getLong("${serviceId}_fable_wk_reset", 0L),
            fetchedAt = snapshot.getLong("${serviceId}_fetched_at", 0L),
            planTier = snapshot.getString("${serviceId}_tier", null),
            billingCycleEnd = snapshot.getLong("${serviceId}_billing_cycle_end", 0L),
        )
    }

    data class ServiceSnapshotData(
        val fiveHourUtil: Float,
        val fiveHourReset: Long,
        val sevenDayUtil: Float,
        val sevenDayReset: Long,
        val fableWeeklyUtil: Float,
        val fableWeeklyReset: Long,
        val fetchedAt: Long,
        val planTier: String? = null,
        val billingCycleEnd: Long = 0L,
    ) {
        val hasData: Boolean get() = fiveHourUtil >= 0f || sevenDayUtil >= 0f
    }

    fun serviceAuthState(serviceId: String): AuthState =
        AuthState.entries.getOrElse(snapshot.getInt("${serviceId}_auth_state", 0)) { AuthState.OK }

    fun setServiceAuthState(serviceId: String, state: AuthState) {
        snapshot.edit().putInt("${serviceId}_auth_state", state.ordinal).apply()
    }

    companion object {
        // Comfortably below the shortest tracked window (5h) so a genuinely new cycle still
        // notifies promptly, but well above any plausible resets_at jitter between polls.
        private const val NOTIFY_DEDUP_TOLERANCE_MS = 60 * 60 * 1000L

        @Volatile private var instance: Storage? = null

        fun get(context: Context): Storage = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(app: Context): Storage {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val secure = EncryptedSharedPreferences.create(
                app,
                "claude_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val snapshot = app.getSharedPreferences("claude_usage", Context.MODE_PRIVATE)
            return Storage(secure, snapshot)
        }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class WidgetWindowMode { WORST, FIVE_HOUR, WEEKLY }

enum class AuthState { OK, NEEDS_LOGIN }

/** When to notify that a given usage window has reset. Set independently per window. */
enum class ResetNotify {
    OFF,          // never
    WHEN_CAPPED,  // only if the window had hit 100% (was actually maxed out) before resetting
    ALWAYS,       // every reset of that window
}
