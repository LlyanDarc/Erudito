package com.example.erudito.domain.sync

import com.example.erudito.data.mapper.toWikipediaArticleOrNull
import com.example.erudito.data.remote.dto.WikipediaPageDto
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat
import com.example.erudito.domain.model.WikipediaArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSyncMergerAndPolicyTest {

    private fun sampleLocalArticle(
        id: Long = 10L,
        title: String = "Platão",
        url: String = "https://pt.wikipedia.org/wiki/Plat%C3%A3o",
        description: String? = "Filósofo grego clássico",
        content: String? = "Platão foi um filósofo e matemático do período clássico na Grécia Antiga, fundador da Academia em Atenas.",
        imageUrl: String? = "https://upload.wikimedia.org/platao.jpg",
        lastUpdated: Long = 1_000L,
        wikipediaPageId: Long? = 123L,
        lastSyncedAt: Long? = null
    ) = Article(
        id = id,
        title = title,
        url = url,
        description = description,
        content = content,
        imageUrl = imageUrl,
        lastUpdated = lastUpdated,
        wikipediaPageId = wikipediaPageId,
        lastSyncedAt = lastSyncedAt
    )

    @Test
    fun mergeWithChangedContentUpdatesFieldsAndBothTimestamps() {
        val local = sampleLocalArticle()
        val remote = WikipediaArticle(
            pageId = 123L,
            title = "Platão",
            description = "Filósofo grego clássico",
            content = "Novo texto expandido e revisado sobre Platão e seus diálogos.",
            imageUrl = "https://upload.wikimedia.org/platao_novo.jpg",
            url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
        )

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 5_000L
        )

        assertTrue(result.hasContentChanged)
        assertEquals(local.id, result.article.id)
        assertEquals("Novo texto expandido e revisado sobre Platão e seus diálogos.", result.article.content)
        assertEquals("https://upload.wikimedia.org/platao_novo.jpg", result.article.imageUrl)
        assertEquals(5_000L, result.article.lastUpdated)
        assertEquals(5_000L, result.article.lastSyncedAt)
    }

    @Test
    fun mergeWithUnchangedContentUpdatesOnlyLastSyncedAtAndPreservesLastUpdated() {
        val local = sampleLocalArticle(lastUpdated = 2_500L, lastSyncedAt = 3_000L)
        val remote = WikipediaArticle(
            pageId = 123L,
            title = local.title,
            description = local.description,
            content = local.content!!,
            imageUrl = local.imageUrl,
            url = local.url
        )

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 9_000L
        )

        assertFalse(result.hasContentChanged)
        assertEquals(2_500L, result.article.lastUpdated)
        assertEquals(9_000L, result.article.lastSyncedAt)
        assertEquals(local.content, result.article.content)
    }

    @Test
    fun mergeNeverOverwritesValidLocalContentWithBlankExtract() {
        val local = sampleLocalArticle()
        val remote = WikipediaArticle(
            pageId = 123L,
            title = "Platão",
            description = "Filósofo grego clássico",
            content = "   ",
            imageUrl = local.imageUrl,
            url = local.url
        )

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 6_000L
        )

        assertFalse(result.hasContentChanged)
        assertEquals(local.content, result.article.content)
        assertEquals(local.lastUpdated, result.article.lastUpdated)
        assertEquals(6_000L, result.article.lastSyncedAt)
    }

    @Test
    fun mergeNeverOverwritesValidLocalContentWithDescriptionOnlyFallbackFromNullExtract() {
        val local = sampleLocalArticle()
        val dtoWithoutExtract = WikipediaPageDto(
            pageId = 123L,
            title = "Platão",
            description = "Filósofo grego clássico",
            extract = null,
            fullUrl = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
        )
        val remote = dtoWithoutExtract.toWikipediaArticleOrNull()!!

        assertFalse(remote.hasExtractedContent)
        assertEquals("Filósofo grego clássico", remote.content)

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 7_000L
        )

        assertFalse(result.hasContentChanged)
        assertEquals(local.content, result.article.content)
        assertEquals(local.imageUrl, result.article.imageUrl)
        assertEquals(local.lastUpdated, result.article.lastUpdated)
        assertEquals(7_000L, result.article.lastSyncedAt)
    }

    @Test
    fun mergeNeverOverwritesLocalContentShorterThanRemoteDescriptionWhenExtractIsNullOrEmpty() {
        val shortLocalContent = "Resumo curto."
        val longDescription = "Filósofo grego clássico, discípulo de Sócrates e fundador da Academia de Atenas."
        assertTrue(shortLocalContent.length < longDescription.length)

        val local = sampleLocalArticle(
            description = longDescription,
            content = shortLocalContent
        )

        val remoteNullExtract = WikipediaPageDto(
            pageId = 123L,
            title = "Platão",
            description = longDescription,
            extract = null,
            fullUrl = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
        ).toWikipediaArticleOrNull()!!

        val resultNull = ArticleSyncMerger.merge(local = local, remote = remoteNullExtract, syncedAtMs = 7_100L)
        assertFalse(remoteNullExtract.hasExtractedContent)
        assertEquals(shortLocalContent, resultNull.article.content)
        assertFalse(resultNull.hasContentChanged)
        assertEquals(local.lastUpdated, resultNull.article.lastUpdated)
        assertEquals(7_100L, resultNull.article.lastSyncedAt)

        val remoteEmptyExtract = WikipediaPageDto(
            pageId = 123L,
            title = "Platão",
            description = longDescription,
            extract = "   ",
            fullUrl = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
        ).toWikipediaArticleOrNull()!!

        val resultEmpty = ArticleSyncMerger.merge(local = local, remote = remoteEmptyExtract, syncedAtMs = 7_200L)
        assertFalse(remoteEmptyExtract.hasExtractedContent)
        assertEquals(shortLocalContent, resultEmpty.article.content)
        assertFalse(resultEmpty.hasContentChanged)
        assertEquals(local.lastUpdated, resultEmpty.article.lastUpdated)
        assertEquals(7_200L, resultEmpty.article.lastSyncedAt)
    }

    @Test
    fun mergeNeverOverwritesLocalContentEqualInLengthToRemoteDescriptionWhenExtractIsMissing() {
        val remoteDescription = "Filósofo grego antigo"
        val exactSameLengthLocal = "A".repeat(remoteDescription.length)
        assertEquals(remoteDescription.length, exactSameLengthLocal.length)

        val local = sampleLocalArticle(
            description = "Descrição antiga",
            content = exactSameLengthLocal
        )
        val remote = WikipediaPageDto(
            pageId = 123L,
            title = "Platão",
            description = remoteDescription,
            extract = "",
            fullUrl = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
        ).toWikipediaArticleOrNull()!!

        assertFalse(remote.hasExtractedContent)

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 7_500L
        )

        assertTrue(result.hasContentChanged) // descrição mudou
        assertEquals(remoteDescription, result.article.description)
        assertEquals(exactSameLengthLocal, result.article.content)
        assertEquals(7_500L, result.article.lastUpdated)
        assertEquals(7_500L, result.article.lastSyncedAt)
    }

    @Test
    fun mergeUpdatesContentWhenRealExtractIsValidEvenIfIdenticalToDescription() {
        val local = sampleLocalArticle(
            description = "Filósofo grego",
            content = "Conteúdo anterior detalhado sobre o filósofo grego na antiguidade."
        )
        val dtoWithRealExtract = WikipediaPageDto(
            pageId = 123L,
            title = "Platão",
            description = "Filósofo grego",
            extract = "Filósofo grego",
            fullUrl = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
        )
        val remote = dtoWithRealExtract.toWikipediaArticleOrNull()!!

        assertTrue(remote.hasExtractedContent)

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 7_800L
        )

        assertTrue(result.hasContentChanged)
        assertEquals("Filósofo grego", result.article.content)
        assertEquals(7_800L, result.article.lastUpdated)
        assertEquals(7_800L, result.article.lastSyncedAt)
    }

    @Test
    fun mergePreservesLocalDescriptionAndImageUrlWhenRemoteOmitsThem() {
        val local = sampleLocalArticle(
            description = "Descrição local preservada",
            imageUrl = "https://upload.wikimedia.org/local.jpg"
        )
        val remote = WikipediaArticle(
            pageId = 123L,
            title = "Platão",
            description = null,
            content = local.content!!,
            imageUrl = "   ",
            url = local.url
        )

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 8_000L
        )

        assertFalse(result.hasContentChanged)
        assertEquals("Descrição local preservada", result.article.description)
        assertEquals("https://upload.wikimedia.org/local.jpg", result.article.imageUrl)
        assertEquals(8_000L, result.article.lastSyncedAt)
    }

    @Test
    fun mergeHandlesRedirectAndTitleChangeWithoutChangingLocalId() {
        val local = sampleLocalArticle(
            id = 42L,
            title = "Plato",
            url = "https://pt.wikipedia.org/wiki/Plato",
            wikipediaPageId = 100L
        )
        val remote = WikipediaArticle(
            pageId = 200L,
            title = "Platão",
            description = "Filósofo grego clássico",
            content = local.content!!,
            imageUrl = local.imageUrl,
            url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
        )

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 10_000L,
            resolvedPageId = 200L
        )

        assertTrue(result.hasContentChanged)
        assertEquals(42L, result.article.id)
        assertEquals(200L, result.article.wikipediaPageId)
        assertEquals("Platão", result.article.title)
        assertEquals("https://pt.wikipedia.org/wiki/Plat%C3%A3o", result.article.url)
        assertEquals(10_000L, result.article.lastUpdated)
        assertEquals(10_000L, result.article.lastSyncedAt)
    }

    @Test
    fun syncPolicyEvaluatesEligibilityAndIntervalsCorrectly() {
        val policy = SyncPolicy(minimumRefreshIntervalMs = 60_000L)
        val now = 1_000_000L

        assertFalse(policy.isEligibleForSync(sampleLocalArticle(wikipediaPageId = null), now))
        assertFalse(policy.isEligibleForSync(sampleLocalArticle(wikipediaPageId = 0L), now))
        assertTrue(policy.isEligibleForSync(sampleLocalArticle(wikipediaPageId = 123L, lastSyncedAt = null), now))
        assertFalse(
            policy.isEligibleForSync(
                sampleLocalArticle(wikipediaPageId = 123L, lastSyncedAt = now - 30_000L),
                now
            )
        )
        assertTrue(
            policy.isEligibleForSync(
                sampleLocalArticle(wikipediaPageId = 123L, lastSyncedAt = now - 60_000L),
                now
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun syncPolicyRejectsNegativeInterval() {
        SyncPolicy(minimumRefreshIntervalMs = -1L)
    }

    @Test
    fun scenarioA_completeStructuredHtmlIsNeverReplacedByPlainTextEmptyOrIncompleteHtml() {
        val localHtml = """<section><p>Equação <span class="erudito-math-inline" data-tex="E=mc^2"></span></p></section>"""
        val local = sampleLocalArticle(
            content = localHtml,
            lastUpdated = 2_000L,
            lastSyncedAt = 3_000L
        ).copy(
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 5,
            unextractableFormulaCount = 0
        )

        // 1. Remoto PLAIN_TEXT não deve degradar STRUCTURED_HTML íntegro
        val remotePlain = WikipediaArticle(
            pageId = 123L,
            title = local.title,
            description = local.description,
            content = "Equação E=mc^2 em texto degradado",
            imageUrl = local.imageUrl,
            url = local.url,
            hasExtractedContent = true,
            contentFormat = ArticleContentFormat.PLAIN_TEXT
        )
        val plainResult = ArticleSyncMerger.merge(local = local, remote = remotePlain, syncedAtMs = 10_000L)
        assertFalse(plainResult.hasContentChanged)
        assertEquals(localHtml, plainResult.article.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, plainResult.article.contentFormat)
        assertEquals(5, plainResult.article.extractedFormulaCount)
        assertEquals(0, plainResult.article.unextractableFormulaCount)
        assertEquals(2_000L, plainResult.article.lastUpdated)
        assertEquals(10_000L, plainResult.article.lastSyncedAt)

        // 2. Remoto vazio não deve sobrescrever STRUCTURED_HTML íntegro
        val remoteEmpty = remotePlain.copy(content = "   ", hasExtractedContent = false)
        val emptyResult = ArticleSyncMerger.merge(local = local, remote = remoteEmpty, syncedAtMs = 11_000L)
        assertFalse(emptyResult.hasContentChanged)
        assertEquals(localHtml, emptyResult.article.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, emptyResult.article.contentFormat)
        assertEquals(11_000L, emptyResult.article.lastSyncedAt)

        // 3. Remoto STRUCTURED_HTML com fórmulas indisponíveis não deve sobrescrever íntegro
        val remoteIncomplete = WikipediaArticle(
            pageId = 123L,
            title = local.title,
            description = local.description,
            content = """<p><span class="erudito-math-unavailable">[Fórmula indisponível]</span></p>""",
            imageUrl = local.imageUrl,
            url = local.url,
            hasExtractedContent = true,
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 4,
            unextractableFormulaCount = 1
        )
        val incompleteResult = ArticleSyncMerger.merge(local = local, remote = remoteIncomplete, syncedAtMs = 12_000L)
        assertFalse(incompleteResult.hasContentChanged)
        assertEquals(localHtml, incompleteResult.article.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, incompleteResult.article.contentFormat)
        assertEquals(5, incompleteResult.article.extractedFormulaCount)
        assertEquals(0, incompleteResult.article.unextractableFormulaCount)
        assertEquals(2_000L, incompleteResult.article.lastUpdated)
        assertEquals(12_000L, incompleteResult.article.lastSyncedAt)
    }

    @Test
    fun scenarioB_incompleteStructuredHtmlIsUpgradedByCompleteStructuredHtmlAndProtectedAgainstDegradation() {
        val localIncompleteHtml = """<p><span class="erudito-math-unavailable">[Fórmula indisponível]</span></p>"""
        val local = sampleLocalArticle(
            content = localIncompleteHtml,
            lastUpdated = 2_000L,
            lastSyncedAt = 3_000L
        ).copy(
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 2,
            unextractableFormulaCount = 1
        )

        // Não regride para PLAIN_TEXT nem aceita outro incompleto
        val remotePlain = WikipediaArticle(
            pageId = 123L,
            title = local.title,
            description = local.description,
            content = "Texto simples",
            imageUrl = local.imageUrl,
            url = local.url,
            hasExtractedContent = true,
            contentFormat = ArticleContentFormat.PLAIN_TEXT
        )
        val plainResult = ArticleSyncMerger.merge(local = local, remote = remotePlain, syncedAtMs = 4_000L)
        assertFalse(plainResult.hasContentChanged)
        assertEquals(localIncompleteHtml, plainResult.article.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, plainResult.article.contentFormat)
        assertEquals(1, plainResult.article.unextractableFormulaCount)

        // Substitui quando chega versão STRUCTURED_HTML íntegra
        val upgradedHtml = """<p><span class="erudito-math-inline" data-tex="x^2 + y^2 = z^2"></span></p>"""
        val remoteComplete = WikipediaArticle(
            pageId = 123L,
            title = local.title,
            description = local.description,
            content = upgradedHtml,
            imageUrl = local.imageUrl,
            url = local.url,
            hasExtractedContent = true,
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 3,
            unextractableFormulaCount = 0
        )
        val upgradedResult = ArticleSyncMerger.merge(local = local, remote = remoteComplete, syncedAtMs = 5_000L)
        assertTrue(upgradedResult.hasContentChanged)
        assertEquals(upgradedHtml, upgradedResult.article.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, upgradedResult.article.contentFormat)
        assertEquals(3, upgradedResult.article.extractedFormulaCount)
        assertEquals(0, upgradedResult.article.unextractableFormulaCount)
        assertTrue(upgradedResult.article.isStructuredHtmlComplete)
        assertEquals(5_000L, upgradedResult.article.lastUpdated)
        assertEquals(5_000L, upgradedResult.article.lastSyncedAt)
    }

    @Test
    fun scenarioC_plainTextUpgradesToCompleteStructuredHtmlButRejectsIncompleteStructuredHtmlWhenLocalIsUsable() {
        val usableLocal = sampleLocalArticle(
            content = "Texto simples aproveitável sobre matemática.",
            lastUpdated = 1_000L
        ).copy(contentFormat = ArticleContentFormat.PLAIN_TEXT)

        // Resposta estruturada incompleta não sobrescreve texto simples aproveitável
        val incompleteStructured = WikipediaArticle(
            pageId = 123L,
            title = usableLocal.title,
            description = usableLocal.description,
            content = """<p>Texto <span class="erudito-math-unavailable">[Fórmula indisponível]</span></p>""",
            imageUrl = usableLocal.imageUrl,
            url = usableLocal.url,
            hasExtractedContent = true,
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 1,
            unextractableFormulaCount = 1
        )
        val rejected = ArticleSyncMerger.merge(local = usableLocal, remote = incompleteStructured, syncedAtMs = 5_000L)
        assertFalse(rejected.hasContentChanged)
        assertEquals("Texto simples aproveitável sobre matemática.", rejected.article.content)
        assertEquals(ArticleContentFormat.PLAIN_TEXT, rejected.article.contentFormat)
        assertEquals(0, rejected.article.unextractableFormulaCount)
        assertEquals(1_000L, rejected.article.lastUpdated)
        assertEquals(5_000L, rejected.article.lastSyncedAt)

        // Resposta estruturada íntegra atualiza texto simples para STRUCTURED_HTML
        val completeHtml = """<p>Texto <span class="erudito-math-inline" data-tex="\pi"></span></p>"""
        val completeStructured = incompleteStructured.copy(
            content = completeHtml,
            extractedFormulaCount = 2,
            unextractableFormulaCount = 0
        )
        val upgraded = ArticleSyncMerger.merge(local = usableLocal, remote = completeStructured, syncedAtMs = 6_000L)
        assertTrue(upgraded.hasContentChanged)
        assertEquals(completeHtml, upgraded.article.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, upgraded.article.contentFormat)
        assertEquals(2, upgraded.article.extractedFormulaCount)
        assertEquals(0, upgraded.article.unextractableFormulaCount)
        assertEquals(6_000L, upgraded.article.lastUpdated)
        assertEquals(6_000L, upgraded.article.lastSyncedAt)

        // Quando local não possui conteúdo aproveitável (null/blank), aceita HTML estruturado mesmo incompleto
        val emptyLocal = usableLocal.copy(content = null)
        val filledFromEmpty = ArticleSyncMerger.merge(local = emptyLocal, remote = incompleteStructured, syncedAtMs = 7_000L)
        assertTrue(filledFromEmpty.hasContentChanged)
        assertEquals(incompleteStructured.content, filledFromEmpty.article.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, filledFromEmpty.article.contentFormat)
        assertEquals(1, filledFromEmpty.article.extractedFormulaCount)
        assertEquals(1, filledFromEmpty.article.unextractableFormulaCount)
    }

    @Test
    fun scenarioD_E_F_rejectsNegativeCountsAndUpdatesMetadataWithoutDegradingStructuredContent() {
        val localHtml = """<p><span class="erudito-math-inline" data-tex="a+b"></span></p>"""
        val local = sampleLocalArticle(
            description = "Descrição antiga",
            content = localHtml,
            lastUpdated = 1_000L,
            lastSyncedAt = 1_500L
        ).copy(
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 1,
            unextractableFormulaCount = 0
        )

        // Contadores negativos não substituem conteúdo nem corrompem formato
        val negativeCountsRemote = WikipediaArticle(
            pageId = 123L,
            title = "Platão (Atualizado)",
            description = "Nova descrição",
            content = "<p>Inválido</p>",
            imageUrl = local.imageUrl,
            url = local.url,
            hasExtractedContent = true,
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = -1,
            unextractableFormulaCount = 0
        )
        val merged = ArticleSyncMerger.merge(local = local, remote = negativeCountsRemote, syncedAtMs = 8_000L)
        assertTrue(merged.hasContentChanged) // título e descrição válidos mudaram (Cenário E)
        assertEquals("Platão (Atualizado)", merged.article.title)
        assertEquals("Nova descrição", merged.article.description)
        assertEquals(localHtml, merged.article.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, merged.article.contentFormat)
        assertEquals(1, merged.article.extractedFormulaCount)
        assertEquals(0, merged.article.unextractableFormulaCount)
        assertEquals(8_000L, merged.article.lastUpdated)
        assertEquals(8_000L, merged.article.lastSyncedAt)
    }
}
