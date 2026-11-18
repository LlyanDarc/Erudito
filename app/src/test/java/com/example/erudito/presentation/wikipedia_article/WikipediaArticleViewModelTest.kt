package com.example.erudito.presentation.wikipedia_article

import androidx.lifecycle.SavedStateHandle
import com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer
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
class WikipediaArticleViewModelTest {

    private lateinit var fakeRepository: FakeWikipediaArticleRepository
    private lateinit var fakeCollectionRepository: FakeCollectionRepository
    private lateinit var fakeArticleRepository: FakeArticleRepository

    @Before
    fun setUp() {
        fakeRepository = FakeWikipediaArticleRepository()
        fakeCollectionRepository = FakeCollectionRepository()
        fakeArticleRepository = FakeArticleRepository()
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        articleRepo: ArticleRepository = fakeArticleRepository,
        collectionRepo: FakeCollectionRepository = fakeCollectionRepository,
        connectivityObserver: FakeConnectivityObserver = FakeConnectivityObserver()
    ): WikipediaArticleViewModel {
        return WikipediaArticleViewModel(
            savedStateHandle = savedStateHandle,
            getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepository),
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
                wikipediaRepository = fakeRepository,
                connectivityObserver = connectivityObserver,
                syncPolicy = SyncPolicy()
            ),
            connectivityObserver = connectivityObserver,
            htmlProcessor = WikipediaHtmlSanitizer()
        )
    }

    @Test
    fun `initial state is Initial when SavedStateHandle has no pageId`() {
        val viewModel = createViewModel(SavedStateHandle())

        assertEquals(WikipediaArticleUiState.Initial, viewModel.uiState.value)
        assertEquals(0, fakeRepository.getArticleCallCount)
    }

    @Test
    fun `invalid non positive pageId avoids repository call and emits Error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 0L))
            )
            advanceUntilIdle()

            assertEquals(0, fakeRepository.getArticleCallCount)
            assertEquals(
                WikipediaArticleUiState.Error("Identificador de artigo inválido."),
                viewModel.uiState.value
            )

            viewModel.loadArticle(-10L)
            assertEquals(0, fakeRepository.getArticleCallCount)
            assertEquals(
                WikipediaArticleUiState.Error("Identificador de artigo inválido."),
                viewModel.uiState.value
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `loads article with image and description successfully`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val expectedArticle = WikipediaArticle(
                pageId = 42L,
                title = "Sócrates",
                description = "Filósofo da Grécia Antiga",
                content = "Sócrates foi um filósofo ateniense.\n\nConsiderado um dos fundadores da filosofia ocidental.",
                imageUrl = "https://upload.wikimedia.org/socrates.jpg",
                url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
            )
            fakeRepository.articleById[42L] = expectedArticle

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 42L))
            )
            advanceUntilIdle()

            assertEquals(1, fakeRepository.getArticleCallCount)
            assertEquals(42L, fakeRepository.lastRequestedPageId)
            val state = viewModel.uiState.value as WikipediaArticleUiState.Success
            assertEquals(expectedArticle, state.article)
            assertEquals("Filósofo da Grécia Antiga", state.article.description)
            assertEquals("https://upload.wikimedia.org/socrates.jpg", state.article.imageUrl)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `loads article without image or description successfully`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val articleWithoutOptionals = WikipediaArticle(
                pageId = 99L,
                title = "Algoritmo de Euclides",
                description = null,
                content = "Método eficiente para calcular o máximo divisor comum.",
                imageUrl = null,
                url = "https://pt.wikipedia.org/wiki/Algoritmo_de_Euclides"
            )
            fakeRepository.articleById[99L] = articleWithoutOptionals

            val viewModel = createViewModel(SavedStateHandle())
            viewModel.loadArticle(99L)
            advanceUntilIdle()

            val state = viewModel.uiState.value as WikipediaArticleUiState.Success
            assertEquals(articleWithoutOptionals, state.article)
            assertNull(state.article.description)
            assertNull(state.article.imageUrl)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `emits Error when repository throws WikipediaException or returns null`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            fakeRepository.exceptionToThrow = WikipediaException.NoConnection()

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 10L))
            )
            advanceUntilIdle()

            assertEquals(
                WikipediaArticleUiState.Error(
                    "Sem conexão com a internet. Verifique sua rede e tente novamente."
                ),
                viewModel.uiState.value
            )

            // Quando artigo não é encontrado (null)
            fakeRepository.exceptionToThrow = null
            viewModel.loadArticle(888L)
            advanceUntilIdle()

            assertEquals(
                WikipediaArticleUiState.Error("Artigo não encontrado na Wikipédia."),
                viewModel.uiState.value
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `retry after failure reloads same pageId and transitions to Success`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            fakeRepository.exceptionToThrow = WikipediaException.Timeout()

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 55L))
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is WikipediaArticleUiState.Error)
            assertEquals(1, fakeRepository.getArticleCallCount)

            val recoveredArticle = WikipediaArticle(
                pageId = 55L,
                title = "Renascimento",
                description = "Movimento cultural europeu",
                content = "O Renascimento foi um período de transformação cultural.",
                imageUrl = null,
                url = "https://pt.wikipedia.org/wiki/Renascimento"
            )
            fakeRepository.exceptionToThrow = null
            fakeRepository.articleById[55L] = recoveredArticle

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(2, fakeRepository.getArticleCallCount)
            assertEquals(WikipediaArticleUiState.Success(recoveredArticle), viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `preserves Loading state during in-flight request and ignores duplicate load for same pageId`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val gate = CompletableDeferred<WikipediaArticle?>()
            fakeRepository.pendingDeferred = gate

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 77L))
            )

            assertEquals(WikipediaArticleUiState.Initial, viewModel.uiState.value)

            runCurrent()
            assertEquals(WikipediaArticleUiState.Loading, viewModel.uiState.value)
            assertEquals(1, fakeRepository.getArticleCallCount)

            // Chamada duplicada para o mesmo pageId enquanto carrega não dispara nova requisição
            viewModel.loadArticle(77L)
            viewModel.retry()
            runCurrent()
            assertEquals(WikipediaArticleUiState.Loading, viewModel.uiState.value)
            assertEquals(1, fakeRepository.getArticleCallCount)

            val article = WikipediaArticle(
                pageId = 77L,
                title = "Aristóteles",
                content = "Filósofo grego clássico.",
                url = "https://pt.wikipedia.org/wiki/Arist%C3%B3teles"
            )
            gate.complete(article)
            advanceUntilIdle()

            assertEquals(WikipediaArticleUiState.Success(article), viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `loads available collections and reactively detects already associated collections`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Filosofia")
            val col2 = fakeCollectionRepository.createCollection("História")
            val wikiArticle = WikipediaArticle(
                pageId = 42L,
                title = "Sócrates",
                content = "Conteúdo",
                url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
            )
            fakeRepository.articleById[42L] = wikiArticle
            fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(
                    title = "Sócrates",
                    url = wikiArticle.url,
                    wikipediaPageId = 42L
                ),
                listOf(col1)
            )

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 42L))
            )
            advanceUntilIdle()

            val colState = viewModel.collectionsUiState.value
            assertFalse(colState.isLoadingCollections)
            assertEquals(2, colState.availableCollections.size)
            assertEquals(setOf(col1), colState.savedCollectionIds)
            assertTrue(colState.isSaved)
            assertEquals(1, colState.savedCollectionsCount)

            // Atualização reativa externa também reflete automaticamente no estado
            val savedId = fakeArticleRepository.getArticleByWikipediaPageId(42L)!!.id
            fakeArticleRepository.addArticleToCollection(savedId, col2)
            advanceUntilIdle()

            assertEquals(setOf(col1, col2), viewModel.collectionsUiState.value.savedCollectionIds)
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
            val wikiArticle = WikipediaArticle(
                pageId = 100L,
                title = "Renascimento",
                content = "Conteúdo",
                url = "https://pt.wikipedia.org/wiki/Renascimento"
            )
            fakeRepository.articleById[100L] = wikiArticle
            fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(title = "Renascimento", url = wikiArticle.url, wikipediaPageId = 100L),
                listOf(col1)
            )

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 100L))
            )
            advanceUntilIdle()

            viewModel.openCollectionPicker()
            assertTrue(viewModel.collectionsUiState.value.isPickerVisible)
            assertEquals(setOf(col1), viewModel.collectionsUiState.value.selectedCollectionIds)

            viewModel.toggleCollectionSelection(col2)
            assertEquals(setOf(col1, col2), viewModel.collectionsUiState.value.selectedCollectionIds)

            // Cancela e verifica que nada foi persistido e seleção volta ao salvo
            viewModel.dismissCollectionPicker()
            advanceUntilIdle()
            assertFalse(viewModel.collectionsUiState.value.isPickerVisible)
            assertEquals(setOf(col1), viewModel.collectionsUiState.value.savedCollectionIds)
            assertEquals(setOf(col1), viewModel.collectionsUiState.value.selectedCollectionIds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `saves article to single and multiple collections and emits success feedback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Ciências")
            val col2 = fakeCollectionRepository.createCollection("Física")
            val wikiArticle = WikipediaArticle(
                pageId = 300L,
                title = "Buraco Negro",
                description = "Região do espaço-tempo",
                content = "Conteúdo completo sobre buracos negros.",
                imageUrl = "https://upload.wikimedia.org/bh.jpg",
                url = "https://pt.wikipedia.org/wiki/Buraco_negro"
            )
            fakeRepository.articleById[300L] = wikiArticle

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 300L))
            )
            val feedbackMessages = mutableListOf<String>()
            val collectorJob = launch { viewModel.feedbackEvents.collect { feedbackMessages.add(it) } }
            advanceUntilIdle()

            // Salva em 1 coleção
            viewModel.openCollectionPicker()
            viewModel.toggleCollectionSelection(col1)
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertFalse(viewModel.collectionsUiState.value.isPickerVisible)
            assertEquals(setOf(col1), viewModel.collectionsUiState.value.savedCollectionIds)
            assertEquals("Artigo salvo na coleção!", feedbackMessages.last())

            // Reabre e salva também na segunda coleção (preservando a primeira)
            viewModel.openCollectionPicker()
            viewModel.toggleCollectionSelection(col2)
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertEquals(setOf(col1, col2), viewModel.collectionsUiState.value.savedCollectionIds)
            assertEquals(1, fakeArticleRepository.getSavedArticles().first().size)
            assertEquals("Artigo salvo em 2 coleções!", feedbackMessages.last())
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `unchecking collection removes only that association preserving article and other collections`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Coleção 1")
            val col2 = fakeCollectionRepository.createCollection("Coleção 2")
            val col3 = fakeCollectionRepository.createCollection("Coleção 3")
            val wikiArticle = WikipediaArticle(
                pageId = 500L,
                title = "Platão",
                content = "Filósofo grego.",
                url = "https://pt.wikipedia.org/wiki/Plat%C3%A3o"
            )
            fakeRepository.articleById[500L] = wikiArticle
            val localId = fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(title = "Platão", url = wikiArticle.url, wikipediaPageId = 500L),
                listOf(col1, col2)
            )

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 500L))
            )
            val feedbackMessages = mutableListOf<String>()
            val collectorJob = launch { viewModel.feedbackEvents.collect { feedbackMessages.add(it) } }
            advanceUntilIdle()

            // Desmarca col1, mantém col2, adiciona col3
            viewModel.openCollectionPicker()
            viewModel.toggleCollectionSelection(col1)
            viewModel.toggleCollectionSelection(col3)
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertEquals(setOf(col2, col3), viewModel.collectionsUiState.value.savedCollectionIds)
            assertNotNull(fakeArticleRepository.getArticleById(localId))
            assertEquals(1, fakeArticleRepository.getSavedArticles().first().size)
            assertEquals("Coleções do artigo atualizadas!", feedbackMessages.last())
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `confirming without changes avoids redundant repository calls`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Filosofia")
            val wikiArticle = WikipediaArticle(
                pageId = 801L,
                title = "Kant",
                content = "Conteúdo",
                url = "https://pt.wikipedia.org/wiki/Kant"
            )
            fakeRepository.articleById[801L] = wikiArticle
            val countingRepo = CountingArticleRepository(fakeArticleRepository)
            countingRepo.saveArticleAndAssociateCollections(
                Article(title = "Kant", url = wikiArticle.url, wikipediaPageId = 801L),
                listOf(col1)
            )
            countingRepo.resetWriteCounters()

            val viewModel = createViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 801L)),
                articleRepo = countingRepo
            )
            advanceUntilIdle()

            viewModel.openCollectionPicker()
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertFalse(viewModel.collectionsUiState.value.isPickerVisible)
            assertEquals(0, countingRepo.saveCalls)
            assertEquals(0, countingRepo.addCalls)
            assertEquals(0, countingRepo.removeCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `createCollection during picker updates list reactively without duplicating article`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val wikiArticle = WikipediaArticle(
                pageId = 901L,
                title = "Galileu",
                content = "Astrônomo italiano.",
                url = "https://pt.wikipedia.org/wiki/Galileu"
            )
            fakeRepository.articleById[901L] = wikiArticle

            val viewModel = createViewModel(
                SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 901L))
            )
            advanceUntilIdle()

            viewModel.openCollectionPicker()
            assertTrue(viewModel.collectionsUiState.value.availableCollections.isEmpty())

            viewModel.createCollection("  Astronomia  ")
            advanceUntilIdle()

            val collections = viewModel.collectionsUiState.value.availableCollections
            assertEquals(1, collections.size)
            assertEquals("Astronomia", collections[0].name)
            // Criar coleção não salva o artigo prematuramente
            assertTrue(fakeArticleRepository.getSavedArticles().first().isEmpty())

            // Usuário seleciona a coleção recém-criada e salva normalmente
            viewModel.toggleCollectionSelection(collections[0].id)
            viewModel.confirmCollectionSelection()
            advanceUntilIdle()

            assertEquals(setOf(collections[0].id), viewModel.collectionsUiState.value.savedCollectionIds)
            assertEquals(1, fakeArticleRepository.getSavedArticles().first().size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `handles persistence failures gracefully and blocks simultaneous save confirmations`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Filosofia")
            val wikiArticle = WikipediaArticle(
                pageId = 950L,
                title = "Nietzsche",
                content = "Conteúdo",
                url = "https://pt.wikipedia.org/wiki/Nietzsche"
            )
            fakeRepository.articleById[950L] = wikiArticle

            val gatedRepo = GatedArticleRepository(fakeArticleRepository)
            val viewModel = createViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 950L)),
                articleRepo = gatedRepo
            )
            val feedbackMessages = mutableListOf<String>()
            val collectorJob = launch { viewModel.feedbackEvents.collect { feedbackMessages.add(it) } }
            advanceUntilIdle()

            viewModel.openCollectionPicker()
            viewModel.toggleCollectionSelection(col1)

            // Dispara primeira confirmação (suspensa no gate)
            viewModel.confirmCollectionSelection()
            runCurrent()
            assertTrue(viewModel.collectionsUiState.value.isSaving)
            assertEquals(1, gatedRepo.saveCallCount)

            // Tentativas simultâneas de confirmar ou fechar devem ser bloqueadas
            viewModel.confirmCollectionSelection()
            viewModel.dismissCollectionPicker()
            runCurrent()
            assertEquals(1, gatedRepo.saveCallCount)
            assertTrue(viewModel.collectionsUiState.value.isPickerVisible)

            // Simula falha na persistência
            gatedRepo.gate.completeExceptionally(IllegalStateException("Erro ao gravar no banco"))
            advanceUntilIdle()

            assertFalse(viewModel.collectionsUiState.value.isSaving)
            assertTrue(viewModel.collectionsUiState.value.isPickerVisible)
            assertEquals("Erro ao gravar no banco", viewModel.collectionsUiState.value.pickerErrorMessage)
            assertEquals("Erro ao gravar no banco", feedbackMessages.last())
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `offline mode serves saved local article without network call and keeps collection associations`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Filosofia")
            fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(
                    title = "Sócrates",
                    description = "Filósofo grego clássico",
                    content = "Conteúdo salvo localmente para leitura offline.",
                    imageUrl = null,
                    url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                    lastUpdated = 123456L,
                    wikipediaPageId = 42L
                ),
                listOf(col1)
            )
            val offlineObserver = FakeConnectivityObserver(initialOnline = false)

            val viewModel = createViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 42L)),
                connectivityObserver = offlineObserver
            )
            advanceUntilIdle()

            assertEquals(0, fakeRepository.getArticleCallCount)
            assertFalse(viewModel.collectionsUiState.value.isOnline)
            val state = viewModel.uiState.value as WikipediaArticleUiState.Success
            assertTrue(state.isLocalFallback)
            assertEquals(123456L, state.localLastUpdated)
            assertEquals("Sócrates", state.article.title)
            assertEquals("Conteúdo salvo localmente para leitura offline.", state.article.content)
            assertEquals(setOf(col1), viewModel.collectionsUiState.value.savedCollectionIds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `offline mode with unsaved article emits connection error without network call or local persistence`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val offlineObserver = FakeConnectivityObserver(initialOnline = false)
            val viewModel = createViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 999L)),
                connectivityObserver = offlineObserver
            )
            advanceUntilIdle()

            assertEquals(0, fakeRepository.getArticleCallCount)
            assertEquals(
                WikipediaArticleUiState.Error(
                    "Sem conexão com a internet. Verifique sua rede e tente novamente."
                ),
                viewModel.uiState.value
            )
            assertTrue(fakeArticleRepository.getSavedArticles().first().isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `online API failure falls back to locally saved copy when available and retry recovers online`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val col1 = fakeCollectionRepository.createCollection("Física")
            fakeArticleRepository.saveArticleAndAssociateCollections(
                Article(
                    title = "Albert Einstein",
                    description = "Cópia local",
                    content = "Texto local salvo.",
                    url = "https://pt.wikipedia.org/wiki/Albert_Einstein",
                    lastUpdated = 9000L,
                    wikipediaPageId = 55L
                ),
                listOf(col1)
            )
            fakeRepository.exceptionToThrow = WikipediaException.Timeout()

            val viewModel = createViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 55L))
            )
            advanceUntilIdle()

            val fallbackState = viewModel.uiState.value as WikipediaArticleUiState.Success
            assertTrue(fallbackState.isLocalFallback)
            assertEquals("Texto local salvo.", fallbackState.article.content)

            // Ao recuperar a API e tentar novamente, volta a exibir versão online
            val onlineArticle = WikipediaArticle(
                pageId = 55L,
                title = "Albert Einstein",
                description = "Físico teórico",
                content = "Texto completo online atualizado.",
                url = "https://pt.wikipedia.org/wiki/Albert_Einstein"
            )
            fakeRepository.exceptionToThrow = null
            fakeRepository.articleById[55L] = onlineArticle

            viewModel.retry()
            advanceUntilIdle()

            val recoveredState = viewModel.uiState.value as WikipediaArticleUiState.Success
            assertFalse(recoveredState.isLocalFallback)
            assertEquals("Texto completo online atualizado.", recoveredState.article.content)
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
        var saveCallCount = 0
        val gate = CompletableDeferred<Long>()

        override suspend fun saveArticleAndAssociateCollections(
            article: Article,
            collectionIds: List<Long>
        ): Long {
            saveCallCount++
            return gate.await()
        }
    }

    private class FakeWikipediaArticleRepository : WikipediaRepository {
        var getArticleCallCount = 0
        var lastRequestedPageId: Long? = null
        val articleById = mutableMapOf<Long, WikipediaArticle>()
        var exceptionToThrow: Throwable? = null
        var pendingDeferred: CompletableDeferred<WikipediaArticle?>? = null

        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> = emptyList()

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? {
            getArticleCallCount++
            lastRequestedPageId = pageId
            pendingDeferred?.let { deferred ->
                pendingDeferred = null
                return deferred.await()
            }
            exceptionToThrow?.let { throw it }
            return articleById[pageId]
        }
    }
}
