package com.example.erudito.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.erudito.data.local.dao.ArticleDao
import com.example.erudito.data.local.dao.CollectionDao
import com.example.erudito.data.local.database.EruditoDatabase
import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.data.local.entity.CollectionArticleCrossRef
import com.example.erudito.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabaseIntegrationTest {

    private lateinit var database: EruditoDatabase
    private lateinit var collectionDao: CollectionDao
    private lateinit var articleDao: ArticleDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EruditoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        collectionDao = database.collectionDao()
        articleDao = database.articleDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndRetrieveCollection() = runTest {
        val collection = CollectionEntity(name = "História Medieval")
        val id = collectionDao.insertCollection(collection)

        val retrieved = collectionDao.getCollectionById(id)
        assertNotNull(retrieved)
        assertEquals("História Medieval", retrieved?.name)
    }

    @Test
    fun renameCollectionInDatabase() = runTest {
        val id = collectionDao.insertCollection(CollectionEntity(name = "Nome Antigo"))
        val existing = collectionDao.getCollectionById(id)
        assertNotNull(existing)

        collectionDao.updateCollection(existing!!.copy(name = "Nome Atualizado"))

        val updated = collectionDao.getCollectionById(id)
        assertEquals("Nome Atualizado", updated?.name)
    }

    @Test
    fun articleCanBelongToMultipleCollectionsPerRN03() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "História"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "Artes"))

        val artId = articleDao.insertArticle(
            ArticleEntity(
                title = "Renascimento",
                url = "https://pt.wikipedia.org/wiki/Renascimento",
                description = "Movimento cultural",
                content = "Conteúdo...",
                imageUrl = null
            )
        )

        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col1Id, articleId = artId))
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col2Id, articleId = artId))

        val col1Articles = articleDao.getArticlesForCollection(col1Id).first()
        val col2Articles = articleDao.getArticlesForCollection(col2Id).first()

        assertEquals(1, col1Articles.size)
        assertEquals(artId, col1Articles[0].id)
        assertEquals(1, col2Articles.size)
        assertEquals(artId, col2Articles[0].id)
    }

    @Test
    fun preventDuplicateArticleInSameCollectionPerRN04() = runTest {
        val colId = collectionDao.insertCollection(CollectionEntity(name = "Física"))
        val artId = articleDao.insertArticle(
            ArticleEntity(
                title = "Relatividade",
                url = "https://pt.wikipedia.org/wiki/Relatividade",
                description = "Teoria de Einstein",
                content = "Conteúdo...",
                imageUrl = null
            )
        )

        // Primeira inserção
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = colId, articleId = artId))
        // Tentativa de duplicidade (OnConflictStrategy.IGNORE)
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = colId, articleId = artId))

        val articles = articleDao.getArticlesForCollection(colId).first()
        assertEquals(1, articles.size)
    }

    @Test
    fun deletingCollectionCascadesCrossRefWithoutDeletingArticlePerRN05() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "Coleção 1"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "Coleção 2"))

        val artId = articleDao.insertArticle(
            ArticleEntity(
                title = "Filosofia Grega",
                url = "https://pt.wikipedia.org/wiki/Filosofia_grega",
                description = "Origens do pensamento",
                content = "Conteúdo...",
                imageUrl = null
            )
        )

        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col1Id, articleId = artId))
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col2Id, articleId = artId))

        // Exclui a Coleção 1
        collectionDao.deleteCollectionById(col1Id)

        // 1. Coleção 1 foi removida
        assertNull(collectionDao.getCollectionById(col1Id))

        // 2. Artigo ainda existe na base de dados
        val articleStillExists = articleDao.getArticleById(artId)
        assertNotNull(articleStillExists)

        // 3. Associação com Coleção 2 continua intacta
        val col2Articles = articleDao.getArticlesForCollection(col2Id).first()
        assertEquals(1, col2Articles.size)
        assertEquals(artId, col2Articles[0].id)

        // 4. Associação com Coleção 1 foi removida via CASCADE
        val col1Articles = articleDao.getArticlesForCollection(col1Id).first()
        assertTrue(col1Articles.isEmpty())
    }

    @Test
    fun getAllCollectionsWithCountComputesCorrectAggregate() = runTest {
        val colId = collectionDao.insertCollection(CollectionEntity(name = "Ciências Gerais"))
        val art1 = articleDao.insertArticle(
            ArticleEntity(title = "A1", url = "http://1", description = null, content = null, imageUrl = null)
        )
        val art2 = articleDao.insertArticle(
            ArticleEntity(title = "A2", url = "http://2", description = null, content = null, imageUrl = null)
        )

        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = colId, articleId = art1))
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = colId, articleId = art2))

        val listWithCount = collectionDao.getAllCollectionsWithCount().first()
        val found = listWithCount.find { it.collection.id == colId }

        assertNotNull(found)
        assertEquals(2, found?.articleCount)
    }
}
