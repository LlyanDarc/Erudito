package com.example.erudito.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.usecase.SearchWikipediaArticlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchWikipediaArticlesUseCase: SearchWikipediaArticlesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.update { current ->
            current.copy(
                query = newQuery,
                errorMessage = null
            )
        }
    }

    fun search() {
        val trimmedQuery = _uiState.value.query.trim()
        if (trimmedQuery.isEmpty()) {
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            try {
                val results = searchWikipediaArticlesUseCase(trimmedQuery)
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        hasSearched = true,
                        lastSearchedQuery = trimmedQuery,
                        results = results,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WikipediaException) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        hasSearched = true,
                        lastSearchedQuery = trimmedQuery,
                        results = emptyList(),
                        errorMessage = e.message
                    )
                }
            } catch (e: IllegalArgumentException) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Digite um termo para pesquisar na Wikipédia."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        hasSearched = true,
                        lastSearchedQuery = trimmedQuery,
                        results = emptyList(),
                        errorMessage = e.message?.takeIf { it.isNotBlank() }
                            ?: "Erro ao pesquisar na Wikipédia. Tente novamente."
                    )
                }
            }
        }
    }

    fun retry() {
        search()
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _uiState.value = SearchUiState()
    }
}
