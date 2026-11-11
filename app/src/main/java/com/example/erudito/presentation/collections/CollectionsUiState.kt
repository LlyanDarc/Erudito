package com.example.erudito.presentation.collections

import com.example.erudito.domain.model.Collection

data class CollectionsUiState(
    val isLoading: Boolean = true,
    val collections: List<Collection> = emptyList(),
    val isCreateDialogOpen: Boolean = false,
    val collectionToRename: Collection? = null,
    val collectionToDelete: Collection? = null,
    val errorMessage: String? = null,
    val userFeedbackMessage: String? = null
)
