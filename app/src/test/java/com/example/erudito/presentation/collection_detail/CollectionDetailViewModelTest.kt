package com.example.erudito.presentation.collection_detail

import androidx.lifecycle.SavedStateHandle
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles
import com.example.erudito.domain.repository.CollectionRepository
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionWithArticlesUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 1L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(repository),
            renameCollectionUseCase = RenameCollectionUseCase(repository),
            deleteCollectionUseCase = DeleteCollectionUseCase(repository)
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

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 1L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(repository),
            renameCollectionUseCase = RenameCollectionUseCase(repository),
            deleteCollectionUseCase = DeleteCollectionUseCase(repository)
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

        val viewModel = CollectionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("collectionId" to 1L)),
            getCollectionWithArticlesUseCase = GetCollectionWithArticlesUseCase(repository),
            renameCollectionUseCase = RenameCollectionUseCase(repository),
            deleteCollectionUseCase = DeleteCollectionUseCase(repository)
        )

        viewModel.deleteCollection()

        val state = viewModel.uiState.value
        assertFalse(state.isDeleted)
        assertFalse(state.isDeleting)
        assertEquals("Falha de I/O ao excluir", state.errorMessage)
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
}
