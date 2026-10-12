package com.goosexgoose.llmit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Log
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

    fun generateState(): String =
        ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun buildAuthUrl(challenge: String, state: String): String = buildString {
        append(Const.OPENAI_AUTH_URL)
        append("?client_id=").append(Const.OPENAI_CLIENT_ID)
        append("&redirect_uri=").append(java.net.URLEncoder.encode(Const.OPENAI_REDIRECT_URI, "UTF-8"))
        append("&response_type=code")
        append("&scope=").append(java.net.URLEncoder.encode(Const.OPENAI_SCOPES, "UTF-8"))
        append("&code_challenge=").append(challenge)
        append("&code_challenge_method=S256")
        append("&state=").append(java.net.URLEncoder.encode(state, "UTF-8"))
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
                    accountIdFromIdToken(json.optString("id_token"))?.let { storage.openaiAccountId = it }
                    storage.setServiceAuthState(serviceId, AuthState.OK)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

    /** chatgpt_account_id from the id_token JWT, so Team users get their workspace's limits. */
    private fun accountIdFromIdToken(idToken: String): String? = try {
        val payload = idToken.split(".").getOrNull(1) ?: return null
        val claims = JSONObject(String(Base64.getUrlDecoder().decode(payload)))
        claims.optJSONObject("https://api.openai.com/auth")
            ?.optString("chatgpt_account_id")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun refreshToken(storage: Storage): TokenRefresh {
        val rt = storage.openaiRefreshToken ?: return TokenRefresh.REJECTED
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", Const.OPENAI_CLIENT_ID)
            .add("refresh_token", rt)
            .build()
        val req = Request.Builder().url(Const.OPENAI_TOKEN_URL).post(body).build()
        return try {
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    200 -> {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val access = json.optString("access_token")
                        // A blank token would read as "not signed in" and silently drop Codex.
                        if (access.isBlank()) return TokenRefresh.FAILED
                        storage.openaiAccessToken = access
                        json.optString("refresh_token").takeIf { it.isNotBlank() }
                            ?.let { storage.openaiRefreshToken = it }
                        accountIdFromIdToken(json.optString("id_token"))?.let { storage.openaiAccountId = it }
                        TokenRefresh.OK
                    }
                    400, 401 -> TokenRefresh.REJECTED
                    else -> TokenRefresh.FAILED
                }
            }
        } catch (_: Exception) {
            TokenRefresh.FAILED
        }
    }

    // One refresh at a time: OpenAI rotates refresh tokens, so two concurrent exchanges can
    // burn the token and log the user out. A waiter re-fetches with the already-refreshed token.
    private val refreshLock = Mutex()

    override suspend fun refresh(context: Context): FetchResult = withContext(Dispatchers.IO) {
        val storage = Storage.get(context)
        if (!isAuthenticated(context)) return@withContext FetchResult.NeedsLogin

        val result = refreshLock.withLock {
            val first = fetchUsage(storage)
            if (first !is FetchResult.NeedsLogin) first
            else when (refreshToken(storage)) {
                TokenRefresh.OK -> fetchUsage(storage)
                TokenRefresh.REJECTED -> FetchResult.NeedsLogin
                TokenRefresh.FAILED -> FetchResult.Soft("openai-refresh-failed")
            }
        }

        if (result !is FetchResult.Soft) {
            storage.setServiceAuthState(serviceId,
                if (result is FetchResult.NeedsLogin) AuthState.NEEDS_LOGIN else AuthState.OK)
        }
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
                Log.d("OpenAiFetch", "HTTP ${resp.code} body=${body.take(2000)}")
                when {
                    resp.code == 200 -> FetchResult.Success(
                        UsageSnapshot.parseOpenAi(body, System.currentTimeMillis())
                    )
                    resp.code == 401 -> FetchResult.NeedsLogin
                    resp.code == 403 && !isHtml(resp.header("Content-Type")) -> FetchResult.NeedsLogin
                    else -> FetchResult.Soft("openai-http-${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("OpenAiFetch", "Exception: ${e.message}")
            FetchResult.Soft(e.message ?: "openai-io")
        }
    }
}
