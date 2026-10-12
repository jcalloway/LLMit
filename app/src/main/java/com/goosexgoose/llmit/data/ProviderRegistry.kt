package com.goosexgoose.llmit.data

import android.content.Context

object ProviderRegistry {
    // Immutable: init() used to mutate a plain list from several threads at cold start.
    private val providers: List<UsageProvider> = listOf(ClaudeProvider, OpenAiProvider, CursorProvider)

    fun all(): List<UsageProvider> = providers

    fun authenticated(context: Context): List<UsageProvider> =
        providers.filter { it.isAuthenticated(context) }

    fun get(serviceId: String): UsageProvider? =
        providers.find { it.serviceId == serviceId }

    fun init() {}
}
