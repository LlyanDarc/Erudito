package com.example.erudito.presentation.article_detail

import androidx.lifecycle.SavedStateHandle
import com.example.erudito.domain.connectivity.FakeConnectivityObserver
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.FakeArticleRepository
import com.example.erudito.domain.repository.FakeCollectionRepository
import com.example.erudito.domain.usecase.AddArticleToCollectionsUseCase
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionIdsForArticleUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.ObserveArticleByIdUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleDetailViewModelTest {

    private lateinit var fakeCollectionRepository: FakeCollectionRepository
    private lateinit var fakeArticleRepository: FakeArticleRepository

    @Before
    fun setUp() {
        fakeCollectionRepository = FakeCollectionRepository()
        fakeArticleRepository = FakeArticleRepository()
    }

    private fun createViewModel(
        articleId: Long,
        articleRepo: ArticleRepository = fakeArticleRepository,
        collectionRepo: FakeCollectionRepository = fakeCollectionRepository,
        connectivityObserver: FakeConnectivityObserver = FakeConnectivityObserver()
    ): ArticleDetailViewModel {
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
            connectivityObserver = connectivityObserver
        )
    }

    @Test
    fun `loads local article and reactively identifies associated and available collections`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Filosofia")
            val col2 = fakeCollectionRepository.createCollection("História")
            val articleId = fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(
                    title = "Sócrates",
                    description = "Filósofo grego",
                    content = "Conteúdo offline",
                    imageUrl = "https://upload.wikimedia.org/socrates.jpg",
                    url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                    wikipediaPageId = 42L
                ),
                listOf(col1)
            )

            val viewModel = createViewModel(articleId)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals("Sócrates", state.article?.title)
            assertEquals("https://upload.wikimedia.org/socrates.jpg", state.article?.imageUrl)
            assertFalse(state.isLoadingCollections)
            assertEquals(2, state.availableCollections.size)
            assertEquals(setOf(col1), state.savedCollectionIds)
            assertTrue(state.isSavedInAnyCollection)
            assertEquals(1, state.savedCollectionsCount)

            // Atualização externa reflete reativamente na tela
            fakeArticleRepository.addArticleToCollection(articleId, col2)
            advanceUntilIdle()

            assertEquals(setOf(col1, col2), viewModel.uiState.value.savedCollectionIds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `emits error state when local article is not found`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = createViewModel(999L)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.article)
            assertEquals("Artigo não encontrado no armazenamento local", state.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `open and dismiss collection picker resets unsaved toggle changes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Filosofia")
            val col2 = fakeCollectionRepository.createCollection("Artes")
            val articleId = fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(title = "Platão", url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"),
                listOf(col1)
            )

            val viewModel = createViewModel(articleId)
            advanceUntilIdle()

            viewModel.openCollectionPicker()
            assertTrue(viewModel.uiState.value.isPickerVisible)
            assertEquals(setOf(col1), viewModel.uiState.value.selectedCollectionIds)

            viewModel.toggleCollectionSelection(col2)
            assertEquals(setOf(col1, col2), viewModel.uiState.value.selectedCollectionIds)

            viewModel.dismissCollectionPicker()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isPickerVisible)
            assertEquals(setOf(col1), viewModel.uiState.value.savedCollectionIds)
            assertEquals(setOf(col1), viewModel.uiState.value.selectedCollectionIds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `adds article to multiple collections and removes unchecked associations without duplicating or deleting article`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Coleção 1")
            val col2 = fakeCollectionRepository.createCollection("Coleção 2")
            val col3 = fakeCollectionRepository.createCollection("Coleção 3")
            val articleId = fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(
                    title = "Aristóteles",
                    url = "https://pt.wikipedia.org/wiki/Arist%C3%B3teles",
                    wikipediaPageId = 77L
                ),
                listOf(col1)
            )

            val viewModel = createViewModel(articleId)
            val feedback = mutableListOf<String>()
            val job = launch { viewModel.feedbackEvents.collect { feedback.add(it) } }
            advanceUntilIdle()

            // Adiciona em col2 e col3 mantendo col1
            viewModel.openCollectionPicker()
            viewModel.toggleCollectionSelection(col2)
            viewModel.toggleCollectionSelection(col3)
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isPickerVisible)
            assertEquals(setOf(col1, col2, col3), viewModel.uiState.value.savedCollectionIds)
            assertEquals(1, fakeArticleRepository.getSavedArticles().first().size)
            assertEquals("Artigo salvo em 3 coleções!", feedback.last())

            // Remove col1 e col3 preservando col2
            viewModel.openCollectionPicker()
            viewModel.toggleCollectionSelection(col1)
            viewModel.toggleCollectionSelection(col3)
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertEquals(setOf(col2), viewModel.uiState.value.savedCollectionIds)
            assertNotNull(fakeArticleRepository.getArticleById(articleId))
            assertEquals("Coleções do artigo atualizadas!", feedback.last())

            // Remove todas as associações: artigo continua preservado no banco
            viewModel.openCollectionPicker()
            viewModel.toggleCollectionSelection(col2)
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.savedCollectionIds.isEmpty())
            assertNotNull(fakeArticleRepository.getArticleById(articleId))
            assertEquals(1, fakeArticleRepository.getSavedArticles().first().size)
            assertEquals(
                "Artigo removido das coleções. Ele continua salvo na biblioteca.",
                feedback.last()
            )
            job.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `confirming without changes avoids redundant repository write calls`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Física")
            val countingRepo = CountingArticleRepository(fakeArticleRepository)
            val articleId = countingRepo.saveArticleAndAssociateCollections(
                Article(title = "Fóton", url = "https://pt.wikipedia.org/wiki/F%C3%B3ton"),
                listOf(col1)
            )
            countingRepo.resetWriteCounters()

            val viewModel = createViewModel(articleId, articleRepo = countingRepo)
            advanceUntilIdle()

            viewModel.openCollectionPicker()
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isPickerVisible)
            assertEquals(0, countingRepo.addCalls)
            assertEquals(0, countingRepo.removeCalls)
            assertEquals(0, countingRepo.saveCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `createCollection during picker updates list reactively and allows associating article`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleId = fakeArticleRepository.insertArticle(
                Article(title = "Galileu", url = "https://pt.wikipedia.org/wiki/Galileu")
            )

            val viewModel = createViewModel(articleId)
            val feedback = mutableListOf<String>()
            val job = launch { viewModel.feedbackEvents.collect { feedback.add(it) } }
            advanceUntilIdle()

            viewModel.openCollectionPicker()
            assertTrue(viewModel.uiState.value.availableCollections.isEmpty())

            viewModel.createCollection("  Astronomia  ")
            advanceUntilIdle()

            val collections = viewModel.uiState.value.availableCollections
            assertEquals(1, collections.size)
            assertEquals("Astronomia", collections[0].name)
            assertEquals("Coleção \"Astronomia\" criada com sucesso!", feedback.last())
            assertTrue(viewModel.uiState.value.savedCollectionIds.isEmpty())

            viewModel.toggleCollectionSelection(collections[0].id)
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertEquals(setOf(collections[0].id), viewModel.uiState.value.savedCollectionIds)
            assertEquals(1, fakeArticleRepository.getSavedArticles().first().size)
            job.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `handles persistence failures gracefully and blocks simultaneous saves`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Biologia")
            val gatedRepo = GatedArticleRepository(fakeArticleRepository)
            val articleId = fakeArticleRepository.insertArticle(
                Article(title = "Charles Darwin", url = "https://pt.wikipedia.org/wiki/Charles_Darwin")
            )

            val viewModel = createViewModel(articleId, articleRepo = gatedRepo)
            val feedback = mutableListOf<String>()
            val job = launch { viewModel.feedbackEvents.collect { feedback.add(it) } }
            advanceUntilIdle()

            viewModel.openCollectionPicker()
            viewModel.toggleCollectionSelection(col1)
            viewModel.confirmCollectionSelection()
            runCurrent()

            assertTrue(viewModel.uiState.value.isSavingCollections)
            assertEquals(1, gatedRepo.addCallCount)

            // Bloqueia chamadas concorrentes enquanto salva
            viewModel.confirmCollectionSelection()
            viewModel.dismissCollectionPicker()
            runCurrent()
            assertEquals(1, gatedRepo.addCallCount)
            assertTrue(viewModel.uiState.value.isPickerVisible)

            gatedRepo.gate.completeExceptionally(IllegalStateException("Erro simulado ao associar"))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSavingCollections)
            assertTrue(viewModel.uiState.value.isPickerVisible)
            assertEquals("Erro simulado ao associar", viewModel.uiState.value.pickerErrorMessage)
            assertEquals("Erro simulado ao associar", feedback.last())
            job.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `reactively reflects external updates to saved article content and connectivity state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val connectivityObserver = FakeConnectivityObserver(initialOnline = false)
            val col1 = fakeCollectionRepository.createCollection("Filosofia")
            val articleId = fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(
                    title = "Sócrates",
                    description = "Versão inicial",
                    content = "Conteúdo inicial",
                    url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                    wikipediaPageId = 42L
                ),
                listOf(col1)
            )

            val viewModel = createViewModel(
                articleId = articleId,
                connectivityObserver = connectivityObserver
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isOnline)
            assertEquals("Conteúdo inicial", viewModel.uiState.value.article?.content)

            // Atualização externa do artigo no repositório deve refletir imediatamente na tela
            fakeArticleRepository.insertArticle(
                Article(
                    id = articleId,
                    title = "Sócrates",
                    description = "Filósofo clássico ateniense",
                    content = "Conteúdo atualizado sincronizado",
                    url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                    wikipediaPageId = 42L
                )
            )
            connectivityObserver.setOnline(true)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isOnline)
            assertEquals("Filósofo clássico ateniense", viewModel.uiState.value.article?.description)
            assertEquals("Conteúdo atualizado sincronizado", viewModel.uiState.value.article?.content)
            assertEquals(setOf(col1), viewModel.uiState.value.savedCollectionIds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class CountingArticleRepository(
        private val delegate: ArticleRepository
    ) : ArticleRepository by delegate {
        var saveCalls = 0
        var addCalls = 0
        var removeCalls = 0

        fun resetWriteCounters() {
            saveCalls = 0
            addCalls = 0
            removeCalls = 0
        }

        override suspend fun saveArticleAndAssociateCollections(
            article: Article,
            collectionIds: List<Long>
        ): Long {
            saveCalls++
            return delegate.saveArticleAndAssociateCollections(article, collectionIds)
        }

        override suspend fun addArticleToCollections(articleId: Long, collectionIds: List<Long>) {
            addCalls++
            delegate.addArticleToCollections(articleId, collectionIds)
        }

        override suspend fun removeArticleFromCollection(articleId: Long, collectionId: Long) {
            removeCalls++
            delegate.removeArticleFromCollection(articleId, collectionId)
        }
    }

    private class GatedArticleRepository(
        private val delegate: ArticleRepository
    ) : ArticleRepository by delegate {
        var addCallCount = 0
        val gate = CompletableDeferred<Unit>()

        override suspend fun addArticleToCollections(articleId: Long, collectionIds: List<Long>) {
            addCallCount++
            gate.await()
        }
    }
}
