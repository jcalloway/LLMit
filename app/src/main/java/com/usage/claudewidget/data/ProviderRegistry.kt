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
