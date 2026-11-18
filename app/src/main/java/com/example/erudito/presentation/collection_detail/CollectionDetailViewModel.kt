package com.example.erudito.presentation.collection_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionWithArticlesUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
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
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCollectionWithArticlesUseCase: GetCollectionWithArticlesUseCase,
    private val renameCollectionUseCase: RenameCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val removeArticleFromCollectionUseCase: RemoveArticleFromCollectionUseCase
) : ViewModel() {

    val collectionId: Long = savedStateHandle.get<Long>("collectionId")
        ?: savedStateHandle.get<String>("collectionId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<String>(Channel.BUFFERED)
    val feedbackEvents: Flow<String> = _feedbackEvents.receiveAsFlow()

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
                    _uiState.update { current ->
                        if (current.isDeleting || current.isDeleted) {
                            current.copy(isLoading = false)
                        } else {
                            current.copy(
                                isLoading = false,
                                collectionWithArticles = result,
                                errorMessage = if (result == null) "Coleção não encontrada" else null
                            )
                        }
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
                val trimmedName = newName.trim()
                renameCollectionUseCase(collectionId, newName)
                _uiState.update { it.copy(isRenameDialogOpen = false) }
                _feedbackEvents.send("Coleção renomeada para \"$trimmedName\"!")
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isRenameDialogOpen = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRenameDialogOpen = false,
                        errorMessage = e.message ?: "Erro ao renomear coleção"
                    )
                }
            }
        }
    }

    fun deleteCollection() {
        viewModelScope.launch {
            val collectionName = _uiState.value.collectionWithArticles?.collection?.name
            _uiState.update { it.copy(isDeleting = true, isDeleteDialogOpen = false) }
            try {
                deleteCollectionUseCase(collectionId)
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        isDeleted = true,
                        deletedCollectionName = collectionName,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isDeleting = false, isDeleteDialogOpen = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = e.message ?: "Erro ao excluir coleção"
                    )
                }
            }
        }
    }

    fun openRemoveArticleDialog(article: Article) {
        if (_uiState.value.isRemovingArticle) return
        _uiState.update { it.copy(articleToRemove = article) }
    }

    fun closeRemoveArticleDialog() {
        if (_uiState.value.isRemovingArticle) return
        _uiState.update { it.copy(articleToRemove = null) }
    }

    fun confirmRemoveArticle() {
        val article = _uiState.value.articleToRemove ?: return
        if (_uiState.value.isRemovingArticle) return

        _uiState.update {
            it.copy(
                isRemovingArticle = true,
                articleToRemove = null
            )
        }

        viewModelScope.launch {
            try {
                removeArticleFromCollectionUseCase(
                    articleId = article.id,
                    collectionId = collectionId
                )
                _uiState.update { it.copy(isRemovingArticle = false) }
                _feedbackEvents.send("Artigo \"${article.title}\" removido da coleção.")
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isRemovingArticle = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRemovingArticle = false,
                        errorMessage = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "Erro ao remover artigo da coleção"
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
