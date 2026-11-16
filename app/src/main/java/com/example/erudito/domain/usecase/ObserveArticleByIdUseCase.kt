package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.repository.ArticleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveArticleByIdUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    operator fun invoke(id: Long): Flow<Article?> {
        return articleRepository.observeArticleById(id)
    }
}
