package com.example.erudito.presentation.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
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
class CollectionsViewModel @Inject constructor(
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val renameCollectionUseCase: RenameCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    init {
        loadCollections()
    }

    private fun loadCollections() {
        viewModelScope.launch {
            getCollectionsUseCase()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Erro ao carregar coleções"
                        )
                    }
                }
                .collect { list ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            collections = list,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(isCreateDialogOpen = true) }
    }

    fun closeCreateDialog() {
        _uiState.update { it.copy(isCreateDialogOpen = false) }
    }

    fun openRenameDialog(collection: Collection) {
        _uiState.update { it.copy(collectionToRename = collection) }
    }

    fun closeRenameDialog() {
        _uiState.update { it.copy(collectionToRename = null) }
    }

    fun openDeleteDialog(collection: Collection) {
        _uiState.update { it.copy(collectionToDelete = collection) }
    }

    fun closeDeleteDialog() {
        _uiState.update { it.copy(collectionToDelete = null) }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            try {
                createCollectionUseCase(name)
                _uiState.update {
                    it.copy(
                        isCreateDialogOpen = false,
                        userFeedbackMessage = "Coleção \"$name\" criada com sucesso!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Erro ao criar coleção")
                }
            }
        }
    }

    fun renameCollection(id: Long, newName: String) {
        viewModelScope.launch {
            try {
                renameCollectionUseCase(id, newName)
                _uiState.update {
                    it.copy(
                        collectionToRename = null,
                        userFeedbackMessage = "Coleção renomeada para \"$newName\"!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Erro ao renomear coleção")
                }
            }
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            try {
                deleteCollectionUseCase(id)
                _uiState.update {
                    it.copy(
                        collectionToDelete = null,
                        userFeedbackMessage = "Coleção excluída com sucesso."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Erro ao excluir coleção")
                }
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(userFeedbackMessage = null, errorMessage = null) }
    }
}
