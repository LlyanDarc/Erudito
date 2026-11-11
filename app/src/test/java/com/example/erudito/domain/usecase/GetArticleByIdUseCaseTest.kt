package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.repository.FakeArticleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GetArticleByIdUseCaseTest {

    private lateinit var fakeRepository: FakeArticleRepository
    private lateinit var getArticleByIdUseCase: GetArticleByIdUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeArticleRepository()
        getArticleByIdUseCase = GetArticleByIdUseCase(fakeRepository)
    }

    @Test
    fun `invoke with existing id returns article`() = runTest {
        val insertedId = fakeRepository.insertArticle(
            Article(
                title = "História da Computação",
                url = "https://pt.wikipedia.org/wiki/Computacao",
                description = "Origens do cálculo mecânico ao computador moderno"
            )
        )

        val found = getArticleByIdUseCase(insertedId)
        assertNotNull(found)
        assertEquals("História da Computação", found?.title)
        assertEquals("Origens do cálculo mecânico ao computador moderno", found?.description)
    }

    @Test
    fun `invoke with non-existing id returns null`() = runTest {
        val found = getArticleByIdUseCase(999L)
        assertNull(found)
    }
}
