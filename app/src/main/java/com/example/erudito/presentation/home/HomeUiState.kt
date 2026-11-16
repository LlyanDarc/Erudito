package com.example.erudito.presentation.home

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.Collection

sealed interface HomeSyncStatus {
    data object Idle : HomeSyncStatus
    data object Syncing : HomeSyncStatus
    data class CompletedWithUpdates(
        val updatedCount: Int,
        val unchangedCount: Int = 0
    ) : HomeSyncStatus
    data class CompletedNoChanges(
        val checkedCount: Int
    ) : HomeSyncStatus
    data class PartialFailure(
        val updatedCount: Int,
        val unchangedCount: Int,
        val failedCount: Int
    ) : HomeSyncStatus
    data class Failed(
        val failedCount: Int
    ) : HomeSyncStatus
    data object Offline : HomeSyncStatus
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val collections: List<Collection> = emptyList(),
    val recentArticles: List<Article> = emptyList(),
    val totalCollectionsCount: Int = 0,
    val totalSavedArticlesCount: Int = 0,
    val isOnline: Boolean = true,
    val syncStatus: HomeSyncStatus = HomeSyncStatus.Idle,
    val error: String? = null
) {
    val isSyncing: Boolean get() = syncStatus is HomeSyncStatus.Syncing
}
