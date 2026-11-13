package com.example.erudito.presentation.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.repository.FakeCollectionRepository
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetArticleByIdUseCase
import com.example.erudito.domain.usecase.GetCollectionWithArticlesUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import com.example.erudito.domain.usecase.SearchWikipediaArticlesUseCase
import com.example.erudito.presentation.article_detail.ArticleDetailViewModel
import com.example.erudito.presentation.collection_detail.CollectionDetailViewModel
import com.example.erudito.presentation.collections.CollectionsViewModel
import com.example.erudito.presentation.home.HomeViewModel
import com.example.erudito.presentation.search.SearchViewModel
import com.example.erudito.presentation.wikipedia_article.WikipediaArticleViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EruditoNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `Screen routes are configured correctly for Bundle 1 and Bundle 2`() {
        assertEquals("home", Screen.Home.route)
        assertEquals("collections", Screen.Collections.route)
        assertEquals("collection/{collectionId}", Screen.CollectionDetail.route)
        assertEquals("article/{articleId}", Screen.ArticleDetail.route)
        assertEquals("search", Screen.Search.route)
        assertEquals("wikipedia_article/{pageId}", Screen.WikipediaArticle.route)
        assertEquals("wikipedia_article/42", Screen.WikipediaArticle.createRoute(42L))
    }

    @Test
    fun `full Bundle 2 navigation flow preserves search state and does not insert articles into local library`() {
        runBlocking {
            val dispatcher = UnconfinedTestDispatcher()
            Dispatchers.setMain(dispatcher)
            try {
                val collectionRepo = FakeCollectionRepository()
                val articleRepo = FakeArticleRepository()
                val wikipediaRepo = FakeNavigationWikipediaRepository(
                    searchResults = listOf(
                        WikipediaSearchResult(
                            pageId = 42L,
                            title = "Marie Curie",
                            description = "Física e química",
                            snippet = "Pioneira no estudo da radioatividade.",
                            thumbnailUrl = null,
                            url = "https://pt.wikipedia.org/wiki/Marie_Curie"
                        )
                    ),
                    articles = mapOf(
                        42L to WikipediaArticle(
                            pageId = 42L,
                            title = "Marie Curie",
                            description = "Física e química",
                            content = "Marie Skłodowska Curie foi uma física e química polonesa.",
                            imageUrl = null,
                            url = "https://pt.wikipedia.org/wiki/Marie_Curie"
                        )
                    )
                )

                lateinit var navController: NavHostController

                composeTestRule.setContent {
                    navController = rememberNavController()
                    TestEruditoNavHost(
                        navController = navController,
                        collectionRepo = collectionRepo,
                        articleRepo = articleRepo,
                        wikipediaRepo = wikipediaRepo
                    )
                }

                composeTestRule.waitForIdle()
                assertEquals(Screen.Home.route, navController.currentDestination?.route)

                // 1. Abre pesquisa pela HomeScreen
                composeTestRule.onNodeWithTag("home_search_button").performClick()
                composeTestRule.waitForIdle()
                assertEquals(Screen.Search.route, navController.currentDestination?.route)
                composeTestRule.onNodeWithText("Explore a Wikipédia").assertExists()

                // 2. Pesquisa e exibe resultado
                composeTestRule.onNodeWithTag("search_query_input").performTextInput("Marie Curie")
                composeTestRule.onNodeWithTag("search_submit_button").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("search_result_card_42").assertExists()

                // 3. Seleciona resultado -> navega passando pageId via rota e SavedStateHandle
                composeTestRule.onNodeWithTag("search_result_card_42").performClick()
                composeTestRule.waitForIdle()
                assertEquals(Screen.WikipediaArticle.route, navController.currentDestination?.route)
                assertEquals(
                    42L,
                    navController.currentBackStackEntry?.arguments?.getLong(WikipediaArticleViewModel.ARG_PAGE_ID)
                )
                composeTestRule.onNodeWithTag("wikipedia_article_title").assertExists()
                composeTestRule.onNodeWithText("Marie Skłodowska Curie foi uma física e química polonesa.")
                    .assertExists()

                // Nenhum artigo pesquisado/lido online foi inserido no armazenamento local
                assertTrue(articleRepo.getSavedArticles().first().isEmpty())

                // 4. Retorna da leitura para a pesquisa preservando os resultados
                composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
                composeTestRule.waitForIdle()
                assertEquals(Screen.Search.route, navController.currentDestination?.route)
                composeTestRule.onNodeWithTag("search_result_card_42").assertExists()

                // 5. Retorna da pesquisa para a HomeScreen
                composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
                composeTestRule.waitForIdle()
                assertEquals(Screen.Home.route, navController.currentDestination?.route)
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    @Test
    fun `system back button pops WikipediaArticle to Search and Search to Home without duplicate destinations`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()
            val wikipediaRepo = FakeNavigationWikipediaRepository(
                searchResults = listOf(
                    WikipediaSearchResult(
                        pageId = 77L,
                        title = "Sócrates",
                        url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
                    )
                ),
                articles = mapOf(
                    77L to WikipediaArticle(
                        pageId = 77L,
                        title = "Sócrates",
                        content = "Filósofo da Grécia Antiga.",
                        url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
                    )
                )
            )

            lateinit var navController: NavHostController
            var triggerSystemBack: () -> Unit = {}

            composeTestRule.setContent {
                navController = rememberNavController()
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                triggerSystemBack = { backDispatcher?.onBackPressed() }

                TestEruditoNavHost(
                    navController = navController,
                    collectionRepo = collectionRepo,
                    articleRepo = articleRepo,
                    wikipediaRepo = wikipediaRepo
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_search_button").performClick()
            composeTestRule.waitForIdle()

            // Garante ausência de destinos duplicados na pilha ao navegar com launchSingleTop
            composeTestRule.runOnUiThread {
                navController.navigate(Screen.Search.route) { launchSingleTop = true }
            }
            composeTestRule.waitForIdle()
            val searchEntriesCount = navController.currentBackStack.value.count {
                it.destination.route == Screen.Search.route
            }
            assertEquals(1, searchEntriesCount)

            composeTestRule.onNodeWithTag("search_query_input").performTextInput("Sócrates")
            composeTestRule.onNodeWithTag("search_submit_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("search_result_card_77").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.WikipediaArticle.route, navController.currentDestination?.route)

            // Botão voltar do Android na tela de leitura -> volta para SearchScreen
            composeTestRule.runOnUiThread { triggerSystemBack() }
            composeTestRule.waitForIdle()
            assertEquals(Screen.Search.route, navController.currentDestination?.route)
            composeTestRule.onNodeWithTag("search_result_card_77").assertExists()

            // Botão voltar do Android na SearchScreen -> volta para HomeScreen
            composeTestRule.runOnUiThread { triggerSystemBack() }
            composeTestRule.waitForIdle()
            assertEquals(Screen.Home.route, navController.currentDestination?.route)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `Bundle 1 navigation routes for collections and offline articles remain intact`() {
        runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val collectionRepo = FakeCollectionRepository()
            val articleRepo = FakeArticleRepository()

            val collectionId = collectionRepo.createCollection("Filosofia")
            val articleId = articleRepo.insertArticle(
                Article(
                    id = 10L,
                    title = "Platão",
                    url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o",
                    description = "Filósofo ateniense",
                    content = "Discípulo de Sócrates e fundador da Academia.",
                    imageUrl = null,
                    lastUpdated = 1_700_000_000_000L
                )
            )
            val savedArticle = articleRepo.getArticleById(articleId)!!
            collectionRepo.setCollectionWithArticles(
                collectionId,
                CollectionWithArticles(
                    collection = Collection(id = collectionId, name = "Filosofia", articleCount = 1),
                    articles = listOf(savedArticle)
                )
            )

            lateinit var navController: NavHostController

            composeTestRule.setContent {
                navController = rememberNavController()
                TestEruditoNavHost(
                    navController = navController,
                    collectionRepo = collectionRepo,
                    articleRepo = articleRepo,
                    wikipediaRepo = FakeNavigationWikipediaRepository()
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("manage_collections_fab").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.Collections.route, navController.currentDestination?.route)

            composeTestRule.onNodeWithTag("collection_card_$collectionId").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.CollectionDetail.route, navController.currentDestination?.route)

            composeTestRule.onNodeWithTag("article_card_$articleId").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.ArticleDetail.route, navController.currentDestination?.route)
            composeTestRule.onNodeWithTag("article_detail_title").assertExists()
            composeTestRule.onNodeWithText("Discípulo de Sócrates e fundador da Academia.").assertExists()
        } finally {
            Dispatchers.resetMain()
        }
        }
    }

    @Composable
    private fun TestEruditoNavHost(
        navController: NavHostController,
        collectionRepo: FakeCollectionRepository,
        articleRepo: FakeArticleRepository,
        wikipediaRepo: WikipediaRepository
    ) {
        EruditoNavHost(
            navController = navController,
            homeViewModelProvider = { backStackEntry ->
                remember(backStackEntry) {
                    HomeViewModel(
                        getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                        getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepo),
                        createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                        renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                        deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
                    )
                }
            },
            collectionsViewModelProvider = { backStackEntry ->
                remember(backStackEntry) {
                    CollectionsViewModel(
                        getCollectionsUseCase = GetCollectionsUseCase(collectionRepo),
                        createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
                        renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                        deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
                    )
                }
            },
            collectionDetailViewModelProvider = { backStackEntry ->
                remember(backStackEntry) {
                    CollectionDetailViewModel(
                        savedStateHandle = backStackEntry.savedStateHandle,
                        getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(collectionRepo),
                        renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
                        deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo)
                    )
                }
            },
            articleDetailViewModelProvider = { backStackEntry ->
                remember(backStackEntry) {
                    ArticleDetailViewModel(
                        savedStateHandle = backStackEntry.savedStateHandle,
                        getArticleByIdUseCase = GetArticleByIdUseCase(articleRepo)
                    )
                }
            },
            searchViewModelProvider = { backStackEntry ->
                remember(backStackEntry) {
                    SearchViewModel(
                        searchWikipediaArticlesUseCase = SearchWikipediaArticlesUseCase(wikipediaRepo)
                    )
                }
            },
            wikipediaArticleViewModelProvider = { backStackEntry ->
                remember(backStackEntry) {
                    WikipediaArticleViewModel(
                        savedStateHandle = backStackEntry.savedStateHandle,
                        getWikipediaArticleUseCase = GetWikipediaArticleUseCase(wikipediaRepo)
                    )
                }
            }
        )
    }

    private class FakeNavigationWikipediaRepository(
        private val searchResults: List<WikipediaSearchResult> = emptyList(),
        private val articles: Map<Long, WikipediaArticle> = emptyMap()
    ) : WikipediaRepository {
        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = searchResults

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? = articles[pageId]
    }
}
