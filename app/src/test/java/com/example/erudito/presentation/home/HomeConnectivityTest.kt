package com.example.erudito.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.erudito.domain.connectivity.FakeConnectivityObserver
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
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeConnectivityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `HomeScreen updates connectivity badges reactively between Online and Modo Offline`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver(initialOnline = true)

            val viewModel = HomeViewModel(
                getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
                createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo),
                connectivityObserver = connectivityObserver
            )

            composeTestRule.setContent {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToCollections = {},
                    onNavigateToCollectionDetail = {},
                    onNavigateToArticleDetail = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_connectivity_badge").assertIsDisplayed()
            composeTestRule.onNode(
                hasText("Online") and hasAnyAncestor(hasTestTag("home_connectivity_badge"))
            ).assertIsDisplayed()
            composeTestRule.onNode(
                hasText("Online") and hasAnyAncestor(hasTestTag("home_search_connectivity_badge")),
                useUnmergedTree = true
            ).assertExists()

            connectivityObserver.setOnline(false)
            composeTestRule.waitForIdle()

            composeTestRule.onNode(
                hasText("Modo Offline") and hasAnyAncestor(hasTestTag("home_connectivity_badge"))
            ).assertIsDisplayed()
            composeTestRule.onNode(
                hasText("Offline") and hasAnyAncestor(hasTestTag("home_search_connectivity_badge")),
                useUnmergedTree = true
            ).assertExists()

            connectivityObserver.setOnline(true)
            composeTestRule.waitForIdle()

            composeTestRule.onNode(
                hasText("Online") and hasAnyAncestor(hasTestTag("home_connectivity_badge"))
            ).assertIsDisplayed()
            composeTestRule.onNode(
                hasText("Online") and hasAnyAncestor(hasTestTag("home_search_connectivity_badge")),
                useUnmergedTree = true
            ).assertExists()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
