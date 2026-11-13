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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _feedbackEvents = Channel<String>(Channel.BUFFERED)
    val feedbackEvents: Flow<String> = _feedbackEvents.receiveAsFlow()

    init {
        loadLibraryData()
    }

    private fun loadLibraryData() {
        viewModelScope.launch {
            combine(
                getCollectionsUseCase(),
                getSavedArticlesUseCase()
            ) { collections, articles ->
                collections to articles
            }.catch { exception ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "Erro ao carregar dados da biblioteca"
                    )
                }
            }.collect { (collections, articles) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        collections = collections,
                        recentArticles = articles,
                        totalCollectionsCount = collections.size,
                        totalSavedArticlesCount = articles.size
                    )
                }
            }
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            try {
                val trimmedName = name.trim()
                createCollectionUseCase(name)
                _feedbackEvents.send("Coleção \"$trimmedName\" criada com sucesso!")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Erro ao criar coleção") }
            }
        }
    }

    fun renameCollection(id: Long, newName: String) {
        viewModelScope.launch {
            try {
                val trimmedName = newName.trim()
                renameCollectionUseCase(id, newName)
                _feedbackEvents.send("Coleção renomeada para \"$trimmedName\"!")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Erro ao renomear coleção") }
            }
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            try {
                val collectionName = _uiState.value.collections.find { it.id == id }?.name
                deleteCollectionUseCase(id)
                val message = if (!collectionName.isNullOrBlank()) {
                    "Coleção \"$collectionName\" excluída com sucesso!"
                } else {
                    "Coleção excluída com sucesso!"
                }
                _feedbackEvents.send(message)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Erro ao excluir coleção") }
            }
        }
    }

    fun showFeedbackMessage(message: String) {
        viewModelScope.launch {
            _feedbackEvents.send(message)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
