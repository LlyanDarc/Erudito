package com.example.erudito.presentation.wikipedia_article

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer
import com.example.erudito.domain.connectivity.ConnectivityObserver
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    private fun buildViewModel(
        savedStateHandle: SavedStateHandle,
        wikipediaRepo: WikipediaRepository,
        collectionRepo: FakeCollectionRepository = FakeCollectionRepository(),
        articleRepo: ArticleRepository = FakeArticleRepository(),
        connectivityObserver: ConnectivityObserver = FakeConnectivityObserver(initialOnline = true)
    ): WikipediaArticleViewModel {
        return WikipediaArticleViewModel(
            savedStateHandle = savedStateHandle,
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
        composeTestRule.onNodeWithTag("wikipedia_article_save_button").assertExists()
        composeTestRule.onNodeWithText("Salvar em coleções").assertExists()
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
            val viewModel = buildViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 500L)),
                wikipediaRepo = fakeRepo
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

    @Test
    fun `WikipediaArticleScreen displays saved count badge and Manage Collections CTA when already saved`() {
        val article = WikipediaArticle(
            pageId = 42L,
            title = "Sócrates",
            content = "Conteúdo",
            url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
        )

        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Success(article),
                onNavigateBack = {},
                onRetry = {},
                onOpenOriginalUrl = {},
                collectionsUiState = WikipediaArticleCollectionsUiState(
                    savedCollectionIds = setOf(1L, 2L),
                    isLoadingCollections = false
                )
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wikipedia_article_saved_status").assertIsDisplayed()
        composeTestRule.onNodeWithText("Salvo em 2 coleções").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gerenciar coleções").assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `WikipediaArticleScreen end-to-end picker saves to multiple collections creates collection and shows feedback`() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val col1 = collectionRepo.createCollection("Filosofia")
            val article = WikipediaArticle(
                pageId = 700L,
                title = "Aristóteles",
                description = "Filósofo grego",
                content = "Aristóteles foi um filósofo e polímata da Grécia Antiga.",
                imageUrl = null,
                url = "https://pt.wikipedia.org/wiki/Arist%C3%B3teles"
            )
            val viewModel = buildViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 700L)),
                wikipediaRepo = FakeScreenWikipediaRepository(article = article),
                collectionRepo = collectionRepo,
                articleRepo = articleRepo
            )

            composeTestRule.setContent {
                WikipediaArticleScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Salvar em coleções").assertIsDisplayed()

            // Abre o seletor de coleções
            composeTestRule.onNodeWithTag("wikipedia_article_save_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_picker_item_$col1").assertIsOff()
            composeTestRule.onNodeWithTag("collection_picker_checkbox_$col1", useUnmergedTree = true).assertExists()

            // Cria nova coleção pelo seletor sem perder o contexto da leitura
            composeTestRule.onNodeWithTag("collection_picker_new_collection_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_name_input").performTextInput("Grécia Antiga")
            composeTestRule.onNodeWithTag("confirm_collection_dialog_button").performClick()
            composeTestRule.waitForIdle()

            val col2 = collectionRepo.getCollections().first().first { it.name == "Grécia Antiga" }.id
            composeTestRule.onNodeWithTag("collection_picker_item_$col2").assertIsOff()

            // Seleciona ambas as coleções e confirma o salvamento
            composeTestRule.onNodeWithTag("collection_picker_item_$col1").performClick()
            composeTestRule.onNodeWithTag("collection_picker_item_$col2").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_item_$col1").assertIsOn()
            composeTestRule.onNodeWithTag("collection_picker_item_$col2").assertIsOn()

            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

            // Seletor fecha, status salvo aparece e Snackbar de sucesso é exibido
            composeTestRule.onNodeWithTag("collection_picker_dialog").assertDoesNotExist()
            composeTestRule.onNodeWithText("Salvo em 2 coleções").assertIsDisplayed()
            composeTestRule.onNodeWithText("Gerenciar coleções").assertIsDisplayed()
            composeTestRule.onNodeWithText("Artigo salvo em 2 coleções!").assertIsDisplayed()

            // Reabre seletor e confirma pré-seleção + badge "Já salvo"; cancela sem alterar
            composeTestRule.onNodeWithTag("wikipedia_article_save_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_item_$col1").assertIsOn()
            composeTestRule.onNodeWithTag("collection_picker_item_$col2").assertIsOn()
            composeTestRule.onNodeWithTag("collection_picker_saved_badge_$col1", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_picker_item_$col1").performClick()
            composeTestRule.onNodeWithTag("collection_picker_cancel_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Salvo em 2 coleções").assertIsDisplayed()
            assertEquals(1, articleRepo.getSavedArticles().first().size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `WikipediaArticleScreen shows error feedback when saving fails`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository().apply {
                runBlocking { createCollection("Física") }
            }
            val article = WikipediaArticle(
                pageId = 808L,
                title = "Fóton",
                content = "Partícula elementar.",
                url = "https://pt.wikipedia.org/wiki/F%C3%B3ton"
            )
            val failingArticleRepo = object : ArticleRepository by FakeArticleRepository() {
                override suspend fun saveArticleAndAssociateCollections(
                    article: Article,
                    collectionIds: List<Long>
                ): Long {
                    throw IllegalStateException("Falha simulada ao salvar artigo")
                }
            }
            val viewModel = buildViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 808L)),
                wikipediaRepo = FakeScreenWikipediaRepository(article = article),
                collectionRepo = collectionRepo,
                articleRepo = failingArticleRepo
            )

            composeTestRule.setContent {
                WikipediaArticleScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("wikipedia_article_save_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("collection_picker_item_1").performClick()
            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("collection_picker_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_picker_error")
                .assertIsDisplayed()
                .assertTextEquals("Falha simulada ao salvar artigo")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `WikipediaArticleScreen displays local fallback banner and hides reload button when offline`() {
        val article = WikipediaArticle(
            pageId = 42L,
            title = "Sócrates",
            content = "Conteúdo salvo localmente.",
            url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
        )

        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Success(
                    article = article,
                    isLocalFallback = true,
                    localLastUpdated = 1_700_000_000_000L
                ),
                onNavigateBack = {},
                onRetry = {},
                onOpenOriginalUrl = {},
                collectionsUiState = WikipediaArticleCollectionsUiState(
                    savedCollectionIds = setOf(1L),
                    isLoadingCollections = false,
                    isOnline = false
                )
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Leitura Local (Offline)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wikipedia_article_source_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cópia Local Offline").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wikipedia_article_offline_fallback_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wikipedia_article_reload_online_button").assertDoesNotExist()
    }

    @Test
    fun `WikipediaArticleScreen shows reload online button in fallback banner when online and triggers retry`() {
        var retried = false
        val article = WikipediaArticle(
            pageId = 42L,
            title = "Sócrates",
            content = "Conteúdo salvo localmente.",
            url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
        )

        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Success(
                    article = article,
                    isLocalFallback = true,
                    localLastUpdated = 1_700_000_000_000L
                ),
                onNavigateBack = {},
                onRetry = { retried = true },
                onOpenOriginalUrl = {},
                collectionsUiState = WikipediaArticleCollectionsUiState(
                    savedCollectionIds = setOf(1L),
                    isLoadingCollections = false,
                    isOnline = true
                )
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wikipedia_article_reload_online_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(retried)
    }

    @Test
    fun `WikipediaArticleScreen shows offline chip during online reading when connection drops`() {
        val article = WikipediaArticle(
            pageId = 42L,
            title = "Sócrates",
            content = "Conteúdo carregado online.",
            url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
        )

        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Success(
                    article = article,
                    isLocalFallback = false
                ),
                onNavigateBack = {},
                onRetry = {},
                onOpenOriginalUrl = {},
                collectionsUiState = WikipediaArticleCollectionsUiState(
                    isLoadingCollections = false,
                    isOnline = false
                )
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wikipedia_article_connectivity_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sem conexão").assertIsDisplayed()
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
