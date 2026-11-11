package com.example.erudito.presentation.article_detail

import com.example.erudito.domain.model.Article

data class ArticleDetailUiState(
    val isLoading: Boolean = true,
    val article: Article? = null,
    val errorMessage: String? = null
)
