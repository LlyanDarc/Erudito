package com.example.erudito.presentation.article_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.connectivity.ConnectivityObserver
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.sync.RefreshArticleOutcome
import com.example.erudito.domain.sync.SkipReason
import com.example.erudito.domain.usecase.AddArticleToCollectionsUseCase
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionIdsForArticleUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.ObserveArticleByIdUseCase
import com.example.erudito.domain.usecase.RefreshSavedArticleUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
class ArticleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeArticleByIdUseCase: ObserveArticleByIdUseCase,
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val addArticleToCollectionsUseCase: AddArticleToCollectionsUseCase,
    private val removeArticleFromCollectionUseCase: RemoveArticleFromCollectionUseCase,
    private val getCollectionIdsForArticleUseCase: GetCollectionIdsForArticleUseCase,
    private val refreshSavedArticleUseCase: RefreshSavedArticleUseCase,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val articleId: Long = savedStateHandle.get<Long>("articleId")
        ?: savedStateHandle.get<String>("articleId")?.toLongOrNull()
        ?: 0L

    private var collectionIdsJob: Job? = null
    private var refreshJob: Job? = null
    private var observedArticleId: Long? = null

    private val _uiState = MutableStateFlow(
        ArticleDetailUiState(isOnline = connectivityObserver.isCurrentlyOnline())
    )
    val uiState: StateFlow<ArticleDetailUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<String>(Channel.BUFFERED)
    val feedbackEvents: Flow<String> = _feedbackEvents.receiveAsFlow()

    init {
        observeConnectivity()
        observeAvailableCollections()
        observeArticle()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    private fun observeAvailableCollections() {
        viewModelScope.launch {
            getCollectionsUseCase()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingCollections = false,
                            pickerErrorMessage = e.message ?: "Erro ao carregar coleções."
                        )
                    }
                }
                .collect { collections ->
                    _uiState.update {
                        it.copy(
                            availableCollections = collections,
                            isLoadingCollections = false
                        )
                    }
                }
        }
    }

    private fun observeCollectionAssociations(targetArticleId: Long) {
        if (targetArticleId <= 0L) return
        if (collectionIdsJob?.isActive == true && observedArticleId == targetArticleId) {
            return
        }
        observedArticleId = targetArticleId
        collectionIdsJob?.cancel()
        collectionIdsJob = viewModelScope.launch {
            getCollectionIdsForArticleUseCase(targetArticleId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            pickerErrorMessage = e.message ?: "Erro ao consultar coleções do artigo."
                        )
                    }
                }
                .collect { collectionIds ->
                    val idSet = collectionIds.toSet()
                    _uiState.update { current ->
                        current.copy(
                            savedCollectionIds = idSet,
                            selectedCollectionIds = if (current.isPickerVisible) {
                                current.selectedCollectionIds
                            } else {
                                idSet
                            }
                        )
                    }
                }
        }
    }

    private fun observeArticle() {
        if (articleId <= 0L) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    article = null,
                    errorMessage = "Artigo não encontrado no armazenamento local"
                )
            }
            return
        }

        viewModelScope.launch {
            observeArticleByIdUseCase(articleId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Erro ao carregar artigo"
                        )
                    }
                }
                .collect { article ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            article = article,
                            errorMessage = if (article == null) {
                                "Artigo não encontrado no armazenamento local"
                            } else {
                                null
                            }
                        )
                    }
                    if (article != null) {
                        observeCollectionAssociations(article.id)
                    } else {
                        collectionIdsJob?.cancel()
                        collectionIdsJob = null
                        observedArticleId = null
                    }
                }
        }
    }

    fun openCollectionPicker() {
        if (_uiState.value.article == null) return
        _uiState.update { current ->
            current.copy(
                isPickerVisible = true,
                selectedCollectionIds = current.savedCollectionIds,
                pickerErrorMessage = null
            )
        }
    }

    fun dismissCollectionPicker() {
        if (_uiState.value.isSavingCollections) return
        _uiState.update { current ->
            current.copy(
                isPickerVisible = false,
                selectedCollectionIds = current.savedCollectionIds,
                pickerErrorMessage = null
            )
        }
    }

    fun toggleCollectionSelection(collectionId: Long) {
        if (_uiState.value.isSavingCollections) return
        _uiState.update { current ->
            val updated = if (collectionId in current.selectedCollectionIds) {
                current.selectedCollectionIds - collectionId
            } else {
                current.selectedCollectionIds + collectionId
            }
            current.copy(
                selectedCollectionIds = updated,
                pickerErrorMessage = null
            )
        }
    }

    fun createCollection(name: String) {
        val currentState = _uiState.value
        if (currentState.isCreatingCollection || currentState.isSavingCollections) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isCreatingCollection = true, pickerErrorMessage = null)
            }
            try {
                val trimmedName = name.trim()
                createCollectionUseCase(name)
                _uiState.update { it.copy(isCreatingCollection = false) }
                _feedbackEvents.send("Coleção \"$trimmedName\" criada com sucesso!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: "Erro ao criar coleção."
                _uiState.update {
                    it.copy(
                        isCreatingCollection = false,
                        pickerErrorMessage = errorMsg
                    )
                }
                _feedbackEvents.send(errorMsg)
            }
        }
    }

    fun confirmCollectionSelection() {
        val currentState = _uiState.value
        if (currentState.isSavingCollections) return
        val article = currentState.article ?: return

        val savedIds = currentState.savedCollectionIds
        val selectedIds = currentState.selectedCollectionIds
        val toAdd = selectedIds - savedIds
        val toRemove = savedIds - selectedIds

        if (selectedIds.isEmpty() && savedIds.isEmpty()) {
            _uiState.update {
                it.copy(pickerErrorMessage = EMPTY_SELECTION_ERROR_MESSAGE)
            }
            return
        }

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            _uiState.update {
                it.copy(isPickerVisible = false, pickerErrorMessage = null)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSavingCollections = true, pickerErrorMessage = null)
            }
            try {
                if (toAdd.isNotEmpty()) {
                    addArticleToCollectionsUseCase(
                        articleId = article.id,
                        collectionIds = toAdd.toList()
                    )
                }

                if (toRemove.isNotEmpty()) {
                    toRemove.forEach { collectionId ->
                        removeArticleFromCollectionUseCase(
                            articleId = article.id,
                            collectionId = collectionId
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isSavingCollections = false,
                        isPickerVisible = false,
                        pickerErrorMessage = null
                    )
                }

                val feedbackMessage = when {
                    selectedIds.isEmpty() ->
                        "Artigo removido das coleções. Ele continua salvo na biblioteca."
                    toAdd.isNotEmpty() && toRemove.isEmpty() -> {
                        if (selectedIds.size == 1) {
                            "Artigo salvo na coleção!"
                        } else {
                            "Artigo salvo em ${selectedIds.size} coleções!"
                        }
                    }
                    else -> "Coleções do artigo atualizadas!"
                }
                _feedbackEvents.send(feedbackMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: SAVE_ERROR_MESSAGE
                _uiState.update {
                    it.copy(
                        isSavingCollections = false,
                        pickerErrorMessage = errorMsg
                    )
                }
                _feedbackEvents.send(errorMsg)
            }
        }
    }

    fun refreshArticle() {
        val currentState = _uiState.value
        val currentArticle = currentState.article ?: return
        if (currentState.isRefreshing || refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val outcome = refreshSavedArticleUseCase(
                    articleId = currentArticle.id,
                    forceRefresh = true
                )
                val message = when (outcome) {
                    is RefreshArticleOutcome.Updated ->
                        "Artigo atualizado com a versão mais recente da Wikipédia."
                    is RefreshArticleOutcome.Unchanged ->
                        "O artigo já está atualizado. Nenhuma alteração encontrada."
                    is RefreshArticleOutcome.Skipped -> when (outcome.reason) {
                        SkipReason.NO_WIKIPEDIA_PAGE_ID ->
                            "Este artigo local não possui vínculo com a Wikipédia para atualização."
                        SkipReason.NO_CONNECTION ->
                            "Dispositivo offline. Não foi possível atualizar o artigo agora."
                        SkipReason.ARTICLE_NOT_FOUND_LOCALLY ->
                            "Artigo não encontrado no armazenamento local."
                        SkipReason.RECENTLY_SYNCED ->
                            "Este artigo foi sincronizado recentemente."
                    }
                    is RefreshArticleOutcome.Failed ->
                        mapFriendlySyncError(outcome)
                }
                _feedbackEvents.send(message)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _feedbackEvents.send(REFRESH_GENERIC_ERROR_MESSAGE)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun mapFriendlySyncError(outcome: RefreshArticleOutcome.Failed): String {
        return if (outcome.cause is WikipediaException && outcome.message.isNotBlank()) {
            outcome.message
        } else {
            REFRESH_GENERIC_ERROR_MESSAGE
        }
    }

    companion object {
        private const val EMPTY_SELECTION_ERROR_MESSAGE =
            "Selecione pelo menos uma coleção para associar o artigo."
        private const val SAVE_ERROR_MESSAGE =
            "Não foi possível atualizar as coleções do artigo."
        private const val REFRESH_GENERIC_ERROR_MESSAGE =
            "Não foi possível atualizar o artigo no momento. O conteúdo local foi preservado."
    }
}
