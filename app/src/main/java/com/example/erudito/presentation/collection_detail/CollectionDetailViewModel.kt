package com.example.erudito.presentation.collection_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionWithArticlesUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCollectionWithArticlesUseCase: GetCollectionWithArticlesUseCase,
    private val renameCollectionUseCase: RenameCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase
) : ViewModel() {

    val collectionId: Long = savedStateHandle.get<Long>("collectionId")
        ?: savedStateHandle.get<String>("collectionId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    init {
        loadCollection()
    }

    private fun loadCollection() {
        viewModelScope.launch {
            getCollectionWithArticlesUseCase(collectionId)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Erro ao carregar coleção"
                        )
                    }
                }
                .collect { result ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            collectionWithArticles = result,
                            errorMessage = if (result == null && !it.isDeleted) "Coleção não encontrada" else null
                        )
                    }
                }
        }
    }

    fun openRenameDialog() {
        _uiState.update { it.copy(isRenameDialogOpen = true) }
    }

    fun closeRenameDialog() {
        _uiState.update { it.copy(isRenameDialogOpen = false) }
    }

    fun openDeleteDialog() {
        _uiState.update { it.copy(isDeleteDialogOpen = true) }
    }

    fun closeDeleteDialog() {
        _uiState.update { it.copy(isDeleteDialogOpen = false) }
    }

    fun renameCollection(newName: String) {
        viewModelScope.launch {
            try {
                renameCollectionUseCase(collectionId, newName)
                _uiState.update {
                    it.copy(
                        isRenameDialogOpen = false,
                        feedbackMessage = "Coleção renomeada com sucesso!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Erro ao renomear coleção")
                }
            }
        }
    }

    fun deleteCollection() {
        viewModelScope.launch {
            try {
                deleteCollectionUseCase(collectionId)
                _uiState.update {
                    it.copy(
                        isDeleteDialogOpen = false,
                        isDeleted = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Erro ao excluir coleção")
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }
}
