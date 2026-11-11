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

    override suspend fun insertArticle(article: Article): Long {
        return articleDao.insertArticle(article.toEntity())
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
}
