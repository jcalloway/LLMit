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
