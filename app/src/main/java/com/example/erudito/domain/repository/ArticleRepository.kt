package com.example.erudito.domain.repository

import com.example.erudito.domain.model.Article
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    fun getSavedArticles(): Flow<List<Article>>
    suspend fun getArticleById(id: Long): Article?
    fun observeArticleById(id: Long): Flow<Article?>
    suspend fun insertArticle(article: Article): Long
    suspend fun updateArticle(article: Article)
    suspend fun deleteArticle(id: Long)
    suspend fun addArticleToCollection(articleId: Long, collectionId: Long)
    suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long)
    fun getArticlesForCollection(collectionId: Long): Flow<List<Article>>
    suspend fun getArticleByWikipediaPageId(wikipediaPageId: Long): Article?
    suspend fun getArticleByUrl(url: String): Article?
    fun getCollectionIdsForArticle(articleId: Long): Flow<List<Long>>
    fun getCollectionIdsForWikipediaArticle(wikipediaPageId: Long, url: String): Flow<List<Long>>
    suspend fun saveArticleAndAssociateCollections(article: Article, collectionIds: List<Long>): Long
    suspend fun addArticleToCollections(articleId: Long, collectionIds: List<Long>)
}
