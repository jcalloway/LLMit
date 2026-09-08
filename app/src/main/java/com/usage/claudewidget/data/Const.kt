package com.usage.claudewidget.data

object Const {
    const val BASE = "https://claude.ai"
    const val ORGS_URL = "$BASE/api/organizations"
    fun usageUrl(orgId: String) = "$BASE/api/organizations/$orgId/usage"

    // The page we load in the WebView to (a) let the user log in and
    // (b) silently re-solve the Cloudflare JS challenge to mint cf_clearance.
    const val LOGIN_URL = "$BASE/login"
    const val CHALLENGE_URL = "$BASE/new"

    const val COOKIE_SESSION = "sessionKey"
    const val COOKIE_CF = "cf_clearance"
    const val COOKIE_LAST_ORG = "lastActiveOrg"

    const val REFRESH_INTERVAL_MIN = 15L
    const val WORK_NAME = "claude-usage-refresh"

    // One-shot check scheduled to fire right at a window's reset, so the "limit available again"
    // notification isn't up to a full 15-minute poll cycle late.
    const val RESET_CHECK_WORK = "claude-usage-reset-check"

    // ---- OpenAI / Codex ----
    const val OPENAI_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val OPENAI_AUTH_URL = "https://auth.openai.com/oauth/authorize"
    const val OPENAI_TOKEN_URL = "https://auth.openai.com/oauth/token"
    const val OPENAI_REDIRECT_URI = "http://localhost:1455/auth/callback"
    const val OPENAI_SCOPES = "openid profile email offline_access api.connectors.read api.connectors.invoke"
    const val OPENAI_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"

    // ---- Cursor ----
    const val CURSOR_CLIENT_ID = "KbZUR41cY7W6zRSdpSUJ7I7mLYBKOCmB"
    fun cursorLoginUrl(challenge: String, uuid: String) =
        "https://cursor.com/loginDeepControl?challenge=$challenge&uuid=$uuid&mode=login&redirectTarget=cli"
    const val CURSOR_POLL_URL = "https://api2.cursor.sh/auth/poll"
    const val CURSOR_TOKEN_URL = "https://api2.cursor.sh/oauth/token"
    const val CURSOR_USAGE_URL = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage"
}
