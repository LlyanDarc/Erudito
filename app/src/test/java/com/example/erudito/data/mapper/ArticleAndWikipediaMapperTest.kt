package com.example.erudito.data.mapper

import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.data.remote.dto.WikipediaImageDto
import com.example.erudito.data.remote.dto.WikipediaPageDto
import com.example.erudito.data.remote.parser.SanitizedWikipediaHtml
import com.example.erudito.domain.mapper.toDomainArticle
import com.example.erudito.domain.mapper.toWikipediaArticle
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat
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
        assertEquals(ArticleContentFormat.PLAIN_TEXT, withBlankExtractFallback.contentFormat)
    }

    @Test
    fun `pageDto toStructuredWikipediaArticleOrNull maps sanitized HTML with metadata and formula counts`() {
        val pageDto = WikipediaPageDto(
            pageId = 6321L,
            title = "Equação diferencial",
            description = "Equação envolvendo derivadas",
            original = WikipediaImageDto("https://upload.wikimedia.org/eq_orig.png"),
            thumbnail = WikipediaImageDto("https://upload.wikimedia.org/eq_thumb.png"),
            fullUrl = "https://pt.wikipedia.org/wiki/Equa%C3%A7%C3%A3o_diferencial"
        )
        val sanitized = SanitizedWikipediaHtml(
            html = """<p>Exemplo <span class="erudito-math-inline" data-tex="y' = x"></span></p>""",
            extractedFormulaCount = 1,
            unextractableFormulaCount = 0
        )

        val mapped = pageDto.toStructuredWikipediaArticleOrNull(sanitized)!!

        assertEquals(6321L, mapped.pageId)
        assertEquals("Equação diferencial", mapped.title)
        assertEquals("Equação envolvendo derivadas", mapped.description)
        assertEquals(sanitized.html, mapped.content)
        assertEquals("https://upload.wikimedia.org/eq_orig.png", mapped.imageUrl)
        assertEquals("https://pt.wikipedia.org/wiki/Equa%C3%A7%C3%A3o_diferencial", mapped.url)
        assertTrue(mapped.hasExtractedContent)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, mapped.contentFormat)
        assertEquals(1, mapped.extractedFormulaCount)
        assertEquals(0, mapped.unextractableFormulaCount)
        assertFalse(mapped.hasUnextractableFormulas)
        assertTrue(mapped.isContentIntegrityComplete)

        val domainArticle = mapped.toDomainArticle(lastUpdated = 123456L, lastSyncedAt = 123456L)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, domainArticle.contentFormat)
        assertEquals(sanitized.html, domainArticle.content)
        assertEquals(1, domainArticle.extractedFormulaCount)
        assertEquals(0, domainArticle.unextractableFormulaCount)
        assertTrue(domainArticle.isStructuredHtmlComplete)

        val entity = domainArticle.toEntity()
        assertEquals(ArticleContentFormat.STRUCTURED_HTML.name, entity.contentFormat)
        assertEquals(1, entity.extractedFormulaCount)
        assertEquals(0, entity.unextractableFormulaCount)

        val reloadedDomain = entity.toDomain()
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, reloadedDomain.contentFormat)
        assertEquals(1, reloadedDomain.extractedFormulaCount)
        assertEquals(0, reloadedDomain.unextractableFormulaCount)
        assertTrue(reloadedDomain.isStructuredHtmlComplete)

        val roundTripped = reloadedDomain.toWikipediaArticle()
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, roundTripped.contentFormat)
        assertEquals(1, roundTripped.extractedFormulaCount)
        assertEquals(0, roundTripped.unextractableFormulaCount)
        assertTrue(roundTripped.hasExtractedContent)
        assertTrue(roundTripped.isContentIntegrityComplete)
    }

    @Test
    fun `toStructuredWikipediaArticleOrNull marks incomplete integrity when unextractable formulas exist`() {
        val pageDto = WikipediaPageDto(
            pageId = 1210L,
            title = "Matemática",
            fullUrl = "https://pt.wikipedia.org/wiki/Matem%C3%A1tica"
        )
        val sanitized = SanitizedWikipediaHtml(
            html = """<p>Texto <span class="erudito-math-unavailable" data-math-status="unavailable">[Fórmula indisponível]</span></p>""",
            extractedFormulaCount = 4,
            unextractableFormulaCount = 1
        )

        val mapped = pageDto.toStructuredWikipediaArticleOrNull(sanitized)!!

        assertEquals(ArticleContentFormat.STRUCTURED_HTML, mapped.contentFormat)
        assertEquals(4, mapped.extractedFormulaCount)
        assertEquals(1, mapped.unextractableFormulaCount)
        assertTrue(mapped.hasUnextractableFormulas)
        assertFalse(mapped.isContentIntegrityComplete)

        val domain = mapped.toDomainArticle()
        val roundTripped = domain.toEntity().toDomain()
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, roundTripped.contentFormat)
        assertEquals(4, roundTripped.extractedFormulaCount)
        assertEquals(1, roundTripped.unextractableFormulaCount)
        assertTrue(roundTripped.hasUnextractableFormulas)
        assertFalse(roundTripped.isContentIntegrityComplete)
        assertFalse(roundTripped.isStructuredHtmlComplete)
    }

    @Test
    fun `articleEntity toDomain falls back safely to PLAIN_TEXT on unknown contentFormat and clamps negative counts`() {
        val entity = ArticleEntity(
            id = 50L,
            title = "Legado",
            url = "https://pt.wikipedia.org/wiki/Legado",
            description = "Desc",
            content = "Texto simples",
            imageUrl = null,
            lastUpdated = 1000L,
            contentFormat = "UNKNOWN_LEGACY_FORMAT",
            extractedFormulaCount = -3,
            unextractableFormulaCount = -1
        )

        val domain = entity.toDomain()

        assertEquals(ArticleContentFormat.PLAIN_TEXT, domain.contentFormat)
        assertEquals(0, domain.extractedFormulaCount)
        assertEquals(0, domain.unextractableFormulaCount)
        assertTrue(domain.isContentIntegrityComplete)
        assertFalse(domain.isStructuredHtmlComplete)
    }

    @Test
    fun `toStructuredWikipediaArticleOrNull returns null for missing page or blank sanitized HTML`() {
        val missingPage = WikipediaPageDto(pageId = 10L, title = "Inexistente", missing = true)
        assertNull(
            missingPage.toStructuredWikipediaArticleOrNull(
                SanitizedWikipediaHtml("<p>Texto</p>", 0, 0)
            )
        )

        val validPage = WikipediaPageDto(pageId = 10L, title = "Vazio")
        assertNull(
            validPage.toStructuredWikipediaArticleOrNull(
                SanitizedWikipediaHtml("   ", 0, 0)
            )
        )
    }

    @Test
    fun `wikipedia mappers enforce HTTPS-only image URLs and discard insecure schemes`() {
        val insecureSchemes = listOf(
            "http://upload.wikimedia.org/insecure.png",
            "data:image/png;base64,iVBORw0KGgo=",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "//upload.wikimedia.org/protocol_relative.png",
            "not a valid url"
        )

        insecureSchemes.forEach { rawUrl ->
            val pageDto = WikipediaPageDto(
                pageId = 700L,
                title = "Segurança",
                extract = "Texto",
                original = WikipediaImageDto(rawUrl),
                thumbnail = WikipediaImageDto(rawUrl)
            )

            assertNull(
                "Expected search thumbnailUrl to be rejected for: $rawUrl",
                pageDto.toSearchResultOrNull()?.thumbnailUrl
            )
            assertNull(
                "Expected plain article imageUrl to be rejected for: $rawUrl",
                pageDto.toWikipediaArticleOrNull()?.imageUrl
            )
            assertNull(
                "Expected structured article imageUrl to be rejected for: $rawUrl",
                pageDto.toStructuredWikipediaArticleOrNull(
                    SanitizedWikipediaHtml("<p>Conteúdo</p>", 0, 0)
                )?.imageUrl
            )
        }

        val secureDto = WikipediaPageDto(
            pageId = 701L,
            title = "HTTPS",
            extract = "Texto",
            original = WikipediaImageDto("  https://upload.wikimedia.org/wikipedia/commons/a/a1/Secure.png  ")
        )
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/a/a1/Secure.png",
            secureDto.toSearchResultOrNull()?.thumbnailUrl
        )
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/a/a1/Secure.png",
            secureDto.toWikipediaArticleOrNull()?.imageUrl
        )
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/a/a1/Secure.png",
            secureDto.toStructuredWikipediaArticleOrNull(
                SanitizedWikipediaHtml("<p>Conteúdo</p>", 0, 0)
            )?.imageUrl
        )
    }
}
