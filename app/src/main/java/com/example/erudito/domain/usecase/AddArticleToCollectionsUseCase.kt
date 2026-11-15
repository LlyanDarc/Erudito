package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.CollectionRepository
import javax.inject.Inject

/**
 * Caso de uso responsável por associar um artigo já salvo localmente
 * a uma ou mais coleções existentes, preservando vínculos anteriores (RN03, RN04).
 */
class AddArticleToCollectionsUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(
        articleId: Long,
        collectionIds: List<Long>
    ) {
        if (articleId <= 0L) {
            throw IllegalArgumentException("Identificador de artigo inválido.")
        }

        val distinctCollectionIds = collectionIds.distinct()
        if (distinctCollectionIds.isEmpty()) {
            throw IllegalArgumentException("Selecione pelo menos uma coleção.")
        }
        if (distinctCollectionIds.any { it <= 0L }) {
            throw IllegalArgumentException("Identificador de coleção inválido.")
        }

        articleRepository.getArticleById(articleId)
            ?: throw IllegalArgumentException("Artigo não encontrado.")

        val missingIds = distinctCollectionIds.filter { collectionRepository.getCollectionById(it) == null }
        if (missingIds.isNotEmpty()) {
            throw IllegalArgumentException("Coleção(ões) não encontrada(s): ${missingIds.joinToString()}")
        }

        articleRepository.addArticleToCollections(
            articleId = articleId,
            collectionIds = distinctCollectionIds
        )
    }
}
