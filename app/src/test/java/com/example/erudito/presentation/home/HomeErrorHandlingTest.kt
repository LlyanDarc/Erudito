package com.example.erudito.presentation.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.erudito.domain.connectivity.FakeConnectivityObserver
import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.CollectionRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeErrorHandlingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `HomeScreen displays error message in Snackbar and clears it after showing`() {
        var clearErrorCalled = false

        composeTestRule.setContent {
            HomeScreenContent(
                uiState = HomeUiState(
                    isLoading = false,
                    error = "Falha ao criar coleção"
                ),
                onNavigateToCollections = {},
                onNavigateToCollectionDetail = {},
                onNavigateToArticleDetail = {},
                onCreateCollection = {},
                onRenameCollection = { _, _ -> },
                onDeleteCollection = {},
                onClearError = { clearErrorCalled = true }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Falha ao criar coleção", useUnmergedTree = true).assertExists()

        // Avança o relógio para concluir a exibição do Snackbar e acionar onClearError()
        composeTestRule.mainClock.advanceTimeBy(5_000L)
        composeTestRule.waitForIdle()
        assertTrue(clearErrorCalled)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `HomeViewModel populates error on failure and clears it via clearError`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver()
            val emptyWikipediaRepo = object : WikipediaRepository {
                override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = emptyList()
                override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? = null
            }
            val viewModel = HomeViewModel(
                getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
                createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo),
                syncSavedArticlesUseCase = SyncSavedArticlesUseCase(
                    articleRepository = articleRepo,
                    refreshSavedArticleUseCase = RefreshSavedArticleUseCase(
                        articleRepository = articleRepo,
                        wikipediaRepository = emptyWikipediaRepo,
                        connectivityObserver = connectivityObserver,
                        syncPolicy = SyncPolicy()
                    ),
                    connectivityObserver = connectivityObserver
                ),
                connectivityObserver = connectivityObserver
            )

            // Dispara erro de validação RN01 pelo ViewModel
            viewModel.createCollection("   ")

            assertEquals(
                "O nome da coleção não pode ser vazio ou composto apenas por espaços.",
                viewModel.uiState.value.error
            )

            viewModel.clearError()
            assertNull(viewModel.uiState.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `HomeViewModel reports error when renaming non-existent collection id`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val viewModel = buildHomeViewModel(collectionRepo)

            viewModel.renameCollection(id = 9999L, newName = "Novo Nome")

            assertEquals("Coleção não encontrada.", viewModel.uiState.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `HomeViewModel does not set UI error when create rename or delete throws CancellationException`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val cancellingRepo = object : CollectionRepository {
                override fun getCollections(): Flow<List<Collection>> = MutableStateFlow(emptyList())
                override fun getCollectionWithArticles(collectionId: Long): Flow<CollectionWithArticles?> =
                    MutableStateFlow(null)
                override suspend fun getCollectionById(collectionId: Long): Collection? = null
                override suspend fun createCollection(name: String): Long =
                    throw CancellationException("Cancelado")
                override suspend fun renameCollection(id: Long, newName: String): Unit =
                    throw CancellationException("Cancelado")
                override suspend fun deleteCollection(id: Long): Unit =
                    throw CancellationException("Cancelado")
            }
            val viewModel = buildHomeViewModel(cancellingRepo)

            viewModel.createCollection("Física")
            assertNull(viewModel.uiState.value.error)

            viewModel.renameCollection(1L, "Química")
            assertNull(viewModel.uiState.value.error)

            viewModel.deleteCollection(1L)
            assertNull(viewModel.uiState.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun buildHomeViewModel(collectionRepo: CollectionRepository): HomeViewModel {
        val articleRepo = FakeArticleRepository()
        val connectivityObserver = FakeConnectivityObserver()
        val emptyWikipediaRepo = object : WikipediaRepository {
            override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = emptyList()
            override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? = null
        }
        return HomeViewModel(
            getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
            getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
            createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
            renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
            deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo),
            syncSavedArticlesUseCase = SyncSavedArticlesUseCase(
                articleRepository = articleRepo,
                refreshSavedArticleUseCase = RefreshSavedArticleUseCase(
                    articleRepository = articleRepo,
                    wikipediaRepository = emptyWikipediaRepo,
                    connectivityObserver = connectivityObserver,
                    syncPolicy = SyncPolicy()
                ),
                connectivityObserver = connectivityObserver
            ),
            connectivityObserver = connectivityObserver
        )
    }
}
