package com.example.erudito.presentation.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.erudito.data.local.database.EruditoDatabase
import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.data.local.entity.CollectionArticleCrossRef
import com.example.erudito.data.local.entity.CollectionEntity
import com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer
import com.example.erudito.data.repository.ArticleRepositoryImpl
import com.example.erudito.data.repository.CollectionRepositoryImpl
import com.example.erudito.domain.connectivity.ConnectivityObserver
import com.example.erudito.domain.connectivity.FakeConnectivityObserver
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.CollectionRepository
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.sync.SyncPolicy
import com.example.erudito.domain.usecase.AddArticleToCollectionsUseCase
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionIdsForArticleUseCase
import com.example.erudito.domain.usecase.GetCollectionWithArticlesUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticleByWikipediaPageIdUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
import com.example.erudito.domain.usecase.ObserveArticleByIdUseCase
import com.example.erudito.domain.usecase.RefreshSavedArticleUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import com.example.erudito.domain.usecase.SaveWikipediaArticleToCollectionsUseCase
import com.example.erudito.domain.usecase.SearchWikipediaArticlesUseCase
import com.example.erudito.domain.usecase.SyncSavedArticlesUseCase
import com.example.erudito.presentation.article_detail.ArticleDetailViewModel
import com.example.erudito.presentation.collection_detail.CollectionDetailViewModel
import com.example.erudito.presentation.collections.CollectionsViewModel
import com.example.erudito.presentation.home.HomeViewModel
import com.example.erudito.presentation.search.SearchViewModel
import com.example.erudito.presentation.wikipedia_article.WikipediaArticleViewModel
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Bundle3EndToEndIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: EruditoDatabase
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var articleRepository: ArticleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = Executor { runnable -> runnable.run() }
        database = Room.inMemoryDatabaseBuilder(context, EruditoDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        collectionRepository = CollectionRepositoryImpl(database.collectionDao())
        articleRepository = ArticleRepositoryImpl(database.articleDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `complete end to end Bundle 3 flow integrates search online save offline reading inline collection creation and individual removal with real Room`() = runBlocking {
        val filosofiaId = database.collectionDao().insertCollection(
                CollectionEntity(name = "Filosofia", createdAt = 1_000L)
            )
            val wikipediaRepo = FakeE2EWikipediaRepository(
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
                        content = "Marie Skłodowska Curie foi uma física e química polonesa naturalizada francesa.",
                        imageUrl = "https://upload.wikimedia.org/marie_curie.jpg",
                        url = "https://pt.wikipedia.org/wiki/Marie_Curie"
                    )
                )
            )

            lateinit var navController: NavHostController

            composeTestRule.setContent {
                navController = rememberNavController()
                RealDbEruditoNavHost(
                    navController = navController,
                    wikipediaRepo = wikipediaRepo
                )
            }

            composeTestRule.waitForIdle()
            assertEquals(Screen.Home.route, navController.currentDestination?.route)

            // 1. Acessa a pesquisa pela HomeScreen
            composeTestRule.onNodeWithTag("home_search_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.Search.route, navController.currentDestination?.route)

            // 2. Pesquisa um artigo na Wikipédia
            composeTestRule.onNodeWithTag("search_query_input").performTextInput("Marie Curie")
            composeTestRule.onNodeWithTag("search_submit_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("search_result_card_42").assertIsDisplayed()

            // 3. Abre o artigo para leitura online
            composeTestRule.onNodeWithTag("search_result_card_42").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.WikipediaArticle.route, navController.currentDestination?.route)
            composeTestRule.onNodeWithTag("wikipedia_article_title").assertIsDisplayed()
            composeTestRule.onNodeWithTag("wikipedia_article_save_button").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Salvar em coleções").assertIsDisplayed()

            // 4. Associa o artigo a múltiplas coleções, criando uma coleção inline ("Física")
            composeTestRule.onNodeWithTag("wikipedia_article_save_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_picker_item_$filosofiaId").assertIsOff()

            composeTestRule.onNodeWithTag("collection_picker_new_collection_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_name_input").performTextInput("Física")
            composeTestRule.onNodeWithTag("confirm_collection_dialog_button").performClick()
            composeTestRule.waitForIdle()

            val fisicaId = collectionRepository.getCollections().first().first { it.name == "Física" }.id

            composeTestRule.onNodeWithTag("collection_picker_item_$filosofiaId").performClick()
            composeTestRule.onNodeWithTag("collection_picker_item_$fisicaId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_item_$filosofiaId").assertIsOn()
            composeTestRule.onNodeWithTag("collection_picker_item_$fisicaId").assertIsOn()

            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("collection_picker_dialog").assertDoesNotExist()
            composeTestRule.onNodeWithTag("wikipedia_article_saved_status").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Salvo em 2 coleções").assertIsDisplayed()
            composeTestRule.onNodeWithTag("wikipedia_article_save_button").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Gerenciar coleções").assertIsDisplayed()

            val savedArticle = database.articleDao().getArticleByWikipediaPageId(42L)
            assertNotNull(savedArticle)
            val savedArticleId = savedArticle!!.id
            assertEquals(1, database.articleDao().getArticleCount())

            // 5. Retorna para Search (preservando resultados) e para Home (refletindo contagens e artigo salvo)
            composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.Search.route, navController.currentDestination?.route)
            composeTestRule.onNodeWithTag("search_result_card_42").assertIsDisplayed()

            composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.Home.route, navController.currentDestination?.route)

            composeTestRule.onNodeWithTag("manage_collections_fab").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.Collections.route, navController.currentDestination?.route)

            // 6. Acessa a coleção "Física" e visualiza o artigo associado
            composeTestRule.onNodeWithTag("collection_card_$fisicaId").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.CollectionDetail.route, navController.currentDestination?.route)
            composeTestRule.onNodeWithText("1 artigo associado").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_detail_list")
                .performScrollToNode(hasTestTag("article_card_$savedArticleId"))
            composeTestRule.onNodeWithTag("article_card_$savedArticleId").assertIsDisplayed()

            // 7. Abre novamente o artigo na leitura offline
            composeTestRule.onNodeWithTag("article_card_$savedArticleId").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.ArticleDetail.route, navController.currentDestination?.route)
            composeTestRule.onNodeWithTag("article_detail_image").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("article_detail_collections_status").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Salvo em 2 coleções").assertIsDisplayed()

            // 8. Modifica associações na leitura offline: cria "Prêmio Nobel", desmarca "Física" e salva
            composeTestRule.onNodeWithTag("article_detail_manage_collections_button")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_picker_item_$filosofiaId").assertIsOn()
            composeTestRule.onNodeWithTag("collection_picker_item_$fisicaId").assertIsOn()
            composeTestRule.onNodeWithTag("collection_picker_saved_badge_$fisicaId", useUnmergedTree = true)
                .assertIsDisplayed()

            composeTestRule.onNodeWithTag("collection_picker_new_collection_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_name_input").performTextInput("Prêmio Nobel")
            composeTestRule.onNodeWithTag("confirm_collection_dialog_button").performClick()
            composeTestRule.waitForIdle()

            val nobelId = collectionRepository.getCollections().first().first { it.name == "Prêmio Nobel" }.id

            composeTestRule.onNodeWithTag("collection_picker_item_$fisicaId").performClick()
            composeTestRule.onNodeWithTag("collection_picker_item_$nobelId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Salvo em 2 coleções").assertIsDisplayed()
            assertEquals(
                setOf(filosofiaId, nobelId),
                articleRepository.getCollectionIdsForArticle(savedArticleId).first().toSet()
            )

            // Volta para CollectionDetail ("Física") e verifica atualização reativa (artigo removido desta coleção)
            composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.CollectionDetail.route, navController.currentDestination?.route)
            composeTestRule.onNodeWithText("0 artigos associados").assertIsDisplayed()
            composeTestRule.onNodeWithText("Nenhum artigo nesta coleção").assertIsDisplayed()

            // Volta para Collections e abre "Filosofia" -> confirma preservação do artigo e testa remoção individual
            composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_card_$filosofiaId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("1 artigo associado").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_detail_list")
                .performScrollToNode(hasTestTag("article_card_$savedArticleId"))
            composeTestRule.onNodeWithTag("remove_article_button_$savedArticleId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("confirm_remove_article_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("0 artigos associados").assertIsDisplayed()
            composeTestRule.onNodeWithText("Artigo \"Marie Curie\" removido da coleção.").assertIsDisplayed()

            // Artigo continua íntegro no Room e associado à coleção "Prêmio Nobel"
            assertEquals(1, database.articleDao().getArticleCount())
            assertNotNull(articleRepository.getArticleById(savedArticleId))
            assertEquals(
                listOf(nobelId),
                articleRepository.getCollectionIdsForArticle(savedArticleId).first()
            )
        assertEquals(
            listOf(savedArticleId),
            articleRepository.getArticlesForCollection(nobelId).first().map { it.id }
        )
    }

    @Test
    fun `reopening Wikipedia article reuses existing local and legacy records without duplicating articles or links`() = runBlocking {
        val artesId = database.collectionDao().insertCollection(
                CollectionEntity(name = "Artes", createdAt = 1_000L)
            )
            val historiaId = database.collectionDao().insertCollection(
                CollectionEntity(name = "História", createdAt = 2_000L)
            )
            val legacyArticleId = database.articleDao().insertArticle(
                ArticleEntity(
                    title = "Renascimento",
                    url = "https://pt.wikipedia.org/wiki/Renascimento",
                    description = "Descrição offline inicial",
                    content = "Conteúdo legado",
                    imageUrl = null,
                    lastUpdated = 500L,
                    wikipediaPageId = null
                )
            )
            database.articleDao().insertCrossRef(
                CollectionArticleCrossRef(collectionId = artesId, articleId = legacyArticleId)
            )

            val wikipediaRepo = FakeE2EWikipediaRepository(
                searchResults = listOf(
                    WikipediaSearchResult(
                        pageId = 9001L,
                        title = "Renascimento",
                        description = "Movimento cultural europeu",
                        snippet = "Humanismo e arte clássica.",
                        thumbnailUrl = null,
                        url = "https://pt.wikipedia.org/wiki/Renascimento"
                    )
                ),
                articles = mapOf(
                    9001L to WikipediaArticle(
                        pageId = 9001L,
                        title = "Renascimento",
                        description = "Movimento cultural europeu",
                        content = "Conteúdo completo atualizado da Wikipédia.",
                        imageUrl = null,
                        url = "https://pt.wikipedia.org/wiki/Renascimento"
                    )
                )
            )

            lateinit var navController: NavHostController

            composeTestRule.setContent {
                navController = rememberNavController()
                RealDbEruditoNavHost(
                    navController = navController,
                    wikipediaRepo = wikipediaRepo
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_search_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("search_query_input").performTextInput("Renascimento")
            composeTestRule.onNodeWithTag("search_submit_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("search_result_card_9001").performClick()
            composeTestRule.waitForIdle()

            // Reconhece automaticamente a associação existente via fallback de URL legado
            composeTestRule.onNodeWithText("Salvo em 1 coleção").assertIsDisplayed()
            composeTestRule.onNodeWithText("Gerenciar coleções").assertIsDisplayed()

            composeTestRule.onNodeWithTag("wikipedia_article_save_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_item_$artesId").assertIsOn()
            composeTestRule.onNodeWithTag("collection_picker_item_$historiaId").assertIsOff()
            composeTestRule.onNodeWithTag("collection_picker_saved_badge_$artesId", useUnmergedTree = true)
                .assertIsDisplayed()

            composeTestRule.onNodeWithTag("collection_picker_item_$historiaId").performClick()
            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Salvo em 2 coleções").assertIsDisplayed()

            // Registro único reutilizado, wikipediaPageId preenchido e sem duplicação nas associações
            assertEquals(1, database.articleDao().getArticleCount())
            val reused = database.articleDao().getArticleById(legacyArticleId)
            assertNotNull(reused)
            assertEquals(9001L, reused?.wikipediaPageId)
            assertEquals("Conteúdo completo atualizado da Wikipédia.", reused?.content)
        assertEquals(
            setOf(artesId, historiaId),
            articleRepository.getCollectionIdsForArticle(legacyArticleId).first().toSet()
        )
    }

    @Test
    fun `removing all collections from offline ArticleDetail preserves article in Home library and allows reassociating`() = runBlocking {
        val colId = database.collectionDao().insertCollection(
                CollectionEntity(name = "Grécia Antiga", createdAt = 1_000L)
            )
            val articleId = articleRepository.saveArticleAndAssociateCollections(
                com.example.erudito.domain.model.Article(
                    title = "Sócrates",
                    url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                    description = "Filósofo clássico",
                    content = "Sócrates foi um filósofo ateniense.",
                    wikipediaPageId = 77L
                ),
                listOf(colId)
            )

            lateinit var navController: NavHostController

            composeTestRule.setContent {
                navController = rememberNavController()
                RealDbEruditoNavHost(
                    navController = navController,
                    wikipediaRepo = FakeE2EWikipediaRepository()
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasScrollToNodeAction())
                .performScrollToNode(hasTestTag("article_card_$articleId"))
            composeTestRule.onNodeWithTag("article_card_$articleId").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.ArticleDetail.route, navController.currentDestination?.route)
            composeTestRule.onNodeWithText("Salvo em 1 coleção").assertIsDisplayed()

            // Desmarca todas as coleções na leitura offline
            composeTestRule.onNodeWithTag("article_detail_manage_collections_button")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_item_$colId").performClick()
            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Nenhuma coleção associada").assertIsDisplayed()
            composeTestRule.onNodeWithText("Artigo removido das coleções. Ele continua salvo na biblioteca.")
                .assertIsDisplayed()

            // Volta para Home e confirma que o artigo permanece salvo na biblioteca e a coleção zerou
            composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(Screen.Home.route, navController.currentDestination?.route)
            composeTestRule.onNode(hasScrollToNodeAction())
                .performScrollToNode(hasTestTag("collection_card_$colId"))
            composeTestRule.onNodeWithText("0 artigos salvos").assertIsDisplayed()
            composeTestRule.onNode(hasScrollToNodeAction())
                .performScrollToNode(hasTestTag("article_card_$articleId"))
            composeTestRule.onNodeWithTag("article_card_$articleId").assertIsDisplayed()
            assertEquals(1, database.articleDao().getArticleCount())
            assertEquals(emptyList<Long>(), articleRepository.getCollectionIdsForArticle(articleId).first())

            // Reabre pela Home e re-associa à coleção offline
            composeTestRule.onNodeWithTag("article_card_$articleId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("article_detail_manage_collections_button")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("collection_picker_item_$colId").assertIsOff()
            composeTestRule.onNodeWithTag("collection_picker_item_$colId").performClick()
            composeTestRule.onNodeWithTag("collection_picker_confirm_button").performClick()
            composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Salvo em 1 coleção").assertIsDisplayed()
        assertEquals(listOf(colId), articleRepository.getCollectionIdsForArticle(articleId).first())
    }

    @Composable
    private fun RealDbEruditoNavHost(
        navController: NavHostController,
        wikipediaRepo: WikipediaRepository,
        connectivityObserver: ConnectivityObserver = FakeConnectivityObserver(initialOnline = true)
    ) {
        val refreshSavedArticleUseCase = RefreshSavedArticleUseCase(
            articleRepository = articleRepository,
            wikipediaRepository = wikipediaRepo,
            connectivityObserver = connectivityObserver,
            syncPolicy = SyncPolicy()
        )
        val syncSavedArticlesUseCase = SyncSavedArticlesUseCase(
            articleRepository = articleRepository,
            refreshSavedArticleUseCase = refreshSavedArticleUseCase,
            connectivityObserver = connectivityObserver
        )
        EruditoNavHost(
            navController = navController,
            homeViewModelProvider = { backStackEntry ->
                navViewModel(backStackEntry) {
                    HomeViewModel(
                        getCollectionsUseCase = GetCollectionsUseCase(collectionRepository),
                        getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepository),
                        createCollectionUseCase = CreateCollectionUseCase(collectionRepository),
                        renameCollectionUseCase = RenameCollectionUseCase(collectionRepository),
                        deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepository),
                        syncSavedArticlesUseCase = syncSavedArticlesUseCase,
                        connectivityObserver = connectivityObserver
                    )
                }
            },
            collectionsViewModelProvider = { backStackEntry ->
                navViewModel(backStackEntry) {
                    CollectionsViewModel(
                        getCollectionsUseCase = GetCollectionsUseCase(collectionRepository),
                        createCollectionUseCase = CreateCollectionUseCase(collectionRepository),
                        renameCollectionUseCase = RenameCollectionUseCase(collectionRepository),
                        deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepository)
                    )
                }
            },
            collectionDetailViewModelProvider = { backStackEntry ->
                navViewModel(backStackEntry) { savedStateHandle ->
                    CollectionDetailViewModel(
                        savedStateHandle = savedStateHandle,
                        getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(collectionRepository),
                        renameCollectionUseCase = RenameCollectionUseCase(collectionRepository),
                        deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepository),
                        removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository)
                    )
                }
            },
            articleDetailViewModelProvider = { backStackEntry ->
                navViewModel(backStackEntry) { savedStateHandle ->
                    ArticleDetailViewModel(
                        savedStateHandle = savedStateHandle,
                        observeArticleByIdUseCase = ObserveArticleByIdUseCase(articleRepository),
                        getCollectionsUseCase = GetCollectionsUseCase(collectionRepository),
                        createCollectionUseCase = CreateCollectionUseCase(collectionRepository),
                        addArticleToCollectionsUseCase = AddArticleToCollectionsUseCase(
                            articleRepository = articleRepository,
                            collectionRepository = collectionRepository
                        ),
                        removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository),
                        getCollectionIdsForArticleUseCase = GetCollectionIdsForArticleUseCase(articleRepository),
                        refreshSavedArticleUseCase = refreshSavedArticleUseCase,
                        connectivityObserver = connectivityObserver,
                        htmlProcessor = WikipediaHtmlSanitizer()
                    )
                }
            },
            searchViewModelProvider = { backStackEntry ->
                navViewModel(backStackEntry) {
                    SearchViewModel(
                        searchWikipediaArticlesUseCase = SearchWikipediaArticlesUseCase(wikipediaRepo),
                        connectivityObserver = connectivityObserver
                    )
                }
            },
            wikipediaArticleViewModelProvider = { backStackEntry ->
                navViewModel(backStackEntry) { savedStateHandle ->
                    WikipediaArticleViewModel(
                        savedStateHandle = savedStateHandle,
                        getWikipediaArticleUseCase = GetWikipediaArticleUseCase(wikipediaRepo),
                        getCollectionsUseCase = GetCollectionsUseCase(collectionRepository),
                        createCollectionUseCase = CreateCollectionUseCase(collectionRepository),
                        getSavedArticlesUseCase = GetSavedArticlesUseCase(articleRepository),
                        saveWikipediaArticleToCollectionsUseCase = SaveWikipediaArticleToCollectionsUseCase(
                            articleRepository = articleRepository,
                            collectionRepository = collectionRepository
                        ),
                        addArticleToCollectionsUseCase = AddArticleToCollectionsUseCase(
                            articleRepository = articleRepository,
                            collectionRepository = collectionRepository
                        ),
                        removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository),
                        getCollectionIdsForArticleUseCase = GetCollectionIdsForArticleUseCase(articleRepository),
                        getSavedArticleByWikipediaPageIdUseCase = GetSavedArticleByWikipediaPageIdUseCase(articleRepository),
                        refreshSavedArticleUseCase = refreshSavedArticleUseCase,
                        connectivityObserver = connectivityObserver,
                        htmlProcessor = WikipediaHtmlSanitizer()
                    )
                }
            }
        )
    }

    @Composable
    private inline fun <reified VM : ViewModel> navViewModel(
        backStackEntry: NavBackStackEntry,
        crossinline create: (SavedStateHandle) -> VM
    ): VM {
        return viewModel(
            viewModelStoreOwner = backStackEntry,
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    return create(extras.createSavedStateHandle()) as T
                }
            }
        )
    }

    private class FakeE2EWikipediaRepository(
        private val searchResults: List<WikipediaSearchResult> = emptyList(),
        private val articles: Map<Long, WikipediaArticle> = emptyMap()
    ) : WikipediaRepository {
        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = searchResults

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? = articles[pageId]
    }
}
