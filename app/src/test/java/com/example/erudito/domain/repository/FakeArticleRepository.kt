package com.example.erudito.domain.repository

import com.example.erudito.domain.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeArticleRepository(
    private val existingCollectionIds: Set<Long>? = null
) : ArticleRepository {
    private val articles = mutableListOf<Article>()
    private val articlesFlow = MutableStateFlow<List<Article>>(emptyList())
    private val collectionArticlesMap = mutableMapOf<Long, MutableList<Long>>() // collectionId -> list of articleId
    private val associationsVersion = MutableStateFlow(0)

    override fun getSavedArticles(): Flow<List<Article>> = articlesFlow

    override suspend fun getArticleById(id: Long): Article? {
        return articles.find { it.id == id }
    }

    override fun observeArticleById(id: Long): Flow<Article?> {
        return articlesFlow.map { list -> list.find { it.id == id } }
    }

    override suspend fun insertArticle(article: Article): Long {
        val newId = if (article.id != 0L) article.id else (articles.maxOfOrNull { it.id } ?: 0L) + 1L
        val saved = article.copy(id = newId)
        val existingIdx = articles.indexOfFirst { it.id == newId }
        if (existingIdx >= 0) {
            articles[existingIdx] = saved
        } else {
            articles.add(saved)
        }
        articlesFlow.value = articles.toList()
        return newId
    }

    override suspend fun updateArticle(article: Article) {
        val existingIdx = articles.indexOfFirst { it.id == article.id }
        if (existingIdx >= 0) {
            articles[existingIdx] = article
            articlesFlow.value = articles.toList()
        }
    }

    override suspend fun deleteArticle(id: Long) {
        articles.removeAll { it.id == id }
        collectionArticlesMap.values.forEach { it.remove(id) }
        articlesFlow.value = articles.toList()
        associationsVersion.value += 1
    }

    override suspend fun addArticleToCollection(articleId: Long, collectionId: Long) {
        val list = collectionArticlesMap.getOrPut(collectionId) { mutableListOf() }
        if (!list.contains(articleId)) {
            list.add(articleId)
            associationsVersion.value += 1
        }
    }

    override suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long) {
        if (collectionArticlesMap[collectionId]?.remove(articleId) == true) {
            associationsVersion.value += 1
        }
    }

    override fun getArticlesForCollection(collectionId: Long): Flow<List<Article>> {
        return kotlinx.coroutines.flow.combine(articlesFlow, associationsVersion) { all, _ ->
            val articleIds = collectionArticlesMap[collectionId] ?: emptyList()
            all.filter { it.id in articleIds }
        }
    }

    override suspend fun getArticleByWikipediaPageId(wikipediaPageId: Long): Article? {
        return articles.find { it.wikipediaPageId == wikipediaPageId }
    }

    override suspend fun getArticleByUrl(url: String): Article? {
        return articles.find { it.url == url }
    }

    override fun getCollectionIdsForArticle(articleId: Long): Flow<List<Long>> {
        return associationsVersion.map {
            collectionArticlesMap.entries
                .filter { (_, articleIds) -> articleId in articleIds }
                .map { it.key }
        }
    }

    override fun getCollectionIdsForWikipediaArticle(
        wikipediaPageId: Long,
        url: String
    ): Flow<List<Long>> {
        return kotlinx.coroutines.flow.combine(articlesFlow, associationsVersion) { all, _ ->
            val target = all.find { it.wikipediaPageId == wikipediaPageId }
                ?: all.find { it.wikipediaPageId == null && it.url == url }
            if (target == null) {
                emptyList()
            } else {
                collectionArticlesMap.entries
                    .filter { (_, articleIds) -> target.id in articleIds }
                    .map { it.key }
            }
        }
    }

    override suspend fun saveArticleAndAssociateCollections(
        article: Article,
        collectionIds: List<Long>
    ): Long {
        val distinctIds = collectionIds.distinct()
        if (existingCollectionIds != null) {
            val missing = distinctIds.filterNot { it in existingCollectionIds }
            if (missing.isNotEmpty()) {
                throw IllegalArgumentException("Coleção(ões) não encontrada(s): ${missing.joinToString()}")
            }
        }

        val existing = article.wikipediaPageId?.let { pageId ->
            articles.find { it.wikipediaPageId == pageId }
        } ?: articles.find { it.wikipediaPageId == null && it.url == article.url }
            ?: if (article.id > 0L) articles.find { it.id == article.id } else null

        val targetId = if (existing != null) {
            val idx = articles.indexOfFirst { it.id == existing.id }
            articles[idx] = article.copy(
                id = existing.id,
                wikipediaPageId = article.wikipediaPageId ?: existing.wikipediaPageId,
                lastSyncedAt = article.lastSyncedAt ?: existing.lastSyncedAt
            )
            articlesFlow.value = articles.toList()
            existing.id
        } else {
            insertArticle(article)
        }

        distinctIds.forEach { collectionId ->
            addArticleToCollection(targetId, collectionId)
        }
        return targetId
    }

    override suspend fun addArticleToCollections(articleId: Long, collectionIds: List<Long>) {
        if (articles.none { it.id == articleId }) {
            throw IllegalArgumentException("Artigo não encontrado: $articleId")
        }
        val distinctIds = collectionIds.distinct()
        if (existingCollectionIds != null) {
            val missing = distinctIds.filterNot { it in existingCollectionIds }
            if (missing.isNotEmpty()) {
                throw IllegalArgumentException("Coleção(ões) não encontrada(s): ${missing.joinToString()}")
            }
        }
        distinctIds.forEach { collectionId ->
            addArticleToCollection(articleId, collectionId)
        }
    }
}
