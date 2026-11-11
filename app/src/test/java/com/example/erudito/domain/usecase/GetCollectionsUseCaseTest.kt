package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.FakeCollectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetCollectionsUseCaseTest {

    private lateinit var fakeRepository: FakeCollectionRepository
    private lateinit var getCollectionsUseCase: GetCollectionsUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeCollectionRepository()
        getCollectionsUseCase = GetCollectionsUseCase(fakeRepository)
    }

    @Test
    fun `invoke with empty repository emits empty list`() = runTest {
        val collections = getCollectionsUseCase().first()
        assertTrue(collections.isEmpty())
    }

    @Test
    fun `invoke emits all stored collections`() = runTest {
        fakeRepository.createCollection("Filosofia Antiga")
        fakeRepository.createCollection("Física Quântica")

        val collections = getCollectionsUseCase().first()
        assertEquals(2, collections.size)
        assertEquals("Filosofia Antiga", collections[0].name)
        assertEquals("Física Quântica", collections[1].name)
    }
}
