package com.example.erudito.domain.mapper

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat
import com.example.erudito.domain.model.WikipediaArticle

fun WikipediaArticle.toDomainArticle(
    lastUpdated: Long = System.currentTimeMillis(),
    lastSyncedAt: Long? = null
): Article {
    return Article(
        id = 0L,
        title = title,
        url = url,
        description = description,
        content = content,
        imageUrl = imageUrl,
        lastUpdated = lastUpdated,
        wikipediaPageId = pageId,
        lastSyncedAt = lastSyncedAt,
        contentFormat = contentFormat,
        extractedFormulaCount = extractedFormulaCount.coerceAtLeast(0),
        unextractableFormulaCount = unextractableFormulaCount.coerceAtLeast(0)
    )
}

fun Article.toWikipediaArticle(fallbackPageId: Long = 0L): WikipediaArticle {
    val localExtracted = content?.trim()?.takeIf { it.isNotEmpty() }
    return WikipediaArticle(
        pageId = wikipediaPageId ?: fallbackPageId,
        title = title,
        description = description,
        content = localExtracted ?: description ?: "",
        imageUrl = imageUrl,
        url = url,
        hasExtractedContent = localExtracted != null,
        contentFormat = if (localExtracted != null) contentFormat else ArticleContentFormat.PLAIN_TEXT,
        extractedFormulaCount = if (localExtracted != null) extractedFormulaCount.coerceAtLeast(0) else 0,
        unextractableFormulaCount = if (localExtracted != null) unextractableFormulaCount.coerceAtLeast(0) else 0
    )
}
