package com.example.erudito.presentation.search

import com.example.erudito.domain.model.WikipediaSearchResult

sealed interface SearchContentState {
    data object Initial : SearchContentState
    data object Loading : SearchContentState
    data class Success(val results: List<WikipediaSearchResult>) : SearchContentState
    data class Empty(val query: String) : SearchContentState
    data class Error(val message: String) : SearchContentState
}

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val lastSearchedQuery: String = "",
    val results: List<WikipediaSearchResult> = emptyList(),
    val errorMessage: String? = null,
    val isOnline: Boolean = true
) {
    val contentState: SearchContentState
        get() = when {
            isLoading -> SearchContentState.Loading
            errorMessage != null -> SearchContentState.Error(errorMessage)
            !hasSearched -> SearchContentState.Initial
            results.isEmpty() -> SearchContentState.Empty(lastSearchedQuery)
            else -> SearchContentState.Success(results)
        }
}
