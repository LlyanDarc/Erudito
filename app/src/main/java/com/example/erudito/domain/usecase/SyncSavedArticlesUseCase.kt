package com.example.erudito.domain.usecase

import com.example.erudito.domain.connectivity.ConnectivityObserver
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.sync.RefreshArticleOutcome
import com.example.erudito.domain.sync.SyncArticleFailure
import com.example.erudito.domain.sync.SyncResult
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

class SyncSavedArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val refreshSavedArticleUseCase: RefreshSavedArticleUseCase,
    private val connectivityObserver: ConnectivityObserver
) {
    suspend operator fun invoke(
        forceRefresh: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): SyncResult {
        if (!connectivityObserver.isCurrentlyOnline()) {
            return SyncResult(abortedDueToNoConnection = true)
        }

        val savedArticles = articleRepository.getSavedArticles().first()
        var updatedCount = 0
        var unchangedCount = 0
        var skippedCount = 0
        val failures = mutableListOf<SyncArticleFailure>()

        for (article in savedArticles) {
            currentCoroutineContext().ensureActive()
            when (
                val outcome = refreshSavedArticleUseCase(
                    articleId = article.id,
                    forceRefresh = forceRefresh,
                    nowMs = nowMs
                )
            ) {
                is RefreshArticleOutcome.Updated -> updatedCount++
                is RefreshArticleOutcome.Unchanged -> unchangedCount++
                is RefreshArticleOutcome.Skipped -> skippedCount++
                is RefreshArticleOutcome.Failed -> {
                    failures.add(
                        SyncArticleFailure(
                            articleId = article.id,
                            title = article.title,
                            message = outcome.message
                        )
                    )
                }
            }
        }

        return SyncResult(
            totalEvaluated = savedArticles.size,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            skippedCount = skippedCount,
            failedCount = failures.size,
            failures = failures,
            abortedDueToNoConnection = false
        )
    }
}
