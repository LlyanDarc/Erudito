package com.example.erudito.presentation.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.usecase.SearchWikipediaArticlesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SearchScreen displays initial informative state`() {
        composeTestRule.setContent {
            SearchScreenContent(
                uiState = SearchUiState(),
                onNavigateBack = {},
                onQueryChange = {},
                onSearch = {},
                onRetry = {},
                onClearSearch = {},
                onArticleSelected = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_initial_state").assertExists()
        composeTestRule.onNodeWithText("Explore a Wikipédia").assertExists()
    }

    @Test
    fun `SearchScreen displays loading indicator when searching`() {
        composeTestRule.setContent {
            SearchScreenContent(
                uiState = SearchUiState(
                    query = "Ada Lovelace",
                    isLoading = true
                ),
                onNavigateBack = {},
                onQueryChange = {},
                onSearch = {},
                onRetry = {},
                onClearSearch = {},
                onArticleSelected = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_loading_state").assertExists()
        composeTestRule.onNodeWithText("Pesquisando na Wikipédia...").assertExists()
    }

    @Test
    fun `SearchScreen displays results with and without optional image or description and invokes callback with pageId`() {
        var selectedPageId: Long? = null
        val results = listOf(
            WikipediaSearchResult(
                pageId = 11L,
                title = "Marie Curie",
                description = "Física e química franco-polonesa",
                snippet = "Primeira pessoa a receber dois Prêmios Nobel.",
                thumbnailUrl = "https://upload.wikimedia.org/marie_curie.jpg",
                url = "https://pt.wikipedia.org/wiki/Marie_Curie"
            ),
            WikipediaSearchResult(
                pageId = 22L,
                title = "Radioatividade",
                description = null,
                snippet = null,
                thumbnailUrl = null,
                url = "https://pt.wikipedia.org/wiki/Radioatividade"
            )
        )

        composeTestRule.setContent {
            SearchScreenContent(
                uiState = SearchUiState(
                    query = "Curie",
                    isLoading = false,
                    hasSearched = true,
                    lastSearchedQuery = "Curie",
                    results = results
                ),
                onNavigateBack = {},
                onQueryChange = {},
                onSearch = {},
                onRetry = {},
                onClearSearch = {},
                onArticleSelected = { selectedPageId = it }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_results_list").assertExists()
        composeTestRule.onNodeWithText("2 resultados para \"Curie\"").assertExists()

        // Item completo com imagem, descrição e trecho
        composeTestRule.onNodeWithText("Marie Curie").assertExists()
        composeTestRule.onNodeWithText("Física e química franco-polonesa").assertExists()
        composeTestRule.onNodeWithText("Primeira pessoa a receber dois Prêmios Nobel.").assertExists()
        composeTestRule.onNodeWithTag("search_result_thumbnail_11", useUnmergedTree = true).assertExists()

        // Item sem imagem, descrição ou trecho não gera erro e omite a miniatura
        composeTestRule.onNodeWithText("Radioatividade").assertExists()
        composeTestRule.onNodeWithTag("search_result_thumbnail_22", useUnmergedTree = true).assertDoesNotExist()

        // Clique no resultado informa o pageId correto
        composeTestRule.onNodeWithTag("search_result_card_22").performClick()
        assertEquals(22L, selectedPageId)
    }

    @Test
    fun `SearchScreen displays empty state when no results are found`() {
        var cleared = false

        composeTestRule.setContent {
            SearchScreenContent(
                uiState = SearchUiState(
                    query = "termoinexistente",
                    isLoading = false,
                    hasSearched = true,
                    lastSearchedQuery = "termoinexistente",
                    results = emptyList()
                ),
                onNavigateBack = {},
                onQueryChange = {},
                onSearch = {},
                onRetry = {},
                onClearSearch = { cleared = true },
                onArticleSelected = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_empty_state").assertExists()
        composeTestRule.onNodeWithText("Nenhum resultado encontrado").assertExists()

        composeTestRule.onNodeWithTag("empty_state_action_button").performClick()
        assertTrue(cleared)
    }

    @Test
    fun `SearchScreen displays error state and triggers retry action`() {
        var retried = false

        composeTestRule.setContent {
            SearchScreenContent(
                uiState = SearchUiState(
                    query = "História",
                    isLoading = false,
                    hasSearched = true,
                    lastSearchedQuery = "História",
                    errorMessage = "Sem conexão com a internet. Verifique sua rede e tente novamente."
                ),
                onNavigateBack = {},
                onQueryChange = {},
                onSearch = {},
                onRetry = { retried = true },
                onClearSearch = {},
                onArticleSelected = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_error_state").assertExists()
        composeTestRule.onNodeWithText("Erro ao pesquisar").assertExists()
        composeTestRule.onNodeWithText("Sem conexão com a internet. Verifique sua rede e tente novamente.").assertExists()

        composeTestRule.onNodeWithTag("empty_state_action_button").performClick()
        assertTrue(retried)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `SearchScreen integrated with SearchViewModel executes search displays results and handles clear`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val fakeRepo = FakeUiWikipediaRepository(
                results = listOf(
                    WikipediaSearchResult(
                        pageId = 777L,
                        title = "Brasil",
                        description = "País na América do Sul",
                        snippet = "O Brasil é o maior país da América do Sul.",
                        thumbnailUrl = null,
                        url = "https://pt.wikipedia.org/wiki/Brasil"
                    )
                )
            )
            val viewModel = SearchViewModel(SearchWikipediaArticlesUseCase(fakeRepo))
            var clickedPageId: Long? = null

            composeTestRule.setContent {
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onArticleSelected = { clickedPageId = it }
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("search_initial_state").assertExists()

            // Tentativa com espaços não executa busca
            composeTestRule.onNodeWithTag("search_query_input").performTextInput("   ")
            composeTestRule.onNodeWithTag("search_submit_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(0, fakeRepo.callCount)
            composeTestRule.onNodeWithTag("search_initial_state").assertExists()

            // Limpa e pesquisa termo válido
            composeTestRule.onNodeWithTag("search_clear_button").performClick()
            composeTestRule.onNodeWithTag("search_query_input").performTextInput("Brasil")
            composeTestRule.onNodeWithTag("search_submit_button").performClick()
            composeTestRule.waitForIdle()

            assertEquals(1, fakeRepo.callCount)
            composeTestRule.onNodeWithText("1 resultado para \"Brasil\"").assertExists()
            composeTestRule.onNodeWithText("País na América do Sul").assertExists()
            composeTestRule.onNodeWithTag("search_result_card_777").assertExists()
            composeTestRule.onNodeWithTag("search_result_card_777").performClick()
            assertEquals(777L, clickedPageId)

            // Limpar pesquisa retorna ao estado inicial
            composeTestRule.onNodeWithTag("search_clear_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("search_initial_state").assertExists()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeUiWikipediaRepository(
        private val results: List<WikipediaSearchResult> = emptyList(),
        private val error: WikipediaException? = null
    ) : WikipediaRepository {
        var callCount = 0

        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> {
            callCount++
            error?.let { throw it }
            return results
        }

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? = null
    }
}
