package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles
import com.example.erudito.domain.repository.FakeCollectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class GetCollectionWithArticlesUseCaseTest {

    private lateinit var fakeRepository: FakeCollectionRepository
    private lateinit var getCollectionWithArticlesUseCase: GetCollectionWithArticlesUseCase

    @Before
    fun setUp() = runTest {
        fakeRepository = FakeCollectionRepository()
        getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(fakeRepository)

        val collectionId = fakeRepository.createCollection("Filosofia")
        val sampleArticles = listOf(
            Article(id = 1, title = "Sócrates", url = "https://pt.wikipedia.org/wiki/Sócrates"),
            Article(id = 2, title = "Platão", url = "https://pt.wikipedia.org/wiki/Platão")
        )
        fakeRepository.setCollectionWithArticles(
            collectionId,
            CollectionWithArticles(
                collection = Collection(id = collectionId, name = "Filosofia", articleCount = 2),
                articles = sampleArticles
            )
        )
    }

    @Test
    fun `invoke returns collection with its associated articles`() = runTest {
        val result = getCollectionWithArticlesUseCase(1L).first()

        assertNotNull(result)
        assertEquals("Filosofia", result?.collection?.name)
        assertEquals(2, result?.articles?.size)
        assertEquals("Sócrates", result?.articles?.get(0)?.title)
        assertEquals("Platão", result?.articles?.get(1)?.title)
    }

    @Test
    fun `invoke with non-existing collection returns null`() = runTest {
        val result = getCollectionWithArticlesUseCase(999L).first()
        org.junit.Assert.assertNull(result)
    }
}
