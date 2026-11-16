package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.repository.ArticleRepository
import javax.inject.Inject

class GetSavedArticleByWikipediaPageIdUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(wikipediaPageId: Long): Article? {
        if (wikipediaPageId <= 0L) return null
        return articleRepository.getArticleByWikipediaPageId(wikipediaPageId)
    }
}
