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
                android.util.Log.d("CursorFetch", "HTTP ${resp.code} body=${body.take(2000)}")
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
