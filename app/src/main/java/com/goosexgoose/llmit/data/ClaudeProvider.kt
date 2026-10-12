package com.goosexgoose.llmit.data

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
        storage.clearClaudeCredentials()
        storage.authState = AuthState.NEEDS_LOGIN
    }
}
