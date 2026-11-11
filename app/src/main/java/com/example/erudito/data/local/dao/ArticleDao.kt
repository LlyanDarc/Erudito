package com.example.erudito.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.data.local.entity.CollectionArticleCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles ORDER BY lastUpdated DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Long): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>): List<Long>

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteArticleById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: CollectionArticleCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<CollectionArticleCrossRef>)

    @Query("DELETE FROM collection_article_cross_ref WHERE collectionId = :collectionId AND articleId = :articleId")
    suspend fun deleteCrossRef(collectionId: Long, articleId: Long)

    @Query(
        """
        SELECT a.* FROM articles a
        INNER JOIN collection_article_cross_ref cr ON a.id = cr.articleId
        WHERE cr.collectionId = :collectionId
        ORDER BY a.lastUpdated DESC
        """
    )
    fun getArticlesForCollection(collectionId: Long): Flow<List<ArticleEntity>>
}
