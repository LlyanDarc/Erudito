package com.example.erudito.domain.repository

import com.example.erudito.domain.model.Article
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    fun getSavedArticles(): Flow<List<Article>>
    suspend fun getArticleById(id: Long): Article?
    suspend fun insertArticle(article: Article): Long
    suspend fun deleteArticle(id: Long)
    suspend fun addArticleToCollection(articleId: Long, collectionId: Long)
    suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long)
    fun getArticlesForCollection(collectionId: Long): Flow<List<Article>>
}
