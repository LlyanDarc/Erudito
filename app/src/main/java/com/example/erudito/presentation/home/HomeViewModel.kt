package com.example.erudito.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val getSavedArticlesUseCase: GetSavedArticlesUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val renameCollectionUseCase: RenameCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadLibraryData()
    }

    private fun loadLibraryData() {
        viewModelScope.launch {
            combine(
                getCollectionsUseCase(),
                getSavedArticlesUseCase()
            ) { collections, articles ->
                HomeUiState(
                    isLoading = false,
                    collections = collections,
                    recentArticles = articles,
                    totalCollectionsCount = collections.size,
                    totalSavedArticlesCount = articles.size,
                    error = null
                )
            }.catch { exception ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "Erro ao carregar dados da biblioteca"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            try {
                createCollectionUseCase(name)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun renameCollection(id: Long, newName: String) {
        viewModelScope.launch {
            try {
                renameCollectionUseCase(id, newName)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            try {
                deleteCollectionUseCase(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
