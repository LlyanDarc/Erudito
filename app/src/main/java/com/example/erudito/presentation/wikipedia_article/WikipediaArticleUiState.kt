package com.example.erudito.presentation.wikipedia_article

import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.WikipediaArticle

sealed interface WikipediaArticleUiState {
    data object Initial : WikipediaArticleUiState
    data object Loading : WikipediaArticleUiState
    data class Success(val article: WikipediaArticle) : WikipediaArticleUiState
    data class Error(val message: String) : WikipediaArticleUiState
}

data class WikipediaArticleCollectionsUiState(
    val availableCollections: List<Collection> = emptyList(),
    val savedCollectionIds: Set<Long> = emptySet(),
    val selectedCollectionIds: Set<Long> = emptySet(),
    val isPickerVisible: Boolean = false,
    val isLoadingCollections: Boolean = true,
    val isSaving: Boolean = false,
    val isCreatingCollection: Boolean = false,
    val pickerErrorMessage: String? = null
) {
    val isSaved: Boolean get() = savedCollectionIds.isNotEmpty()
    val savedCollectionsCount: Int get() = savedCollectionIds.size
}
