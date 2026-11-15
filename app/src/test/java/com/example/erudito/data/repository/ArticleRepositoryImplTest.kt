package com.example.erudito.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.erudito.data.local.dao.CollectionDao
import com.example.erudito.data.local.database.EruditoDatabase
import com.example.erudito.data.local.entity.CollectionEntity
import com.example.erudito.domain.model.Article
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArticleRepositoryImplTest {

    private lateinit var database: EruditoDatabase
    private lateinit var collectionDao: CollectionDao
    private lateinit var repository: ArticleRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EruditoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        collectionDao = database.collectionDao()
        repository = ArticleRepositoryImpl(database.articleDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saveArticleAndAssociateCollections persists article and queries work by pageId and url`() = runTest {
        val col1 = collectionDao.insertCollection(CollectionEntity(name = "Ciências"))
        val col2 = collectionDao.insertCollection(CollectionEntity(name = "História"))

        val article = Article(
            title = "Galileu Galilei",
            url = "https://pt.wikipedia.org/wiki/Galileu_Galilei",
            description = "Astrônomo italiano",
            content = "Conteúdo completo.",
            imageUrl = "https://upload.wikimedia.org/galileu.jpg",
            lastUpdated = 5000L,
            wikipediaPageId = 600L
        )

        val savedId = repository.saveArticleAndAssociateCollections(article, listOf(col1, col2))

        val byPageId = repository.getArticleByWikipediaPageId(600L)
        assertNotNull(byPageId)
        assertEquals(savedId, byPageId?.id)
        assertEquals(600L, byPageId?.wikipediaPageId)

        val byUrl = repository.getArticleByUrl("https://pt.wikipedia.org/wiki/Galileu_Galilei")
        assertNotNull(byUrl)
        assertEquals(savedId, byUrl?.id)

        assertEquals(
            setOf(col1, col2),
            repository.getCollectionIdsForArticle(savedId).first().toSet()
        )
        assertEquals(
            setOf(col1, col2),
            repository.getCollectionIdsForWikipediaArticle(
                wikipediaPageId = 600L,
                url = "https://pt.wikipedia.org/wiki/Galileu_Galilei"
            ).first().toSet()
        )
    }

    @Test
    fun `saveArticleAndAssociateCollections reuses legacy article by url and updates wikipediaPageId`() = runTest {
        val col1 = collectionDao.insertCollection(CollectionEntity(name = "Coleção Antiga"))
        val col2 = collectionDao.insertCollection(CollectionEntity(name = "Coleção Nova"))

        val legacyId = repository.insertArticle(
            Article(
                title = "Filosofia Grega",
                url = "https://pt.wikipedia.org/wiki/Filosofia_grega",
                description = "Antiga",
                content = "Conteúdo antigo",
                wikipediaPageId = null
            )
        )
        repository.addArticleToCollection(articleId = legacyId, collectionId = col1)

        // Fallback por URL antes mesmo de salvar com wikipediaPageId
        assertEquals(
            listOf(col1),
            repository.getCollectionIdsForWikipediaArticle(
                wikipediaPageId = 8080L,
                url = "https://pt.wikipedia.org/wiki/Filosofia_grega"
            ).first()
        )

        val updatedId = repository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Filosofia Grega",
                url = "https://pt.wikipedia.org/wiki/Filosofia_grega",
                description = "Atualizada",
                content = "Conteúdo completo da Wikipédia",
                imageUrl = "https://upload.wikimedia.org/filosofia.jpg",
                wikipediaPageId = 8080L
            ),
            collectionIds = listOf(col2)
        )

        assertEquals(legacyId, updatedId)
        assertEquals(1, repository.getSavedArticles().first().size)
        assertEquals(8080L, repository.getArticleById(legacyId)?.wikipediaPageId)
        assertEquals(
            setOf(col1, col2),
            repository.getCollectionIdsForArticle(legacyId).first().toSet()
        )
    }

    @Test
    fun `saveArticleAndAssociateCollections rolls back when collection does not exist`() = runTest {
        val col1 = collectionDao.insertCollection(CollectionEntity(name = "Existente"))

        try {
            repository.saveArticleAndAssociateCollections(
                article = Article(
                    title = "Incompleto",
                    url = "https://pt.wikipedia.org/wiki/Incompleto",
                    wikipediaPageId = 999L
                ),
                collectionIds = listOf(col1, 404L)
            )
            fail("Deveria lançar exceção para coleção inexistente")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        assertNull(repository.getArticleByWikipediaPageId(999L))
        assertEquals(0, repository.getSavedArticles().first().size)
        assertEquals(0, repository.getArticlesForCollection(col1).first().size)
    }

    @Test
    fun `addArticleToCollections associates existing article without duplicating or overwriting it`() = runTest {
        val col1 = collectionDao.insertCollection(CollectionEntity(name = "Coleção 1"))
        val col2 = collectionDao.insertCollection(CollectionEntity(name = "Coleção 2"))
        val col3 = collectionDao.insertCollection(CollectionEntity(name = "Coleção 3"))

        val articleId = repository.insertArticle(
            Article(
                title = "Aristóteles",
                url = "https://pt.wikipedia.org/wiki/Arist%C3%B3teles",
                description = "Filósofo grego",
                content = "Texto original",
                lastUpdated = 1234L,
                wikipediaPageId = 42L
            )
        )
        repository.addArticleToCollection(articleId = articleId, collectionId = col1)

        repository.addArticleToCollections(
            articleId = articleId,
            collectionIds = listOf(col1, col2, col3, col2)
        )

        assertEquals(1, repository.getSavedArticles().first().size)
        assertEquals(1234L, repository.getArticleById(articleId)?.lastUpdated)
        assertEquals(
            setOf(col1, col2, col3),
            repository.getCollectionIdsForArticle(articleId).first().toSet()
        )
    }

    @Test
    fun `addArticleToCollections rolls back atomically when any collection does not exist`() = runTest {
        val col1 = collectionDao.insertCollection(CollectionEntity(name = "Coleção 1"))
        val col2 = collectionDao.insertCollection(CollectionEntity(name = "Coleção 2"))
        val articleId = repository.insertArticle(
            Article(
                title = "Platão",
                url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o",
                wikipediaPageId = 43L
            )
        )
        repository.addArticleToCollection(articleId = articleId, collectionId = col1)

        try {
            repository.addArticleToCollections(
                articleId = articleId,
                collectionIds = listOf(col2, 9999L)
            )
            fail("Deveria lançar exceção para coleção inexistente")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        assertEquals(listOf(col1), repository.getCollectionIdsForArticle(articleId).first())
    }
}
