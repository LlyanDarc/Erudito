package com.example.erudito.domain.sync

import com.example.erudito.domain.model.Article

data class SyncPolicy(
    val minimumRefreshIntervalMs: Long = DEFAULT_MINIMUM_REFRESH_INTERVAL_MS
) {
    init {
        require(minimumRefreshIntervalMs >= 0L) {
            "O intervalo mínimo de sincronização não pode ser negativo."
        }
    }

    fun isEligibleForSync(
        article: Article,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val pageId = article.wikipediaPageId
        if (pageId == null || pageId <= 0L) return false

        val lastSyncedAt = article.lastSyncedAt ?: return true
        return (nowMs - lastSyncedAt) >= minimumRefreshIntervalMs
    }

    companion object {
        const val DEFAULT_MINIMUM_REFRESH_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L
    }
}
