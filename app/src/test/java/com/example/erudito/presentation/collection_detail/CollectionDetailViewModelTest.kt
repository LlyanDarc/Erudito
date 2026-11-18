package com.example.erudito.presentation.collection_detail

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteCollection does not emit collection not found error when flow emits null mid-deletion`() = runTest {
        val sampleCollection = CollectionWithArticles(
            collection = Collection(id = 1L, name = "Filosofia", articleCount = 1),
            articles = listOf(Article(id = 1L, title = "Sócrates", url = "https://pt.wikipedia.org/wiki/Socrates"))
        )
        val repository = RaceConditionTestCollectionRepository(initial = sampleCollection)
        val articleRepository = FakeArticleRepository()

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 1L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(repository),
            renameCollectionUseCase = RenameCollectionUseCase(repository),
            deleteCollectionUseCase = DeleteCollectionUseCase(repository),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository)
        )

        val observedErrors = mutableListOf<String?>()
        val job = launch(testDispatcher) {
            viewModel.uiState.collect { state ->
                observedErrors.add(state.errorMessage)
            }
        }

        viewModel.deleteCollection()

        val finalState = viewModel.uiState.value
        assertTrue(finalState.isDeleted)
        assertFalse(finalState.isDeleting)
        assertEquals("Filosofia", finalState.deletedCollectionName)
        assertNull(finalState.errorMessage)
        assertFalse(
            "Nunca deveria ter emitido 'Coleção não encontrada' durante exclusão bem-sucedida",
            observedErrors.contains("Coleção não encontrada")
        )
        job.cancel()
    }

    @Test
    fun `renameCollection emits rename feedback event with updated name`() = runTest {
        val sampleCollection = CollectionWithArticles(
            collection = Collection(id = 1L, name = "g", articleCount = 0),
            articles = emptyList()
        )
        val repository = RaceConditionTestCollectionRepository(initial = sampleCollection)
        val articleRepository = FakeArticleRepository()

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 1L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(repository),
            renameCollectionUseCase = RenameCollectionUseCase(repository),
            deleteCollectionUseCase = DeleteCollectionUseCase(repository),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository)
        )

        val feedback = mutableListOf<String>()
        val job = launch(testDispatcher) {
            viewModel.feedbackEvents.collect { feedback.add(it) }
        }

        viewModel.renameCollection("g renomeada")

        assertEquals(listOf("Coleção renomeada para \"g renomeada\"!"), feedback)
        assertFalse(viewModel.uiState.value.isRenameDialogOpen)
        job.cancel()
    }

    @Test
    fun `deleteCollection exposes real error when repository throws exception`() = runTest {
        val sampleCollection = CollectionWithArticles(
            collection = Collection(id = 1L, name = "Filosofia", articleCount = 0),
            articles = emptyList()
        )
        val repository = RaceConditionTestCollectionRepository(
            initial = sampleCollection,
            deleteError = IllegalStateException("Falha de I/O ao excluir")
        )
        val articleRepository = FakeArticleRepository()

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 1L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(repository),
            renameCollectionUseCase = RenameCollectionUseCase(repository),
            deleteCollectionUseCase = DeleteCollectionUseCase(repository),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository)
        )

        viewModel.deleteCollection()

        val state = viewModel.uiState.value
        assertFalse(state.isDeleted)
        assertFalse(state.isDeleting)
        assertEquals("Falha de I/O ao excluir", state.errorMessage)
    }

    @Test
    fun `confirmRemoveArticle removes only link with current collection preserves article and other associations and updates reactively`() = runTest {
        val articleRepository = FakeArticleRepository()
        val collectionRepository = ReactiveCollectionRepository(articleRepository)

        val col1Id = collectionRepository.createCollection("Filosofia")
        val col2Id = collectionRepository.createCollection("Grécia Antiga")

        val socratesId = articleRepository.saveArticleAndAssociateCollections(
            Article(
                title = "Sócrates",
                url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                wikipediaPageId = 42L
            ),
            listOf(col1Id, col2Id)
        )
        val plataoId = articleRepository.saveArticleAndAssociateCollections(
            Article(
                title = "Platão",
                url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o",
                wikipediaPageId = 43L
            ),
            listOf(col1Id)
        )

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to col1Id)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(collectionRepository),
            renameCollectionUseCase = RenameCollectionUseCase(collectionRepository),
            deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepository),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository)
        )

        val feedback = mutableListOf<String>()
        val feedbackJob = launch(testDispatcher) {
            viewModel.feedbackEvents.collect { feedback.add(it) }
        }

        assertEquals(2, viewModel.uiState.value.collectionWithArticles?.articles?.size)

        val socrates = viewModel.uiState.value.collectionWithArticles!!.articles.first { it.id == socratesId }
        viewModel.openRemoveArticleDialog(socrates)
        assertEquals(socrates, viewModel.uiState.value.articleToRemove)

        viewModel.confirmRemoveArticle()

        // Dialog fecha e lista atualiza reativamente contendo apenas Platão
        assertNull(viewModel.uiState.value.articleToRemove)
        assertFalse(viewModel.uiState.value.isRemovingArticle)
        assertNull(viewModel.uiState.value.errorMessage)
        val remainingInCol1 = viewModel.uiState.value.collectionWithArticles?.articles.orEmpty()
        assertEquals(listOf(plataoId), remainingInCol1.map { it.id })

        // Artigo continua salvo no banco e associado à outra coleção
        assertNotNull(articleRepository.getArticleById(socratesId))
        assertEquals(listOf(col2Id), articleRepository.getCollectionIdsForArticle(socratesId).first())
        assertEquals(listOf("Artigo \"Sócrates\" removido da coleção."), feedback)

        feedbackJob.cancel()
    }

    @Test
    fun `closeRemoveArticleDialog cancels removal without altering associations`() = runTest {
        val articleRepository = FakeArticleRepository()
        val collectionRepository = ReactiveCollectionRepository(articleRepository)
        val col1Id = collectionRepository.createCollection("História")
        val articleId = articleRepository.saveArticleAndAssociateCollections(
            Article(title = "Roma Antiga", url = "https://pt.wikipedia.org/wiki/Roma_Antiga"),
            listOf(col1Id)
        )

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to col1Id)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(collectionRepository),
            renameCollectionUseCase = RenameCollectionUseCase(collectionRepository),
            deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepository),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository)
        )

        val article = viewModel.uiState.value.collectionWithArticles!!.articles.first()
        viewModel.openRemoveArticleDialog(article)
        assertEquals(article, viewModel.uiState.value.articleToRemove)

        viewModel.closeRemoveArticleDialog()
        assertNull(viewModel.uiState.value.articleToRemove)
        assertEquals(listOf(col1Id), articleRepository.getCollectionIdsForArticle(articleId).first())
        assertEquals(1, viewModel.uiState.value.collectionWithArticles?.articles?.size)
    }

    @Test
    fun `confirmRemoveArticle exposes errorMessage when removal fails`() = runTest {
        val sampleArticle = Article(id = 10L, title = "Galileu", url = "https://pt.wikipedia.org/wiki/Galileu")
        val collectionRepo = RaceConditionTestCollectionRepository(
            initial = CollectionWithArticles(
                collection = Collection(id = 1L, name = "Astronomia", articleCount = 1),
                articles = listOf(sampleArticle)
            )
        )
        val failingArticleRepo = object : ArticleRepository by FakeArticleRepository() {
            override suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long) {
                throw IllegalStateException("Falha ao remover vínculo no banco")
            }
        }

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 1L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(collectionRepo),
            renameCollectionUseCase = RenameCollectionUseCase(collectionRepo),
            deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(failingArticleRepo)
        )

        viewModel.openRemoveArticleDialog(sampleArticle)
        viewModel.confirmRemoveArticle()

        val state = viewModel.uiState.value
        assertFalse(state.isRemovingArticle)
        assertNull(state.articleToRemove)
        assertEquals("Falha ao remover vínculo no banco", state.errorMessage)
        assertEquals(1, state.collectionWithArticles?.articles?.size)
    }

    @Test
    fun `renameCollection reports error and preserves dialog closed when collection id does not exist`() = runTest {
        val articleRepository = FakeArticleRepository()
        val collectionRepository = ReactiveCollectionRepository(articleRepository)

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 9999L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(collectionRepository),
            renameCollectionUseCase = RenameCollectionUseCase(collectionRepository),
            deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepository),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(articleRepository)
        )
        val feedback = mutableListOf<String>()
        val feedbackJob = launch(testDispatcher) {
            viewModel.feedbackEvents.collect { feedback.add(it) }
        }

        viewModel.openRenameDialog()
        viewModel.renameCollection("Novo Nome")

        assertFalse(viewModel.uiState.value.isRenameDialogOpen)
        assertEquals("Coleção não encontrada.", viewModel.uiState.value.errorMessage)
        assertTrue(feedback.isEmpty())
        feedbackJob.cancel()
    }

    @Test
    fun `CancellationException restores loading and dialog states without error in CollectionDetailViewModel`() = runTest {
        val sampleArticle = Article(id = 10L, title = "Galileu", url = "https://pt.wikipedia.org/wiki/Galileu")
        val sampleCollection = CollectionWithArticles(
            collection = Collection(id = 1L, name = "Astronomia", articleCount = 1),
            articles = listOf(sampleArticle)
        )
        val cancellingCollectionRepo = object : CollectionRepository {
            override fun getCollections(): Flow<List<Collection>> = MutableStateFlow(emptyList())
            override fun getCollectionWithArticles(collectionId: Long): Flow<CollectionWithArticles?> =
                MutableStateFlow(sampleCollection)
            override suspend fun getCollectionById(collectionId: Long): Collection? = sampleCollection.collection
            override suspend fun createCollection(name: String): Long = 1L
            override suspend fun renameCollection(id: Long, newName: String): Unit =
                throw CancellationException("Cancelado")
            override suspend fun deleteCollection(id: Long): Unit =
                throw CancellationException("Cancelado")
        }
        val cancellingArticleRepo = object : ArticleRepository by FakeArticleRepository() {
            override suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long) {
                throw CancellationException("Cancelado")
            }
        }

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 1L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(cancellingCollectionRepo),
            renameCollectionUseCase = RenameCollectionUseCase(cancellingCollectionRepo),
            deleteCollectionUseCase = DeleteCollectionUseCase(cancellingCollectionRepo),
            removeArticleFromCollectionUseCase = RemoveArticleFromCollectionUseCase(cancellingArticleRepo)
        )

        viewModel.openRenameDialog()
        viewModel.renameCollection("Novo Nome")
        assertFalse(viewModel.uiState.value.isRenameDialogOpen)
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.openDeleteDialog()
        viewModel.deleteCollection()
        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.isDeleted)
        assertFalse(viewModel.uiState.value.isDeleteDialogOpen)
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.openRemoveArticleDialog(sampleArticle)
        viewModel.confirmRemoveArticle()
        assertFalse(viewModel.uiState.value.isRemovingArticle)
        assertNull(viewModel.uiState.value.articleToRemove)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    private class RaceConditionTestCollectionRepository(
        initial: CollectionWithArticles?,
        private val deleteError: Throwable? = null
    ) : CollectionRepository {

        private val flow = MutableStateFlow(initial)

        override fun getCollections(): Flow<List<Collection>> = MutableStateFlow(emptyList())

        override fun getCollectionWithArticles(collectionId: Long): Flow<CollectionWithArticles?> = flow

        override suspend fun getCollectionById(collectionId: Long): Collection? = flow.value?.collection

        override suspend fun createCollection(name: String): Long = 1L

        override suspend fun renameCollection(id: Long, newName: String) {}

        override suspend fun deleteCollection(id: Long) {
            deleteError?.let { throw it }
            // Simula o InvalidationTracker do Room emitindo null ANTES de deleteCollection retornar
            flow.value = null
        }
    }

    private class ReactiveCollectionRepository(
        private val articleRepository: FakeArticleRepository
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
            if (idx < 0) {
                throw NoSuchElementException("Coleção não encontrada.")
            }
            collections[idx] = collections[idx].copy(name = newName)
            collectionsFlow.value = collections.toList()
        }

        override suspend fun deleteCollection(id: Long) {
            collections.removeAll { it.id == id }
            collectionsFlow.value = collections.toList()
        }
    }
}
