package com.example.erudito.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.erudito.domain.connectivity.FakeConnectivityObserver
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.repository.FakeCollectionRepository
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.sync.SyncPolicy
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.RefreshSavedArticleUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import com.example.erudito.domain.usecase.SyncSavedArticlesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildHomeViewModel(
        collectionRepo: FakeCollectionRepository,
        articleRepo: ArticleRepository,
        wikipediaRepo: WikipediaRepository,
        connectivityObserver: FakeConnectivityObserver,
        syncPolicy: SyncPolicy = SyncPolicy()
    ): HomeViewModel {
        val refreshUseCase = RefreshSavedArticleUseCase(
            articleRepository = articleRepo,
            wikipediaRepository = wikipediaRepo,
            connectivityObserver = connectivityObserver,
            syncPolicy = syncPolicy
        )
        val syncUseCase = SyncSavedArticlesUseCase(
            articleRepository = articleRepo,
            refreshSavedArticleUseCase = refreshUseCase,
            connectivityObserver = connectivityObserver
        )
        return HomeViewModel(
            getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
            getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
            createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
            renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
            deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo),
            syncSavedArticlesUseCase = syncUseCase,
            connectivityObserver = connectivityObserver
        )
    }

    @Test
    fun `auto sync respects SyncPolicy freshness and skips API calls for recently synced articles`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver(initialOnline = true)
            val wikipediaRepo = RecordingWikipediaRepository()

            articleRepo.insertArticle(
                Article(
                    title = "Sócrates",
                    url = "https://pt.wikipedia.org/wiki/Socrates",
                    description = "Filósofo",
                    content = "Conteúdo atual",
                    lastUpdated = 1_000L,
                    wikipediaPageId = 10L,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )

            val viewModel = buildHomeViewModel(
                collectionRepo = collectionRepo,
                articleRepo = articleRepo,
                wikipediaRepo = wikipediaRepo,
                connectivityObserver = connectivityObserver
            )
            advanceUntilIdle()

            assertEquals(0, wikipediaRepo.totalCalls)
            assertEquals(HomeSyncStatus.Idle, viewModel.uiState.value.syncStatus)
            assertFalse(viewModel.uiState.value.isSyncing)
            assertEquals(1, viewModel.uiState.value.recentArticles.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `auto sync runs once per Home initialization updates stale articles and ignores subsequent Room emissions`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver(initialOnline = true)
            val wikipediaRepo = RecordingWikipediaRepository(
                articlesByPageId = mutableMapOf(
                    42L to WikipediaArticle(
                        pageId = 42L,
                        title = "Marie Curie",
                        description = "Física e química premiada",
                        content = "Conteúdo atualizado da Wikipédia.",
                        url = "https://pt.wikipedia.org/wiki/Marie_Curie",
                        hasExtractedContent = true
                    )
                )
            )

            val initialId = articleRepo.insertArticle(
                Article(
                    title = "Marie Curie",
                    url = "https://pt.wikipedia.org/wiki/Marie_Curie",
                    description = "Descrição antiga",
                    content = "Conteúdo antigo",
                    lastUpdated = 10_000L,
                    wikipediaPageId = 42L,
                    lastSyncedAt = null
                )
            )

            val viewModel = buildHomeViewModel(
                collectionRepo = collectionRepo,
                articleRepo = articleRepo,
                wikipediaRepo = wikipediaRepo,
                connectivityObserver = connectivityObserver
            )
            advanceUntilIdle()

            assertEquals(1, wikipediaRepo.callsForPage(42L))
            val status = viewModel.uiState.value.syncStatus as HomeSyncStatus.CompletedWithUpdates
            assertEquals(1, status.updatedCount)

            val persisted = articleRepo.getArticleById(initialId)!!
            assertEquals("Conteúdo atualizado da Wikipédia.", persisted.content)
            assertTrue(persisted.lastSyncedAt != null)

            // Nova emissão local do Room não deve disparar nova sincronização automática
            articleRepo.insertArticle(
                Article(
                    title = "Platão",
                    url = "https://pt.wikipedia.org/wiki/Platao",
                    content = "Conteúdo Platão",
                    lastUpdated = 20_000L,
                    wikipediaPageId = 77L,
                    lastSyncedAt = null
                )
            )
            advanceUntilIdle()

            assertEquals(1, wikipediaRepo.totalCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `blocks duplicate concurrent sync while initial sync is in flight`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver(initialOnline = true)
            val gate = CompletableDeferred<WikipediaArticle?>()
            val wikipediaRepo = RecordingWikipediaRepository(
                pendingGates = mutableMapOf(15L to gate)
            )

            articleRepo.insertArticle(
                Article(
                    title = "Aristóteles",
                    url = "https://pt.wikipedia.org/wiki/Aristoteles",
                    content = "Conteúdo local",
                    lastUpdated = 5_000L,
                    wikipediaPageId = 15L,
                    lastSyncedAt = null
                )
            )

            val viewModel = buildHomeViewModel(
                collectionRepo = collectionRepo,
                articleRepo = articleRepo,
                wikipediaRepo = wikipediaRepo,
                connectivityObserver = connectivityObserver
            )
            runCurrent()

            assertTrue(viewModel.uiState.value.isSyncing)
            assertEquals(HomeSyncStatus.Syncing, viewModel.uiState.value.syncStatus)
            assertEquals(1, wikipediaRepo.totalCalls)

            // Tentativa concorrente enquanto sincronização está ativa deve ser ignorada
            viewModel.retrySync()
            viewModel.dismissSyncStatus()
            runCurrent()

            assertEquals(1, wikipediaRepo.totalCalls)
            assertTrue(viewModel.uiState.value.isSyncing)

            gate.complete(
                WikipediaArticle(
                    pageId = 15L,
                    title = "Aristóteles",
                    content = "Conteúdo local",
                    url = "https://pt.wikipedia.org/wiki/Aristoteles",
                    hasExtractedContent = true
                )
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSyncing)
            assertEquals(HomeSyncStatus.CompletedNoChanges(checkedCount = 1), viewModel.uiState.value.syncStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `offline mode skips network calls shows local articles immediately and syncs when connection returns`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver(initialOnline = false)
            val wikipediaRepo = RecordingWikipediaRepository(
                articlesByPageId = mutableMapOf(
                    88L to WikipediaArticle(
                        pageId = 88L,
                        title = "Nikola Tesla",
                        content = "Versão atualizada de Tesla.",
                        url = "https://pt.wikipedia.org/wiki/Nikola_Tesla",
                        hasExtractedContent = true
                    )
                )
            )

            articleRepo.insertArticle(
                Article(
                    title = "Nikola Tesla",
                    url = "https://pt.wikipedia.org/wiki/Nikola_Tesla",
                    content = "Versão local de Tesla.",
                    lastUpdated = 1_000L,
                    wikipediaPageId = 88L,
                    lastSyncedAt = null
                )
            )

            val viewModel = buildHomeViewModel(
                collectionRepo = collectionRepo,
                articleRepo = articleRepo,
                wikipediaRepo = wikipediaRepo,
                connectivityObserver = connectivityObserver
            )
            advanceUntilIdle()

            assertEquals(0, wikipediaRepo.totalCalls)
            assertFalse(viewModel.uiState.value.isOnline)
            assertEquals(HomeSyncStatus.Offline, viewModel.uiState.value.syncStatus)
            assertEquals(1, viewModel.uiState.value.recentArticles.size)
            assertEquals("Versão local de Tesla.", viewModel.uiState.value.recentArticles.first().content)

            // Recupera conexão -> dispara sincronização automática uma única vez
            connectivityObserver.setOnline(true)
            advanceUntilIdle()

            assertEquals(1, wikipediaRepo.callsForPage(88L))
            assertTrue(viewModel.uiState.value.isOnline)
            assertEquals(
                HomeSyncStatus.CompletedWithUpdates(updatedCount = 1, unchangedCount = 0),
                viewModel.uiState.value.syncStatus
            )
            assertEquals(
                "Versão atualizada de Tesla.",
                articleRepo.getSavedArticles().first().first().content
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `partial failure exposes retry and total failure never shows success or leaks technical message`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver(initialOnline = true)
            val wikipediaRepo = RecordingWikipediaRepository(
                articlesByPageId = mutableMapOf(
                    1L to WikipediaArticle(
                        pageId = 1L,
                        title = "Artigo 1",
                        content = "Conteúdo 1 atualizado",
                        url = "https://pt.wikipedia.org/wiki/1",
                        hasExtractedContent = true
                    )
                ),
                exceptionsByPageId = mutableMapOf(
                    2L to IllegalStateException("SQLITE_BUSY: raw internal trace")
                )
            )

            articleRepo.insertArticle(
                Article(
                    title = "Artigo 1",
                    url = "https://pt.wikipedia.org/wiki/1",
                    content = "Conteúdo 1 antigo",
                    lastUpdated = 1_000L,
                    wikipediaPageId = 1L
                )
            )
            articleRepo.insertArticle(
                Article(
                    title = "Artigo 2",
                    url = "https://pt.wikipedia.org/wiki/2",
                    content = "Conteúdo 2 preservado",
                    lastUpdated = 2_000L,
                    wikipediaPageId = 2L
                )
            )

            val viewModel = buildHomeViewModel(
                collectionRepo = collectionRepo,
                articleRepo = articleRepo,
                wikipediaRepo = wikipediaRepo,
                connectivityObserver = connectivityObserver
            )
            val feedbacks = mutableListOf<String>()
            val feedbackJob = launch { viewModel.feedbackEvents.collect { feedbacks.add(it) } }
            advanceUntilIdle()

            val partial = viewModel.uiState.value.syncStatus as HomeSyncStatus.PartialFailure
            assertEquals(1, partial.updatedCount)
            assertEquals(1, partial.failedCount)
            assertTrue(feedbacks.isEmpty())

            // Agora falha total com exceção técnica
            wikipediaRepo.exceptionsByPageId[1L] = RuntimeException("Retrofit internal crash")
            wikipediaRepo.articlesByPageId.clear()
            // Força novo artigo elegível para re-sincronizar ambos
            articleRepo.insertArticle(
                Article(
                    title = "Artigo 3",
                    url = "https://pt.wikipedia.org/wiki/3",
                    content = "Conteúdo 3",
                    lastUpdated = 3_000L,
                    wikipediaPageId = 3L
                )
            )
            wikipediaRepo.exceptionsByPageId[3L] = WikipediaException.HttpError(
                statusCode = 503,
                message = "Serviço temporariamente indisponível."
            )

            viewModel.retrySync()
            advanceUntilIdle()

            val failed = viewModel.uiState.value.syncStatus as HomeSyncStatus.Failed
            assertEquals(2, failed.failedCount)
            assertFalse(viewModel.uiState.value.isSyncing)
            assertFalse(feedbacks.last().contains("Retrofit internal crash"))
            assertEquals("Não foi possível sincronizar os artigos salvos no momento.", feedbacks.last())
            feedbackJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cancellation during sync resets syncing state instead of sticking in loading`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver(initialOnline = true)
            val wikipediaRepo = RecordingWikipediaRepository(
                exceptionsByPageId = mutableMapOf(
                    50L to CancellationException("Cancelado pelo teste")
                )
            )
            articleRepo.insertArticle(
                Article(
                    title = "Cancelamento",
                    url = "https://pt.wikipedia.org/wiki/Cancelamento",
                    content = "Conteúdo",
                    lastUpdated = 1_000L,
                    wikipediaPageId = 50L
                )
            )

            val viewModel = buildHomeViewModel(
                collectionRepo = collectionRepo,
                articleRepo = articleRepo,
                wikipediaRepo = wikipediaRepo,
                connectivityObserver = connectivityObserver
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSyncing)
            assertEquals(HomeSyncStatus.Idle, viewModel.uiState.value.syncStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `HomeScreenContent renders sync status banners accurately and allows retry and dismiss`() {
        var retried = false
        var dismissed = false
        var syncStatusState by mutableStateOf<HomeSyncStatus>(HomeSyncStatus.Syncing)

        composeTestRule.setContent {
            HomeScreenContent(
                uiState = HomeUiState(
                    isLoading = false,
                    isOnline = true,
                    syncStatus = syncStatusState
                ),
                onNavigateToCollections = {},
                onNavigateToCollectionDetail = {},
                onNavigateToArticleDetail = {},
                onCreateCollection = {},
                onRenameCollection = { _, _ -> },
                onDeleteCollection = {},
                onRetrySync = { retried = true },
                onDismissSyncStatus = { dismissed = true }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_sync_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_sync_progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sincronizando artigos salvos...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_sync_dismiss_button").assertDoesNotExist()

        // Atualiza para PartialFailure com botões de tentar novamente e dispensar
        syncStatusState = HomeSyncStatus.PartialFailure(
            updatedCount = 2,
            unchangedCount = 1,
            failedCount = 1
        )

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sincronização parcial: 2 atualizado(s) e 1 falha(s).").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_sync_retry_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(retried)
        composeTestRule.onNodeWithTag("home_sync_dismiss_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(dismissed)
    }

    private class RecordingWikipediaRepository(
        val articlesByPageId: MutableMap<Long, WikipediaArticle> = mutableMapOf(),
        val exceptionsByPageId: MutableMap<Long, Throwable> = mutableMapOf(),
        val pendingGates: MutableMap<Long, CompletableDeferred<WikipediaArticle?>> = mutableMapOf()
    ) : WikipediaRepository {
        private val callCounts = mutableMapOf<Long, Int>()
        val totalCalls: Int get() = callCounts.values.sum()

        fun callsForPage(pageId: Long): Int = callCounts[pageId] ?: 0

        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = emptyList()

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? {
            callCounts[pageId] = (callCounts[pageId] ?: 0) + 1
            pendingGates.remove(pageId)?.let { gate ->
                return gate.await()
            }
            exceptionsByPageId[pageId]?.let { throw it }
            return articlesByPageId[pageId]
        }
    }
}
