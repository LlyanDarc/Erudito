package com.example.erudito.data.mapper

import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.domain.model.Article

fun ArticleEntity.toDomain(): Article {
    return Article(
        id = id,
        title = title,
        url = url,
        description = description,
        content = content,
        imageUrl = imageUrl,
        lastUpdated = lastUpdated,
        wikipediaPageId = wikipediaPageId
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
        wikipediaPageId = wikipediaPageId
    )
}
