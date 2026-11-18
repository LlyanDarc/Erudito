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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _feedbackEvents = Channel<String>(Channel.BUFFERED)
    val feedbackEvents: Flow<String> = _feedbackEvents.receiveAsFlow()

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
                            collections = list
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
                val trimmedName = name.trim()
                createCollectionUseCase(name)
                _uiState.update { it.copy(isCreateDialogOpen = false) }
                _feedbackEvents.send("Coleção \"$trimmedName\" criada com sucesso!")
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isCreateDialogOpen = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreateDialogOpen = false,
                        errorMessage = e.message ?: "Erro ao criar coleção"
                    )
                }
            }
        }
    }

    fun renameCollection(id: Long, newName: String) {
        viewModelScope.launch {
            try {
                val trimmedName = newName.trim()
                renameCollectionUseCase(id, newName)
                _uiState.update { it.copy(collectionToRename = null) }
                _feedbackEvents.send("Coleção renomeada para \"$trimmedName\"!")
            } catch (e: CancellationException) {
                _uiState.update { it.copy(collectionToRename = null) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        collectionToRename = null,
                        errorMessage = e.message ?: "Erro ao renomear coleção"
                    )
                }
            }
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            try {
                val collectionName = _uiState.value.collections.find { it.id == id }?.name
                deleteCollectionUseCase(id)
                _uiState.update { it.copy(collectionToDelete = null) }
                val message = if (!collectionName.isNullOrBlank()) {
                    "Coleção \"$collectionName\" excluída com sucesso!"
                } else {
                    "Coleção excluída com sucesso!"
                }
                _feedbackEvents.send(message)
            } catch (e: CancellationException) {
                _uiState.update { it.copy(collectionToDelete = null) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        collectionToDelete = null,
                        errorMessage = e.message ?: "Erro ao excluir coleção"
                    )
                }
            }
        }
    }

    fun showFeedbackMessage(message: String) {
        viewModelScope.launch {
            _feedbackEvents.send(message)
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
