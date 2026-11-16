package com.example.erudito.domain.usecase

import com.example.erudito.domain.connectivity.ConnectivityObserver
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.sync.ArticleSyncMerger
import com.example.erudito.domain.sync.RefreshArticleOutcome
import com.example.erudito.domain.sync.SkipReason
import com.example.erudito.domain.sync.SyncPolicy
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class RefreshSavedArticleUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val wikipediaRepository: WikipediaRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val syncPolicy: SyncPolicy
) {
    suspend operator fun invoke(
        articleId: Long,
        forceRefresh: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): RefreshArticleOutcome {
        if (articleId <= 0L) {
            return RefreshArticleOutcome.Skipped(SkipReason.ARTICLE_NOT_FOUND_LOCALLY)
        }

        val localArticle = articleRepository.getArticleById(articleId)
            ?: return RefreshArticleOutcome.Skipped(SkipReason.ARTICLE_NOT_FOUND_LOCALLY)

        val pageId = localArticle.wikipediaPageId
        if (pageId == null || pageId <= 0L) {
            return RefreshArticleOutcome.Skipped(SkipReason.NO_WIKIPEDIA_PAGE_ID)
        }

        if (!forceRefresh && !syncPolicy.isEligibleForSync(localArticle, nowMs)) {
            return RefreshArticleOutcome.Skipped(SkipReason.RECENTLY_SYNCED)
        }

        if (!connectivityObserver.isCurrentlyOnline()) {
            return RefreshArticleOutcome.Skipped(SkipReason.NO_CONNECTION)
        }

        return try {
            val remoteArticle = wikipediaRepository.getArticleByPageId(pageId)
                ?: return RefreshArticleOutcome.Failed(ARTICLE_NOT_FOUND_REMOTELY_MESSAGE)

            val resolvedPageId = if (remoteArticle.pageId > 0L && remoteArticle.pageId != pageId) {
                val conflicting = articleRepository.getArticleByWikipediaPageId(remoteArticle.pageId)
                if (conflicting == null || conflicting.id == localArticle.id) {
                    remoteArticle.pageId
                } else {
                    pageId
                }
            } else {
                pageId
            }

            val mergeResult = ArticleSyncMerger.merge(
                local = localArticle,
                remote = remoteArticle,
                syncedAtMs = nowMs,
                resolvedPageId = resolvedPageId
            )

            articleRepository.updateArticle(mergeResult.article)

            if (mergeResult.hasContentChanged) {
                RefreshArticleOutcome.Updated(mergeResult.article)
            } else {
                RefreshArticleOutcome.Unchanged(mergeResult.article)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WikipediaException) {
            RefreshArticleOutcome.Failed(e.message, e)
        } catch (e: Exception) {
            RefreshArticleOutcome.Failed(
                message = e.message?.takeIf { it.isNotBlank() } ?: GENERIC_SYNC_ERROR_MESSAGE,
                cause = e
            )
        }
    }

    private companion object {
        const val ARTICLE_NOT_FOUND_REMOTELY_MESSAGE = "Artigo não encontrado na Wikipédia."
        const val GENERIC_SYNC_ERROR_MESSAGE = "Não foi possível sincronizar o artigo."
    }
}
