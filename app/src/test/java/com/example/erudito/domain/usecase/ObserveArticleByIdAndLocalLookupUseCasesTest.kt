package com.example.erudito.domain.usecase

import com.example.erudito.domain.mapper.toWikipediaArticle
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.repository.FakeArticleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveArticleByIdAndLocalLookupUseCasesTest {

    @Test
    fun `ObserveArticleByIdUseCase emits initial article and updates reactively`() = runTest {
        val repo = FakeArticleRepository()
        val id = repo.insertArticle(
            Article(
                title = "Sócrates",
                url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                content = "Versão inicial",
                lastUpdated = 1_000L
            )
        )
        val useCase = ObserveArticleByIdUseCase(repo)

        val initial = useCase(id).first()
        assertNotNull(initial)
        assertEquals("Versão inicial", initial?.content)

        repo.insertArticle(initial!!.copy(content = "Versão atualizada", lastUpdated = 2_000L))

        val updated = useCase(id).first()
        assertEquals("Versão atualizada", updated?.content)
        assertEquals(2_000L, updated?.lastUpdated)
    }

    @Test
    fun `GetSavedArticleByWikipediaPageIdUseCase returns saved article for valid pageId and null otherwise`() = runTest {
        val repo = FakeArticleRepository()
        repo.insertArticle(
            Article(
                title = "Marie Curie",
                url = "https://pt.wikipedia.org/wiki/Marie_Curie",
                content = "Conteúdo salvo",
                wikipediaPageId = 42L
            )
        )

        val useCase = GetSavedArticleByWikipediaPageIdUseCase(repo)

        val byPageId = useCase(wikipediaPageId = 42L)
        assertEquals("Marie Curie", byPageId?.title)

        assertNull(useCase(wikipediaPageId = 0L))
        assertNull(useCase(wikipediaPageId = -1L))
        assertNull(useCase(wikipediaPageId = 999L))
    }

    @Test
    fun `Article toWikipediaArticle maps fields and preserves fallbackPageId when wikipediaPageId is null`() {
        val legacyArticle = Article(
            id = 5L,
            title = "Platão",
            url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o",
            description = "Filósofo grego",
            content = "   ",
            imageUrl = "https://upload.wikimedia.org/platao.jpg",
            wikipediaPageId = null
        )

        val mapped = legacyArticle.toWikipediaArticle(fallbackPageId = 77L)
        assertEquals(77L, mapped.pageId)
        assertEquals("Platão", mapped.title)
        assertEquals("Filósofo grego", mapped.description)
        assertEquals("Filósofo grego", mapped.content)
        assertEquals("https://upload.wikimedia.org/platao.jpg", mapped.imageUrl)
        assertEquals("https://pt.wikipedia.org/wiki/Plat%C3%A3o", mapped.url)
    }
}
