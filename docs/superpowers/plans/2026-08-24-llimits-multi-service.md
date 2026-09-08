# LLiMits Multi-Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand "Claude Usage" into "LLiMits", adding OpenAI/Codex and Cursor usage tracking with per-service and combined home-screen widgets.

**Architecture:** Hybrid approach. Extract the existing Claude fetch logic into a `ClaudeProvider` behind a minimal `UsageProvider` interface. Add `OpenAiProvider` (OAuth PKCE) and `CursorProvider` (device-code auth) as standalone classes. Shared infrastructure (widgets, scheduling, notifications) iterates all authenticated providers.

**Tech Stack:** Kotlin, Jetpack Glance 1.1.1, WorkManager 2.10.0, OkHttp 4.12.0, EncryptedSharedPreferences, AlarmManager exact alarms, Jetpack Compose (settings UI).

**Spec:** `docs/superpowers/specs/2026-08-24-llimits-multi-service-design.md`

---

## File Plan

### New files
- `data/UsageProvider.kt` — interface + sealed AuthResult
- `data/ClaudeProvider.kt` — extracted from UsageRepository
- `data/OpenAiProvider.kt` — OAuth PKCE auth + usage fetch
- `data/CursorProvider.kt` — device-code auth + usage fetch
- `data/ProviderRegistry.kt` — singleton list of all providers
- `widget/ClaudeWidget.kt` — per-service widget (rename of UsageWidget)
- `widget/ClaudeWidgetReceiver.kt` — rename of UsageWidgetReceiver
- `widget/OpenAiWidget.kt` + `widget/OpenAiWidgetReceiver.kt`
- `widget/CursorWidget.kt` + `widget/CursorWidgetReceiver.kt`
- `widget/CombinedWidget.kt` + `widget/CombinedWidgetReceiver.kt`
- `widget/CombinedGlanceUi.kt` — stacked summary composable
- `ui/OpenAiLoginActivity.kt` — WebView for OAuth PKCE
- `ui/CursorLoginActivity.kt` — WebView for device-code polling
- `res/xml/claude_widget_info.xml` — copy of usage_widget_info.xml
- `res/xml/openai_widget_info.xml`
- `res/xml/cursor_widget_info.xml`
- `res/xml/combined_widget_info.xml`
- `res/drawable/ic_openai.xml` — OpenAI logo vector
- `res/drawable/ic_cursor.xml` — Cursor logo vector

### Modified files
- `data/Storage.kt` — prefixed credential accessors per service, per-service snapshot save/load
- `data/Const.kt` — add OpenAI + Cursor URLs, rename constants
- `data/UsageModels.kt` — add factory methods for OpenAI/Cursor response shapes
- `work/RefreshWorker.kt` — iterate all providers
- `work/ResetAlarmReceiver.kt` — iterate all providers
- `work/RefreshScheduler.kt` — earliest reset across all services
- `work/UsageNotifier.kt` — per-service notification channels + IDs
- `widget/Actions.kt` — parameterize RefreshAction by serviceId
- `widget/UsageGlanceUi.kt` — no changes (WidgetState is already generic)
- `ui/MainActivity.kt` — service tabs, app title rename, per-service login/settings
- `AndroidManifest.xml` — register new widgets, activities
- `res/values/strings.xml` — app name, per-service strings
- `res/values/colors.xml` + `values-night/colors.xml` — no changes needed

---

### Task 1: UsageProvider interface + AuthResult

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/data/UsageProvider.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package com.usage.claudewidget.data

import android.content.Context

sealed interface AuthResult {
    data object Success : AuthResult
    data class NeedsWebLogin(val url: String) : AuthResult
    data class Failed(val reason: String) : AuthResult
}

interface UsageProvider {
    val serviceId: String
    val displayName: String
    fun isAuthenticated(context: Context): Boolean
    suspend fun refresh(context: Context): FetchResult
    fun logout(context: Context)
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/data/UsageProvider.kt
git commit -m "feat: add UsageProvider interface and AuthResult"
```

---

### Task 2: ProviderRegistry

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/data/ProviderRegistry.kt`

- [ ] **Step 1: Create the registry**

```kotlin
package com.usage.claudewidget.data

import android.content.Context

object ProviderRegistry {
    private val providers = mutableListOf<UsageProvider>()

    fun register(provider: UsageProvider) {
        if (providers.none { it.serviceId == provider.serviceId }) {
            providers.add(provider)
        }
    }

    fun all(): List<UsageProvider> = providers.toList()

    fun authenticated(context: Context): List<UsageProvider> =
        providers.filter { it.isAuthenticated(context) }

    fun get(serviceId: String): UsageProvider? =
        providers.find { it.serviceId == serviceId }

    fun init() {
        register(ClaudeProvider)
        register(OpenAiProvider)
        register(CursorProvider)
    }
}
```

Note: `ClaudeProvider`, `OpenAiProvider`, `CursorProvider` don't exist yet. This file will compile once Tasks 3-5 are done. Keep it as a reference; it will be filled in as providers are created.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/data/ProviderRegistry.kt
git commit -m "feat: add ProviderRegistry singleton"
```

---

### Task 3: Storage — prefix credentials per service

**Files:**
- Modify: `app/src/main/java/com/usage/claudewidget/data/Storage.kt`

- [ ] **Step 1: Add prefixed credential helpers for OpenAI and Cursor**

Add after the existing `orgId` property (around line 30):

```kotlin
// ---- OpenAI credentials ----
var openaiAccessToken: String?
    get() = secure.getString("openai_access_token", null)
    set(v) = secure.edit().putString("openai_access_token", v).apply()

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
    set(v) = secure.edit().putString("cursor_access_token", v).apply()

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
```

- [ ] **Step 2: Add per-service snapshot save/load**

Add after the existing `saveSnapshot` method (around line 209):

```kotlin
fun saveServiceSnapshot(serviceId: String, s: UsageSnapshot) {
    snapshot.edit()
        .putFloat("${serviceId}_fh_util", s.fiveHour.utilization)
        .putLong("${serviceId}_fh_reset", s.fiveHour.resetsAtEpochMs)
        .putFloat("${serviceId}_wk_util", s.sevenDay.utilization)
        .putLong("${serviceId}_wk_reset", s.sevenDay.resetsAtEpochMs)
        .putFloat("${serviceId}_fable_wk_util", s.fableWeekly?.utilization ?: -1f)
        .putLong("${serviceId}_fable_wk_reset", s.fableWeekly?.resetsAtEpochMs ?: 0L)
        .putLong("${serviceId}_fetched_at", s.fetchedAtEpochMs)
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
) {
    val hasData: Boolean get() = fiveHourUtil >= 0f || sevenDayUtil >= 0f
}

/** Per-service auth state, separate from the existing Claude-specific one. */
fun serviceAuthState(serviceId: String): AuthState =
    AuthState.entries.getOrElse(snapshot.getInt("${serviceId}_auth_state", 0)) { AuthState.OK }

fun setServiceAuthState(serviceId: String, state: AuthState) {
    snapshot.edit().putInt("${serviceId}_auth_state", state.ordinal).apply()
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/data/Storage.kt
git commit -m "feat: add per-service credential and snapshot storage"
```

---

### Task 4: Extract ClaudeProvider from UsageRepository

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/data/ClaudeProvider.kt`
- Modify: `app/src/main/java/com/usage/claudewidget/data/UsageRepository.kt` (keep as-is for now; ClaudeProvider wraps it)

- [ ] **Step 1: Create ClaudeProvider**

```kotlin
package com.usage.claudewidget.data

import android.content.Context

object ClaudeProvider : UsageProvider {
    override val serviceId = "claude"
    override val displayName = "Claude"

    override fun isAuthenticated(context: Context): Boolean =
        Storage.get(context).isLoggedIn

    override suspend fun refresh(context: Context): FetchResult =
        UsageRepository(context).refresh()

    override fun logout(context: Context) {
        val storage = Storage.get(context)
        storage.clearCredentials()
        storage.authState = AuthState.NEEDS_LOGIN
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/data/ClaudeProvider.kt
git commit -m "feat: extract ClaudeProvider wrapping UsageRepository"
```

---

### Task 5: Const — add OpenAI + Cursor URLs

**Files:**
- Modify: `app/src/main/java/com/usage/claudewidget/data/Const.kt`

- [ ] **Step 1: Add service constants**

Add to `Const.kt` after the existing constants:

```kotlin
// ---- OpenAI / Codex ----
const val OPENAI_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
const val OPENAI_AUTH_URL = "https://auth.openai.com/oauth/authorize"
const val OPENAI_TOKEN_URL = "https://auth.openai.com/oauth/token"
const val OPENAI_REDIRECT_URI = "http://localhost:1455/auth/callback"
const val OPENAI_SCOPES = "openid profile email offline_access"
const val OPENAI_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"

// ---- Cursor ----
const val CURSOR_CLIENT_ID = "KbZUR41cY7W6zRSdpSUJ7I7mLYBKOCmB"
fun cursorLoginUrl(challenge: String, uuid: String) =
    "https://cursor.com/loginDeepControl?challenge=$challenge&uuid=$uuid&mode=login&redirectTarget=cli"
const val CURSOR_POLL_URL = "https://api2.cursor.sh/auth/poll"
const val CURSOR_TOKEN_URL = "https://api2.cursor.sh/oauth/token"
const val CURSOR_USAGE_URL = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage"
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/data/Const.kt
git commit -m "feat: add OpenAI and Cursor endpoint constants"
```

---

### Task 6: UsageModels — add OpenAI and Cursor parsers

**Files:**
- Modify: `app/src/main/java/com/usage/claudewidget/data/UsageModels.kt`

- [ ] **Step 1: Add parse methods for OpenAI and Cursor responses**

Add to `UsageSnapshot.Companion` after the existing `parse` method:

```kotlin
fun parseOpenAi(body: String, now: Long): UsageSnapshot {
    val root = JSONObject(body)
    val primary = root.getJSONObject("primary_window")
    val secondary = root.getJSONObject("secondary_window")
    return UsageSnapshot(
        fiveHour = Window(
            utilization = (primary.optDouble("utilization", 0.0) * 100).toFloat(),
            resetsAtEpochMs = Iso8601.toEpochMs(primary.optString("resets_at", "")),
        ),
        sevenDay = Window(
            utilization = (secondary.optDouble("utilization", 0.0) * 100).toFloat(),
            resetsAtEpochMs = Iso8601.toEpochMs(secondary.optString("resets_at", "")),
        ),
        fableWeekly = null,
        fetchedAtEpochMs = now,
    )
}

fun parseCursor(body: String, now: Long): UsageSnapshot {
    val root = JSONObject(body)
    val usage = root.optJSONObject("planUsage") ?: root
    val pct = usage.optDouble("totalPercentUsed", 0.0).toFloat()
    val limit = usage.optLong("limit", 0L)
    val used = usage.optLong("used", 0L)
    // Cursor has a single billing window; map it to sevenDay (the "primary" meter).
    // There's no 5h equivalent, so set fiveHour to the same values.
    return UsageSnapshot(
        fiveHour = Window(utilization = pct, resetsAtEpochMs = 0L),
        sevenDay = Window(utilization = pct, resetsAtEpochMs = 0L),
        fableWeekly = null,
        fetchedAtEpochMs = now,
    )
}
```

Note: OpenAI returns utilization as a 0-1 fraction (multiplied by 100 here). Cursor returns `totalPercentUsed` already as 0-100. Cursor has no per-window reset times in the API response (billing period); the widget will show the percentage without a reset countdown.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/data/UsageModels.kt
git commit -m "feat: add OpenAI and Cursor usage response parsers"
```

---

### Task 7: OpenAiProvider — OAuth PKCE + fetch

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/data/OpenAiProvider.kt`

- [ ] **Step 1: Create the provider**

```kotlin
package com.usage.claudewidget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

object OpenAiProvider : UsageProvider {
    override val serviceId = "openai"
    override val displayName = "OpenAI"

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun isAuthenticated(context: Context): Boolean =
        Storage.get(context).isOpenaiLoggedIn

    override fun logout(context: Context) {
        Storage.get(context).clearOpenaiCredentials()
    }

    fun generatePkce(): Pair<String, String> {
        val verifier = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        return verifier to challenge
    }

    fun buildAuthUrl(challenge: String): String = buildString {
        append(Const.OPENAI_AUTH_URL)
        append("?client_id=").append(Const.OPENAI_CLIENT_ID)
        append("&redirect_uri=").append(java.net.URLEncoder.encode(Const.OPENAI_REDIRECT_URI, "UTF-8"))
        append("&response_type=code")
        append("&scope=").append(java.net.URLEncoder.encode(Const.OPENAI_SCOPES, "UTF-8"))
        append("&code_challenge=").append(challenge)
        append("&code_challenge_method=S256")
    }

    suspend fun exchangeCode(context: Context, code: String, verifier: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", Const.OPENAI_CLIENT_ID)
                .add("code", code)
                .add("redirect_uri", Const.OPENAI_REDIRECT_URI)
                .add("code_verifier", verifier)
                .build()
            val req = Request.Builder().url(Const.OPENAI_TOKEN_URL).post(body).build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.code != 200) return@withContext false
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val storage = Storage.get(context)
                    storage.openaiAccessToken = json.optString("access_token")
                    storage.openaiRefreshToken = json.optString("refresh_token")
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

    private suspend fun refreshToken(context: Context): Boolean = withContext(Dispatchers.IO) {
        val storage = Storage.get(context)
        val rt = storage.openaiRefreshToken ?: return@withContext false
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", Const.OPENAI_CLIENT_ID)
            .add("refresh_token", rt)
            .build()
        val req = Request.Builder().url(Const.OPENAI_TOKEN_URL).post(body).build()
        try {
            client.newCall(req).execute().use { resp ->
                if (resp.code != 200) return@withContext false
                val json = JSONObject(resp.body?.string().orEmpty())
                storage.openaiAccessToken = json.optString("access_token")
                json.optString("refresh_token").takeIf { it.isNotBlank() }
                    ?.let { storage.openaiRefreshToken = it }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun refresh(context: Context): FetchResult = withContext(Dispatchers.IO) {
        val storage = Storage.get(context)
        if (!isAuthenticated(context)) return@withContext FetchResult.NeedsLogin

        // Try fetch, refresh token on 401, retry once.
        var result = fetchUsage(storage)
        if (result is FetchResult.NeedsLogin && refreshToken(context)) {
            result = fetchUsage(Storage.get(context))
        }

        storage.setServiceAuthState(serviceId,
            if (result is FetchResult.NeedsLogin) AuthState.NEEDS_LOGIN else AuthState.OK)
        if (result is FetchResult.Success) storage.saveServiceSnapshot(serviceId, result.snapshot)
        result
    }

    private fun fetchUsage(storage: Storage): FetchResult {
        val token = storage.openaiAccessToken ?: return FetchResult.NeedsLogin
        val req = Request.Builder()
            .url(Const.OPENAI_USAGE_URL)
            .header("Authorization", "Bearer $token")
            .apply { storage.openaiAccountId?.let { header("ChatGPT-Account-Id", it) } }
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.code == 200 -> FetchResult.Success(
                        UsageSnapshot.parseOpenAi(body, System.currentTimeMillis())
                    )
                    resp.code == 401 || resp.code == 403 -> FetchResult.NeedsLogin
                    else -> FetchResult.Soft("openai-http-${resp.code}")
                }
            }
        } catch (e: Exception) {
            FetchResult.Soft(e.message ?: "openai-io")
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/data/OpenAiProvider.kt
git commit -m "feat: add OpenAiProvider with OAuth PKCE and usage fetch"
```

---

### Task 8: CursorProvider — device-code auth + fetch

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/data/CursorProvider.kt`

- [ ] **Step 1: Create the provider**

```kotlin
package com.usage.claudewidget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

object CursorProvider : UsageProvider {
    override val serviceId = "cursor"
    override val displayName = "Cursor"

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun isAuthenticated(context: Context): Boolean =
        Storage.get(context).isCursorLoggedIn

    override fun logout(context: Context) {
        Storage.get(context).clearCursorCredentials()
    }

    data class LoginChallenge(
        val uuid: String,
        val verifier: String,
        val challenge: String,
        val loginUrl: String,
    )

    fun createLoginChallenge(): LoginChallenge {
        val uuid = UUID.randomUUID().toString()
        val verifier = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        return LoginChallenge(
            uuid = uuid,
            verifier = verifier,
            challenge = challenge,
            loginUrl = Const.cursorLoginUrl(challenge, uuid),
        )
    }

    suspend fun pollForToken(context: Context, uuid: String, verifier: String): Boolean =
        withContext(Dispatchers.IO) {
            repeat(60) {
                val body = JSONObject().apply {
                    put("uuid", uuid)
                    put("verifier", verifier)
                }
                val req = Request.Builder()
                    .url(Const.CURSOR_POLL_URL)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                try {
                    client.newCall(req).execute().use { resp ->
                        if (resp.code == 200) {
                            val json = JSONObject(resp.body?.string().orEmpty())
                            val token = json.optString("accessToken")
                                .ifBlank { json.optString("access_token") }
                            if (token.isNotBlank()) {
                                val storage = Storage.get(context)
                                storage.cursorAccessToken = token
                                storage.cursorRefreshToken = json.optString("refreshToken")
                                    .ifBlank { json.optString("refresh_token") }
                                    .ifBlank { null }
                                return@withContext true
                            }
                        }
                    }
                } catch (_: Exception) { }
                delay(2000)
            }
            false
        }

    private suspend fun refreshToken(context: Context): Boolean = withContext(Dispatchers.IO) {
        val storage = Storage.get(context)
        val rt = storage.cursorRefreshToken ?: return@withContext false
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", Const.CURSOR_CLIENT_ID)
            .add("refresh_token", rt)
            .build()
        val req = Request.Builder().url(Const.CURSOR_TOKEN_URL).post(body).build()
        try {
            client.newCall(req).execute().use { resp ->
                if (resp.code != 200) return@withContext false
                val json = JSONObject(resp.body?.string().orEmpty())
                storage.cursorAccessToken = json.optString("access_token")
                    .ifBlank { json.optString("accessToken") }
                json.optString("refresh_token").ifBlank { json.optString("refreshToken") }
                    .takeIf { it.isNotBlank() }?.let { storage.cursorRefreshToken = it }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun refresh(context: Context): FetchResult = withContext(Dispatchers.IO) {
        val storage = Storage.get(context)
        if (!isAuthenticated(context)) return@withContext FetchResult.NeedsLogin

        var result = fetchUsage(storage)
        if (result is FetchResult.NeedsLogin && refreshToken(context)) {
            result = fetchUsage(Storage.get(context))
        }

        storage.setServiceAuthState(serviceId,
            if (result is FetchResult.NeedsLogin) AuthState.NEEDS_LOGIN else AuthState.OK)
        if (result is FetchResult.Success) storage.saveServiceSnapshot(serviceId, result.snapshot)
        result
    }

    private fun fetchUsage(storage: Storage): FetchResult {
        val token = storage.cursorAccessToken ?: return FetchResult.NeedsLogin
        val req = Request.Builder()
            .url(Const.CURSOR_USAGE_URL)
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.code == 200 -> FetchResult.Success(
                        UsageSnapshot.parseCursor(body, System.currentTimeMillis())
                    )
                    resp.code == 401 || resp.code == 403 -> FetchResult.NeedsLogin
                    else -> FetchResult.Soft("cursor-http-${resp.code}")
                }
            }
        } catch (e: Exception) {
            FetchResult.Soft(e.message ?: "cursor-io")
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/data/CursorProvider.kt
git commit -m "feat: add CursorProvider with device-code auth and usage fetch"
```

---

### Task 9: OpenAiLoginActivity — OAuth WebView

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/ui/OpenAiLoginActivity.kt`

- [ ] **Step 1: Create the activity**

```kotlin
package com.usage.claudewidget.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.usage.claudewidget.data.Const
import com.usage.claudewidget.data.OpenAiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenAiLoginActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var verifier: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (v, challenge) = OpenAiProvider.generatePkce()
        verifier = v

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Strip WebView marker so Google Sign-In works inside OpenAI's auth page.
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
        }

        val frame = FrameLayout(this).also { it.addView(webView) }
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(Const.OPENAI_REDIRECT_URI)) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) {
                        scope.launch {
                            val ok = OpenAiProvider.exchangeCode(this@OpenAiLoginActivity, code, verifier)
                            setResult(if (ok) RESULT_OK else RESULT_CANCELED)
                            finish()
                        }
                    } else {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                    return true
                }
                return false
            }
        }

        webView.loadUrl(OpenAiProvider.buildAuthUrl(challenge))
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/ui/OpenAiLoginActivity.kt
git commit -m "feat: add OpenAiLoginActivity with OAuth PKCE WebView"
```

---

### Task 10: CursorLoginActivity — device-code WebView + polling

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/ui/CursorLoginActivity.kt`

- [ ] **Step 1: Create the activity**

```kotlin
package com.usage.claudewidget.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.usage.claudewidget.data.CursorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CursorLoginActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val challenge = CursorProvider.createLoginChallenge()

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
        }

        val frame = FrameLayout(this).also { it.addView(webView) }
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Once the login page has loaded, start polling in the background.
                // The user completes login in the WebView; polling detects it.
            }
        }

        webView.loadUrl(challenge.loginUrl)

        // Start polling immediately; it retries for up to 2 minutes.
        scope.launch {
            val ok = CursorProvider.pollForToken(
                this@CursorLoginActivity, challenge.uuid, challenge.verifier
            )
            setResult(if (ok) RESULT_OK else RESULT_CANCELED)
            finish()
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/ui/CursorLoginActivity.kt
git commit -m "feat: add CursorLoginActivity with device-code WebView"
```

---

### Task 11: Per-service widgets — Claude, OpenAI, Cursor

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/widget/ClaudeWidget.kt`
- Create: `app/src/main/java/com/usage/claudewidget/widget/ClaudeWidgetReceiver.kt`
- Create: `app/src/main/java/com/usage/claudewidget/widget/OpenAiWidget.kt`
- Create: `app/src/main/java/com/usage/claudewidget/widget/OpenAiWidgetReceiver.kt`
- Create: `app/src/main/java/com/usage/claudewidget/widget/CursorWidget.kt`
- Create: `app/src/main/java/com/usage/claudewidget/widget/CursorWidgetReceiver.kt`
- Create: `app/src/main/java/com/usage/claudewidget/widget/ServiceWidget.kt` (shared base)
- Create: `res/xml/claude_widget_info.xml`, `res/xml/openai_widget_info.xml`, `res/xml/cursor_widget_info.xml`

- [ ] **Step 1: Create ServiceWidget base class**

This replaces the monolithic `UsageWidget` with a parameterized version that reads from a specific service's snapshot.

```kotlin
package com.usage.claudewidget.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.usage.claudewidget.R
import com.usage.claudewidget.data.AuthState
import com.usage.claudewidget.data.Storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.roundToInt

abstract class ServiceWidget(private val serviceId: String) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(60.dp, 60.dp), DpSize(180.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = readState(context)
        provideContent {
            val state by remember { widgetStateFlow(context) }.collectAsState(initial = initial)
            GlanceTheme { UsageWidgetContent(state) }
        }
    }

    private fun widgetStateFlow(context: Context): Flow<WidgetState> = callbackFlow {
        val storage = Storage.get(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readState(context))
        }
        storage.registerSnapshotListener(listener)
        trySend(readState(context))
        awaitClose { storage.unregisterSnapshotListener(listener) }
    }.conflate()

    private fun readState(context: Context): WidgetState {
        val s = Storage.get(context)
        val now = System.currentTimeMillis()
        val absolute = s.showAbsoluteResetTime

        // Claude uses the original unprefixed keys; other services use prefixed.
        val snap = if (serviceId == "claude") {
            Storage.ServiceSnapshotData(
                fiveHourUtil = s.fiveHourUtil,
                fiveHourReset = s.fiveHourReset,
                sevenDayUtil = s.sevenDayUtil,
                sevenDayReset = s.sevenDayReset,
                fableWeeklyUtil = s.fableWeeklyUtil,
                fableWeeklyReset = s.fableWeeklyReset,
                fetchedAt = s.fetchedAt,
            )
        } else {
            s.loadServiceSnapshot(serviceId)
        }

        val isLoggedIn = when (serviceId) {
            "claude" -> s.isLoggedIn
            "openai" -> s.isOpenaiLoggedIn
            "cursor" -> s.isCursorLoggedIn
            else -> false
        }
        val authState = if (serviceId == "claude") s.authState else s.serviceAuthState(serviceId)

        val fiveHourLabel = if (snap.fiveHourReset <= 0L) "-" else if (absolute) {
            context.getString(R.string.resets_at, TimeFmt.clockTime(context, snap.fiveHourReset))
        } else {
            context.getString(R.string.resets_in, TimeFmt.resetsIn(snap.fiveHourReset, now))
        }
        val sevenDayLabel = if (snap.sevenDayReset <= 0L) "-" else if (absolute) {
            context.getString(R.string.resets_at, TimeFmt.dateAndTime(context, snap.sevenDayReset))
        } else {
            context.getString(R.string.resets_in, TimeFmt.resetsIn(snap.sevenDayReset, now))
        }
        val showFable = serviceId == "claude" && s.showFableWeekly && snap.fableWeeklyUtil >= 0f
        val fableLabel = if (!showFable) "" else if (absolute) {
            context.getString(R.string.resets_at, TimeFmt.dateAndTime(context, snap.fableWeeklyReset))
        } else {
            context.getString(R.string.resets_in, TimeFmt.resetsIn(snap.fableWeeklyReset, now))
        }

        return WidgetState(
            needsLogin = !isLoggedIn || authState == AuthState.NEEDS_LOGIN,
            hasData = snap.hasData,
            fiveHourPct = snap.fiveHourUtil.coerceAtLeast(0f).roundToInt(),
            fiveHourResetLabel = fiveHourLabel,
            sevenDayPct = snap.sevenDayUtil.coerceAtLeast(0f).roundToInt(),
            sevenDayResetLabel = sevenDayLabel,
            showFableWeekly = showFable,
            fableWeeklyPct = snap.fableWeeklyUtil.coerceAtLeast(0f).roundToInt(),
            fableWeeklyResetLabel = fableLabel,
            stale = TimeFmt.isStale(snap.fetchedAt, now),
            refreshing = s.refreshRequestedAt > 0L && now - s.refreshRequestedAt < 60_000L,
            bgOpacityPercent = s.widgetBgOpacityPercent,
        )
    }
}
```

- [ ] **Step 2: Create concrete widget + receiver classes**

`ClaudeWidget.kt`:
```kotlin
package com.usage.claudewidget.widget

class ClaudeWidget : ServiceWidget("claude")
```

`ClaudeWidgetReceiver.kt`:
```kotlin
package com.usage.claudewidget.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.usage.claudewidget.work.RefreshScheduler

class ClaudeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClaudeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.ensurePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshScheduler.ensurePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}
```

`OpenAiWidget.kt`:
```kotlin
package com.usage.claudewidget.widget

class OpenAiWidget : ServiceWidget("openai")
```

`OpenAiWidgetReceiver.kt`:
```kotlin
package com.usage.claudewidget.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.usage.claudewidget.work.RefreshScheduler

class OpenAiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OpenAiWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.ensurePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshScheduler.ensurePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}
```

`CursorWidget.kt`:
```kotlin
package com.usage.claudewidget.widget

class CursorWidget : ServiceWidget("cursor")
```

`CursorWidgetReceiver.kt`:
```kotlin
package com.usage.claudewidget.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.usage.claudewidget.work.RefreshScheduler

class CursorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CursorWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.ensurePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshScheduler.ensurePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}
```

- [ ] **Step 3: Create widget info XMLs**

`res/xml/claude_widget_info.xml` (copy of existing `usage_widget_info.xml`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:minResizeWidth="40dp"
    android:minResizeHeight="40dp"
    android:maxResizeWidth="320dp"
    android:maxResizeHeight="240dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen"
    android:description="@string/claude_widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:previewImage="@drawable/ic_lobster" />
```

`res/xml/openai_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:minResizeWidth="40dp"
    android:minResizeHeight="40dp"
    android:maxResizeWidth="320dp"
    android:maxResizeHeight="240dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen"
    android:description="@string/openai_widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:previewImage="@drawable/ic_openai" />
```

`res/xml/cursor_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:minResizeWidth="40dp"
    android:minResizeHeight="40dp"
    android:maxResizeWidth="320dp"
    android:maxResizeHeight="240dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen"
    android:description="@string/cursor_widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:previewImage="@drawable/ic_cursor" />
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/widget/ServiceWidget.kt \
       app/src/main/java/com/usage/claudewidget/widget/ClaudeWidget.kt \
       app/src/main/java/com/usage/claudewidget/widget/ClaudeWidgetReceiver.kt \
       app/src/main/java/com/usage/claudewidget/widget/OpenAiWidget.kt \
       app/src/main/java/com/usage/claudewidget/widget/OpenAiWidgetReceiver.kt \
       app/src/main/java/com/usage/claudewidget/widget/CursorWidget.kt \
       app/src/main/java/com/usage/claudewidget/widget/CursorWidgetReceiver.kt \
       app/src/main/res/xml/claude_widget_info.xml \
       app/src/main/res/xml/openai_widget_info.xml \
       app/src/main/res/xml/cursor_widget_info.xml
git commit -m "feat: add per-service widget classes and XML definitions"
```

---

### Task 12: Combined widget

**Files:**
- Create: `app/src/main/java/com/usage/claudewidget/widget/CombinedGlanceUi.kt`
- Create: `app/src/main/java/com/usage/claudewidget/widget/CombinedWidget.kt`
- Create: `app/src/main/java/com/usage/claudewidget/widget/CombinedWidgetReceiver.kt`
- Create: `res/xml/combined_widget_info.xml`

- [ ] **Step 1: Create CombinedGlanceUi**

```kotlin
package com.usage.claudewidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.usage.claudewidget.R

data class CombinedServiceState(
    val serviceId: String,
    val label: String,
    val worstPct: Int,
    val resetLabel: String,
)

data class CombinedWidgetState(
    val services: List<CombinedServiceState>,
    val bgOpacityPercent: Int = 100,
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

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgFor(state.bgOpacityPercent))
            .cornerRadius(20.dp)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 6.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.services.isEmpty()) {
            Text("No services", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                state.services.forEachIndexed { i, svc ->
                    if (i > 0) Spacer(GlanceModifier.height(if (compact) 4.dp else 8.dp))
                    if (compact) {
                        CompactServiceRow(svc)
                    } else {
                        FullServiceRow(svc)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullServiceRow(svc: CombinedServiceState) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                svc.label,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.width(36.dp),
            )
            Text(
                "${svc.worstPct}%",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                svc.resetLabel,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = (svc.worstPct.coerceIn(0, 100)) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = colorForPct(svc.worstPct),
            backgroundColor = ColorProvider(R.color.bar_track),
        )
    }
}

@Composable
private fun CompactServiceRow(svc: CombinedServiceState) {
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
            "${svc.worstPct}%",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold),
        )
    }
}

// Reuse widgetBgFor from UsageGlanceUi — it's package-private, same package.
```

- [ ] **Step 2: Create CombinedWidget**

```kotlin
package com.usage.claudewidget.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.usage.claudewidget.data.Storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.roundToInt

class CombinedWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(60.dp, 60.dp), DpSize(180.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = readState(context)
        provideContent {
            val state by remember { stateFlow(context) }.collectAsState(initial = initial)
            GlanceTheme { CombinedWidgetContent(state) }
        }
    }

    private fun stateFlow(context: Context): Flow<CombinedWidgetState> = callbackFlow {
        val storage = Storage.get(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readState(context))
        }
        storage.registerSnapshotListener(listener)
        trySend(readState(context))
        awaitClose { storage.unregisterSnapshotListener(listener) }
    }.conflate()

    private fun readState(context: Context): CombinedWidgetState {
        val s = Storage.get(context)
        val now = System.currentTimeMillis()
        val services = mutableListOf<CombinedServiceState>()

        if (s.isLoggedIn && s.hasSnapshot) {
            val pct5 = s.fiveHourUtil.coerceAtLeast(0f).roundToInt()
            val pct7 = s.sevenDayUtil.coerceAtLeast(0f).roundToInt()
            val worst = maxOf(pct5, pct7)
            val reset = if (pct5 >= pct7) s.fiveHourReset else s.sevenDayReset
            services.add(CombinedServiceState("claude", "CLD", worst,
                TimeFmt.resetsIn(reset, now)))
        }

        if (s.isOpenaiLoggedIn) {
            val snap = s.loadServiceSnapshot("openai")
            if (snap.hasData) {
                val worst = maxOf(snap.fiveHourUtil, snap.sevenDayUtil).coerceAtLeast(0f).roundToInt()
                val reset = if (snap.fiveHourUtil >= snap.sevenDayUtil) snap.fiveHourReset else snap.sevenDayReset
                services.add(CombinedServiceState("openai", "OAI", worst,
                    TimeFmt.resetsIn(reset, now)))
            }
        }

        if (s.isCursorLoggedIn) {
            val snap = s.loadServiceSnapshot("cursor")
            if (snap.hasData) {
                services.add(CombinedServiceState("cursor", "CUR",
                    snap.fiveHourUtil.coerceAtLeast(0f).roundToInt(), "-"))
            }
        }

        return CombinedWidgetState(
            services = services,
            bgOpacityPercent = s.widgetBgOpacityPercent,
        )
    }
}
```

- [ ] **Step 3: Create CombinedWidgetReceiver**

```kotlin
package com.usage.claudewidget.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.usage.claudewidget.work.RefreshScheduler

class CombinedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CombinedWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.ensurePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshScheduler.ensurePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}
```

- [ ] **Step 4: Create combined_widget_info.xml**

`res/xml/combined_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="40dp"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:minResizeWidth="110dp"
    android:minResizeHeight="40dp"
    android:maxResizeWidth="320dp"
    android:maxResizeHeight="320dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen"
    android:description="@string/combined_widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:previewImage="@drawable/ic_lobster" />
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/widget/CombinedGlanceUi.kt \
       app/src/main/java/com/usage/claudewidget/widget/CombinedWidget.kt \
       app/src/main/java/com/usage/claudewidget/widget/CombinedWidgetReceiver.kt \
       app/src/main/res/xml/combined_widget_info.xml
git commit -m "feat: add combined summary widget"
```

---

### Task 13: Update RefreshWorker + ResetAlarmReceiver to iterate providers

**Files:**
- Modify: `app/src/main/java/com/usage/claudewidget/work/RefreshWorker.kt`
- Modify: `app/src/main/java/com/usage/claudewidget/work/ResetAlarmReceiver.kt`

- [ ] **Step 1: Update RefreshWorker**

Replace the body of `doWork()`:

```kotlin
override suspend fun doWork(): Result {
    ProviderRegistry.init()
    var anySuccess = false
    var anySoft = false

    for (provider in ProviderRegistry.authenticated(applicationContext)) {
        when (val result = provider.refresh(applicationContext)) {
            is FetchResult.Success -> {
                anySuccess = true
                try {
                    UsageNotifier.ensureChannel(applicationContext)
                    UsageNotifier.maybeNotifyForService(
                        applicationContext, Storage.get(applicationContext),
                        provider.serviceId, result.snapshot
                    )
                } catch (_: Exception) { }
            }
            is FetchResult.NeedsLogin -> { }
            is FetchResult.Soft -> { anySoft = true }
        }
    }

    Storage.get(applicationContext).refreshRequestedAt = 0L
    updateAllWidgets(applicationContext)
    if (anySuccess) RefreshScheduler.scheduleResetCheck(applicationContext)

    return when {
        anySoft && runAttemptCount < MAX_RETRIES -> Result.retry()
        else -> Result.success()
    }
}
```

Add import for `com.usage.claudewidget.data.ProviderRegistry`.

- [ ] **Step 2: Update ResetAlarmReceiver**

Replace the try block inside `onReceive`:

```kotlin
try {
    ProviderRegistry.init()
    var anySuccess = false
    for (provider in ProviderRegistry.authenticated(appContext)) {
        val result = provider.refresh(appContext)
        if (result is FetchResult.Success) {
            anySuccess = true
            try {
                UsageNotifier.ensureChannel(appContext)
                UsageNotifier.maybeNotifyForService(
                    appContext, Storage.get(appContext),
                    provider.serviceId, result.snapshot
                )
            } catch (_: Exception) { }
        }
    }
    Storage.get(appContext).refreshRequestedAt = 0L
    updateAllWidgets(appContext)
    if (anySuccess) RefreshScheduler.scheduleResetCheck(appContext)
} catch (_: Exception) { }
```

- [ ] **Step 3: Create helper to update all widget types**

Add to a new file or to an existing utility. Simplest: add a top-level function in `ServiceWidget.kt`:

```kotlin
suspend fun updateAllWidgets(context: Context) {
    try { ClaudeWidget().updateAll(context) } catch (_: Exception) {}
    try { OpenAiWidget().updateAll(context) } catch (_: Exception) {}
    try { CursorWidget().updateAll(context) } catch (_: Exception) {}
    try { CombinedWidget().updateAll(context) } catch (_: Exception) {}
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/work/RefreshWorker.kt \
       app/src/main/java/com/usage/claudewidget/work/ResetAlarmReceiver.kt \
       app/src/main/java/com/usage/claudewidget/widget/ServiceWidget.kt
git commit -m "feat: iterate all providers in RefreshWorker and ResetAlarmReceiver"
```

---

### Task 14: Update Actions.kt for per-service tap refresh

**Files:**
- Modify: `app/src/main/java/com/usage/claudewidget/widget/Actions.kt`

- [ ] **Step 1: Parameterize RefreshAction or keep as-is**

The simplest approach: keep `RefreshAction` refreshing ALL authenticated providers (same as the background refresh). The tap on any widget refreshes everything. This avoids needing to pass a serviceId through Glance action parameters, which is awkward with Glance's `ActionParameters`.

Replace the fetch section in `onAction`:

```kotlin
try {
    ProviderRegistry.init()
    var anyOutcome = "?"
    for (provider in ProviderRegistry.authenticated(context)) {
        val r = provider.refresh(context)
        val tag = "${provider.serviceId}=${when (r) {
            is FetchResult.Success -> "OK"
            is FetchResult.NeedsLogin -> "NeedsLogin"
            is FetchResult.Soft -> "Soft(${r.reason})"
        }}"
        anyOutcome = if (anyOutcome == "?") tag else "$anyOutcome, $tag"
    }
    outcome = anyOutcome
} catch (e: Exception) {
    outcome = "EX(${e.javaClass.simpleName}: ${e.message})"
}
```

Update the widget update call to `updateAllWidgets(context)`.

Add import for `com.usage.claudewidget.data.ProviderRegistry`.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/widget/Actions.kt
git commit -m "feat: refresh all providers on widget tap"
```

---

### Task 15: UsageNotifier — per-service channels

**Files:**
- Modify: `app/src/main/java/com/usage/claudewidget/work/UsageNotifier.kt`

- [ ] **Step 1: Add maybeNotifyForService method**

Add a new method that handles per-service notification IDs:

```kotlin
private val SERVICE_NOTIFY_IDS = mapOf(
    "claude" to Triple(ID_5H, ID_7D, ID_FABLE),
    "openai" to Triple(3001, 3002, 3003),
    "cursor" to Triple(4001, 4002, 4003),
)

private val SERVICE_RESET_IDS = mapOf(
    "claude" to Triple(ID_RESET_5H, ID_RESET_7D, ID_RESET_FABLE),
    "openai" to Triple(5001, 5002, 5003),
    "cursor" to Triple(6001, 6002, 6003),
)

fun maybeNotifyForService(
    context: Context, storage: Storage, serviceId: String, snapshot: UsageSnapshot
) {
    val (id5h, id7d, idFable) = SERVICE_NOTIFY_IDS[serviceId] ?: return
    val (rid5h, rid7d, ridFable) = SERVICE_RESET_IDS[serviceId] ?: return
    val prefix = "${serviceId}_"

    if (storage.nearLimitAlertsEnabled) {
        checkWindow(context, storage, "${prefix}5h", id5h, R.string.notify_5h_title, snapshot.fiveHour)
        checkWindow(context, storage, "${prefix}7d", id7d, R.string.notify_7d_title, snapshot.sevenDay)
        snapshot.fableWeekly?.let {
            checkWindow(context, storage, "${prefix}fable", idFable, R.string.notify_fable_title, it)
        }
    }

    checkReset(context, storage, "${prefix}5h", storage.reset5hMode, rid5h, R.string.reset_5h_title, snapshot.fiveHour)
    checkReset(context, storage, "${prefix}7d", storage.resetWeeklyMode, rid7d, R.string.reset_7d_title, snapshot.sevenDay)
    snapshot.fableWeekly?.let {
        checkReset(context, storage, "${prefix}fable", storage.resetWeeklyMode, ridFable, R.string.reset_fable_title, it)
    }
}
```

The existing `maybeNotify` stays for backward compat (it's the Claude-specific path). Both use the same `checkWindow`/`checkReset` internals.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/work/UsageNotifier.kt
git commit -m "feat: per-service notification channels and IDs"
```

---

### Task 16: AndroidManifest — register new widgets and activities

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Replace the existing UsageWidgetReceiver entry and add new ones**

Replace the `UsageWidgetReceiver` block with `ClaudeWidgetReceiver`, and add entries for OpenAI, Cursor, Combined widgets, plus the two login activities:

```xml
<!-- Claude widget -->
<receiver
    android:name=".widget.ClaudeWidgetReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/claude_widget_info" />
</receiver>

<!-- OpenAI widget -->
<receiver
    android:name=".widget.OpenAiWidgetReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/openai_widget_info" />
</receiver>

<!-- Cursor widget -->
<receiver
    android:name=".widget.CursorWidgetReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/cursor_widget_info" />
</receiver>

<!-- Combined widget -->
<receiver
    android:name=".widget.CombinedWidgetReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/combined_widget_info" />
</receiver>

<!-- OAuth login activities -->
<activity
    android:name=".ui.OpenAiLoginActivity"
    android:exported="false"
    android:label="Sign in to OpenAI"
    android:theme="@style/Theme.ClaudeUsage.Login" />

<activity
    android:name=".ui.CursorLoginActivity"
    android:exported="false"
    android:label="Sign in to Cursor"
    android:theme="@style/Theme.ClaudeUsage.Login" />
```

Remove the old `UsageWidgetReceiver` block.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register per-service widgets and login activities in manifest"
```

---

### Task 17: Strings + drawable resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_openai.xml`
- Create: `app/src/main/res/drawable/ic_cursor.xml`

- [ ] **Step 1: Update strings.xml**

Change `app_name` and add per-service widget descriptions:

```xml
<string name="app_name">LLiMits</string>
<string name="claude_widget_description">Claude usage (5H / 1W) on your home screen</string>
<string name="openai_widget_description">OpenAI/Codex usage on your home screen</string>
<string name="cursor_widget_description">Cursor usage on your home screen</string>
<string name="combined_widget_description">All AI service usage on your home screen</string>
```

Keep the existing `widget_description` for backward compat (or remove if nothing references it after the manifest change).

- [ ] **Step 2: Create OpenAI icon placeholder**

`res/drawable/ic_openai.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#000000">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,8c1.1,0 2,0.9 2,2v4c0,1.1 -0.9,2 -2,2s-2,-0.9 -2,-2v-4c0,-1.1 0.9,-2 2,-2z"/>
</vector>
```

Note: This is a placeholder. Replace with the actual OpenAI logomark SVG path when available. The tint will be overridden by the widget's color scheme.

- [ ] **Step 3: Create Cursor icon placeholder**

`res/drawable/ic_cursor.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#000000">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M5,3l14,9 -5.5,1.5L12,19z"/>
</vector>
```

Note: Placeholder cursor-arrow shape. Replace with actual Cursor logo SVG path.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml \
       app/src/main/res/drawable/ic_openai.xml \
       app/src/main/res/drawable/ic_cursor.xml
git commit -m "feat: add LLiMits strings and service icon placeholders"
```

---

### Task 18: MainActivity — multi-service settings UI

**Files:**
- Modify: `app/src/main/java/com/usage/claudewidget/ui/MainActivity.kt`

- [ ] **Step 1: Rename title and add service login sections**

Change the top bar title from `"Claude Usage"` to `"LLiMits"`.

In line 196, change:
```kotlin
Text("Claude Usage", ...)
```
to:
```kotlin
Text("LLiMits", ...)
```

- [ ] **Step 2: Add OpenAI and Cursor account sections**

After the existing "Account" `SectionCard` (ends around line 234), add:

```kotlin
SectionCard(title = "OpenAI / Codex", icon = Icons.Filled.AccountCircle) {
    val openaiLoggedIn = storage.isOpenaiLoggedIn
    Text(
        if (openaiLoggedIn) "Signed in ✓" else "Not signed in",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = {
                openaiLoginLauncher.launch(
                    Intent(context, OpenAiLoginActivity::class.java)
                )
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (openaiLoggedIn) "Re-sign in" else "Sign in", modifier = Modifier.padding(start = 8.dp))
        }
        if (openaiLoggedIn) {
            OutlinedButton(onClick = {
                OpenAiProvider.logout(context)
                status = describe(storage)
            }) { Text("Sign out") }
        }
    }
}

SectionCard(title = "Cursor", icon = Icons.Filled.AccountCircle) {
    val cursorLoggedIn = storage.isCursorLoggedIn
    Text(
        if (cursorLoggedIn) "Signed in ✓" else "Not signed in",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = {
                cursorLoginLauncher.launch(
                    Intent(context, CursorLoginActivity::class.java)
                )
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (cursorLoggedIn) "Re-sign in" else "Sign in", modifier = Modifier.padding(start = 8.dp))
        }
        if (cursorLoggedIn) {
            OutlinedButton(onClick = {
                CursorProvider.logout(context)
                status = describe(storage)
            }) { Text("Sign out") }
        }
    }
}
```

- [ ] **Step 3: Add activity result launchers for OpenAI and Cursor**

Near the existing `loginLauncher` (around line 156), add:

```kotlin
val openaiLoginLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {
    status = describe(storage)
    if (it.resultCode == Activity.RESULT_OK) {
        scope.launch {
            OpenAiProvider.refresh(context)
            updateAllWidgets(context)
        }
    }
}

val cursorLoginLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {
    status = describe(storage)
    if (it.resultCode == Activity.RESULT_OK) {
        scope.launch {
            CursorProvider.refresh(context)
            updateAllWidgets(context)
        }
    }
}
```

Add imports:
```kotlin
import com.usage.claudewidget.data.OpenAiProvider
import com.usage.claudewidget.data.CursorProvider
import com.usage.claudewidget.widget.updateAllWidgets
```

- [ ] **Step 4: Update BUILD_TAG**

```kotlin
private const val BUILD_TAG = "llimits-1"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/ui/MainActivity.kt
git commit -m "feat: add multi-service login UI and rename to LLiMits"
```

---

### Task 19: Wire up ProviderRegistry.init() at app startup

**Files:**
- Modify: `app/src/main/java/com/usage/claudewidget/ui/MainActivity.kt`

- [ ] **Step 1: Call init in onCreate**

At the top of `MainActivity.onCreate`, before `setContent`:

```kotlin
ProviderRegistry.init()
```

Add import: `import com.usage.claudewidget.data.ProviderRegistry`

- [ ] **Step 2: Also init in RefreshScheduler entry points**

In `RefreshScheduler.ensurePeriodic`, at the top:
```kotlin
ProviderRegistry.init()
```

This ensures providers are registered before any background work runs.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/usage/claudewidget/ui/MainActivity.kt \
       app/src/main/java/com/usage/claudewidget/work/RefreshScheduler.kt
git commit -m "feat: wire ProviderRegistry.init at app and worker startup"
```

---

### Task 20: Clean up old UsageWidget / UsageWidgetReceiver

**Files:**
- Delete or deprecate: `app/src/main/java/com/usage/claudewidget/widget/UsageWidget.kt`
- Delete or deprecate: `app/src/main/java/com/usage/claudewidget/widget/UsageWidgetReceiver.kt`
- Delete: `app/src/main/res/xml/usage_widget_info.xml`

- [ ] **Step 1: Remove old files**

Delete `UsageWidget.kt`, `UsageWidgetReceiver.kt`, and `usage_widget_info.xml`. Their functionality is now in `ServiceWidget`/`ClaudeWidget`/`ClaudeWidgetReceiver`/`claude_widget_info.xml`.

Search the codebase for any remaining `UsageWidget` references and update them to `ClaudeWidget` (e.g., in `Actions.kt`, `RefreshWorker.kt`, `ResetAlarmReceiver.kt`). The `updateAllWidgets()` helper from Task 13 already replaces the old `UsageWidget.updateAll(context)` call pattern.

- [ ] **Step 2: Verify no broken references**

```bash
grep -r "UsageWidget" app/src/main/ --include="*.kt"
grep -r "UsageWidgetReceiver" app/src/main/ --include="*.kt" --include="*.xml"
grep -r "usage_widget_info" app/src/main/ --include="*.xml"
```

All should return empty.

- [ ] **Step 3: Commit**

```bash
git rm app/src/main/java/com/usage/claudewidget/widget/UsageWidget.kt \
      app/src/main/java/com/usage/claudewidget/widget/UsageWidgetReceiver.kt \
      app/src/main/res/xml/usage_widget_info.xml
git commit -m "chore: remove old UsageWidget in favor of per-service widgets"
```

---

### Task 21: Build and verify

- [ ] **Step 1: Build debug APK**

Ask the user before building. Then:

```powershell
$env:JAVA_OPTS = "-Djavax.net.ssl.trustStore=$PWD\gradle-cacerts.jks -Djavax.net.ssl.trustStorePassword=changeit"
.\gradlew.bat assembleDebug
```

- [ ] **Step 2: Fix any compile errors**

Address missing imports, type mismatches, or reference issues found during build. Common issues to watch for:
- `widgetBgFor` visibility between `UsageGlanceUi.kt` and `CombinedGlanceUi.kt` (both are in `widget` package, so package-private works)
- `updateAllWidgets` must be a `suspend fun` or called from a coroutine
- `ProviderRegistry.init()` must be called before any provider access

- [ ] **Step 3: Install and test on device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify:
1. App opens and shows "LLiMits" title
2. Claude sign-in still works
3. OpenAI sign-in opens WebView, completes OAuth, shows "Signed in"
4. Cursor sign-in opens WebView, polls, shows "Signed in"
5. Widget picker shows 4 widgets: Claude, OpenAI, Cursor, Combined
6. Each per-service widget shows data after sign-in + refresh
7. Combined widget stacks all authenticated services

- [ ] **Step 4: Commit any build fixes**

```bash
git add -A
git commit -m "fix: build fixes for multi-service integration"
```
