package com.example.erudito.domain.usecase

import com.example.erudito.data.mapper.toDomainArticle
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.CollectionRepository
import javax.inject.Inject

/**
 * Caso de uso responsável por persistir um artigo vindo da Wikipédia e associá-lo
 * a uma ou mais coleções existentes, reutilizando registros já salvos (RN02, RN03, RN04).
 */
class SaveWikipediaArticleToCollectionsUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(
        wikipediaArticle: WikipediaArticle,
        collectionIds: List<Long>
    ): Long {
        if (wikipediaArticle.pageId <= 0L) {
            throw IllegalArgumentException("Identificador de artigo da Wikipédia inválido.")
        }
        if (wikipediaArticle.title.isBlank()) {
            throw IllegalArgumentException("O título do artigo não pode ser vazio.")
        }
        if (wikipediaArticle.url.isBlank()) {
            throw IllegalArgumentException("A URL do artigo não pode ser vazia.")
        }

        val distinctCollectionIds = collectionIds.distinct()
        if (distinctCollectionIds.isEmpty()) {
            throw IllegalArgumentException("Selecione pelo menos uma coleção para salvar o artigo.")
        }
        if (distinctCollectionIds.any { it <= 0L }) {
            throw IllegalArgumentException("Identificador de coleção inválido.")
        }

        val missingIds = distinctCollectionIds.filter { collectionRepository.getCollectionById(it) == null }
        if (missingIds.isNotEmpty()) {
            throw IllegalArgumentException("Coleção(ões) não encontrada(s): ${missingIds.joinToString()}")
        }

        val article = wikipediaArticle.toDomainArticle()
        return articleRepository.saveArticleAndAssociateCollections(
            article = article,
            collectionIds = distinctCollectionIds
        )
    }
}
