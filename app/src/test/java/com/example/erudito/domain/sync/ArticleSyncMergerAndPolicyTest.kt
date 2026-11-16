package com.example.erudito.domain.sync

import com.example.erudito.data.mapper.toWikipediaArticleOrNull
import com.example.erudito.data.remote.dto.WikipediaPageDto
import com.example.erudito.domain.model.Article
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
    fun mergeNeverOverwritesValidLocalContentWithDescriptionOnlyFallbackFromDto() {
        val local = sampleLocalArticle()
        val dtoWithoutExtract = WikipediaPageDto(
            pageId = 123L,
            title = "Platão",
            description = "Filósofo grego clássico",
            extract = null,
            fullUrl = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
        )
        val remote = dtoWithoutExtract.toWikipediaArticleOrNull()!!

        val result = ArticleSyncMerger.merge(
            local = local,
            remote = remote,
            syncedAtMs = 7_000L
        )

        assertFalse(result.hasContentChanged)
        assertEquals(local.content, result.article.content)
        assertEquals(local.imageUrl, result.article.imageUrl)
        assertEquals(7_000L, result.article.lastSyncedAt)
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
}
