package com.example.erudito.presentation.collections

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.repository.FakeCollectionRepository
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import com.example.erudito.presentation.home.HomeScreen
import com.example.erudito.presentation.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CollectionFeedbackMessagesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `HomeViewModel emits distinct creation, rename, and deletion messages without reusing previous messages`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val viewModel = HomeViewModel(
                getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
                createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
            )

            val events = mutableListOf<String>()
            val collectorJob = launch(dispatcher) {
                viewModel.feedbackEvents.collect { events.add(it) }
            }

            viewModel.createCollection("g")
            val createdId = viewModel.uiState.value.collections.first { it.name == "g" }.id

            viewModel.renameCollection(createdId, "g atualizado")
            viewModel.deleteCollection(createdId)

            assertEquals(
                listOf(
                    "Coleção \"g\" criada com sucesso!",
                    "Coleção renomeada para \"g atualizado\"!",
                    "Coleção \"g atualizado\" excluída com sucesso!"
                ),
                events
            )
            assertNull(viewModel.uiState.value.error)

            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `CollectionsViewModel emits distinct messages for create, rename, and delete and preserves error handling`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val viewModel = CollectionsViewModel(
                getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
            )

            val events = mutableListOf<String>()
            val collectorJob = launch(dispatcher) {
                viewModel.feedbackEvents.collect { events.add(it) }
            }

            viewModel.createCollection("g")
            val createdId = viewModel.uiState.value.collections.first { it.name == "g" }.id
            viewModel.renameCollection(createdId, "g2")
            viewModel.deleteCollection(createdId)

            assertEquals(
                listOf(
                    "Coleção \"g\" criada com sucesso!",
                    "Coleção renomeada para \"g2\"!",
                    "Coleção \"g2\" excluída com sucesso!"
                ),
                events
            )

            // Garante que o tratamento de erro continua funcionando
            viewModel.createCollection("   ")
            assertEquals(
                "O nome da coleção não pode ser vazio ou composto apenas por espaços.",
                viewModel.uiState.value.errorMessage
            )
            viewModel.clearFeedbackMessage()
            assertNull(viewModel.uiState.value.errorMessage)

            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `CollectionsScreen displays deletion message and never replays consumed creation message after navigation`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val viewModel = CollectionsViewModel(
                getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
            )

            var showCollectionsScreen by mutableStateOf(true)

            composeTestRule.setContent {
                if (showCollectionsScreen) {
                    CollectionsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {},
                        onNavigateToCollectionDetail = {}
                    )
                }
            }

            // 1. Cria a coleção "g"
            viewModel.createCollection("g")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Coleção \"g\" criada com sucesso!", useUnmergedTree = true).assertExists()

            // 2. Simula sair da tela antes de terminar o Snackbar e retornar depois
            showCollectionsScreen = false
            composeTestRule.waitForIdle()
            showCollectionsScreen = true
            composeTestRule.waitForIdle()

            // A mensagem antiga de criação NÃO deve reaparecer após retorno à tela
            composeTestRule.onNodeWithText("Coleção \"g\" criada com sucesso!", useUnmergedTree = true).assertDoesNotExist()

            // 3. Exclui a coleção "g" pela UI da lista
            val createdId = viewModel.uiState.value.collections.first { it.name == "g" }.id
            composeTestRule.onNodeWithTag("collection_menu_button_$createdId", useUnmergedTree = true).performClick()
            composeTestRule.onNodeWithTag("menu_delete_$createdId", useUnmergedTree = true).performClick()
            composeTestRule.onNodeWithTag("confirm_delete_button", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Coleção \"g\" excluída com sucesso!", useUnmergedTree = true).assertExists()
            composeTestRule.onNodeWithText("Coleção \"g\" criada com sucesso!", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `HomeScreen displays deletion feedback message when deleting collection g`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val viewModel = HomeViewModel(
                getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
                createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
            )

            composeTestRule.setContent {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToCollections = {},
                    onNavigateToCollectionDetail = {},
                    onNavigateToArticleDetail = {}
                )
            }

            viewModel.createCollection("g")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Coleção \"g\" criada com sucesso!", useUnmergedTree = true).assertExists()

            // Avança o relógio para encerrar o Snackbar de criação e garantir que o botão de menu não fique coberto
            composeTestRule.mainClock.advanceTimeBy(5_000L)
            composeTestRule.waitForIdle()

            val createdId = viewModel.uiState.value.collections.first { it.name == "g" }.id
            composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_menu_button_$createdId", useUnmergedTree = true)
                .performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("menu_delete_$createdId", useUnmergedTree = true).performClick()
            composeTestRule.onNodeWithTag("confirm_delete_button", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Coleção \"g\" excluída com sucesso!", useUnmergedTree = true).assertExists()
            composeTestRule.onNodeWithText("Coleção \"g\" criada com sucesso!", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `HomeScreen immediately replaces active creation snackbar with deletion snackbar`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val viewModel = HomeViewModel(
                getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
                createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
            )

            composeTestRule.setContent {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToCollections = {},
                    onNavigateToCollectionDetail = {},
                    onNavigateToArticleDetail = {}
                )
            }

            viewModel.createCollection("g")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Coleção \"g\" criada com sucesso!", useUnmergedTree = true).assertExists()

            val createdId = viewModel.uiState.value.collections.first { it.name == "g" }.id
            viewModel.deleteCollection(createdId)
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Coleção \"g\" excluída com sucesso!", useUnmergedTree = true).assertExists()
            composeTestRule.onNodeWithText("Coleção \"g\" criada com sucesso!", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
