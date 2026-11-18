package com.example.erudito.presentation.wikipedia_article

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer
import com.example.erudito.domain.connectivity.FakeConnectivityObserver
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.repository.FakeCollectionRepository
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.sync.SyncPolicy
import com.example.erudito.domain.usecase.AddArticleToCollectionsUseCase
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionIdsForArticleUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticleByWikipediaPageIdUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
import com.example.erudito.domain.usecase.RefreshSavedArticleUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import com.example.erudito.domain.usecase.SaveWikipediaArticleToCollectionsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WikipediaArticleRefreshSavedCopyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildViewModel(
        pageId: Long,
        articleRepo: FakeArticleRepository,
        collectionRepo: FakeCollectionRepository,
        wikipediaRepo: WikipediaRepository,
        connectivityObserver: FakeConnectivityObserver = FakeConnectivityObserver(initialOnline = true)
    ): WikipediaArticleViewModel {
        return WikipediaArticleViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to pageId)),
            getWikipediaArticleUseCase = GetWikipediaArticleUseCase(wikipediaRepo),
            getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
            createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
            getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
            saveWikipediaArticleToCollectionsUseCase = SaveWikipediaArticleToCollectionsUseCase(
                articleRepository = articleRepo,
                collectionRepository = collectionRepo
            ),
            addArticleToCollectionsUseCase = AddArticleToCollectionsUseCase(
                articleRepository = articleRepo,
                collectionRepository = collectionRepo
            ),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepo),
            getCollectionIdsForArticleUseCase = GetCollectionIdsForArticleUseCase(articleRepo),
            getSavedArticleByWikipediaPageIdUseCase = GetSavedArticleByWikipediaPageIdUseCase(articleRepo),
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
    fun `unsaved online article hides refresh saved copy action and never auto saves`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = FakeCollectionRepository()
            val wikipediaRepo = MutableWikipediaRepo(
                article = WikipediaArticle(
                    pageId = 42L,
                    title = "Marie Curie",
                    content = "Versão online",
                    url = "https://pt.wikipedia.org/wiki/Marie_Curie",
                    hasExtractedContent = true
                )
            )
            val viewModel = buildViewModel(42L, articleRepo, collectionRepo, wikipediaRepo)
            advanceUntilIdle()

            assertNull(viewModel.collectionsUiState.value.savedArticleId)
            assertFalse(viewModel.collectionsUiState.value.canRefreshSavedCopy)
            assertTrue(articleRepo.getSavedArticles().first().isEmpty())

            viewModel.refreshSavedCopy()
            advanceUntilIdle()
            assertEquals(1, wikipediaRepo.callCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `saved copy refresh updates local Room article guards concurrency and preserves on failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = FakeCollectionRepository()
            val colId = collectionRepo.createCollection("Ciências")
            val savedId = articleRepo.saveArticleAndAssociateCollections(
                Article(
                    title = "Sócrates",
                    url = "https://pt.wikipedia.org/wiki/Socrates",
                    description = "Filósofo",
                    content = "Conteúdo antigo salvo",
                    lastUpdated = 1_000L,
                    wikipediaPageId = 77L,
                    lastSyncedAt = System.currentTimeMillis()
                ),
                listOf(colId)
            )
            val wikipediaRepo = MutableWikipediaRepo(
                article = WikipediaArticle(
                    pageId = 77L,
                    title = "Sócrates",
                    description = "Filósofo",
                    content = "Conteúdo antigo salvo",
                    url = "https://pt.wikipedia.org/wiki/Socrates",
                    hasExtractedContent = true
                )
            )
            val viewModel = buildViewModel(77L, articleRepo, collectionRepo, wikipediaRepo)
            val feedbacks = mutableListOf<String>()
            val feedbackJob = launch { viewModel.feedbackEvents.collect { feedbacks.add(it) } }
            advanceUntilIdle()

            assertTrue(viewModel.collectionsUiState.value.canRefreshSavedCopy)
            assertEquals(savedId, viewModel.collectionsUiState.value.savedArticleId)

            // Prepara nova versão remota atrás de gate para verificar concorrência
            val gate = CompletableDeferred<WikipediaArticle?>()
            wikipediaRepo.pendingGate = gate
            viewModel.refreshSavedCopy()
            runCurrent()

            assertTrue(viewModel.collectionsUiState.value.isRefreshingSavedCopy)
            assertEquals(2, wikipediaRepo.callCount)

            viewModel.refreshSavedCopy()
            runCurrent()
            assertEquals(2, wikipediaRepo.callCount)

            gate.complete(
                WikipediaArticle(
                    pageId = 77L,
                    title = "Sócrates",
                    description = "Filósofo ateniense clássico",
                    content = "Conteúdo atualizado da Wikipédia.",
                    url = "https://pt.wikipedia.org/wiki/Socrates",
                    hasExtractedContent = true
                )
            )
            advanceUntilIdle()

            assertFalse(viewModel.collectionsUiState.value.isRefreshingSavedCopy)
            val updated = articleRepo.getArticleById(savedId)!!
            assertEquals("Conteúdo atualizado da Wikipédia.", updated.content)
            assertNotNull(updated.lastSyncedAt)
            assertEquals(
                "Cópia salva atualizada com a versão mais recente da Wikipédia.",
                feedbacks.last()
            )
            assertEquals(
                "Conteúdo atualizado da Wikipédia.",
                (viewModel.uiState.value as WikipediaArticleUiState.Success).article.content
            )

            // Falha na atualização seguinte preserva cópia local e não vaza mensagem técnica
            wikipediaRepo.exception = IllegalStateException("RAW_INTERNAL_DB_ERR")
            viewModel.refreshSavedCopy()
            advanceUntilIdle()

            assertFalse(viewModel.collectionsUiState.value.isRefreshingSavedCopy)
            assertFalse(feedbacks.last().contains("RAW_INTERNAL_DB_ERR"))
            assertEquals(
                "Não foi possível atualizar a cópia salva. O conteúdo local foi preservado.",
                feedbacks.last()
            )
            assertEquals("Conteúdo atualizado da Wikipédia.", articleRepo.getArticleById(savedId)!!.content)
            feedbackJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `WikipediaArticleScreen shows refresh saved copy button only when saved copy exists`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = FakeCollectionRepository()
            val colId = kotlinx.coroutines.runBlocking { collectionRepo.createCollection("Filosofia") }
            kotlinx.coroutines.runBlocking {
                articleRepo.saveArticleAndAssociateCollections(
                    Article(
                        title = "Aristóteles",
                        url = "https://pt.wikipedia.org/wiki/Aristoteles",
                        content = "Versão local antiga",
                        lastUpdated = 1_000L,
                        wikipediaPageId = 90L
                    ),
                    listOf(colId)
                )
            }
            val wikipediaRepo = MutableWikipediaRepo(
                article = WikipediaArticle(
                    pageId = 90L,
                    title = "Aristóteles",
                    content = "Versão online mais nova",
                    url = "https://pt.wikipedia.org/wiki/Aristoteles",
                    hasExtractedContent = true
                )
            )
            val viewModel = buildViewModel(90L, articleRepo, collectionRepo, wikipediaRepo)

            composeTestRule.setContent {
                WikipediaArticleScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("wikipedia_article_refresh_saved_button")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Cópia salva atualizada com a versão mais recente da Wikipédia.")
                .assertIsDisplayed()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class MutableWikipediaRepo(
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
