package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.FakeCollectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeleteCollectionUseCaseTest {

    private lateinit var fakeRepository: FakeCollectionRepository
    private lateinit var deleteCollectionUseCase: DeleteCollectionUseCase

    @Before
    fun setUp() = runTest {
        fakeRepository = FakeCollectionRepository()
        deleteCollectionUseCase = DeleteCollectionUseCase(fakeRepository)
        fakeRepository.createCollection("Coleção Para Excluir")
    }

    @Test
    fun `invoke removes collection from repository`() = runTest {
        val initialCollections = fakeRepository.getCollections().first()
        assertEquals(1, initialCollections.size)

        deleteCollectionUseCase(1L)

        val updatedCollections = fakeRepository.getCollections().first()
        assertTrue(updatedCollections.isEmpty())
    }
}
