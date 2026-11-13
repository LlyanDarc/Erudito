package com.example.erudito.presentation.collection_detail

import com.example.erudito.domain.model.CollectionWithArticles

data class CollectionDetailUiState(
    val isLoading: Boolean = true,
    val collectionWithArticles: CollectionWithArticles? = null,
    val isRenameDialogOpen: Boolean = false,
    val isDeleteDialogOpen: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedCollectionName: String? = null,
    val errorMessage: String? = null
)
