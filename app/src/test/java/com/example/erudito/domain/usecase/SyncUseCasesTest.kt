package com.example.erudito.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.erudito.data.local.database.EruditoDatabase
import com.example.erudito.data.local.entity.CollectionEntity
import com.example.erudito.data.repository.ArticleRepositoryImpl
import com.example.erudito.domain.connectivity.ConnectivityObserver
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.sync.RefreshArticleOutcome
import com.example.erudito.domain.sync.SkipReason
import com.example.erudito.domain.sync.SyncPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncUseCasesTest {

    private lateinit var database: EruditoDatabase
    private lateinit var articleRepository: ArticleRepositoryImpl
    private lateinit var fakeWikipediaRepository: FakeSyncWikipediaRepository
    private lateinit var fakeConnectivityObserver: FakeSyncConnectivityObserver
    private lateinit var refreshUseCase: RefreshSavedArticleUseCase
    private lateinit var syncUseCase: SyncSavedArticlesUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EruditoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        articleRepository = ArticleRepositoryImpl(database.articleDao())
        fakeWikipediaRepository = FakeSyncWikipediaRepository()
        fakeConnectivityObserver = FakeSyncConnectivityObserver(online = true)
        val syncPolicy = SyncPolicy(minimumRefreshIntervalMs = 60_000L)

        refreshUseCase = RefreshSavedArticleUseCase(
            articleRepository = articleRepository,
            wikipediaRepository = fakeWikipediaRepository,
            connectivityObserver = fakeConnectivityObserver,
            syncPolicy = syncPolicy
        )
        syncUseCase = SyncSavedArticlesUseCase(
            articleRepository = articleRepository,
            refreshSavedArticleUseCase = refreshUseCase,
            connectivityObserver = fakeConnectivityObserver
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshSavedArticleUpdatesRoomAndPreservesAllCollectionLinks() = runTest {
        val col1 = database.collectionDao().insertCollection(CollectionEntity(name = "Filosofia"))
        val col2 = database.collectionDao().insertCollection(CollectionEntity(name = "Grécia Antiga"))

        val savedId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Sócrates",
                url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                description = "Filósofo ateniense",
                content = "Conteúdo antigo sobre Sócrates.",
                imageUrl = "https://upload.wikimedia.org/socrates_old.jpg",
                lastUpdated = 1_000L,
                wikipediaPageId = 501L,
                lastSyncedAt = null
            ),
            collectionIds = listOf(col1, col2)
        )

        fakeWikipediaRepository.responses[501L] = Result.success(
            WikipediaArticle(
                pageId = 501L,
                title = "Sócrates",
                description = "Filósofo clássico ateniense",
                content = "Conteúdo atualizado e completo sobre Sócrates.",
                imageUrl = "https://upload.wikimedia.org/socrates_new.jpg",
                url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
            )
        )

        val outcome = refreshUseCase(articleId = savedId, nowMs = 50_000L)
        assertTrue(outcome is RefreshArticleOutcome.Updated)

        val reloaded = articleRepository.getArticleById(savedId)!!
        assertEquals("Conteúdo atualizado e completo sobre Sócrates.", reloaded.content)
        assertEquals("Filósofo clássico ateniense", reloaded.description)
        assertEquals("https://upload.wikimedia.org/socrates_new.jpg", reloaded.imageUrl)
        assertEquals(50_000L, reloaded.lastUpdated)
        assertEquals(50_000L, reloaded.lastSyncedAt)

        val linkedCollections = articleRepository.getCollectionIdsForArticle(savedId).first().toSet()
        assertEquals(setOf(col1, col2), linkedCollections)
    }

    @Test
    fun refreshSavedArticleSkipsCuratedArticleWithoutWikipediaPageId() = runTest {
        val curatedId = articleRepository.insertArticle(
            Article(
                title = "Filosofia Grega",
                url = "https://pt.wikipedia.org/wiki/Filosofia_grega",
                description = "Curadoria",
                content = "Conteúdo curado offline.",
                imageUrl = null,
                lastUpdated = 1_000L,
                wikipediaPageId = null,
                lastSyncedAt = null
            )
        )

        val outcome = refreshUseCase(articleId = curatedId, forceRefresh = true, nowMs = 10_000L)
        assertEquals(RefreshArticleOutcome.Skipped(SkipReason.NO_WIKIPEDIA_PAGE_ID), outcome)
        assertTrue(fakeWikipediaRepository.requestedPageIds.isEmpty())

        val reloaded = articleRepository.getArticleById(curatedId)!!
        assertNull(reloaded.lastSyncedAt)
        assertEquals("Conteúdo curado offline.", reloaded.content)
    }

    @Test
    fun refreshSavedArticleRespectsSyncPolicyAndAllowsForceRefresh() = runTest {
        val savedId = articleRepository.insertArticle(
            Article(
                title = "Aristóteles",
                url = "https://pt.wikipedia.org/wiki/Arist%C3%B3teles",
                description = "Filósofo",
                content = "Conteúdo inicial",
                imageUrl = null,
                lastUpdated = 1_000L,
                wikipediaPageId = 601L,
                lastSyncedAt = 90_000L
            )
        )
        fakeWikipediaRepository.responses[601L] = Result.success(
            WikipediaArticle(
                pageId = 601L,
                title = "Aristóteles",
                description = "Filósofo",
                content = "Conteúdo inicial",
                imageUrl = null,
                url = "https://pt.wikipedia.org/wiki/Arist%C3%B3teles"
            )
        )

        // Dentro da janela de 60_000 ms -> ignorado
        val skipped = refreshUseCase(articleId = savedId, forceRefresh = false, nowMs = 110_000L)
        assertEquals(RefreshArticleOutcome.Skipped(SkipReason.RECENTLY_SYNCED), skipped)
        assertTrue(fakeWikipediaRepository.requestedPageIds.isEmpty())

        // forceRefresh = true -> executa mesmo dentro da janela
        val forced = refreshUseCase(articleId = savedId, forceRefresh = true, nowMs = 110_000L)
        assertTrue(forced is RefreshArticleOutcome.Unchanged)
        assertEquals(listOf(601L), fakeWikipediaRepository.requestedPageIds)

        val updated = articleRepository.getArticleById(savedId)!!
        assertEquals(1_000L, updated.lastUpdated)
        assertEquals(110_000L, updated.lastSyncedAt)
    }

    @Test
    fun refreshSavedArticleHandlesRedirectWithoutDuplicatingRows() = runTest {
        val col1 = database.collectionDao().insertCollection(CollectionEntity(name = "História"))
        val savedId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Império Bizantino (antigo)",
                url = "https://pt.wikipedia.org/wiki/Imperio_Bizantino_Old",
                description = "Resumo antigo",
                content = "Conteúdo antigo",
                imageUrl = null,
                lastUpdated = 1_000L,
                wikipediaPageId = 700L
            ),
            collectionIds = listOf(col1)
        )

        fakeWikipediaRepository.responses[700L] = Result.success(
            WikipediaArticle(
                pageId = 701L,
                title = "Império Bizantino",
                description = "Império Romano do Oriente",
                content = "Conteúdo atualizado após redirecionamento.",
                imageUrl = "https://upload.wikimedia.org/bizancio.jpg",
                url = "https://pt.wikipedia.org/wiki/Imp%C3%A9rio_Bizantino"
            )
        )

        val outcome = refreshUseCase(articleId = savedId, nowMs = 80_000L)
        assertTrue(outcome is RefreshArticleOutcome.Updated)
        assertEquals(1, articleRepository.getSavedArticles().first().size)

        val stored = articleRepository.getArticleById(savedId)!!
        assertEquals(savedId, stored.id)
        assertEquals(701L, stored.wikipediaPageId)
        assertEquals("Império Bizantino", stored.title)
        assertEquals(listOf(col1), articleRepository.getCollectionIdsForArticle(savedId).first())
    }

    @Test
    fun refreshSavedArticleNeverUpdatesLastSyncedAtOnErrors() = runTest {
        val savedId = articleRepository.insertArticle(
            Article(
                title = "Isaac Newton",
                url = "https://pt.wikipedia.org/wiki/Isaac_Newton",
                description = "Físico e matemático",
                content = "Conteúdo local preservado.",
                imageUrl = "https://upload.wikimedia.org/newton.jpg",
                lastUpdated = 2_000L,
                wikipediaPageId = 801L,
                lastSyncedAt = 3_000L
            )
        )

        val errors = listOf(
            WikipediaException.NoConnection(),
            WikipediaException.Timeout(),
            WikipediaException.HttpError(503, "Serviço indisponível"),
            WikipediaException.ApiError("missingtitle", "Erro da API"),
            WikipediaException.InvalidResponse(),
            IllegalStateException("Erro inesperado")
        )

        errors.forEach { error ->
            fakeWikipediaRepository.responses[801L] = Result.failure(error)
            val outcome = refreshUseCase(articleId = savedId, forceRefresh = true, nowMs = 99_000L)
            assertTrue("Esperava Failed para ${error::class.simpleName}", outcome is RefreshArticleOutcome.Failed)

            val stored = articleRepository.getArticleById(savedId)!!
            assertEquals(3_000L, stored.lastSyncedAt)
            assertEquals(2_000L, stored.lastUpdated)
            assertEquals("Conteúdo local preservado.", stored.content)
        }
    }

    @Test
    fun refreshSavedArticleDoesNotAutoSaveMissingLocalArticle() = runTest {
        val outcome = refreshUseCase(articleId = 9999L, forceRefresh = true, nowMs = 10_000L)
        assertEquals(RefreshArticleOutcome.Skipped(SkipReason.ARTICLE_NOT_FOUND_LOCALLY), outcome)
        assertTrue(fakeWikipediaRepository.requestedPageIds.isEmpty())
        assertTrue(articleRepository.getSavedArticles().first().isEmpty())
    }

    @Test
    fun refreshSavedArticlePropagatesCancellationException() = runTest {
        val savedId = articleRepository.insertArticle(
            Article(
                title = "Cancelamento",
                url = "https://pt.wikipedia.org/wiki/Cancelamento",
                description = null,
                content = "Texto",
                imageUrl = null,
                lastUpdated = 1_000L,
                wikipediaPageId = 901L
            )
        )
        fakeWikipediaRepository.responses[901L] = Result.failure(CancellationException("Cancelado"))

        try {
            refreshUseCase(articleId = savedId, nowMs = 5_000L)
            fail("CancellationException deveria ter sido propagada")
        } catch (_: CancellationException) {
            // Esperado
        }
    }

    @Test
    fun syncSavedArticlesAbortsCleanlyWhenOfflineWithoutNetworkCalls() = runTest {
        fakeConnectivityObserver.setOnline(false)
        articleRepository.insertArticle(
            Article(
                title = "Galáxia",
                url = "https://pt.wikipedia.org/wiki/Gal%C3%A1xia",
                description = "Astronomia",
                content = "Conteúdo local",
                imageUrl = null,
                lastUpdated = 1_000L,
                wikipediaPageId = 1001L
            )
        )

        val result = syncUseCase(nowMs = 20_000L)
        assertTrue(result.abortedDueToNoConnection)
        assertFalse(result.isSuccess)
        assertTrue(fakeWikipediaRepository.requestedPageIds.isEmpty())
    }

    @Test
    fun syncSavedArticlesIsolatesPartialFailuresAndReportsDetailedCounts() = runTest {
        val colId = database.collectionDao().insertCollection(CollectionEntity(name = "Ciências"))

        val updatedId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Artigo Atualizável",
                url = "https://pt.wikipedia.org/wiki/Artigo_Atualizavel",
                description = "Desc",
                content = "Texto antigo",
                imageUrl = null,
                lastUpdated = 1_000L,
                wikipediaPageId = 1101L
            ),
            collectionIds = listOf(colId)
        )
        val unchangedId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Artigo Sem Mudança",
                url = "https://pt.wikipedia.org/wiki/Artigo_Sem_Mudanca",
                description = "Desc",
                content = "Texto igual",
                imageUrl = null,
                lastUpdated = 2_000L,
                wikipediaPageId = 1102L
            ),
            collectionIds = listOf(colId)
        )
        val failedId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Artigo Com Falha",
                url = "https://pt.wikipedia.org/wiki/Artigo_Com_Falha",
                description = "Desc",
                content = "Texto preservado",
                imageUrl = null,
                lastUpdated = 3_000L,
                wikipediaPageId = 1103L,
                lastSyncedAt = 4_000L
            ),
            collectionIds = listOf(colId)
        )
        val skippedId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Artigo Curado",
                url = "https://pt.wikipedia.org/wiki/Artigo_Curado",
                description = "Curadoria",
                content = "Texto curado",
                imageUrl = null,
                lastUpdated = 5_000L,
                wikipediaPageId = null
            ),
            collectionIds = listOf(colId)
        )

        fakeWikipediaRepository.responses[1101L] = Result.success(
            WikipediaArticle(
                pageId = 1101L,
                title = "Artigo Atualizável",
                description = "Desc",
                content = "Texto atualizado da Wikipédia",
                imageUrl = null,
                url = "https://pt.wikipedia.org/wiki/Artigo_Atualizavel"
            )
        )
        fakeWikipediaRepository.responses[1102L] = Result.success(
            WikipediaArticle(
                pageId = 1102L,
                title = "Artigo Sem Mudança",
                description = "Desc",
                content = "Texto igual",
                imageUrl = null,
                url = "https://pt.wikipedia.org/wiki/Artigo_Sem_Mudanca"
            )
        )
        fakeWikipediaRepository.responses[1103L] = Result.failure(
            WikipediaException.HttpError(500, "Erro interno da Wikipédia")
        )

        val result = syncUseCase(forceRefresh = true, nowMs = 150_000L)

        assertFalse(result.abortedDueToNoConnection)
        assertTrue(result.hasErrors)
        assertFalse(result.isSuccess)
        assertEquals(4, result.totalEvaluated)
        assertEquals(1, result.updatedCount)
        assertEquals(1, result.unchangedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(1, result.failedCount)
        assertEquals(1, result.failures.size)
        assertEquals(failedId, result.failures.first().articleId)
        assertEquals("Artigo Com Falha", result.failures.first().title)

        assertEquals("Texto atualizado da Wikipédia", articleRepository.getArticleById(updatedId)?.content)
        assertEquals(150_000L, articleRepository.getArticleById(updatedId)?.lastSyncedAt)
        assertEquals(2_000L, articleRepository.getArticleById(unchangedId)?.lastUpdated)
        assertEquals(150_000L, articleRepository.getArticleById(unchangedId)?.lastSyncedAt)
        assertEquals(4_000L, articleRepository.getArticleById(failedId)?.lastSyncedAt)
        assertNull(articleRepository.getArticleById(skippedId)?.lastSyncedAt)
    }

    @Test
    fun syncSavedArticlesPreservesPrePopulatedCuratedArticlesInRoom() = runTest {
        EruditoDatabase.populateInitialLibrary(database)

        val wikiColId = database.collectionDao().getAllCollectionsWithCount().first().first().collection.id
        val wikiArticleId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Astronomia",
                url = "https://pt.wikipedia.org/wiki/Astronomia",
                description = "Ciência espacial",
                content = "Texto antigo",
                imageUrl = null,
                lastUpdated = 1_000L,
                wikipediaPageId = 2020L
            ),
            collectionIds = listOf(wikiColId)
        )
        fakeWikipediaRepository.responses[2020L] = Result.success(
            WikipediaArticle(
                pageId = 2020L,
                title = "Astronomia",
                description = "Ciência espacial",
                content = "Texto atualizado",
                imageUrl = null,
                url = "https://pt.wikipedia.org/wiki/Astronomia"
            )
        )

        val result = syncUseCase(nowMs = 200_000L)

        assertEquals(4, result.totalEvaluated)
        assertEquals(1, result.updatedCount)
        assertEquals(3, result.skippedCount)
        assertEquals(0, result.failedCount)
        assertEquals(listOf(2020L), fakeWikipediaRepository.requestedPageIds)

        val allArticles = articleRepository.getSavedArticles().first()
        val curatedArticles = allArticles.filter { it.wikipediaPageId == null }
        assertEquals(3, curatedArticles.size)
        assertTrue(curatedArticles.all { it.lastSyncedAt == null })

        val syncedWiki = articleRepository.getArticleById(wikiArticleId)
        assertNotNull(syncedWiki)
        assertEquals("Texto atualizado", syncedWiki?.content)
        assertEquals(200_000L, syncedWiki?.lastSyncedAt)
    }

    @Test
    fun syncSavedArticlesPropagatesCancellationException() = runTest {
        articleRepository.insertArticle(
            Article(
                title = "Cancelamento em lote",
                url = "https://pt.wikipedia.org/wiki/Cancelamento_lote",
                description = null,
                content = "Texto",
                imageUrl = null,
                lastUpdated = 1_000L,
                wikipediaPageId = 3030L
            )
        )
        fakeWikipediaRepository.responses[3030L] = Result.failure(CancellationException("Corrotina cancelada"))

        try {
            syncUseCase(nowMs = 10_000L)
            fail("CancellationException deveria ter sido propagada no sync em lote")
        } catch (_: CancellationException) {
            // Esperado
        }
    }

    @Test
    fun syncPreservesStructuredHtmlAndFormulaCountsInRoomWhenRemoteIsDegraded() = runTest {
        val colId = database.collectionDao().insertCollection(CollectionEntity(name = "Álgebra"))
        val structuredHtml = """<section><p>Identidade <span class="erudito-math-inline" data-tex="a^2-b^2=(a-b)(a+b)"></span></p></section>"""

        val savedId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Álgebra linear",
                url = "https://pt.wikipedia.org/wiki/%C3%81lgebra_linear",
                description = "Ramo da matemática",
                content = structuredHtml,
                imageUrl = null,
                lastUpdated = 10_000L,
                wikipediaPageId = 10342L,
                lastSyncedAt = 10_000L,
                contentFormat = ArticleContentFormat.STRUCTURED_HTML,
                extractedFormulaCount = 8,
                unextractableFormulaCount = 0
            ),
            collectionIds = listOf(colId)
        )

        // Simula retorno remoto degradado (PLAIN_TEXT ou HTML com fórmula indisponível)
        fakeWikipediaRepository.responses[10342L] = Result.success(
            WikipediaArticle(
                pageId = 10342L,
                title = "Álgebra linear",
                description = "Ramo da matemática",
                content = "Texto degradado sem KaTeX",
                url = "https://pt.wikipedia.org/wiki/%C3%81lgebra_linear",
                hasExtractedContent = true,
                contentFormat = ArticleContentFormat.PLAIN_TEXT
            )
        )

        val outcome = refreshUseCase(articleId = savedId, forceRefresh = true, nowMs = 90_000L)
        assertTrue(outcome is RefreshArticleOutcome.Unchanged)

        val reloaded = articleRepository.getArticleById(savedId)!!
        assertEquals(structuredHtml, reloaded.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, reloaded.contentFormat)
        assertEquals(8, reloaded.extractedFormulaCount)
        assertEquals(0, reloaded.unextractableFormulaCount)
        assertEquals(10_000L, reloaded.lastUpdated)
        assertEquals(90_000L, reloaded.lastSyncedAt)
        assertEquals(listOf(colId), articleRepository.getCollectionIdsForArticle(savedId).first())
    }

    @Test
    fun syncUpgradesIncompleteStructuredHtmlInRoomToCompleteAndPreservesCrossRefs() = runTest {
        val col1 = database.collectionDao().insertCollection(CollectionEntity(name = "Cálculo"))
        val col2 = database.collectionDao().insertCollection(CollectionEntity(name = "Equações"))

        val savedId = articleRepository.saveArticleAndAssociateCollections(
            article = Article(
                title = "Equação diferencial",
                url = "https://pt.wikipedia.org/wiki/Equa%C3%A7%C3%A3o_diferencial",
                description = "Equação com derivadas",
                content = """<p><span class="erudito-math-unavailable">[Fórmula indisponível]</span></p>""",
                lastUpdated = 5_000L,
                wikipediaPageId = 6321L,
                lastSyncedAt = 5_000L,
                contentFormat = ArticleContentFormat.STRUCTURED_HTML,
                extractedFormulaCount = 3,
                unextractableFormulaCount = 1
            ),
            collectionIds = listOf(col1, col2)
        )

        val completeHtml = """<section><div class="erudito-math-block" data-tex="\frac{dy}{dx} = y"></div></section>"""
        fakeWikipediaRepository.responses[6321L] = Result.success(
            WikipediaArticle(
                pageId = 6321L,
                title = "Equação diferencial",
                description = "Equação com derivadas",
                content = completeHtml,
                url = "https://pt.wikipedia.org/wiki/Equa%C3%A7%C3%A3o_diferencial",
                hasExtractedContent = true,
                contentFormat = ArticleContentFormat.STRUCTURED_HTML,
                extractedFormulaCount = 4,
                unextractableFormulaCount = 0
            )
        )

        val outcome = refreshUseCase(articleId = savedId, forceRefresh = true, nowMs = 120_000L)
        assertTrue(outcome is RefreshArticleOutcome.Updated)

        val reloaded = articleRepository.getArticleById(savedId)!!
        assertEquals(completeHtml, reloaded.content)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, reloaded.contentFormat)
        assertEquals(4, reloaded.extractedFormulaCount)
        assertEquals(0, reloaded.unextractableFormulaCount)
        assertTrue(reloaded.isStructuredHtmlComplete)
        assertEquals(120_000L, reloaded.lastUpdated)
        assertEquals(120_000L, reloaded.lastSyncedAt)
        assertEquals(setOf(col1, col2), articleRepository.getCollectionIdsForArticle(savedId).first().toSet())
    }

    private class FakeSyncWikipediaRepository : WikipediaRepository {
        val responses = mutableMapOf<Long, Result<WikipediaArticle?>>()
        val requestedPageIds = mutableListOf<Long>()

        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = emptyList()

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? {
            requestedPageIds.add(pageId)
            val result = responses[pageId] ?: return null
            return result.getOrThrow()
        }
    }

    private class FakeSyncConnectivityObserver(online: Boolean) : ConnectivityObserver {
        private val onlineState = MutableStateFlow(online)
        override val isOnline: Flow<Boolean> = onlineState

        override fun isCurrentlyOnline(): Boolean = onlineState.value

        fun setOnline(online: Boolean) {
            onlineState.value = online
        }
    }
}
