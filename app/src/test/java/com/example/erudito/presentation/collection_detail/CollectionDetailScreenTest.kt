package com.example.erudito.presentation.collection_detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.CollectionRepository
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionWithArticlesUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
class CollectionDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `CollectionDetailScreen cancels removal dialog without modifying collection or article`() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = ReactiveScreenCollectionRepository(articleRepo)
            val colId = collectionRepo.createCollection("Filosofia")
            val articleId = articleRepo.saveArticleAndAssociateCollections(
                Article(title = "Sócrates", url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"),
                listOf(colId)
            )
            val viewModel = buildViewModel(colId, collectionRepo, articleRepo)

            composeTestRule.setContent {
                CollectionDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToArticleDetail = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("article_card_$articleId").assertIsDisplayed()
            composeTestRule.onNodeWithText("1 artigo associado").assertIsDisplayed()

            composeTestRule.onNodeWithTag("remove_article_button_$articleId").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("remove_article_confirmation_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithText("Remover da coleção").assertIsDisplayed()

            composeTestRule.onNodeWithTag("cancel_remove_article_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("remove_article_confirmation_dialog").assertDoesNotExist()
            composeTestRule.onNodeWithTag("article_card_$articleId").assertIsDisplayed()
            assertEquals(listOf(colId), articleRepo.getCollectionIdsForArticle(articleId).first())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `CollectionDetailScreen confirms individual removal updates list reactively preserves article and other collections and shows Snackbar`() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val articleRepo = FakeArticleRepository()
            val collectionRepo = ReactiveScreenCollectionRepository(articleRepo)
            val col1Id = collectionRepo.createCollection("Filosofia")
            val col2Id = collectionRepo.createCollection("Grécia Antiga")

            val socratesId = articleRepo.saveArticleAndAssociateCollections(
                Article(
                    title = "Sócrates",
                    description = "Filósofo clássico",
                    url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                    wikipediaPageId = 42L
                ),
                listOf(col1Id, col2Id)
            )
            val plataoId = articleRepo.saveArticleAndAssociateCollections(
                Article(
                    title = "Platão",
                    description = "Fundador da Academia",
                    url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o",
                    wikipediaPageId = 43L
                ),
                listOf(col1Id)
            )
            val viewModel = buildViewModel(col1Id, collectionRepo, articleRepo)

            composeTestRule.setContent {
                CollectionDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToArticleDetail = {}
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("2 artigos associados").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_detail_list")
                .performScrollToNode(hasTestTag("article_card_$socratesId"))
            composeTestRule.onNodeWithTag("article_card_$socratesId").assertIsDisplayed()
            composeTestRule.onNodeWithTag("collection_detail_list")
                .performScrollToNode(hasTestTag("article_card_$plataoId"))
            composeTestRule.onNodeWithTag("article_card_$plataoId").assertIsDisplayed()

            // Remove Sócrates da coleção Filosofia
            composeTestRule.onNodeWithTag("collection_detail_list")
                .performScrollToNode(hasTestTag("article_card_$socratesId"))
            composeTestRule.onNodeWithTag("remove_article_button_$socratesId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("remove_article_confirmation_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("confirm_remove_article_button").performClick()
            composeTestRule.waitForIdle()

            // Lista e contador atualizados reativamente, Snackbar exibido
            composeTestRule.onNodeWithTag("remove_article_confirmation_dialog").assertDoesNotExist()
            composeTestRule.onNodeWithTag("article_card_$socratesId").assertDoesNotExist()
            composeTestRule.onNodeWithTag("article_card_$plataoId").assertIsDisplayed()
            composeTestRule.onNodeWithText("1 artigo associado").assertIsDisplayed()
            composeTestRule.onNodeWithText("Artigo \"Sócrates\" removido da coleção.").assertIsDisplayed()

            // Artigo Sócrates permanece no banco e na outra coleção
            assertNotNull(articleRepo.getArticleById(socratesId))
            assertEquals(listOf(col2Id), articleRepo.getCollectionIdsForArticle(socratesId).first())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `CollectionDetailScreen shows empty state after removing last article and shows error Snackbar on failure`() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val baseRepo = FakeArticleRepository()
            var shouldFailRemove = true
            val togglingFailRepo = object : ArticleRepository by baseRepo {
                override suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long) {
                    if (shouldFailRemove) {
                        throw IllegalStateException("Falha simulada ao remover artigo")
                    }
                    baseRepo.removeArticleFromCollection(articleId, collectionId)
                }
            }
            val collectionRepo = ReactiveScreenCollectionRepository(togglingFailRepo)
            val colId = collectionRepo.createCollection("Física")
            val articleId = baseRepo.saveArticleAndAssociateCollections(
                Article(title = "Fóton", url = "https://pt.wikipedia.org/wiki/F%C3%B3ton"),
                listOf(colId)
            )
            val viewModel = buildViewModel(colId, collectionRepo, togglingFailRepo)

            composeTestRule.setContent {
                CollectionDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToArticleDetail = {}
                )
            }

            composeTestRule.waitForIdle()

            // Primeira tentativa falha e exibe erro via Snackbar mantendo o artigo na coleção
            composeTestRule.onNodeWithTag("remove_article_button_$articleId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("confirm_remove_article_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Falha simulada ao remover artigo").assertIsDisplayed()
            composeTestRule.onNodeWithTag("article_card_$articleId").assertIsDisplayed()

            // Segunda tentativa bem-sucedida remove o último artigo e exibe estado vazio da coleção
            shouldFailRemove = false
            composeTestRule.onNodeWithTag("remove_article_button_$articleId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("confirm_remove_article_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Nenhum artigo nesta coleção").assertIsDisplayed()
            composeTestRule.onNodeWithText("0 artigos associados").assertIsDisplayed()
            assertNotNull(baseRepo.getArticleById(articleId))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun buildViewModel(
        collectionId: Long,
        collectionRepo: CollectionRepository,
        articleRepo: ArticleRepository
    ): CollectionDetailViewModel {
        return CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to collectionId)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(collectionRepo),
            renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
            deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepo)
        )
    }

    private class ReactiveScreenCollectionRepository(
        private val articleRepository: ArticleRepository
    ) : CollectionRepository {
        private val collections = mutableListOf<Collection>()
        private val collectionsFlow = MutableStateFlow<List<Collection>>(emptyList())

        override fun getCollections(): Flow<List<Collection>> = collectionsFlow

        override fun getCollectionWithArticles(collectionId: Long): Flow<CollectionWithArticles?> {
            return combine(
                collectionsFlow,
                articleRepository.getArticlesForCollection(collectionId)
            ) { allCollections, articles ->
                val col = allCollections.find { it.id == collectionId } ?: return@combine null
                CollectionWithArticles(
                    collection = col.copy(articleCount = articles.size),
                    articles = articles
                )
            }
        }

        override suspend fun getCollectionById(collectionId: Long): Collection? {
            return collections.find { it.id == collectionId }
        }

        override suspend fun createCollection(name: String): Long {
            val newId = (collections.maxOfOrNull { it.id } ?: 0L) + 1L
            collections.add(Collection(id = newId, name = name, articleCount = 0))
            collectionsFlow.value = collections.toList()
            return newId
        }

        override suspend fun renameCollection(id: Long, newName: String) {
            val idx = collections.indexOfFirst { it.id == id }
            if (idx >= 0) {
                collections[idx] = collections[idx].copy(name = newName)
                collectionsFlow.value = collections.toList()
            }
        }

        override suspend fun deleteCollection(id: Long) {
            collections.removeAll { it.id == id }
            collectionsFlow.value = collections.toList()
        }
    }
}
