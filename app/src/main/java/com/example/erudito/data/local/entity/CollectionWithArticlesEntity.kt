package com.example.erudito.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class CollectionWithArticlesEntity(
    @Embedded
    val collection: CollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = CollectionArticleCrossRef::class,
            parentColumn = "collectionId",
            entityColumn = "articleId"
        )
    )
    val articles: List<ArticleEntity>
)
