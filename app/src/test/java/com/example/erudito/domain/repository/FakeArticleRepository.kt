package com.example.erudito.domain.repository

import com.example.erudito.domain.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeArticleRepository : ArticleRepository {
    private val articles = mutableListOf<Article>()
    private val articlesFlow = MutableStateFlow<List<Article>>(emptyList())
    private val collectionArticlesMap = mutableMapOf<Long, MutableList<Long>>() // collectionId -> list of articleId

    override fun getSavedArticles(): Flow<List<Article>> = articlesFlow

    override suspend fun getArticleById(id: Long): Article? {
        return articles.find { it.id == id }
    }

    override suspend fun insertArticle(article: Article): Long {
        val newId = if (article.id != 0L) article.id else (articles.maxOfOrNull { it.id } ?: 0L) + 1L
        val saved = article.copy(id = newId)
        articles.add(saved)
        articlesFlow.value = articles.toList()
        return newId
    }

    override suspend fun deleteArticle(id: Long) {
        articles.removeAll { it.id == id }
        articlesFlow.value = articles.toList()
    }

    override suspend fun addArticleToCollection(articleId: Long, collectionId: Long) {
        val list = collectionArticlesMap.getOrPut(collectionId) { mutableListOf() }
        if (!list.contains(articleId)) {
            list.add(articleId)
        }
    }

    override suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long) {
        collectionArticlesMap[collectionId]?.remove(articleId)
    }

    override fun getArticlesForCollection(collectionId: Long): Flow<List<Article>> {
        return articlesFlow.map { all ->
            val articleIds = collectionArticlesMap[collectionId] ?: emptyList()
            all.filter { it.id in articleIds }
        }
    }
}
