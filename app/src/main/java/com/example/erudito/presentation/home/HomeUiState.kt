package com.example.erudito.presentation.home

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.Collection

data class HomeUiState(
    val isLoading: Boolean = true,
    val collections: List<Collection> = emptyList(),
    val recentArticles: List<Article> = emptyList(),
    val totalCollectionsCount: Int = 0,
    val totalSavedArticlesCount: Int = 0,
    val isOnline: Boolean = true,
    val error: String? = null
)
