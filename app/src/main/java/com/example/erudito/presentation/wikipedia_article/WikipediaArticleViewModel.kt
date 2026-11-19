package com.example.erudito.presentation.wikipedia_article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.connectivity.ConnectivityObserver
import com.example.erudito.domain.html.StructuredArticleHtmlProcessor
import com.example.erudito.domain.mapper.toWikipediaArticle
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.sync.RefreshArticleOutcome
import com.example.erudito.domain.sync.SkipReason
import com.example.erudito.domain.usecase.AddArticleToCollectionsUseCase
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionIdsForArticleUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticleByWikipediaPageIdUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
import com.example.erudito.domain.usecase.RefreshSavedArticleUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import com.example.erudito.domain.usecase.SaveWikipediaArticleToCollectionsUseCase
import com.example.erudito.presentation.components.CollectionPickerCoordinator
import com.example.erudito.presentation.components.CollectionPickerMessages
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WikipediaArticleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWikipediaArticleUseCase: GetWikipediaArticleUseCase,
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val getSavedArticlesUseCase: GetSavedArticlesUseCase,
    private val saveWikipediaArticleToCollectionsUseCase: SaveWikipediaArticleToCollectionsUseCase,
    private val addArticleToCollectionsUseCase: AddArticleToCollectionsUseCase,
    private val removeArticleFromCollectionUseCase: RemoveArticleFromCollectionUseCase,
    private val getCollectionIdsForArticleUseCase: GetCollectionIdsForArticleUseCase,
    private val getSavedArticleByWikipediaPageIdUseCase: GetSavedArticleByWikipediaPageIdUseCase,
    private val refreshSavedArticleUseCase: RefreshSavedArticleUseCase,
    private val connectivityObserver: ConnectivityObserver,
    val htmlProcessor: StructuredArticleHtmlProcessor
) : ViewModel() {

    private val hasPageIdArg: Boolean = savedStateHandle.contains(ARG_PAGE_ID)

    val pageId: Long = savedStateHandle.get<Long>(ARG_PAGE_ID)
        ?: savedStateHandle.get<String>(ARG_PAGE_ID)?.toLongOrNull()
        ?: 0L

    private var activePageId: Long = pageId
    private var loadJob: Job? = null
    private var collectionIdsJob: Job? = null
    private var refreshSavedJob: Job? = null
    private var observedArticleKey: Pair<Long, String>? = null
    private var cachedSavedArticles: List<Article> = emptyList()

    private val _uiState = MutableStateFlow<WikipediaArticleUiState>(WikipediaArticleUiState.Initial)
    val uiState: StateFlow<WikipediaArticleUiState> = _uiState.asStateFlow()

    private val _collectionsUiState = MutableStateFlow(
        WikipediaArticleCollectionsUiState(isOnline = connectivityObserver.isCurrentlyOnline())
    )
    val collectionsUiState: StateFlow<WikipediaArticleCollectionsUiState> = _collectionsUiState.asStateFlow()

    private val _feedbackEvents = Channel<String>(Channel.BUFFERED)
    val feedbackEvents: Flow<String> = _feedbackEvents.receiveAsFlow()

    init {
        observeConnectivity()
        observeAvailableCollections()
        observeSavedArticles()
        if (hasPageIdArg) {
            loadArticle(pageId)
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _collectionsUiState.update { it.copy(isOnline = online) }
            }
        }
    }

    private fun observeAvailableCollections() {
        viewModelScope.launch {
            getCollectionsUseCase()
                .catch { e ->
                    _collectionsUiState.update {
                        it.copy(
                            isLoadingCollections = false,
                            pickerErrorMessage = e.message ?: "Erro ao carregar coleções."
                        )
                    }
                }
                .collect { collections ->
                    _collectionsUiState.update {
                        it.copy(
                            availableCollections = collections,
                            isLoadingCollections = false
                        )
                    }
                }
        }
    }

    private fun observeSavedArticles() {
        viewModelScope.launch {
            getSavedArticlesUseCase()
                .catch { /* Mantém cache anterior em caso de falha transitória */ }
                .collect { articles ->
                    cachedSavedArticles = articles
                    val displayed = (_uiState.value as? WikipediaArticleUiState.Success)?.article
                    updateSavedCopyState(displayed)
                }
        }
    }

    private fun updateSavedCopyState(displayedArticle: WikipediaArticle?) {
        val matched = if (displayedArticle != null) {
            cachedSavedArticles.find { it.wikipediaPageId == displayedArticle.pageId }
                ?: cachedSavedArticles.find {
                    it.wikipediaPageId == null && it.url == displayedArticle.url
                }
        } else {
            cachedSavedArticles.find { it.wikipediaPageId == activePageId }
        }
        _collectionsUiState.update {
            it.copy(
                savedArticleId = matched?.id,
                canRefreshSavedCopy = matched != null && (matched.wikipediaPageId ?: 0L) > 0L
            )
        }
    }

    private fun observeCollectionAssociationsForArticle(article: WikipediaArticle) {
        val key = article.pageId to article.url
        if (collectionIdsJob?.isActive == true && observedArticleKey == key) {
            return
        }
        observedArticleKey = key
        collectionIdsJob?.cancel()
        collectionIdsJob = viewModelScope.launch {
            getCollectionIdsForArticleUseCase(article)
                .catch { e ->
                    _collectionsUiState.update {
                        it.copy(pickerErrorMessage = e.message ?: "Erro ao consultar coleções do artigo.")
                    }
                }
                .collect { collectionIds ->
                    val idSet = collectionIds.toSet()
                    _collectionsUiState.update { current ->
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

    private fun resetArticleAssociationsObservation() {
        collectionIdsJob?.cancel()
        collectionIdsJob = null
        observedArticleKey = null
        _collectionsUiState.update {
            it.copy(
                savedCollectionIds = emptySet(),
                selectedCollectionIds = emptySet(),
                isPickerVisible = false,
                pickerErrorMessage = null,
                savedArticleId = null,
                canRefreshSavedCopy = false,
                isRefreshingSavedCopy = false
            )
        }
    }

    fun loadArticle(targetPageId: Long = activePageId) {
        if (targetPageId <= 0L) {
            loadJob?.cancel()
            loadJob = null
            activePageId = targetPageId
            resetArticleAssociationsObservation()
            _uiState.value = WikipediaArticleUiState.Error(INVALID_PAGE_ID_MESSAGE)
            return
        }

        if (loadJob?.isActive == true && activePageId == targetPageId) {
            return
        }

        activePageId = targetPageId
        loadJob?.cancel()
        resetArticleAssociationsObservation()
        loadJob = viewModelScope.launch {
            _uiState.value = WikipediaArticleUiState.Loading
            if (!connectivityObserver.isCurrentlyOnline()) {
                val localArticle = findSavedLocalArticleByPageId(targetPageId)
                if (localArticle != null) {
                    val wikiArticle = localArticle.toWikipediaArticle(fallbackPageId = targetPageId)
                    _uiState.value = WikipediaArticleUiState.Success(
                        article = wikiArticle,
                        isLocalFallback = true,
                        localLastUpdated = localArticle.lastUpdated
                    )
                    updateSavedCopyState(wikiArticle)
                    observeCollectionAssociationsForArticle(wikiArticle)
                } else {
                    _uiState.value = WikipediaArticleUiState.Error(NO_CONNECTION_MESSAGE)
                }
                return@launch
            }

            try {
                val article = getWikipediaArticleUseCase(targetPageId)
                if (article != null) {
                    _uiState.value = WikipediaArticleUiState.Success(article)
                    updateSavedCopyState(article)
                    observeCollectionAssociationsForArticle(article)
                } else {
                    _uiState.value = WikipediaArticleUiState.Error(ARTICLE_NOT_FOUND_MESSAGE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WikipediaException) {
                val localArticle = findSavedLocalArticleByPageId(targetPageId)
                if (localArticle != null) {
                    val wikiArticle = localArticle.toWikipediaArticle(fallbackPageId = targetPageId)
                    _uiState.value = WikipediaArticleUiState.Success(
                        article = wikiArticle,
                        isLocalFallback = true,
                        localLastUpdated = localArticle.lastUpdated
                    )
                    updateSavedCopyState(wikiArticle)
                    observeCollectionAssociationsForArticle(wikiArticle)
                } else {
                    _uiState.value = WikipediaArticleUiState.Error(e.message)
                }
            } catch (e: IllegalArgumentException) {
                _uiState.value = WikipediaArticleUiState.Error(
                    e.message ?: INVALID_PAGE_ID_MESSAGE
                )
            } catch (e: Exception) {
                _uiState.value = WikipediaArticleUiState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR_MESSAGE
                )
            }
        }
    }

    private suspend fun findSavedLocalArticleByPageId(targetPageId: Long): Article? {
        val persisted = try {
            getSavedArticleByWikipediaPageIdUseCase(targetPageId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return persisted ?: cachedSavedArticles.find { it.wikipediaPageId == targetPageId }
    }

    fun retry() {
        loadArticle(activePageId)
    }

    fun openCollectionPicker() {
        if (_uiState.value !is WikipediaArticleUiState.Success) return
        _collectionsUiState.update { current ->
            current.copy(
                isPickerVisible = true,
                selectedCollectionIds = current.savedCollectionIds,
                pickerErrorMessage = null
            )
        }
    }

    fun dismissCollectionPicker() {
        if (_collectionsUiState.value.isSaving) return
        _collectionsUiState.update { current ->
            current.copy(
                isPickerVisible = false,
                selectedCollectionIds = current.savedCollectionIds,
                pickerErrorMessage = null
            )
        }
    }

    fun toggleCollectionSelection(collectionId: Long) {
        if (_collectionsUiState.value.isSaving) return
        _collectionsUiState.update { current ->
            current.copy(
                selectedCollectionIds = CollectionPickerCoordinator.toggleSelection(
                    currentSelection = current.selectedCollectionIds,
                    collectionId = collectionId
                ),
                pickerErrorMessage = null
            )
        }
    }

    fun createCollection(name: String) {
        val currentState = _collectionsUiState.value
        if (currentState.isCreatingCollection || currentState.isSaving) return

        viewModelScope.launch {
            CollectionPickerCoordinator.createCollection(
                name = name,
                createCollectionUseCase = createCollectionUseCase,
                onStart = {
                    _collectionsUiState.update {
                        it.copy(isCreatingCollection = true, pickerErrorMessage = null)
                    }
                },
                onSuccess = {
                    _collectionsUiState.update { it.copy(isCreatingCollection = false) }
                },
                onFailure = { errorMsg ->
                    _collectionsUiState.update {
                        it.copy(
                            isCreatingCollection = false,
                            pickerErrorMessage = errorMsg
                        )
                    }
                },
                onCancelled = {
                    _collectionsUiState.update { it.copy(isCreatingCollection = false) }
                },
                emitFeedback = { _feedbackEvents.send(it) }
            )
        }
    }

    fun confirmCollectionSelection() {
        val currentState = _collectionsUiState.value
        if (currentState.isSaving) return
        val article = (_uiState.value as? WikipediaArticleUiState.Success)?.article ?: return

        viewModelScope.launch {
            CollectionPickerCoordinator.confirmSelection(
                savedIds = currentState.savedCollectionIds,
                selectedIds = currentState.selectedCollectionIds,
                messages = PICKER_MESSAGES,
                onValidationError = { errorMsg ->
                    _collectionsUiState.update { it.copy(pickerErrorMessage = errorMsg) }
                },
                onNoChanges = {
                    _collectionsUiState.update {
                        it.copy(isPickerVisible = false, pickerErrorMessage = null)
                    }
                },
                onStart = {
                    _collectionsUiState.update {
                        it.copy(isSaving = true, pickerErrorMessage = null)
                    }
                },
                persistChanges = { toAdd, toRemove ->
                    val localArticle = findLocalArticle(article)

                    if (toAdd.isNotEmpty()) {
                        if (localArticle != null && localArticle.wikipediaPageId == article.pageId) {
                            addArticleToCollectionsUseCase(
                                articleId = localArticle.id,
                                collectionIds = toAdd.toList()
                            )
                        } else {
                            saveWikipediaArticleToCollectionsUseCase(
                                wikipediaArticle = article,
                                collectionIds = toAdd.toList()
                            )
                        }
                    }

                    if (toRemove.isNotEmpty()) {
                        val targetArticleId = localArticle?.id ?: findLocalArticle(article)?.id
                        if (targetArticleId != null) {
                            toRemove.forEach { collectionId ->
                                removeArticleFromCollectionUseCase(
                                    articleId = targetArticleId,
                                    collectionId = collectionId
                                )
                            }
                        }
                    }
                },
                onSuccess = {
                    _collectionsUiState.update {
                        it.copy(
                            isSaving = false,
                            isPickerVisible = false,
                            pickerErrorMessage = null
                        )
                    }
                },
                onFailure = { errorMsg ->
                    _collectionsUiState.update {
                        it.copy(
                            isSaving = false,
                            pickerErrorMessage = errorMsg
                        )
                    }
                },
                onCancelled = {
                    _collectionsUiState.update { it.copy(isSaving = false) }
                },
                emitFeedback = { _feedbackEvents.send(it) }
            )
        }
    }

    fun refreshSavedCopy() {
        val currentCollectionsState = _collectionsUiState.value
        val targetArticleId = currentCollectionsState.savedArticleId ?: return
        if (currentCollectionsState.isRefreshingSavedCopy || refreshSavedJob?.isActive == true) return

        refreshSavedJob = viewModelScope.launch {
            _collectionsUiState.update { it.copy(isRefreshingSavedCopy = true) }
            try {
                val outcome = refreshSavedArticleUseCase(
                    articleId = targetArticleId,
                    forceRefresh = true
                )
                val message = when (outcome) {
                    is RefreshArticleOutcome.Updated -> {
                        val currentSuccess = _uiState.value as? WikipediaArticleUiState.Success
                        if (currentSuccess != null) {
                            _uiState.value = currentSuccess.copy(
                                article = outcome.article.toWikipediaArticle(fallbackPageId = activePageId),
                                localLastUpdated = outcome.article.lastUpdated
                            )
                        }
                        "Cópia salva atualizada com a versão mais recente da Wikipédia."
                    }
                    is RefreshArticleOutcome.Unchanged ->
                        "A cópia salva já está atualizada. Nenhuma alteração encontrada."
                    is RefreshArticleOutcome.Skipped -> when (outcome.reason) {
                        SkipReason.NO_WIKIPEDIA_PAGE_ID ->
                            "Este artigo local não possui vínculo com a Wikipédia para atualização."
                        SkipReason.NO_CONNECTION ->
                            "Dispositivo offline. Não foi possível atualizar a cópia salva agora."
                        SkipReason.ARTICLE_NOT_FOUND_LOCALLY ->
                            "Cópia local não encontrada para atualização."
                        SkipReason.RECENTLY_SYNCED ->
                            "A cópia salva foi sincronizada recentemente."
                    }
                    is RefreshArticleOutcome.Failed -> {
                        if (outcome.cause is WikipediaException && outcome.message.isNotBlank()) {
                            outcome.message
                        } else {
                            "Não foi possível atualizar a cópia salva. O conteúdo local foi preservado."
                        }
                    }
                }
                _feedbackEvents.send(message)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _feedbackEvents.send(
                    "Não foi possível atualizar a cópia salva. O conteúdo local foi preservado."
                )
            } finally {
                _collectionsUiState.update { it.copy(isRefreshingSavedCopy = false) }
            }
        }
    }

    fun onOpenExternalLinkFailed() {
        _feedbackEvents.trySend(OPEN_LINK_ERROR_MESSAGE)
    }

    private suspend fun findLocalArticle(wikiArticle: WikipediaArticle): Article? {
        val findIn: (List<Article>) -> Article? = { articles ->
            articles.find { it.wikipediaPageId == wikiArticle.pageId }
                ?: articles.find { it.wikipediaPageId == null && it.url == wikiArticle.url }
        }
        return findIn(cachedSavedArticles) ?: try {
            val latest = getSavedArticlesUseCase().first()
            cachedSavedArticles = latest
            findIn(latest)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val ARG_PAGE_ID = "pageId"
        private const val INVALID_PAGE_ID_MESSAGE = "Identificador de artigo inválido."
        private const val ARTICLE_NOT_FOUND_MESSAGE = "Artigo não encontrado na Wikipédia."
        private const val NO_CONNECTION_MESSAGE =
            "Sem conexão com a internet. Verifique sua rede e tente novamente."
        private const val GENERIC_ERROR_MESSAGE =
            "Não foi possível carregar o artigo da Wikipédia. Tente novamente."
        private const val OPEN_LINK_ERROR_MESSAGE =
            "Não foi possível abrir o link no navegador."
        private val PICKER_MESSAGES = CollectionPickerMessages(
            emptySelectionError = "Selecione pelo menos uma coleção para salvar o artigo.",
            allRemovedFeedback = "Artigo removido das coleções selecionadas."
        )
    }
}
