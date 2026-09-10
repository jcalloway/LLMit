package com.goosexgoose.llmit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
                Log.d("OpenAiFetch", "HTTP ${resp.code} body=${body.take(2000)}")
                when {
                    resp.code == 200 -> FetchResult.Success(
                        UsageSnapshot.parseOpenAi(body, System.currentTimeMillis())
                    )
                    resp.code == 401 || resp.code == 403 -> FetchResult.NeedsLogin
                    else -> FetchResult.Soft("openai-http-${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("OpenAiFetch", "Exception: ${e.message}")
            FetchResult.Soft(e.message ?: "openai-io")
        }
    }
}
