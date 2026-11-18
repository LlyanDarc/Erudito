package com.example.erudito.presentation.article_detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer
import com.example.erudito.domain.connectivity.FakeConnectivityObserver
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle
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
import com.example.erudito.domain.usecase.ObserveArticleByIdUseCase
import com.example.erudito.domain.usecase.RefreshSavedArticleUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArticleDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildViewModel(
        articleId: Long,
        collectionRepo: FakeCollectionRepository,
        articleRepo: ArticleRepository,
        connectivityObserver: FakeConnectivityObserver = FakeConnectivityObserver()
    ): ArticleDetailViewModel {
        val emptyWikipediaRepo = object : WikipediaRepository {
            override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = emptyList()
            override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? = null
        }
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
                wikipediaRepository = emptyWikipediaRepo,
                connectivityObserver = connectivityObserver,
                syncPolicy = SyncPolicy()
            ),
            connectivityObserver = connectivityObserver,
            htmlProcessor = WikipediaHtmlSanitizer()
        )
    }

    @Test
    fun `ArticleDetailScreen preserves offline reading renders image when available and hides when null`() {
        val articleWithImage = Article(
            id = 1L,
            title = "Marie Curie",
            description = "Pioneira na radioatividade",
            content = "Conteúdo offline completo sobre Marie Curie.",
            imageUrl = "https://upload.wikimedia.org/marie_curie.jpg",
            url = "https://pt.wikipedia.org/wiki/Marie_Curie"
        )

        composeTestRule.setContent {
            ArticleDetailScreenContent(
                uiState = ArticleDetailUiState(
                    isLoading = false,
                    article = articleWithImage,
                    isLoadingCollections = false
                ),
                onNavigateBack = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("article_detail_title").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Marie Curie").assertIsDisplayed()
        composeTestRule.onNodeWithTag("article_detail_description").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("article_detail_image").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("article_detail_manage_collections_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Nenhuma coleção associada").assertIsDisplayed()
        composeTestRule.onNodeWithTag("article_detail_content").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `ArticleDetailScreen omits image container when imageUrl is null and renders error state gracefully`() {
        composeTestRule.setContent {
            ArticleDetailScreenContent(
                uiState = ArticleDetailUiState(
                    isLoading = false,
                    article = Article(
                        id = 2L,
                        title = "Teorema de Pitágoras",
                        description = null,
                        content = "Soma dos quadrados dos catetos.",
                        imageUrl = null,
                        url = "https://pt.wikipedia.org/wiki/Teorema_de_Pit%C3%A1goras"
                    ),
                    isLoadingCollections = false
                ),
                onNavigateBack = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Teorema de Pitágoras").assertIsDisplayed()
        composeTestRule.onNodeWithTag("article_detail_image").assertDoesNotExist()
        composeTestRule.onNodeWithTag("article_detail_description").assertDoesNotExist()
    }

    @Test
    fun `ArticleDetailScreen end-to-end manages collections adds removes creates new collection and preserves article`(): Unit = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val col1 = collectionRepo.createCollection("Filosofia")
            val col2 = collectionRepo.createCollection("História")
            val articleId = articleRepo.saveArticleAndAssociateCollections(
                Article(
                    title = "Sócrates",
                    description = "Filósofo ateniense",
                    content = "Sócrates foi um filósofo grego.",
                    imageUrl = null,
                    url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                    wikipediaPageId = 42L
                ),
                listOf(col1)
            )
            val viewModel = buildViewModel(articleId, collectionRepo, articleRepo)

            composeTestRule.setContent {
                ArticleDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Salvo em 1 coleção").assertIsDisplayed()
            composeTestRule.onNodeWithText("Filosofia").assertIsDisplayed()

            // Abre seletor e valida pré-seleção + badge "Já salvo"
            composeTestRule.onNodeWithTag("article_detail_manage_collections_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_dialog").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Gerenciar coleções").assertCountEquals(2)
            composeTestRule.onNodeWithTag("collection_picker_item_$col1").assertIsOn()
            composeTestRule.onNodeWithTag("collection_picker_item_$col2").assertIsOff()
            composeTestRule.onNodeWithTag("collection_picker_saved_badge_$col1", useUnmergedTree = true).assertIsDisplayed()

            // Cria nova coleção inline durante o gerenciamento
            composeTestRule.onNodeWithTag("collection_picker_new_collection_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_name_input").performTextInput("Grécia Antiga")
            composeTestRule.onNodeWithTag("confirm_collection_dialog_button").performClick()
            composeTestRule.waitForIdle()

            val col3 = collectionRepo.getCollections().first().first { it.name == "Grécia Antiga" }.id

            // Desmarca col1 e seleciona col2 e col3
            composeTestRule.onNodeWithTag("collection_picker_item_$col1").performClick()
            composeTestRule.onNodeWithTag("collection_picker_item_$col2").performClick()
            composeTestRule.onNodeWithTag("collection_picker_item_$col3").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

            // Valida fechamento, atualização reativa da tela, Snackbar e persistência sem duplicação
            composeTestRule.onNodeWithTag("collection_picker_dialog").assertDoesNotExist()
            composeTestRule.onNodeWithText("Salvo em 2 coleções").assertIsDisplayed()
            composeTestRule.onNodeWithText("História, Grécia Antiga").assertIsDisplayed()
            composeTestRule.onNodeWithText("Coleções do artigo atualizadas!").assertIsDisplayed()

            assertNotNull(articleRepo.getArticleById(articleId))
            assertEquals(1, articleRepo.getSavedArticles().first().size)
            assertEquals(listOf(col2, col3), articleRepo.getCollectionIdsForArticle(articleId).first())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ArticleDetailScreen shows error inside picker when updating collections fails`(): Unit = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val col1 = collectionRepo.createCollection("Ciências")
            val baseRepo = FakeArticleRepository()
            val articleId = baseRepo.insertArticle(
                Article(title = "Átomo", content = "Unidade básica.", url = "https://pt.wikipedia.org/wiki/%C3%81tomo")
            )
            val failingRepo = object : ArticleRepository by baseRepo {
                override suspend fun addArticleToCollections(articleId: Long, collectionIds: List<Long>) {
                    throw IllegalStateException("Falha simulada ao atualizar coleções")
                }
            }
            val viewModel = buildViewModel(articleId, collectionRepo, failingRepo)

            composeTestRule.setContent {
                ArticleDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("article_detail_manage_collections_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_item_$col1").performClick()
            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("collection_picker_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_picker_error")
                .assertIsDisplayed()
                .assertTextEquals("Falha simulada ao atualizar coleções")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ArticleDetailScreen displays Open on Wikipedia button and clickable URL opening persisted link via LocalUriHandler`(): Unit = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val persistedUrl = "https://pt.wikipedia.org/wiki/Ada_Lovelace"
            val articleId = articleRepo.insertArticle(
                Article(
                    title = "Ada Lovelace",
                    description = "Matemática e escritora inglesa",
                    content = "Reconhecida como a primeira programadora da história.",
                    imageUrl = null,
                    url = persistedUrl,
                    wikipediaPageId = 1815L
                )
            )
            val viewModel = buildViewModel(articleId, collectionRepo, articleRepo)
            val openedUris = mutableListOf<String>()
            val recordingUriHandler = object : UriHandler {
                override fun openUri(uri: String) {
                    openedUris.add(uri)
                }
            }

            composeTestRule.setContent {
                CompositionLocalProvider(LocalUriHandler provides recordingUriHandler) {
                    ArticleDetailScreen(
                        viewModel = viewModel,
                        onNavigateBack = {}
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("article_detail_url_card").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("article_detail_url_text").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(persistedUrl).assertIsDisplayed()
            composeTestRule.onNodeWithTag("article_detail_open_url_button").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Abrir na Wikipédia").assertIsDisplayed()

            // Clique no botão "Abrir na Wikipédia"
            composeTestRule.onNodeWithTag("article_detail_open_url_button").performClick()
            // Clique diretamente no endereço exibido
            composeTestRule.onNodeWithTag("article_detail_url_text").performClick()

            assertEquals(listOf(persistedUrl, persistedUrl), openedUris)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ArticleDetailScreen disables Wikipedia link actions when persisted URL is blank`() {
        var clicked = false

        composeTestRule.setContent {
            ArticleDetailScreenContent(
                uiState = ArticleDetailUiState(
                    isLoading = false,
                    article = Article(
                        id = 99L,
                        title = "Artigo sem URL",
                        content = "Conteúdo local.",
                        url = "   "
                    ),
                    isLoadingCollections = false
                ),
                onNavigateBack = {},
                onOpenOriginalUrl = { clicked = true }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("article_detail_open_url_button")
            .performScrollTo()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag("article_detail_url_text")
            .performScrollTo()
            .performClick()

        assertEquals(false, clicked)
    }

    @Test
    fun `ArticleDetailScreen displays live connectivity badge and preserves offline reading when disconnected`(): Unit = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val connectivityObserver = FakeConnectivityObserver(initialOnline = false)
            val articleId = articleRepo.insertArticle(
                Article(
                    title = "Alan Turing",
                    content = "Pioneiro da ciência da computação.",
                    url = "https://pt.wikipedia.org/wiki/Alan_Turing",
                    wikipediaPageId = 101L
                )
            )
            val viewModel = buildViewModel(articleId, collectionRepo, articleRepo, connectivityObserver)

            composeTestRule.setContent {
                ArticleDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("article_detail_connectivity_badge").assertIsDisplayed()
            composeTestRule.onNodeWithText("Sem conexão").assertIsDisplayed()
            composeTestRule.onNodeWithText("Pioneiro da ciência da computação.").assertIsDisplayed()

            connectivityObserver.setOnline(true)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Online").assertIsDisplayed()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
