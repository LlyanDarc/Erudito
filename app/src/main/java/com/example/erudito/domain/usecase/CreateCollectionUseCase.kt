package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.CollectionRepository
import javax.inject.Inject

/**
 * Caso de uso responsável por criar uma nova coleção.
 * Regra de Negócio RN01: Uma coleção não pode ser criada com um nome vazio
 * ou composto apenas por espaços.
 */
class CreateCollectionUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(name: String): Long {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw IllegalArgumentException("O nome da coleção não pode ser vazio ou composto apenas por espaços.")
        }
        return collectionRepository.createCollection(trimmedName)
    }
}
