package com.example.erudito.presentation.search

import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.usecase.SearchWikipediaArticlesUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var fakeRepository: FakeWikipediaRepository
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        fakeRepository = FakeWikipediaRepository()
        viewModel = SearchViewModel(
            searchWikipediaArticlesUseCase = SearchWikipediaArticlesUseCase(fakeRepository)
        )
    }

    @Test
    fun `initial state is SearchContentState Initial with empty query and no error`() {
        val state = viewModel.uiState.value

        assertEquals("", state.query)
        assertFalse(state.isLoading)
        assertFalse(state.hasSearched)
        assertTrue(state.results.isEmpty())
        assertNull(state.errorMessage)
        assertEquals(SearchContentState.Initial, state.contentState)
    }

    @Test
    fun `search with valid query updates state to Success with results`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val expectedResults = listOf(
                WikipediaSearchResult(
                    pageId = 101L,
                    title = "Alan Turing",
                    description = "Matemático e cientista da computação britânico",
                    snippet = "Alan Mathison Turing foi um matemático...",
                    thumbnailUrl = "https://upload.wikimedia.org/turing.jpg",
                    url = "https://pt.wikipedia.org/wiki/Alan_Turing"
                ),
                WikipediaSearchResult(
                    pageId = 202L,
                    title = "Máquina de Turing",
                    description = null,
                    snippet = "Modelo abstrato de computação...",
                    thumbnailUrl = null,
                    url = "https://pt.wikipedia.org/wiki/M%C3%A1quina_de_Turing"
                )
            )
            fakeRepository.nextResults = expectedResults

            viewModel.onQueryChange("  Alan Turing  ")
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, fakeRepository.searchCallCount)
            assertEquals("Alan Turing", fakeRepository.lastQuery)
            assertFalse(state.isLoading)
            assertTrue(state.hasSearched)
            assertEquals("Alan Turing", state.lastSearchedQuery)
            assertEquals(expectedResults, state.results)
            assertNull(state.errorMessage)
            assertEquals(SearchContentState.Success(expectedResults), state.contentState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `search with no matching results updates state to Empty`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            fakeRepository.nextResults = emptyList()

            viewModel.onQueryChange("xpto999inexistente")
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, fakeRepository.searchCallCount)
            assertFalse(state.isLoading)
            assertTrue(state.hasSearched)
            assertTrue(state.results.isEmpty())
            assertNull(state.errorMessage)
            assertEquals(SearchContentState.Empty("xpto999inexistente"), state.contentState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `search with empty query avoids unnecessary search and preserves Initial state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            viewModel.onQueryChange("")
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(0, fakeRepository.searchCallCount)
            assertFalse(state.isLoading)
            assertFalse(state.hasSearched)
            assertNull(state.errorMessage)
            assertEquals(SearchContentState.Initial, state.contentState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `search with whitespace only query avoids unnecessary search and preserves Initial state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            viewModel.onQueryChange("   \t\n  ")
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(0, fakeRepository.searchCallCount)
            assertFalse(state.isLoading)
            assertFalse(state.hasSearched)
            assertNull(state.errorMessage)
            assertEquals(SearchContentState.Initial, state.contentState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `search sets Loading state while request is in progress then transitions to Success`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val gate = CompletableDeferred<List<WikipediaSearchResult>>()
            fakeRepository.pendingDeferred = gate

            viewModel.onQueryChange("Sócrates")
            viewModel.search()
            runCurrent()

            val loadingState = viewModel.uiState.value
            assertTrue(loadingState.isLoading)
            assertNull(loadingState.errorMessage)
            assertEquals(SearchContentState.Loading, loadingState.contentState)

            val expected = listOf(
                WikipediaSearchResult(
                    pageId = 42L,
                    title = "Sócrates",
                    url = "https://pt.wikipedia.org/wiki/S%C3%B3crates"
                )
            )
            gate.complete(expected)
            advanceUntilIdle()

            val finalState = viewModel.uiState.value
            assertFalse(finalState.isLoading)
            assertTrue(finalState.hasSearched)
            assertEquals(SearchContentState.Success(expected), finalState.contentState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `search handles WikipediaException and updates state to Error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            fakeRepository.exceptionToThrow = WikipediaException.NoConnection()

            viewModel.onQueryChange("Aristóteles")
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.results.isEmpty())
            assertEquals(
                "Sem conexão com a internet. Verifique sua rede e tente novamente.",
                state.errorMessage
            )
            assertEquals(
                SearchContentState.Error("Sem conexão com a internet. Verifique sua rede e tente novamente."),
                state.contentState
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `retry after error executes search again and recovers when repository succeeds`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            fakeRepository.exceptionToThrow = WikipediaException.Timeout()
            viewModel.onQueryChange("Renascimento")
            viewModel.search()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.contentState is SearchContentState.Error)

            val expected = listOf(
                WikipediaSearchResult(
                    pageId = 300L,
                    title = "Renascimento",
                    description = "Movimento cultural",
                    url = "https://pt.wikipedia.org/wiki/Renascimento"
                )
            )
            fakeRepository.exceptionToThrow = null
            fakeRepository.nextResults = expected

            viewModel.retry()
            advanceUntilIdle()

            val recoveredState = viewModel.uiState.value
            assertEquals(2, fakeRepository.searchCallCount)
            assertFalse(recoveredState.isLoading)
            assertNull(recoveredState.errorMessage)
            assertEquals(SearchContentState.Success(expected), recoveredState.contentState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `onQueryChange clears previous error and clearSearch resets entire state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            fakeRepository.exceptionToThrow = WikipediaException.ApiError(
                code = "badquery",
                message = "Consulta inválida na API."
            )
            viewModel.onQueryChange("teste")
            viewModel.search()
            advanceUntilIdle()

            assertEquals("Consulta inválida na API.", viewModel.uiState.value.errorMessage)

            viewModel.onQueryChange("novo termo")
            assertEquals("novo termo", viewModel.uiState.value.query)
            assertNull(viewModel.uiState.value.errorMessage)

            viewModel.clearSearch()
            assertEquals(SearchUiState(), viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeWikipediaRepository : WikipediaRepository {
        var searchCallCount = 0
        var lastQuery: String? = null
        var nextResults: List<WikipediaSearchResult> = emptyList()
        var exceptionToThrow: Throwable? = null
        var pendingDeferred: CompletableDeferred<List<WikipediaSearchResult>>? = null

        override suspend fun searchArticles(query: String): List<WikipediaSearchResult> {
            searchCallCount++
            lastQuery = query
            pendingDeferred?.let { deferred ->
                pendingDeferred = null
                return deferred.await()
            }
            exceptionToThrow?.let { throw it }
            return nextResults
        }

        override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? = null
    }
}
