package com.example.erudito.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Tabela intermediária de relacionamento N:N entre coleções e artigos.
 * Regras:
 * RN03: Um artigo pode pertencer a várias coleções simultaneamente.
 * RN04: Um artigo não pode ser associado mais de uma vez à mesma coleção (chave primária composta).
 * RN05: Ao excluir uma coleção, suas associações são removidas via CASCADE sem excluir o artigo.
 */
@Entity(
    tableName = "collection_article_cross_ref",
    primaryKeys = ["collectionId", "articleId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("collectionId"),
        Index("articleId")
    ]
)
data class CollectionArticleCrossRef(
    val collectionId: Long,
    val articleId: Long
)
