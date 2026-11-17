package com.example.erudito.data.mapper

import com.example.erudito.data.remote.dto.WikipediaApiResponseDto
import com.example.erudito.data.remote.dto.WikipediaPageDto
import com.example.erudito.data.remote.parser.SanitizedWikipediaHtml
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaSearchResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun WikipediaApiResponseDto.toSearchResults(): List<WikipediaSearchResult> {
    val pages = query?.pages ?: return emptyList()
    return pages
        .sortedBy { it.index ?: Int.MAX_VALUE }
        .mapNotNull { it.toSearchResultOrNull() }
}

fun WikipediaPageDto.toSearchResultOrNull(): WikipediaSearchResult? {
    if (missing == true) return null
    val id = pageId?.takeIf { it > 0L } ?: return null
    val safeTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val safeDescription = description?.trim()?.takeIf { it.isNotEmpty() }
    val safeSnippet = extract?.trim()?.takeIf { it.isNotEmpty() }
    val safeThumbnailUrl = (thumbnail?.source ?: original?.source)?.trim()?.takeIf { it.isNotEmpty() }
    val safeUrl = (fullUrl ?: canonicalUrl)?.trim()?.takeIf { it.isNotEmpty() }
        ?: buildWikipediaArticleUrl(safeTitle)

    return WikipediaSearchResult(
        pageId = id,
        title = safeTitle,
        description = safeDescription,
        snippet = safeSnippet,
        thumbnailUrl = safeThumbnailUrl,
        url = safeUrl
    )
}

fun WikipediaApiResponseDto.toWikipediaArticleOrNull(): WikipediaArticle? {
    val page = query?.pages?.firstOrNull() ?: return null
    return page.toWikipediaArticleOrNull()
}

fun WikipediaPageDto.toWikipediaArticleOrNull(): WikipediaArticle? {
    if (missing == true) return null
    val id = pageId?.takeIf { it > 0L } ?: return null
    val safeTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val safeDescription = description?.trim()?.takeIf { it.isNotEmpty() }
    val extractedContent = extract?.trim()?.takeIf { it.isNotEmpty() }
    val safeContent = extractedContent
        ?: safeDescription
        ?: ""
    val safeImageUrl = (original?.source ?: thumbnail?.source)?.trim()?.takeIf { it.isNotEmpty() }
    val safeUrl = (fullUrl ?: canonicalUrl)?.trim()?.takeIf { it.isNotEmpty() }
        ?: buildWikipediaArticleUrl(safeTitle)

    return WikipediaArticle(
        pageId = id,
        title = safeTitle,
        description = safeDescription,
        content = safeContent,
        imageUrl = safeImageUrl,
        url = safeUrl,
        hasExtractedContent = extractedContent != null,
        contentFormat = ArticleContentFormat.PLAIN_TEXT
    )
}

fun WikipediaPageDto.toStructuredWikipediaArticleOrNull(
    sanitizedHtml: SanitizedWikipediaHtml
): WikipediaArticle? {
    if (missing == true) return null
    val id = pageId?.takeIf { it > 0L } ?: return null
    val safeTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val safeHtml = sanitizedHtml.html.trim().takeIf { it.isNotEmpty() } ?: return null
    val safeDescription = description?.trim()?.takeIf { it.isNotEmpty() }
    val safeImageUrl = (original?.source ?: thumbnail?.source)?.trim()?.takeIf { it.isNotEmpty() }
    val safeUrl = (fullUrl ?: canonicalUrl)?.trim()?.takeIf { it.isNotEmpty() }
        ?: buildWikipediaArticleUrl(safeTitle)

    return WikipediaArticle(
        pageId = id,
        title = safeTitle,
        description = safeDescription,
        content = safeHtml,
        imageUrl = safeImageUrl,
        url = safeUrl,
        hasExtractedContent = true,
        contentFormat = ArticleContentFormat.STRUCTURED_HTML,
        extractedFormulaCount = sanitizedHtml.extractedFormulaCount,
        unextractableFormulaCount = sanitizedHtml.unextractableFormulaCount
    )
}

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

internal fun buildWikipediaArticleUrl(title: String): String {
    val encodedTitle = URLEncoder.encode(
        title.trim().replace(' ', '_'),
        StandardCharsets.UTF_8.name()
    )
    return "https://pt.wikipedia.org/wiki/$encodedTitle"
}
