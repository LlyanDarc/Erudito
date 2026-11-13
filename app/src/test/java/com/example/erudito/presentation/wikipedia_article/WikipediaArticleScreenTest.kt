package com.example.erudito.presentation.wikipedia_article

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
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
class WikipediaArticleScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `WikipediaArticleScreen displays loading indicator while Loading`() {
        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Loading,
                onNavigateBack = {},
                onRetry = {},
                onOpenOriginalUrl = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wikipedia_article_loading").assertExists()
        composeTestRule.onNodeWithText("Carregando artigo da Wikipédia...").assertExists()
    }

    @Test
    fun `WikipediaArticleScreen displays title content image description and opens original URL`() {
        var openedUrl: String? = null
        val article = WikipediaArticle(
            pageId = 42L,
            title = "Marie Curie",
            description = "Física e química pioneira na radioatividade",
            content = "Marie Skłodowska Curie foi uma física e química polonesa naturalizada francesa.\n\nEla foi a primeira mulher a ganhar um Prêmio Nobel.",
            imageUrl = "https://upload.wikimedia.org/marie_curie.jpg",
            url = "https://pt.wikipedia.org/wiki/Marie_Curie"
        )

        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Success(article),
                onNavigateBack = {},
                onRetry = {},
                onOpenOriginalUrl = { openedUrl = it }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wikipedia_article_title").assertExists()
        composeTestRule.onNodeWithText("Marie Curie").assertExists()
        composeTestRule.onNodeWithTag("wikipedia_article_description").assertExists()
        composeTestRule.onNodeWithText("Física e química pioneira na radioatividade").assertExists()
        composeTestRule.onNodeWithTag("wikipedia_article_image").assertExists()
        composeTestRule.onNodeWithTag("wikipedia_article_content").assertExists()
        composeTestRule.onNodeWithText(
            "Marie Skłodowska Curie foi uma física e química polonesa naturalizada francesa.",
            substring = true
        ).assertExists()
        composeTestRule.onNodeWithText(
            "Ela foi a primeira mulher a ganhar um Prêmio Nobel.",
            substring = true
        ).assertExists()

        composeTestRule.onNodeWithTag("wikipedia_article_url_text").performScrollTo()
        composeTestRule.onNodeWithText("https://pt.wikipedia.org/wiki/Marie_Curie").assertExists()
        composeTestRule.onNodeWithTag("wikipedia_article_open_url_button")
            .performScrollTo()
            .performClick()

        assertEquals("https://pt.wikipedia.org/wiki/Marie_Curie", openedUrl)
    }

    @Test
    fun `WikipediaArticleScreen renders article without image or description gracefully`() {
        val article = WikipediaArticle(
            pageId = 100L,
            title = "Teorema de Pitágoras",
            description = null,
            content = "Em qualquer triângulo retângulo, o quadrado da hipotenusa é igual à soma dos quadrados dos catetos.",
            imageUrl = null,
            url = "https://pt.wikipedia.org/wiki/Teorema_de_Pit%C3%A1goras"
        )

        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Success(article),
                onNavigateBack = {},
                onRetry = {},
                onOpenOriginalUrl = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Teorema de Pitágoras").assertExists()
        composeTestRule.onNodeWithText(
            "Em qualquer triângulo retângulo, o quadrado da hipotenusa é igual à soma dos quadrados dos catetos."
        ).assertExists()
        composeTestRule.onNodeWithTag("wikipedia_article_image").assertDoesNotExist()
        composeTestRule.onNodeWithTag("wikipedia_article_description").assertDoesNotExist()
    }

    @Test
    fun `WikipediaArticleScreen displays error state and triggers retry callback`() {
        var retried = false

        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Error(
                    "Sem conexão com a internet. Verifique sua rede e tente novamente."
                ),
                onNavigateBack = {},
                onRetry = { retried = true },
                onOpenOriginalUrl = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wikipedia_article_error").assertExists()
        composeTestRule.onNodeWithText("Não foi possível carregar o artigo").assertExists()
        composeTestRule.onNodeWithText(
            "Sem conexão com a internet. Verifique sua rede e tente novamente."
        ).assertExists()

        composeTestRule.onNodeWithTag("empty_state_action_button").performClick()
        assertTrue(retried)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `WikipediaArticleScreen integrated with ViewModel recovers from error and opens URI via LocalUriHandler`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val fakeRepo = FakeScreenWikipediaRepository(
                article = WikipediaArticle(
                    pageId = 500L,
                    title = "Brasil",
                    description = "República Federativa do Brasil",
                    content = "O Brasil é o maior país da América do Sul.",
                    imageUrl = null,
                    url = "https://pt.wikipedia.org/wiki/Brasil"
                ),
                failFirstAttempt = true
            )
            val viewModel = WikipediaArticleViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 500L)),
                getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepo)
            )
            var openedUri: String? = null
            val recordingUriHandler = object : UriHandler {
                override fun openUri(uri: String) {
                    openedUri = uri
                }
            }

            composeTestRule.setContent {
                CompositionLocalProvider(LocalUriHandler provides recordingUriHandler) {
                    WikipediaArticleScreen(
                        viewModel = viewModel,
                        onNavigateBack = {}
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("wikipedia_article_error").assertExists()

            // Clica em Tentar novamente e valida carregamento com sucesso
            composeTestRule.onNodeWithTag("empty_state_action_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Brasil").assertExists()
            composeTestRule.onNodeWithText("República Federativa do Brasil").assertExists()
            composeTestRule.onNodeWithText("O Brasil é o maior país da América do Sul.").assertExists()

            composeTestRule.onNodeWithTag("wikipedia_article_open_url_button")
                .performScrollTo()
                .performClick()

            assertEquals("https://pt.wikipedia.org/wiki/Brasil", openedUri)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeScreenWikipediaRepository(
        private val article: WikipediaArticle,
        private var failFirstAttempt: Boolean = false
    ) : WikipediaRepository {
        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = emptyList()

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? {
            if (failFirstAttempt) {
                failFirstAttempt = false
                throw WikipediaException.NoConnection()
            }
            return if (pageId == article.pageId) article else null
        }
    }
}
