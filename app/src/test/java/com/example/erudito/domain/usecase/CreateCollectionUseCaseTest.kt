package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.FakeCollectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CreateCollectionUseCaseTest {

    private lateinit var fakeRepository: FakeCollectionRepository
    private lateinit var createCollectionUseCase: CreateCollectionUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeCollectionRepository()
        createCollectionUseCase = CreateCollectionUseCase(fakeRepository)
    }

    @Test
    fun `invoke with valid name creates collection successfully`() = runTest {
        val collectionId = createCollectionUseCase("Ciência e Astronomia")

        assertEquals(1L, collectionId)
        val collections = fakeRepository.getCollections().first()
        assertEquals(1, collections.size)
        assertEquals("Ciência e Astronomia", collections[0].name)
    }

    @Test
    fun `invoke trims whitespace from collection name`() = runTest {
        val collectionId = createCollectionUseCase("   História do Brasil   ")

        assertEquals(1L, collectionId)
        val collections = fakeRepository.getCollections().first()
        assertEquals("História do Brasil", collections[0].name)
    }

    @Test
    fun `invoke with empty name throws IllegalArgumentException per RN01`() = runTest {
        try {
            createCollectionUseCase("")
            fail("Deveria ter lançado IllegalArgumentException para nome vazio")
        } catch (e: IllegalArgumentException) {
            assertEquals("O nome da coleção não pode ser vazio ou composto apenas por espaços.", e.message)
        }
    }

    @Test
    fun `invoke with whitespace only throws IllegalArgumentException per RN01`() = runTest {
        try {
            createCollectionUseCase("     ")
            fail("Deveria ter lançado IllegalArgumentException para nome contendo apenas espaços")
        } catch (e: IllegalArgumentException) {
            assertEquals("O nome da coleção não pode ser vazio ou composto apenas por espaços.", e.message)
        }
    }
}
