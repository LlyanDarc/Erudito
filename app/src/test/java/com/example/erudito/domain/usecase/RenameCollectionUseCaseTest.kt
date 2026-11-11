package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.FakeCollectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RenameCollectionUseCaseTest {

    private lateinit var fakeRepository: FakeCollectionRepository
    private lateinit var renameCollectionUseCase: RenameCollectionUseCase

    @Before
    fun setUp() = runTest {
        fakeRepository = FakeCollectionRepository()
        renameCollectionUseCase = RenameCollectionUseCase(fakeRepository)
        fakeRepository.createCollection("Nome Antigo")
    }

    @Test
    fun `invoke with valid name renames collection successfully`() = runTest {
        renameCollectionUseCase(1L, "Novo Nome Atualizado")

        val collections = fakeRepository.getCollections().first()
        assertEquals(1, collections.size)
        assertEquals("Novo Nome Atualizado", collections[0].name)
    }

    @Test
    fun `invoke trims whitespace when renaming`() = runTest {
        renameCollectionUseCase(1L, "   Nome com Espaços   ")

        val collections = fakeRepository.getCollections().first()
        assertEquals("Nome com Espaços", collections[0].name)
    }

    @Test
    fun `invoke with empty name throws IllegalArgumentException per RN01`() = runTest {
        try {
            renameCollectionUseCase(1L, "")
            fail("Deveria ter lançado IllegalArgumentException para nome vazio")
        } catch (e: IllegalArgumentException) {
            assertEquals("O nome da coleção não pode ser vazio ou composto apenas por espaços.", e.message)
        }
    }

    @Test
    fun `invoke with whitespace only throws IllegalArgumentException per RN01`() = runTest {
        try {
            renameCollectionUseCase(1L, "     ")
            fail("Deveria ter lançado IllegalArgumentException para nome contendo apenas espaços")
        } catch (e: IllegalArgumentException) {
            assertEquals("O nome da coleção não pode ser vazio ou composto apenas por espaços.", e.message)
        }
    }
}
