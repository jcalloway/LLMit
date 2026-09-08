# LLiMits: Multi-Service AI Usage Tracking

**Date:** 2026-08-24
**Status:** Approved design, pending implementation plan

## Overview

Rename "Claude Usage" to "LLiMits" (LLM + Limits). Add OpenAI/Codex and Cursor usage tracking alongside the existing Claude tracking. Each service gets its own home-screen widget; a combined summary widget stacks all authenticated services.

## Phase 1 Scope

- **Claude**: keep existing WebView cookie auth, unchanged fetch logic
- **OpenAI/Codex**: OAuth 2.0 Authorization Code + PKCE
- **Cursor**: device-code-style auth via loginDeepControl + poll

Phase 2 candidates: xAI/Grok (OAuth details already researched), Google AI Studio.

## Architecture: Hybrid (Approach C)

Shared widget rendering, notification, and scheduling infrastructure. Standalone auth and fetch logic per service. No forced abstraction layer; each provider is a self-contained class behind a minimal interface.

## Data Model

### UsageProvider interface

```kotlin
interface UsageProvider {
    val serviceId: String          // "claude", "openai", "cursor"
    val displayName: String
    suspend fun authenticate(context: Context): AuthResult
    suspend fun refresh(context: Context): UsageSnapshot
    fun isAuthenticated(context: Context): Boolean
    fun logout(context: Context)
}
```

### UsageSnapshot (existing, unchanged)

Already generic: windows with pct + resetLabel. Each provider maps its API response into this shape.

- **Claude**: 5h window, 7d window, optional Fable weekly window
- **OpenAI**: `primary_window` (5h) and `secondary_window` (7d) from `chatgpt.com/backend-api/wham/usage`
- **Cursor**: single billing-period window from `api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage` (used/limit/remaining/totalPercentUsed)

### WidgetState (existing, reused)

Same `WidgetState` data class drives all per-service widgets. The combined widget uses a list of `(serviceId, displayName, worstWindowPct, worstWindowResetLabel)` tuples.

## Authentication

### Claude (unchanged)

WebView loads claude.ai, user signs in, app scrapes sessionKey + cf_clearance cookies. Stored in EncryptedSharedPreferences with `claude_` prefix.

### OpenAI/Codex (OAuth PKCE)

| Parameter | Value |
|---|---|
| Client ID | `app_EMoamEEZ73f0CkXaXp7hrann` |
| Auth URL | `https://auth.openai.com/oauth/authorize` |
| Token URL | `https://auth.openai.com/oauth/token` |
| Redirect URI | `http://localhost:1455/auth/callback` |
| Scopes | `openid profile email offline_access` |
| PKCE | S256 |

Flow:
1. Generate code_verifier + code_challenge
2. Open WebView to auth URL with PKCE params
3. Intercept redirect to localhost with auth code
4. Exchange code for access_token + refresh_token via token URL
5. Store tokens in EncryptedSharedPreferences with `openai_` prefix

Token refresh: POST to token URL with `grant_type=refresh_token` before each fetch. On 401 after refresh attempt, mark service as needing re-login.

Usage fetch: GET `https://chatgpt.com/backend-api/wham/usage` with Bearer token + ChatGPT-Account-Id header.

### Cursor (device-code auth)

| Parameter | Value |
|---|---|
| Client ID | `KbZUR41cY7W6zRSdpSUJ7I7mLYBKOCmB` |
| Login URL | `https://cursor.com/loginDeepControl?challenge={pkce}&uuid={uuid}&mode=login&redirectTarget=cli` |
| Poll URL | `POST https://api2.cursor.sh/auth/poll` |
| Refresh URL | `POST https://api2.cursor.sh/oauth/token` |

Flow:
1. Generate UUID + PKCE challenge
2. Open WebView to login URL
3. After user completes login, poll auth/poll with UUID until token returned
4. Store access_token + refresh_token with `cursor_` prefix

Usage fetch: POST `https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage` with Bearer token. Returns planUsage with limit/remaining/used/totalPercentUsed.

## Widgets

### Per-service widgets (3)

One `GlanceAppWidget` subclass per service. Each reuses the existing `UsageWidgetContent` composable with a `WidgetState` populated from that service's provider. User picks which to add from the Android widget picker.

Widget info XML files: `claude_widget_info.xml`, `openai_widget_info.xml`, `cursor_widget_info.xml`. Each declares the same size breakpoints (60x60 compact, 180x110 full).

### Combined summary widget

New composable `CombinedWidgetContent`. Stacks one row per authenticated service:
- 12dp service icon + 3-char abbreviation (CLD, OAI, CUR)
- Worst-window percentage + traffic-light color bar
- Tap opens settings

If only one service is authenticated, renders the full single-service layout instead.

Compact mode (< 130dp wide): icon + pct only, no bar.

## Notifications

### Channels

One notification channel per service per type:
- `claude_usage_alerts`, `openai_usage_alerts`, `cursor_usage_alerts`
- `claude_resets`, `openai_resets`, `cursor_resets`

Users can mute per-service in Android notification settings.

### Thresholds

Same alert threshold logic as today (configurable in settings). Applied per-service independently. Each service's worst window triggers its own notification.

## Scheduling

### AlarmManager (unchanged logic)

`ResetAlarmReceiver` iterates `ProviderRegistry.authenticatedProviders()` and calls `refresh()` on each. Re-arms for the earliest next-reset across all services.

### WorkManager backstop (unchanged)

15-minute periodic job calls the same provider iteration.

### Boot receiver (unchanged)

Re-arms alarm + periodic job on device boot.

## Storage

### EncryptedSharedPreferences (credentials)

All keys prefixed by serviceId:
- `claude_sessionKey`, `claude_cfClearance`, `claude_userAgent`, `claude_orgId`
- `openai_accessToken`, `openai_refreshToken`, `openai_accountId`
- `cursor_accessToken`, `cursor_refreshToken`, `cursor_userId`

### Plain SharedPreferences (widget snapshot)

Prefixed snapshot keys: `claude_snapshot_json`, `openai_snapshot_json`, `cursor_snapshot_json`.

Each widget reads only its own prefix. Combined widget reads all.

## Settings UI

`MainActivity` adds a service selector (tabs or expandable sections). Each service section contains:
- Login/logout button with auth status indicator
- Alert threshold sliders (per-window)
- Reset notification toggle

Global section (top): background opacity slider, diagnostics.

App title changes from "Claude Usage" to "LLiMits".

## File Plan

```
data/
  UsageProvider.kt          # interface + AuthResult sealed class
  ClaudeProvider.kt         # extracted from current UsageRepository
  OpenAiProvider.kt         # OAuth PKCE + usage fetch
  CursorProvider.kt         # device-code + usage fetch
  ProviderRegistry.kt       # singleton list, iteration helpers
  Storage.kt                # add prefixed getters/setters
  UsageModels.kt            # unchanged

widget/
  UsageGlanceUi.kt          # unchanged (renders WidgetState)
  ClaudeWidget.kt           # rename from UsageWidget.kt
  OpenAiWidget.kt           # same shape, different provider
  CursorWidget.kt           # same shape, different provider
  CombinedWidget.kt         # GlanceAppWidget for combined view
  CombinedGlanceUi.kt       # stacked summary composable
  Actions.kt                # parameterize by serviceId

ui/
  MainActivity.kt           # service tabs, rename title
  OpenAiLoginActivity.kt    # WebView OAuth flow
  CursorLoginActivity.kt    # WebView device-code flow

work/
  ResetAlarmReceiver.kt     # iterate all providers
  RefreshScheduler.kt       # unchanged API

res/
  xml/claude_widget_info.xml    # rename from usage_widget_info.xml
  xml/openai_widget_info.xml
  xml/cursor_widget_info.xml
  xml/combined_widget_info.xml
  drawable/ic_openai.xml
  drawable/ic_cursor.xml
  values/strings.xml            # add per-service strings, rename app
```

## Excluded from Phase 1

- Multiple accounts per service
- xAI/Grok (Phase 2; OAuth details documented)
- Cross-service aggregate analytics or usage history
- Per-service widget color theming (all use traffic-light palette)
- Usage history graphs or trends
