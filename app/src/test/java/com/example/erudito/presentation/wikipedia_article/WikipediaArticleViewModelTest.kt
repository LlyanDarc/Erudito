package com.example.erudito.presentation.wikipedia_article

import androidx.lifecycle.SavedStateHandle
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WikipediaArticleViewModelTest {

    private lateinit var fakeRepository: FakeWikipediaArticleRepository

    @Before
    fun setUp() {
        fakeRepository = FakeWikipediaArticleRepository()
    }

    @Test
    fun `initial state is Initial when SavedStateHandle has no pageId`() {
        val viewModel = WikipediaArticleViewModel(
            savedStateHandle = SavedStateHandle(),
            getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepository)
        )

        assertEquals(WikipediaArticleUiState.Initial, viewModel.uiState.value)
        assertEquals(0, fakeRepository.getArticleCallCount)
    }

    @Test
    fun `invalid non positive pageId avoids repository call and emits Error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = WikipediaArticleViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 0L)),
                getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepository)
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

            val viewModel = WikipediaArticleViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 42L)),
                getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepository)
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

            val viewModel = WikipediaArticleViewModel(
                savedStateHandle = SavedStateHandle(),
                getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepository)
            )
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

            val viewModel = WikipediaArticleViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 10L)),
                getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepository)
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

            val viewModel = WikipediaArticleViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 55L)),
                getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepository)
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

            val viewModel = WikipediaArticleViewModel(
                savedStateHandle = SavedStateHandle(mapOf(WikipediaArticleViewModel.ARG_PAGE_ID to 77L)),
                getWikipediaArticleUseCase = GetWikipediaArticleUseCase(fakeRepository)
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
