package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.repository.FakeCollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ArticleCollectionIntegrationUseCasesTest {

    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var collectionRepository: FakeCollectionRepository
    private lateinit var saveWikipediaArticleUseCase: SaveWikipediaArticleToCollectionsUseCase
    private lateinit var addArticleToCollectionsUseCase: AddArticleToCollectionsUseCase
    private lateinit var removeArticleFromCollectionUseCase: RemoveArticleFromCollectionUseCase
    private lateinit var getCollectionIdsForArticleUseCase: GetCollectionIdsForArticleUseCase

    @Before
    fun setUp() {
        articleRepository = FakeArticleRepository()
        collectionRepository = FakeCollectionRepository()
        saveWikipediaArticleUseCase = SaveWikipediaArticleToCollectionsUseCase(
            articleRepository = articleRepository,
            collectionRepository = collectionRepository
        )
        addArticleToCollectionsUseCase = AddArticleToCollectionsUseCase(
            articleRepository = articleRepository,
            collectionRepository = collectionRepository
        )
        removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(
            articleRepository = articleRepository
        )
        getCollectionIdsForArticleUseCase = GetCollectionIdsForArticleUseCase(
            articleRepository = articleRepository
        )
    }

    @Test
    fun `saveWikipediaArticle persists article maps fields and links multiple collections`() = runTest {
        val col1 = collectionRepository.createCollection("Filosofia")
        val col2 = collectionRepository.createCollection("Grécia Antiga")
        val wikiArticle = sampleWikipediaArticle(pageId = 700L, title = "Aristóteles")

        val savedId = saveWikipediaArticleUseCase(wikiArticle, listOf(col1, col2, col1))

        assertTrue(savedId > 0L)
        val stored = articleRepository.getArticleById(savedId)
        assertNotNull(stored)
        assertEquals(700L, stored?.wikipediaPageId)
        assertEquals("Aristóteles", stored?.title)
        assertEquals(wikiArticle.url, stored?.url)
        assertEquals(wikiArticle.description, stored?.description)
        assertEquals(wikiArticle.content, stored?.content)
        assertEquals(wikiArticle.imageUrl, stored?.imageUrl)

        val linkedCollections = getCollectionIdsForArticleUseCase(savedId).first()
        assertEquals(setOf(col1, col2), linkedCollections.toSet())
        assertEquals(1, articleRepository.getSavedArticles().first().size)
    }

    @Test
    fun `saveWikipediaArticle reuses existing article without duplicating record or links`() = runTest {
        val col1 = collectionRepository.createCollection("Ciências")
        val col2 = collectionRepository.createCollection("Física")
        val initialWiki = sampleWikipediaArticle(pageId = 800L, title = "Relatividade")

        val firstId = saveWikipediaArticleUseCase(initialWiki, listOf(col1))
        val updatedWiki = initialWiki.copy(
            description = "Teoria física formulada por Einstein",
            content = "Conteúdo atualizado da Wikipédia"
        )

        val secondId = saveWikipediaArticleUseCase(updatedWiki, listOf(col1, col2))

        assertEquals(firstId, secondId)
        assertEquals(1, articleRepository.getSavedArticles().first().size)
        assertEquals("Conteúdo atualizado da Wikipédia", articleRepository.getArticleById(firstId)?.content)
        assertEquals(
            setOf(col1, col2),
            getCollectionIdsForArticleUseCase(updatedWiki).first().toSet()
        )
    }

    @Test
    fun `saveWikipediaArticle rejects empty collection selection`() = runTest {
        val wikiArticle = sampleWikipediaArticle(pageId = 10L, title = "Sócrates")

        try {
            saveWikipediaArticleUseCase(wikiArticle, emptyList())
            fail("Deveria rejeitar seleção vazia de coleções")
        } catch (e: IllegalArgumentException) {
            assertEquals("Selecione pelo menos uma coleção para salvar o artigo.", e.message)
        }

        assertTrue(articleRepository.getSavedArticles().first().isEmpty())
    }

    @Test
    fun `saveWikipediaArticle rejects invalid or non-existent collection IDs`() = runTest {
        val validCol = collectionRepository.createCollection("Existente")
        val wikiArticle = sampleWikipediaArticle(pageId = 11L, title = "Platão")

        try {
            saveWikipediaArticleUseCase(wikiArticle, listOf(0L))
            fail("Deveria rejeitar ID de coleção inválido")
        } catch (e: IllegalArgumentException) {
            assertEquals("Identificador de coleção inválido.", e.message)
        }

        try {
            saveWikipediaArticleUseCase(wikiArticle, listOf(validCol, 999L))
            fail("Deveria rejeitar coleção inexistente")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("999") == true)
        }

        assertTrue(articleRepository.getSavedArticles().first().isEmpty())
    }

    @Test
    fun `saveWikipediaArticle rejects invalid wikipediaArticle fields and propagates persistence errors`() = runTest {
        val col1 = collectionRepository.createCollection("Válida")

        try {
            saveWikipediaArticleUseCase(sampleWikipediaArticle(pageId = 0L, title = "Inválido"), listOf(col1))
            fail("Deveria rejeitar pageId inválido")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        val failingRepo = FailingArticleRepository()
        val failingUseCase = SaveWikipediaArticleToCollectionsUseCase(failingRepo, collectionRepository)
        try {
            failingUseCase(sampleWikipediaArticle(pageId = 123L, title = "Teste"), listOf(col1))
            fail("Deveria propagar exceção de persistência")
        } catch (e: IllegalStateException) {
            assertEquals("Falha simulada de persistência", e.message)
        }
    }

    @Test
    fun `addArticleToCollections reuses local article preserves existing links and avoids duplicates`() = runTest {
        val col1 = collectionRepository.createCollection("Coleção 1")
        val col2 = collectionRepository.createCollection("Coleção 2")
        val col3 = collectionRepository.createCollection("Coleção 3")

        val articleId = articleRepository.insertArticle(
            Article(
                title = "Renascimento",
                url = "https://pt.wikipedia.org/wiki/Renascimento",
                description = "Movimento cultural",
                content = "Conteúdo offline",
                wikipediaPageId = null
            )
        )
        articleRepository.addArticleToCollection(articleId = articleId, collectionId = col1)

        addArticleToCollectionsUseCase(articleId, listOf(col1, col2, col3, col2))

        assertEquals(1, articleRepository.getSavedArticles().first().size)
        assertEquals(
            setOf(col1, col2, col3),
            getCollectionIdsForArticleUseCase(articleId).first().toSet()
        )
    }

    @Test
    fun `addArticleToCollections validates articleId empty selection and non-existent collections`() = runTest {
        val col1 = collectionRepository.createCollection("História")
        val articleId = articleRepository.insertArticle(
            Article(title = "Iluminismo", url = "https://pt.wikipedia.org/wiki/Iluminismo")
        )

        try {
            addArticleToCollectionsUseCase(0L, listOf(col1))
            fail("Deveria rejeitar articleId inválido")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        try {
            addArticleToCollectionsUseCase(999L, listOf(col1))
            fail("Deveria rejeitar artigo inexistente")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        try {
            addArticleToCollectionsUseCase(articleId, emptyList())
            fail("Deveria rejeitar lista vazia de coleções")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        try {
            addArticleToCollectionsUseCase(articleId, listOf(col1, 888L))
            fail("Deveria rejeitar coleção inexistente")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        assertTrue(getCollectionIdsForArticleUseCase(articleId).first().isEmpty())
    }

    @Test
    fun `removeArticleFromCollection removes only target association preserving article collection and other links`() = runTest {
        val col1 = collectionRepository.createCollection("Filosofia")
        val col2 = collectionRepository.createCollection("Ética")
        val wikiArticle = sampleWikipediaArticle(pageId = 505L, title = "Kant")

        val articleId = saveWikipediaArticleUseCase(wikiArticle, listOf(col1, col2))

        removeArticleFromCollectionUseCase(articleId = articleId, collectionId = col1)

        assertNotNull(articleRepository.getArticleById(articleId))
        assertNotNull(collectionRepository.getCollectionById(col1))
        assertEquals(listOf(col2), getCollectionIdsForArticleUseCase(articleId).first())
        assertTrue(articleRepository.getArticlesForCollection(col1).first().isEmpty())
        assertEquals(1, articleRepository.getArticlesForCollection(col2).first().size)
    }

    @Test
    fun `removeArticleFromCollection validates articleId and collectionId`() = runTest {
        try {
            removeArticleFromCollectionUseCase(articleId = 0L, collectionId = 1L)
            fail("Deveria rejeitar articleId inválido")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        try {
            removeArticleFromCollectionUseCase(articleId = 1L, collectionId = -5L)
            fail("Deveria rejeitar collectionId inválido")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }
    }

    @Test
    fun `getCollectionIdsForArticle observes reactive updates and supports legacy URL fallback`() = runTest {
        val col1 = collectionRepository.createCollection("Legado")
        val col2 = collectionRepository.createCollection("Nova")
        val legacyUrl = "https://pt.wikipedia.org/wiki/Filosofia_grega"

        val legacyId = articleRepository.insertArticle(
            Article(
                title = "Filosofia Grega",
                url = legacyUrl,
                wikipediaPageId = null
            )
        )
        articleRepository.addArticleToCollection(articleId = legacyId, collectionId = col1)

        // Observa por wikipediaPageId + url com fallback para artigo legado (wikipediaPageId == null)
        assertEquals(
            listOf(col1),
            getCollectionIdsForArticleUseCase(wikipediaPageId = 9999L, url = legacyUrl).first()
        )

        addArticleToCollectionsUseCase(legacyId, listOf(col2))

        assertEquals(
            setOf(col1, col2),
            getCollectionIdsForArticleUseCase(wikipediaPageId = 9999L, url = legacyUrl).first().toSet()
        )

        removeArticleFromCollectionUseCase(articleId = legacyId, collectionId = col1)

        assertEquals(
            listOf(col2),
            getCollectionIdsForArticleUseCase(legacyId).first()
        )
    }

    private fun sampleWikipediaArticle(pageId: Long, title: String): WikipediaArticle {
        return WikipediaArticle(
            pageId = pageId,
            title = title,
            description = "Descrição de $title",
            content = "Conteúdo completo de $title",
            imageUrl = "https://upload.wikimedia.org/${title.lowercase()}.jpg",
            url = "https://pt.wikipedia.org/wiki/$title"
        )
    }

    private class FailingArticleRepository : ArticleRepository by FakeArticleRepository() {
        override suspend fun saveArticleAndAssociateCollections(
            article: Article,
            collectionIds: List<Long>
        ): Long {
            throw IllegalStateException("Falha simulada de persistência")
        }
    }
}
