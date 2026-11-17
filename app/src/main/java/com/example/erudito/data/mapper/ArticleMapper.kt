package com.example.erudito.data.mapper

import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat

fun ArticleEntity.toDomain(): Article {
    val resolvedFormat = runCatching {
        ArticleContentFormat.valueOf(contentFormat)
    }.getOrDefault(ArticleContentFormat.PLAIN_TEXT)

    return Article(
        id = id,
        title = title,
        url = url,
        description = description,
        content = content,
        imageUrl = imageUrl,
        lastUpdated = lastUpdated,
        wikipediaPageId = wikipediaPageId,
        lastSyncedAt = lastSyncedAt,
        contentFormat = resolvedFormat,
        extractedFormulaCount = extractedFormulaCount.coerceAtLeast(0),
        unextractableFormulaCount = unextractableFormulaCount.coerceAtLeast(0)
    )
}

fun Article.toEntity(): ArticleEntity {
    return ArticleEntity(
        id = id,
        title = title,
        url = url,
        description = description,
        content = content,
        imageUrl = imageUrl,
        lastUpdated = lastUpdated,
        wikipediaPageId = wikipediaPageId,
        lastSyncedAt = lastSyncedAt,
        contentFormat = contentFormat.name,
        extractedFormulaCount = extractedFormulaCount.coerceAtLeast(0),
        unextractableFormulaCount = unextractableFormulaCount.coerceAtLeast(0)
    )
}
