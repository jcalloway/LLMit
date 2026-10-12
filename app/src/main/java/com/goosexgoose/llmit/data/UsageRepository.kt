package com.goosexgoose.llmit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

sealed interface FetchResult {
    data class Success(val snapshot: UsageSnapshot) : FetchResult
    /** sessionKey is dead; user must log in again. */
    data object NeedsLogin : FetchResult
    /** Transient (no network, Cloudflare unresolved, server error). Keep last snapshot. */
    data class Soft(val reason: String) : FetchResult
}

/** Outcome of an OAuth refresh-token exchange: only REJECTED means the user must sign in again. */
enum class TokenRefresh { OK, REJECTED, FAILED }

/** A 403 served as HTML is a Cloudflare challenge page, not an auth rejection. */
internal fun isHtml(contentType: String?): Boolean = contentType?.contains("text/html", ignoreCase = true) == true

class UsageRepository(private val context: Context) {

    private val storage = Storage.get(context)
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false) // a 302 to /login means the session is dead
        .build()

    suspend fun refresh(): FetchResult = withContext(Dispatchers.IO) {
        if (!storage.isLoggedIn) return@withContext FetchResult.NeedsLogin

        val org = storage.orgId?.also {
            if (storage.claudeTier == null) discoverOrg()
        } ?: when (val o = discoverOrg()) {
            null -> return@withContext FetchResult.Soft("org-unknown")
            else -> o.also { storage.orgId = it }
        }

        when (val r = getUsage(org)) {
            is FetchResult.Soft -> {
                // Could be an expired cf_clearance: try one silent WebView re-solve.
                if (r.reason == "cloudflare") {
                    val resolved = CloudflareResolver.resolve(context, storage)
                    if (resolved) getUsage(org) else FetchResult.Soft("cloudflare-unresolved")
                } else r
            }
            else -> r
        }.also { result ->
            storage.authState =
                if (result is FetchResult.NeedsLogin) AuthState.NEEDS_LOGIN else AuthState.OK
            if (result is FetchResult.Success) storage.saveSnapshot(result.snapshot)
        }
    }

    private fun getUsage(org: String): FetchResult {
        val req = buildRequest(Const.usageUrl(org)) ?: return FetchResult.NeedsLogin
        return try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Log.d("ClaudeFetch", "HTTP ${resp.code} body=${body.take(2000)}")
                when {
                    resp.code == 200 -> FetchResult.Success(
                        UsageSnapshot.parse(body, System.currentTimeMillis())
                    )
                    resp.code == 401 || resp.isRedirect -> FetchResult.NeedsLogin
                    resp.code == 403 || resp.code == 503 || body.contains("Just a moment") ->
                        FetchResult.Soft("cloudflare")
                    else -> FetchResult.Soft("http-${resp.code}")
                }
            }
        } catch (e: Exception) {
            FetchResult.Soft(e.message ?: "io")
        }
    }

    private fun discoverOrg(): String? {
        val req = buildRequest(Const.ORGS_URL) ?: return null
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.code != 200) return null
                val raw = resp.body?.string().orEmpty()
                Log.d("ClaudeOrgs", "body=${raw.take(3000)}")
                val arr = JSONArray(raw)
                if (arr.length() == 0) return null
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val caps = o.optJSONArray("capabilities")
                    val isChat = caps != null && (0 until caps.length()).any {
                        caps.optString(it).contains("chat", true) ||
                            caps.optString(it).contains("claude_ai", true)
                    }
                    if (isChat) {
                        storage.claudeTier = parseTierFromCaps(caps)
                        return o.optString("uuid").ifBlank { o.optString("id") }
                    }
                }
                val first = arr.getJSONObject(0)
                storage.claudeTier = parseTierFromCaps(first.optJSONArray("capabilities"))
                first.let { it.optString("uuid").ifBlank { it.optString("id") } }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTierFromCaps(caps: org.json.JSONArray?): String {
        if (caps == null) return "Free"
        for (i in 0 until caps.length()) {
            val c = caps.optString(i, "")
            if (c.startsWith("claude_max")) return "Max"
            if (c.startsWith("claude_pro")) return "Pro"
            if (c.startsWith("claude_team")) return "Team"
            if (c.startsWith("claude_enterprise")) return "Enterprise"
        }
        return "Free"
    }

    private fun buildRequest(url: String): Request? {
        val session = storage.sessionKey ?: return null
        val cf = storage.cfClearance
        val cookie = buildString {
            append("${Const.COOKIE_SESSION}=$session")
            if (!cf.isNullOrBlank()) append("; ${Const.COOKIE_CF}=$cf")
        }
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("Cookie", cookie)
        storage.userAgent?.let { builder.header("User-Agent", it) }
        return builder.get().build()
    }
}
