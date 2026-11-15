package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.repository.ArticleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso responsável por observar reativamente os IDs das coleções
 * associadas a um artigo (identificado por ID local ou por artigo da Wikipédia com fallback de URL).
 */
class GetCollectionIdsForArticleUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    operator fun invoke(articleId: Long): Flow<List<Long>> {
        if (articleId <= 0L) {
            throw IllegalArgumentException("Identificador de artigo inválido.")
        }
        return articleRepository.getCollectionIdsForArticle(articleId)
    }

    operator fun invoke(wikipediaPageId: Long, url: String): Flow<List<Long>> {
        if (wikipediaPageId <= 0L) {
            throw IllegalArgumentException("Identificador de artigo da Wikipédia inválido.")
        }
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty()) {
            throw IllegalArgumentException("A URL do artigo não pode ser vazia.")
        }
        return articleRepository.getCollectionIdsForWikipediaArticle(
            wikipediaPageId = wikipediaPageId,
            url = normalizedUrl
        )
    }

    operator fun invoke(wikipediaArticle: WikipediaArticle): Flow<List<Long>> {
        return invoke(
            wikipediaPageId = wikipediaArticle.pageId,
            url = wikipediaArticle.url
        )
    }
}
