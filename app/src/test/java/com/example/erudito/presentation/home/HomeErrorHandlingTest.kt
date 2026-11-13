package com.example.erudito.presentation.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.repository.FakeCollectionRepository
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
            val viewModel = HomeViewModel(
                getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
                createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
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
}
