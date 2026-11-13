package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.repository.WikipediaRepository
import javax.inject.Inject

class GetWikipediaArticleUseCase @Inject constructor(
    private val wikipediaRepository: WikipediaRepository
) {
    suspend operator fun invoke(pageId: Long): WikipediaArticle? {
        if (pageId <= 0L) {
            throw IllegalArgumentException("Identificador de artigo inválido.")
        }
        return wikipediaRepository.getArticleByPageId(pageId)
    }
}
