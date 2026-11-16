package com.example.erudito.presentation.article_detail

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.Collection

data class ArticleDetailUiState(
    val isLoading: Boolean = true,
    val article: Article? = null,
    val errorMessage: String? = null,
    val availableCollections: List<Collection> = emptyList(),
    val savedCollectionIds: Set<Long> = emptySet(),
    val selectedCollectionIds: Set<Long> = emptySet(),
    val isPickerVisible: Boolean = false,
    val isLoadingCollections: Boolean = true,
    val isSavingCollections: Boolean = false,
    val isCreatingCollection: Boolean = false,
    val pickerErrorMessage: String? = null,
    val isOnline: Boolean = true,
    val isRefreshing: Boolean = false
) {
    val isSavedInAnyCollection: Boolean get() = savedCollectionIds.isNotEmpty()
    val savedCollectionsCount: Int get() = savedCollectionIds.size
    val canRefreshRemotely: Boolean get() = (article?.wikipediaPageId ?: 0L) > 0L
}
