package com.example.erudito.domain.usecase

import com.example.erudito.domain.repository.ArticleRepository
import javax.inject.Inject

/**
 * Caso de uso responsável por remover a associação de um artigo com uma coleção específica,
 * preservando o artigo localmente e seus vínculos com outras coleções.
 */
class RemoveArticleFromCollectionUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(
        articleId: Long,
        collectionId: Long
    ) {
        if (articleId <= 0L) {
            throw IllegalArgumentException("Identificador de artigo inválido.")
        }
        if (collectionId <= 0L) {
            throw IllegalArgumentException("Identificador de coleção inválido.")
        }

        articleRepository.removeArticleFromCollection(
            articleId = articleId,
            collectionId = collectionId
        )
    }
}
