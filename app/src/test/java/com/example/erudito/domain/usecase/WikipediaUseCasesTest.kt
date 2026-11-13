package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.WikipediaRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WikipediaUseCasesTest {

    private lateinit var fakeRepository: FakeWikipediaRepository
    private lateinit var searchUseCase: SearchWikipediaArticlesUseCase
    private lateinit var getArticleUseCase: GetWikipediaArticleUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeWikipediaRepository()
        searchUseCase = SearchWikipediaArticlesUseCase(fakeRepository)
        getArticleUseCase = GetWikipediaArticleUseCase(fakeRepository)
    }

    @Test
    fun `search with empty query throws IllegalArgumentException`() = runTest {
        try {
            searchUseCase("")
            fail("Deveria ter lançado IllegalArgumentException para consulta vazia")
        } catch (e: IllegalArgumentException) {
            assertEquals("Digite um termo para pesquisar na Wikipédia.", e.message)
        }
    }

    @Test
    fun `search with whitespace only throws IllegalArgumentException`() = runTest {
        try {
            searchUseCase("    ")
            fail("Deveria ter lançado IllegalArgumentException para consulta apenas com espaços")
        } catch (e: IllegalArgumentException) {
            assertEquals("Digite um termo para pesquisar na Wikipédia.", e.message)
        }
    }

    @Test
    fun `search trims query and returns matching results`() = runTest {
        val expected = listOf(
            WikipediaSearchResult(
                pageId = 10L,
                title = "Renascimento",
                description = "Movimento cultural",
                snippet = "Movimento europeu...",
                thumbnailUrl = "https://upload.wikimedia.org/thumb.jpg",
                url = "https://pt.wikipedia.org/wiki/Renascimento"
            )
        )
        fakeRepository.searchResults = expected

        val results = searchUseCase("  Renascimento  ")

        assertEquals("Renascimento", fakeRepository.lastQuery)
        assertEquals(expected, results)
    }

    @Test
    fun `search returns empty list when repository has no results`() = runTest {
        fakeRepository.searchResults = emptyList()

        val results = searchUseCase("termoinexistente123")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `getArticle with valid pageId returns full article`() = runTest {
        val expected = WikipediaArticle(
            pageId = 42L,
            title = "Sócrates",
            description = "Filósofo grego",
            content = "Sócrates foi um filósofo ateniense...",
            imageUrl = "https://upload.wikimedia.org/socrates.jpg",
            url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
        )
        fakeRepository.articleById[42L] = expected

        val result = getArticleUseCase(42L)

        assertEquals(expected, result)
    }

    @Test
    fun `getArticle returns null when article does not exist`() = runTest {
        val result = getArticleUseCase(999L)

        assertNull(result)
    }

    @Test
    fun `getArticle with non positive pageId throws IllegalArgumentException`() = runTest {
        try {
            getArticleUseCase(0L)
            fail("Deveria ter lançado IllegalArgumentException para pageId inválido")
        } catch (e: IllegalArgumentException) {
            assertEquals("Identificador de artigo inválido.", e.message)
        }
    }

    private class FakeWikipediaRepository : WikipediaRepository {
        var lastQuery: String? = null
        var searchResults: List<WikipediaSearchResult> = emptyList()
        val articleById = mutableMapOf<Long, WikipediaArticle>()

        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> {
            lastQuery = query
            return searchResults
        }

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? {
            return articleById[pageId]
        }
    }
}
