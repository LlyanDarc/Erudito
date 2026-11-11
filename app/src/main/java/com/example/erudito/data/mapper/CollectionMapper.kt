package com.example.erudito.data.mapper

import com.example.erudito.data.local.entity.CollectionEntity
import com.example.erudito.data.local.entity.CollectionWithArticlesEntity
import com.example.erudito.data.local.entity.CollectionWithCount
import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles

fun CollectionEntity.toDomain(articleCount: Int = 0): Collection {
    return Collection(
        id = id,
        name = name,
        createdAt = createdAt,
        articleCount = articleCount
    )
}

fun CollectionWithCount.toDomain(): Collection {
    return Collection(
        id = collection.id,
        name = collection.name,
        createdAt = collection.createdAt,
        articleCount = articleCount
    )
}

fun Collection.toEntity(): CollectionEntity {
    return CollectionEntity(
        id = id,
        name = name,
        createdAt = createdAt
    )
}

fun CollectionWithArticlesEntity.toDomain(): CollectionWithArticles {
    return CollectionWithArticles(
        collection = collection.toDomain(articleCount = articles.size),
        articles = articles.map { it.toDomain() }
    )
}
