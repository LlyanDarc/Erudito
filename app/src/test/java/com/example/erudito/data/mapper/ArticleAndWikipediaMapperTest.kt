package com.example.erudito.data.mapper

import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.data.remote.dto.WikipediaPageDto
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleAndWikipediaMapperTest {

    @Test
    fun `wikipediaArticle toDomainArticle preserves all fields and maps pageId to wikipediaPageId`() {
        val before = System.currentTimeMillis()
        val wikiArticle = WikipediaArticle(
            pageId = 8452L,
            title = "Aristóteles",
            description = "Filósofo da Grécia Antiga",
            content = "Aristóteles foi um filósofo e polímata da Grécia Antiga.",
            imageUrl = "https://upload.wikimedia.org/aristoteles.jpg",
            url = "https://pt.wikipedia.org/wiki/Arist%C3%B3teles"
        )

        val domainArticle = wikiArticle.toDomainArticle()
        val after = System.currentTimeMillis()

        assertEquals(0L, domainArticle.id)
        assertEquals(8452L, domainArticle.wikipediaPageId)
        assertEquals("Aristóteles", domainArticle.title)
        assertEquals("Filósofo da Grécia Antiga", domainArticle.description)
        assertEquals("Aristóteles foi um filósofo e polímata da Grécia Antiga.", domainArticle.content)
        assertEquals("https://upload.wikimedia.org/aristoteles.jpg", domainArticle.imageUrl)
        assertEquals("https://pt.wikipedia.org/wiki/Arist%C3%B3teles", domainArticle.url)
        assertTrue(domainArticle.lastUpdated in before..after)
    }

    @Test
    fun `wikipediaArticle toDomainArticle preserves null optional fields and explicit timestamp`() {
        val wikiArticle = WikipediaArticle(
            pageId = 123L,
            title = "Termodinâmica",
            description = null,
            content = "Ramo da física.",
            imageUrl = null,
            url = "https://pt.wikipedia.org/wiki/Termodin%C3%A2mica"
        )

        val domainArticle = wikiArticle.toDomainArticle(lastUpdated = 99999L)

        assertEquals(123L, domainArticle.wikipediaPageId)
        assertNull(domainArticle.description)
        assertNull(domainArticle.imageUrl)
        assertEquals(99999L, domainArticle.lastUpdated)
    }

    @Test
    fun `article toEntity preserves wikipediaPageId and all fields`() {
        val article = Article(
            id = 14L,
            title = "Platão",
            url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o",
            description = "Filósofo ateniense",
            content = "Fundador da Academia.",
            imageUrl = "https://upload.wikimedia.org/platao.jpg",
            lastUpdated = 55555L,
            wikipediaPageId = 909L
        )

        val entity = article.toEntity()

        assertEquals(14L, entity.id)
        assertEquals("Platão", entity.title)
        assertEquals("https://pt.wikipedia.org/wiki/Plat%C3%A3o", entity.url)
        assertEquals("Filósofo ateniense", entity.description)
        assertEquals("Fundador da Academia.", entity.content)
        assertEquals("https://upload.wikimedia.org/platao.jpg", entity.imageUrl)
        assertEquals(55555L, entity.lastUpdated)
        assertEquals(909L, entity.wikipediaPageId)
    }

    @Test
    fun `articleEntity toDomain preserves null and non-null wikipediaPageId`() {
        val legacyEntity = ArticleEntity(
            id = 3L,
            title = "Renascimento",
            url = "https://pt.wikipedia.org/wiki/Renascimento",
            description = "Movimento cultural",
            content = "Conteúdo offline.",
            imageUrl = null,
            lastUpdated = 11111L,
            wikipediaPageId = null
        )

        val legacyDomain = legacyEntity.toDomain()
        assertEquals(3L, legacyDomain.id)
        assertNull(legacyDomain.wikipediaPageId)
        assertEquals("Renascimento", legacyDomain.title)
        assertEquals(11111L, legacyDomain.lastUpdated)

        val wikiEntity = legacyEntity.copy(id = 4L, wikipediaPageId = 321L)
        val wikiDomain = wikiEntity.toDomain()
        assertEquals(4L, wikiDomain.id)
        assertEquals(321L, wikiDomain.wikipediaPageId)
    }

    @Test
    fun `pageDto toWikipediaArticleOrNull distinguishes real extract from description fallback`() {
        val withRealExtract = WikipediaPageDto(
            pageId = 77L,
            title = "Sócrates",
            description = "Filósofo grego",
            extract = "Texto completo extraído da Wikipédia."
        ).toWikipediaArticleOrNull()!!

        assertTrue(withRealExtract.hasExtractedContent)
        assertEquals("Texto completo extraído da Wikipédia.", withRealExtract.content)

        val withNullExtractFallback = WikipediaPageDto(
            pageId = 78L,
            title = "Tales",
            description = "Primeiro filósofo ocidental",
            extract = null
        ).toWikipediaArticleOrNull()!!

        assertFalse(withNullExtractFallback.hasExtractedContent)
        assertEquals("Primeiro filósofo ocidental", withNullExtractFallback.content)

        val withBlankExtractFallback = WikipediaPageDto(
            pageId = 79L,
            title = "Anaximandro",
            description = "Filósofo pré-socrático",
            extract = "   "
        ).toWikipediaArticleOrNull()!!

        assertFalse(withBlankExtractFallback.hasExtractedContent)
        assertEquals("Filósofo pré-socrático", withBlankExtractFallback.content)
    }
}
