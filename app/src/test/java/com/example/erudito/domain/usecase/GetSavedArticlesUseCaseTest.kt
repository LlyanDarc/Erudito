package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.repository.FakeArticleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetSavedArticlesUseCaseTest {

    private lateinit var fakeRepository: FakeArticleRepository
    private lateinit var getSavedArticlesUseCase: GetSavedArticlesUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeArticleRepository()
        getSavedArticlesUseCase = GetSavedArticlesUseCase(fakeRepository)
    }

    @Test
    fun `invoke with empty repository emits empty list`() = runTest {
        val articles = getSavedArticlesUseCase().first()
        assertTrue(articles.isEmpty())
    }

    @Test
    fun `invoke emits all saved articles`() = runTest {
        fakeRepository.insertArticle(
            Article(title = "Artigo 1", url = "https://pt.wikipedia.org/wiki/1")
        )
        fakeRepository.insertArticle(
            Article(title = "Artigo 2", url = "https://pt.wikipedia.org/wiki/2")
        )

        val articles = getSavedArticlesUseCase().first()
        assertEquals(2, articles.size)
        assertEquals("Artigo 1", articles[0].title)
        assertEquals("Artigo 2", articles[1].title)
    }
}
