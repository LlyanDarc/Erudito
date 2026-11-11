package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.repository.ArticleRepository
import javax.inject.Inject

class GetArticleByIdUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(id: Long): Article? {
        return articleRepository.getArticleById(id)
    }
}
