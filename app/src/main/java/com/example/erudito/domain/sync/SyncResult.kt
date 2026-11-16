package com.example.erudito.domain.sync

import com.example.erudito.domain.model.Article

sealed class RefreshArticleOutcome {
    data class Updated(val article: Article) : RefreshArticleOutcome()
    data class Unchanged(val article: Article) : RefreshArticleOutcome()
    data class Skipped(val reason: SkipReason) : RefreshArticleOutcome()
    data class Failed(
        val message: String,
        val cause: Throwable? = null
    ) : RefreshArticleOutcome()
}

enum class SkipReason {
    ARTICLE_NOT_FOUND_LOCALLY,
    NO_WIKIPEDIA_PAGE_ID,
    RECENTLY_SYNCED,
    NO_CONNECTION
}

data class SyncArticleFailure(
    val articleId: Long,
    val title: String,
    val message: String
)

data class SyncResult(
    val totalEvaluated: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val failures: List<SyncArticleFailure> = emptyList(),
    val abortedDueToNoConnection: Boolean = false
) {
    val hasErrors: Boolean get() = failedCount > 0
    val isSuccess: Boolean get() = !abortedDueToNoConnection && failedCount == 0
}
