package com.example.erudito.data.repository

import com.example.erudito.data.local.dao.ArticleDao
import com.example.erudito.data.local.entity.CollectionArticleCrossRef
import com.example.erudito.data.mapper.toDomain
import com.example.erudito.data.mapper.toEntity
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.repository.ArticleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArticleRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao
) : ArticleRepository {

    override fun getSavedArticles(): Flow<List<Article>> {
        return articleDao.getAllArticles().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getArticleById(id: Long): Article? {
        return articleDao.getArticleById(id)?.toDomain()
    }

    override fun observeArticleById(id: Long): Flow<Article?> {
        return articleDao.observeArticleById(id).map { it?.toDomain() }
    }

    override suspend fun insertArticle(article: Article): Long {
        return articleDao.insertArticle(article.toEntity())
    }

    override suspend fun updateArticle(article: Article) {
        articleDao.updateArticle(article.toEntity())
    }

    override suspend fun deleteArticle(id: Long) {
        articleDao.deleteArticleById(id)
    }

    override suspend fun addArticleToCollection(articleId: Long, collectionId: Long) {
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = collectionId, articleId = articleId))
    }

    override suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long) {
        articleDao.deleteCrossRef(collectionId = collectionId, articleId = articleId)
    }

    override fun getArticlesForCollection(collectionId: Long): Flow<List<Article>> {
        return articleDao.getArticlesForCollection(collectionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getArticleByWikipediaPageId(wikipediaPageId: Long): Article? {
        return articleDao.getArticleByWikipediaPageId(wikipediaPageId)?.toDomain()
    }

    override suspend fun getArticleByUrl(url: String): Article? {
        return articleDao.getArticleByUrl(url)?.toDomain()
    }

    override fun getCollectionIdsForArticle(articleId: Long): Flow<List<Long>> {
        return articleDao.getCollectionIdsForArticle(articleId)
    }

    override fun getCollectionIdsForWikipediaArticle(
        wikipediaPageId: Long,
        url: String
    ): Flow<List<Long>> {
        return articleDao.getCollectionIdsForWikipediaArticle(
            wikipediaPageId = wikipediaPageId,
            url = url
        )
    }

    override suspend fun saveArticleAndAssociateCollections(
        article: Article,
        collectionIds: List<Long>
    ): Long {
        return articleDao.saveArticleAndAssociateCollections(
            article = article.toEntity(),
            collectionIds = collectionIds
        )
    }

    override suspend fun addArticleToCollections(articleId: Long, collectionIds: List<Long>) {
        articleDao.addArticleToCollections(
            articleId = articleId,
            collectionIds = collectionIds
        )
    }
}
