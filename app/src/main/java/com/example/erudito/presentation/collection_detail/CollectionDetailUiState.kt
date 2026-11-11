package com.example.erudito.presentation.collection_detail

import com.example.erudito.domain.model.CollectionWithArticles

data class CollectionDetailUiState(
    val isLoading: Boolean = true,
    val collectionWithArticles: CollectionWithArticles? = null,
    val isRenameDialogOpen: Boolean = false,
    val isDeleteDialogOpen: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
    val feedbackMessage: String? = null
)
