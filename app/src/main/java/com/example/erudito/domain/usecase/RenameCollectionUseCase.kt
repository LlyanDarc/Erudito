package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.CollectionRepository
import javax.inject.Inject

/**
 * Caso de uso responsável por renomear uma coleção existente.
 * Regra de Negócio RN01: O novo nome não pode ser vazio ou composto apenas por espaços.
 */
class RenameCollectionUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(collectionId: Long, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) {
            throw IllegalArgumentException("O nome da coleção não pode ser vazio ou composto apenas por espaços.")
        }
        collectionRepository.renameCollection(collectionId, trimmedName)
    }
}
