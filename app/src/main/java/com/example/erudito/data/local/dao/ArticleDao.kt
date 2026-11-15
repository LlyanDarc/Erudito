package com.example.erudito.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.data.local.entity.CollectionArticleCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ArticleDao {

    @Query("SELECT * FROM articles ORDER BY lastUpdated DESC")
    abstract fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT COUNT(*) FROM articles")
    abstract suspend fun getArticleCount(): Int

    @Query("SELECT * FROM articles WHERE id = :id")
    abstract suspend fun getArticleById(id: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE wikipediaPageId = :wikipediaPageId LIMIT 1")
    abstract suspend fun getArticleByWikipediaPageId(wikipediaPageId: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE url = :url LIMIT 1")
    abstract suspend fun getArticleByUrl(url: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE url = :url AND wikipediaPageId IS NULL LIMIT 1")
    abstract suspend fun getLegacyArticleByUrl(url: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertArticle(article: ArticleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertArticles(articles: List<ArticleEntity>): List<Long>

    @Update
    abstract suspend fun updateArticle(article: ArticleEntity)

    @Query("DELETE FROM articles WHERE id = :id")
    abstract suspend fun deleteArticleById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCrossRef(crossRef: CollectionArticleCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCrossRefs(crossRefs: List<CollectionArticleCrossRef>)

    @Query("DELETE FROM collection_article_cross_ref WHERE collectionId = :collectionId AND articleId = :articleId")
    abstract suspend fun deleteCrossRef(collectionId: Long, articleId: Long)

    @Query(
        """
        SELECT a.* FROM articles a
        INNER JOIN collection_article_cross_ref cr ON a.id = cr.articleId
        WHERE cr.collectionId = :collectionId
        ORDER BY a.lastUpdated DESC
        """
    )
    abstract fun getArticlesForCollection(collectionId: Long): Flow<List<ArticleEntity>>

    @Query("SELECT collectionId FROM collection_article_cross_ref WHERE articleId = :articleId")
    abstract fun getCollectionIdsForArticle(articleId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT DISTINCT cr.collectionId
        FROM collection_article_cross_ref cr
        INNER JOIN articles a ON cr.articleId = a.id
        WHERE a.id = (
            SELECT id FROM articles
            WHERE wikipediaPageId = :wikipediaPageId
               OR (wikipediaPageId IS NULL AND url = :url)
            ORDER BY CASE WHEN wikipediaPageId = :wikipediaPageId THEN 0 ELSE 1 END
            LIMIT 1
        )
        """
    )
    abstract fun getCollectionIdsForWikipediaArticle(
        wikipediaPageId: Long,
        url: String
    ): Flow<List<Long>>

    @Query("SELECT id FROM collections WHERE id IN (:collectionIds)")
    abstract suspend fun getExistingCollectionIds(collectionIds: List<Long>): List<Long>

    @Transaction
    open suspend fun saveArticleAndAssociateCollections(
        article: ArticleEntity,
        collectionIds: List<Long>
    ): Long {
        val existing = article.wikipediaPageId?.let { getArticleByWikipediaPageId(it) }
            ?: getLegacyArticleByUrl(article.url)
            ?: if (article.id > 0L) getArticleById(article.id) else null

        val targetArticleId = if (existing != null) {
            val updated = article.copy(
                id = existing.id,
                wikipediaPageId = article.wikipediaPageId ?: existing.wikipediaPageId
            )
            updateArticle(updated)
            existing.id
        } else {
            insertArticle(article)
        }

        val distinctCollectionIds = collectionIds.distinct()
        if (distinctCollectionIds.isNotEmpty()) {
            val existingCollectionIds = getExistingCollectionIds(distinctCollectionIds).toSet()
            val missingCollectionIds = distinctCollectionIds.filterNot { it in existingCollectionIds }
            if (missingCollectionIds.isNotEmpty()) {
                throw IllegalArgumentException(
                    "Coleção(ões) não encontrada(s): ${missingCollectionIds.joinToString()}"
                )
            }

            distinctCollectionIds.forEach { collectionId ->
                insertCrossRef(
                    CollectionArticleCrossRef(
                        collectionId = collectionId,
                        articleId = targetArticleId
                    )
                )
            }
        }

        return targetArticleId
    }

    @Transaction
    open suspend fun addArticleToCollections(
        articleId: Long,
        collectionIds: List<Long>
    ) {
        val existingArticle = getArticleById(articleId)
            ?: throw IllegalArgumentException("Artigo não encontrado: $articleId")

        val distinctCollectionIds = collectionIds.distinct()
        if (distinctCollectionIds.isEmpty()) return

        val existingCollectionIds = getExistingCollectionIds(distinctCollectionIds).toSet()
        val missingCollectionIds = distinctCollectionIds.filterNot { it in existingCollectionIds }
        if (missingCollectionIds.isNotEmpty()) {
            throw IllegalArgumentException(
                "Coleção(ões) não encontrada(s): ${missingCollectionIds.joinToString()}"
            )
        }

        distinctCollectionIds.forEach { collectionId ->
            insertCrossRef(
                CollectionArticleCrossRef(
                    collectionId = collectionId,
                    articleId = existingArticle.id
                )
            )
        }
    }
}
