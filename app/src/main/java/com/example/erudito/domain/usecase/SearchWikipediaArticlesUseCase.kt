package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.WikipediaRepository
import javax.inject.Inject

class SearchWikipediaArticlesUseCase @Inject constructor(
    private val wikipediaRepository: WikipediaRepository
) {
    suspend operator fun invoke(query: String): List<WikipediaSearchResult> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            throw IllegalArgumentException("Digite um termo para pesquisar na Wikipédia.")
        }
        return wikipediaRepository.searchArticles(trimmedQuery)
    }
}
