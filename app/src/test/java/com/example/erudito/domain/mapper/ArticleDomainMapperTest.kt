package com.example.erudito.domain.mapper

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat
import com.example.erudito.domain.model.WikipediaArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleDomainMapperTest {

    @Test
    fun `toDomainArticle and toWikipediaArticle round-trip structured content and formula counts`() {
        val wikiArticle = WikipediaArticle(
            pageId = 777L,
            title = "Cálculo diferencial",
            description = "Ramo da matemática",
            content = "<p>Derivada <span class=\"erudito-math-inline\" data-tex=\"f'(x)\"></span></p>",
            imageUrl = "https://upload.wikimedia.org/calc.png",
            url = "https://pt.wikipedia.org/wiki/C%C3%A1lculo_diferencial",
            hasExtractedContent = true,
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 3,
            unextractableFormulaCount = 1
        )

        val domainArticle = wikiArticle.toDomainArticle(
            lastUpdated = 123456L,
            lastSyncedAt = 654321L
        )

        assertEquals(0L, domainArticle.id)
        assertEquals(777L, domainArticle.wikipediaPageId)
        assertEquals(123456L, domainArticle.lastUpdated)
        assertEquals(654321L, domainArticle.lastSyncedAt)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, domainArticle.contentFormat)
        assertEquals(3, domainArticle.extractedFormulaCount)
        assertEquals(1, domainArticle.unextractableFormulaCount)

        val roundTripped = domainArticle.toWikipediaArticle()
        assertEquals(777L, roundTripped.pageId)
        assertEquals(wikiArticle.title, roundTripped.title)
        assertEquals(wikiArticle.description, roundTripped.description)
        assertEquals(wikiArticle.content, roundTripped.content)
        assertEquals(wikiArticle.imageUrl, roundTripped.imageUrl)
        assertEquals(wikiArticle.url, roundTripped.url)
        assertTrue(roundTripped.hasExtractedContent)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, roundTripped.contentFormat)
        assertEquals(3, roundTripped.extractedFormulaCount)
        assertEquals(1, roundTripped.unextractableFormulaCount)
    }

    @Test
    fun `toWikipediaArticle falls back to description plain text and fallbackPageId when content is blank`() {
        val article = Article(
            id = 10L,
            title = "Sócrates",
            url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
            description = "Filósofo grego",
            content = "   ",
            wikipediaPageId = null,
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 5,
            unextractableFormulaCount = 2
        )

        val wikiArticle = article.toWikipediaArticle(fallbackPageId = 99L)

        assertEquals(99L, wikiArticle.pageId)
        assertEquals("Filósofo grego", wikiArticle.content)
        assertFalse(wikiArticle.hasExtractedContent)
        assertEquals(ArticleContentFormat.PLAIN_TEXT, wikiArticle.contentFormat)
        assertEquals(0, wikiArticle.extractedFormulaCount)
        assertEquals(0, wikiArticle.unextractableFormulaCount)
    }
}
