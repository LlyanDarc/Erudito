package com.example.erudito.domain.sync

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle

data class MergeResult(
    val article: Article,
    val hasContentChanged: Boolean
)

object ArticleSyncMerger {

    fun merge(
        local: Article,
        remote: WikipediaArticle,
        syncedAtMs: Long,
        resolvedPageId: Long? = local.wikipediaPageId
    ): MergeResult {
        val mergedTitle = remote.title.trim().takeIf { it.isNotEmpty() } ?: local.title
        val mergedUrl = remote.url.trim().takeIf { it.isNotEmpty() } ?: local.url
        val mergedDescription = remote.description?.trim()?.takeIf { it.isNotEmpty() }
            ?: local.description
        val mergedImageUrl = remote.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: local.imageUrl

        val remoteContentTrimmed = remote.content.trim()
        val remoteDescriptionTrimmed = remote.description?.trim()
        val isDescriptionOnlyFallback = remoteDescriptionTrimmed != null &&
            remoteContentTrimmed == remoteDescriptionTrimmed &&
            !local.content.isNullOrBlank() &&
            local.content.trim().length > remoteContentTrimmed.length

        val mergedContent = if (remoteContentTrimmed.isEmpty() || isDescriptionOnlyFallback) {
            local.content
        } else {
            remoteContentTrimmed
        }

        val hasContentChanged =
            mergedTitle != local.title ||
                mergedUrl != local.url ||
                mergedDescription != local.description ||
                mergedContent != local.content ||
                mergedImageUrl != local.imageUrl

        val updatedArticle = local.copy(
            title = mergedTitle,
            url = mergedUrl,
            description = mergedDescription,
            content = mergedContent,
            imageUrl = mergedImageUrl,
            lastUpdated = if (hasContentChanged) syncedAtMs else local.lastUpdated,
            wikipediaPageId = resolvedPageId ?: local.wikipediaPageId,
            lastSyncedAt = syncedAtMs
        )

        return MergeResult(
            article = updatedArticle,
            hasContentChanged = hasContentChanged
        )
    }
}
