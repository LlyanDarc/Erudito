package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.CollectionRepository
import javax.inject.Inject

/**
 * Caso de uso responsável por excluir uma coleção.
 * Regra de Negócio RN05: Ao excluir uma coleção, suas associações com artigos
 * também devem ser removidas, sem excluir artigos que ainda estejam em outras coleções.
 */
class DeleteCollectionUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(collectionId: Long) {
        collectionRepository.deleteCollection(collectionId)
    }
}
