package com.example.erudito.presentation.article_detail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.erudito.data.local.database.EruditoDatabase
import com.example.erudito.data.local.entity.CollectionEntity
import com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer
import com.example.erudito.data.repository.ArticleRepositoryImpl
import com.example.erudito.data.repository.CollectionRepositoryImpl
import com.example.erudito.domain.connectivity.FakeConnectivityObserver
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.CollectionRepository
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.repository.FakeCollectionRepository
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.sync.SyncPolicy
import com.example.erudito.domain.usecase.AddArticleToCollectionsUseCase
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionIdsForArticleUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.ObserveArticleByIdUseCase
import com.example.erudito.domain.usecase.RefreshSavedArticleUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArticleDetailSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildViewModel(
        articleId: Long,
        articleRepo: ArticleRepository,
        collectionRepo: CollectionRepository,
        wikipediaRepo: WikipediaRepository,
        connectivityObserver: FakeConnectivityObserver = FakeConnectivityObserver(initialOnline = true)
    ): ArticleDetailViewModel {
        return ArticleDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("articleId" to articleId)),
            observeArticleByIdUseCase = ObserveArticleByIdUseCase(articleRepo),
            getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
            createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
            addArticleToCollectionsUseCase = AddArticleToCollectionsUseCase(
                articleRepository = articleRepo,
                collectionRepository = collectionRepo
            ),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepo),
            getCollectionIdsForArticleUseCase = GetCollectionIdsForArticleUseCase(articleRepo),
            refreshSavedArticleUseCase = RefreshSavedArticleUseCase(
                articleRepository = articleRepo,
                wikipediaRepository = wikipediaRepo,
                connectivityObserver = connectivityObserver,
                syncPolicy = SyncPolicy()
            ),
            connectivityObserver = connectivityObserver,
            htmlProcessor = WikipediaHtmlSanitizer()
        )
    }

    @Test
    fun `manual refresh forces update despite recent lastSyncedAt updates Room reactively and guards concurrency`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = FakeCollectionRepository()
            val colId = collectionRepo.createCollection("Física")
            val recentSyncTime = System.currentTimeMillis()
            val savedId = articleRepo.saveArticleAndAssociateCollections(
                Article(
                    title = "Marie Curie",
                    url = "https://pt.wikipedia.org/wiki/Marie_Curie",
                    description = "Descrição antiga",
                    content = "Conteúdo antigo salvo localmente.",
                    lastUpdated = 1_000L,
                    wikipediaPageId = 42L,
                    lastSyncedAt = recentSyncTime
                ),
                listOf(colId)
            )
            val gate = CompletableDeferred<WikipediaArticle?>()
            val wikipediaRepo = RecordingWikipediaRepo(pendingGate = gate)
            val viewModel = buildViewModel(
                articleId = savedId,
                articleRepo = articleRepo,
                collectionRepo = collectionRepo,
                wikipediaRepo = wikipediaRepo
            )
            val feedbacks = mutableListOf<String>()
            val feedbackJob = launch { viewModel.feedbackEvents.collect { feedbacks.add(it) } }
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.canRefreshRemotely)

            // Dispara atualização manual (deve ignorar janela de 6h via forceRefresh = true)
            viewModel.refreshArticle()
            runCurrent()

            assertTrue(viewModel.uiState.value.isRefreshing)
            assertEquals(1, wikipediaRepo.callCount)

            // Clique simultâneo deve ser bloqueado
            viewModel.refreshArticle()
            runCurrent()
            assertEquals(1, wikipediaRepo.callCount)

            gate.complete(
                WikipediaArticle(
                    pageId = 42L,
                    title = "Marie Curie",
                    description = "Física e química polonesa",
                    content = "Conteúdo atualizado pela Wikipédia.",
                    url = "https://pt.wikipedia.org/wiki/Marie_Curie",
                    hasExtractedContent = true
                )
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRefreshing)
            val updated = viewModel.uiState.value.article!!
            assertEquals("Física e química polonesa", updated.description)
            assertEquals("Conteúdo atualizado pela Wikipédia.", updated.content)
            assertTrue(updated.lastUpdated > 1_000L)
            assertNotNull(updated.lastSyncedAt)
            assertEquals(listOf(colId), articleRepo.getCollectionIdsForArticle(savedId).first())
            assertEquals("Artigo atualizado com a versão mais recente da Wikipédia.", feedbacks.last())
            feedbackJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `manual refresh with identical remote content preserves lastUpdated updates lastSyncedAt and reports unchanged`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = FakeCollectionRepository()
            val savedId = articleRepo.insertArticle(
                Article(
                    title = "Sócrates",
                    url = "https://pt.wikipedia.org/wiki/Socrates",
                    description = "Filósofo grego",
                    content = "Conteúdo inalterado.",
                    imageUrl = "https://upload.wikimedia.org/socrates.jpg",
                    lastUpdated = 55_000L,
                    wikipediaPageId = 77L,
                    lastSyncedAt = 10_000L
                )
            )
            val wikipediaRepo = RecordingWikipediaRepo(
                article = WikipediaArticle(
                    pageId = 77L,
                    title = "Sócrates",
                    description = "Filósofo grego",
                    content = "Conteúdo inalterado.",
                    imageUrl = "https://upload.wikimedia.org/socrates.jpg",
                    url = "https://pt.wikipedia.org/wiki/Socrates",
                    hasExtractedContent = true
                )
            )
            val viewModel = buildViewModel(savedId, articleRepo, collectionRepo, wikipediaRepo)
            val feedbacks = mutableListOf<String>()
            val feedbackJob = launch { viewModel.feedbackEvents.collect { feedbacks.add(it) } }
            advanceUntilIdle()

            viewModel.refreshArticle()
            advanceUntilIdle()

            val article = viewModel.uiState.value.article!!
            assertEquals(55_000L, article.lastUpdated)
            assertTrue((article.lastSyncedAt ?: 0L) > 10_000L)
            assertEquals("O artigo já está atualizado. Nenhuma alteração encontrada.", feedbacks.last())
            feedbackJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `manual refresh failure and offline mode preserve local content N-N collections and hide technical errors`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = FakeCollectionRepository()
            val col1 = collectionRepo.createCollection("Filosofia")
            val col2 = collectionRepo.createCollection("História")
            val savedId = articleRepo.saveArticleAndAssociateCollections(
                Article(
                    title = "Platão",
                    url = "https://pt.wikipedia.org/wiki/Platao",
                    description = "Descrição preservada",
                    content = "Conteúdo local preservado.",
                    lastUpdated = 33_000L,
                    wikipediaPageId = 99L,
                    lastSyncedAt = 22_000L
                ),
                listOf(col1, col2)
            )
            val connectivityObserver = FakeConnectivityObserver(initialOnline = false)
            val wikipediaRepo = RecordingWikipediaRepo()
            val viewModel = buildViewModel(
                articleId = savedId,
                articleRepo = articleRepo,
                collectionRepo = collectionRepo,
                wikipediaRepo = wikipediaRepo,
                connectivityObserver = connectivityObserver
            )
            val feedbacks = mutableListOf<String>()
            val feedbackJob = launch { viewModel.feedbackEvents.collect { feedbacks.add(it) } }
            advanceUntilIdle()

            // Offline -> não chama rede e exibe mensagem objetiva
            viewModel.refreshArticle()
            advanceUntilIdle()
            assertEquals(0, wikipediaRepo.callCount)
            assertEquals("Dispositivo offline. Não foi possível atualizar o artigo agora.", feedbacks.last())

            // Volta a ficar online, porém falha com exceção técnica inesperada -> nunca vaza mensagem técnica
            connectivityObserver.setOnline(true)
            wikipediaRepo.exception = IllegalStateException("FATAL_SQL_RAW_STACK")
            viewModel.refreshArticle()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRefreshing)
            assertFalse(feedbacks.last().contains("FATAL_SQL_RAW_STACK"))
            assertEquals(
                "Não foi possível atualizar o artigo no momento. O conteúdo local foi preservado.",
                feedbacks.last()
            )
            val preserved = articleRepo.getArticleById(savedId)!!
            assertEquals("Conteúdo local preservado.", preserved.content)
            assertEquals(33_000L, preserved.lastUpdated)
            assertEquals(22_000L, preserved.lastSyncedAt)
            assertEquals(setOf(col1, col2), articleRepo.getCollectionIdsForArticle(savedId).first().toSet())

            // Falha WikipediaException exibe mensagem amigável de domínio
            wikipediaRepo.exception = WikipediaException.Timeout()
            viewModel.refreshArticle()
            advanceUntilIdle()
            assertEquals(WikipediaException.Timeout().message, feedbacks.last())
            feedbackJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `article without wikipediaPageId disables remote refresh and skips API cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = FakeCollectionRepository()
            val savedId = articleRepo.insertArticle(
                Article(
                    title = "Artigo Manual",
                    url = "https://pt.wikipedia.org/wiki/Manual",
                    content = "Criado sem pageId.",
                    lastUpdated = 1_000L,
                    wikipediaPageId = null
                )
            )
            val wikipediaRepo = RecordingWikipediaRepo()
            val viewModel = buildViewModel(savedId, articleRepo, collectionRepo, wikipediaRepo)
            val feedbacks = mutableListOf<String>()
            val feedbackJob = launch { viewModel.feedbackEvents.collect { feedbacks.add(it) } }
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.canRefreshRemotely)

            viewModel.refreshArticle()
            advanceUntilIdle()

            assertEquals(0, wikipediaRepo.callCount)
            assertEquals(
                "Este artigo local não possui vínculo com a Wikipédia para atualização.",
                feedbacks.last()
            )
            feedbackJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cancellation resets isRefreshing state cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = FakeCollectionRepository()
            val savedId = articleRepo.insertArticle(
                Article(
                    title = "Cancel",
                    url = "https://pt.wikipedia.org/wiki/Cancel",
                    content = "Texto",
                    wikipediaPageId = 123L
                )
            )
            val wikipediaRepo = RecordingWikipediaRepo(
                exception = CancellationException("Cancelado")
            )
            val viewModel = buildViewModel(savedId, articleRepo, collectionRepo, wikipediaRepo)
            advanceUntilIdle()

            viewModel.refreshArticle()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRefreshing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ArticleDetailScreen renders sync timestamp refresh button loading and no-pageId notice`() {
        val syncedArticle = Article(
            id = 1L,
            title = "Galileu",
            url = "https://pt.wikipedia.org/wiki/Galileu",
            content = "Astrônomo italiano.",
            lastUpdated = 1_700_000_000_000L,
            wikipediaPageId = 45L,
            lastSyncedAt = 1_700_050_000_000L
        )
        var currentState by mutableStateOf(
            ArticleDetailUiState(
                isLoading = false,
                article = syncedArticle,
                isLoadingCollections = false,
                isRefreshing = false
            )
        )

        composeTestRule.setContent {
            ArticleDetailScreenContent(
                uiState = currentState,
                onNavigateBack = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("article_detail_last_synced_at")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("article_detail_refresh_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Atualizar artigo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("article_detail_no_remote_update_notice").assertDoesNotExist()

        // Sem wikipediaPageId -> mostra aviso informativo e oculta botão de atualização
        currentState = ArticleDetailUiState(
            isLoading = false,
            article = syncedArticle.copy(wikipediaPageId = null, lastSyncedAt = null),
            isLoadingCollections = false
        )

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("article_detail_last_synced_at").assertDoesNotExist()
        composeTestRule.onNodeWithTag("article_detail_refresh_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("article_detail_no_remote_update_notice")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Artigo local sem vínculo remoto — atualização pela Wikipédia indisponível."
        ).assertIsDisplayed()
    }

    @Test
    fun `ArticleDetailScreen integrated with Room updates UI reactively on manual refresh`() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = Executor { runnable -> runnable.run() }
        val db = Room.inMemoryDatabaseBuilder(context, EruditoDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        try {
            val collectionRepo = CollectionRepositoryImpl(db.collectionDao())
            val articleRepo = ArticleRepositoryImpl(db.articleDao())
            val colId = db.collectionDao().insertCollection(
                CollectionEntity(name = "Astronomia", createdAt = 1_000L)
            )
            val savedId = articleRepo.saveArticleAndAssociateCollections(
                Article(
                    title = "Kepler",
                    url = "https://pt.wikipedia.org/wiki/Kepler",
                    description = "Astrônomo",
                    content = "Texto inicial local.",
                    lastUpdated = 1_000L,
                    wikipediaPageId = 505L
                ),
                listOf(colId)
            )
            val wikipediaRepo = RecordingWikipediaRepo(
                article = WikipediaArticle(
                    pageId = 505L,
                    title = "Johannes Kepler",
                    description = "Astrônomo alemão",
                    content = "Leis do movimento planetário atualizadas.",
                    url = "https://pt.wikipedia.org/wiki/Kepler",
                    hasExtractedContent = true
                )
            )
            val viewModel = buildViewModel(
                articleId = savedId,
                articleRepo = articleRepo,
                collectionRepo = collectionRepo,
                wikipediaRepo = wikipediaRepo
            )

            composeTestRule.setContent {
                ArticleDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Kepler").assertIsDisplayed()
            composeTestRule.onNodeWithTag("article_detail_refresh_button")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Artigo atualizado com a versão mais recente da Wikipédia.")
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Johannes Kepler")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("article_detail_last_synced_at")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Leis do movimento planetário atualizadas.")
                .performScrollTo()
                .assertIsDisplayed()
            assertEquals(listOf(colId), articleRepo.getCollectionIdsForArticle(savedId).first())
        } finally {
            Dispatchers.resetMain()
            db.close()
        }
    }

    private class RecordingWikipediaRepo(
        var article: WikipediaArticle? = null,
        var exception: Throwable? = null,
        var pendingGate: CompletableDeferred<WikipediaArticle?>? = null
    ) : WikipediaRepository {
        var callCount = 0

        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = emptyList()

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? {
            callCount++
            pendingGate?.let { gate ->
                pendingGate = null
                return gate.await()
            }
            exception?.let { throw it }
            return article
        }
    }
}
