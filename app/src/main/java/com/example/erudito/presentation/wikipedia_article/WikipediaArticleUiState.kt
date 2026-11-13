package com.example.erudito.presentation.wikipedia_article

import com.example.erudito.domain.model.WikipediaArticle

sealed interface WikipediaArticleUiState {
    data object Initial : WikipediaArticleUiState
    data object Loading : WikipediaArticleUiState
    data class Success(val article: WikipediaArticle) : WikipediaArticleUiState
    data class Error(val message: String) : WikipediaArticleUiState
}
