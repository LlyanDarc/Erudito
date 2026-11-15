package com.example.erudito.presentation.wikipedia_article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.usecase.AddArticleToCollectionsUseCase
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionIdsForArticleUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
import com.example.erudito.domain.usecase.RemoveArticleFromCollectionUseCase
import com.example.erudito.domain.usecase.SaveWikipediaArticleToCollectionsUseCase
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
    private val getCollectionIdsForArticleUseCase: GetCollectionIdsForArticleUseCase
) : ViewModel() {

    private val hasPageIdArg: Boolean = savedStateHandle.contains(ARG_PAGE_ID)

    val pageId: Long = savedStateHandle.get<Long>(ARG_PAGE_ID)
        ?: savedStateHandle.get<String>(ARG_PAGE_ID)?.toLongOrNull()
        ?: 0L

    private var activePageId: Long = pageId
    private var loadJob: Job? = null
    private var collectionIdsJob: Job? = null
    private var observedArticleKey: Pair<Long, String>? = null
    private var cachedSavedArticles: List<Article> = emptyList()

    private val _uiState = MutableStateFlow<WikipediaArticleUiState>(WikipediaArticleUiState.Initial)
    val uiState: StateFlow<WikipediaArticleUiState> = _uiState.asStateFlow()

    private val _collectionsUiState = MutableStateFlow(WikipediaArticleCollectionsUiState())
    val collectionsUiState: StateFlow<WikipediaArticleCollectionsUiState> = _collectionsUiState.asStateFlow()

    private val _feedbackEvents = Channel<String>(Channel.BUFFERED)
    val feedbackEvents: Flow<String> = _feedbackEvents.receiveAsFlow()

    init {
        observeAvailableCollections()
        observeSavedArticles()
        if (hasPageIdArg) {
            loadArticle(pageId)
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
                }
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
                pickerErrorMessage = null
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
            try {
                val article = getWikipediaArticleUseCase(targetPageId)
                if (article != null) {
                    _uiState.value = WikipediaArticleUiState.Success(article)
                    observeCollectionAssociationsForArticle(article)
                } else {
                    _uiState.value = WikipediaArticleUiState.Error(ARTICLE_NOT_FOUND_MESSAGE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WikipediaException) {
                _uiState.value = WikipediaArticleUiState.Error(e.message)
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
        val currentState = _collectionsUiState.value
        if (currentState.isCreatingCollection || currentState.isSaving) return

        viewModelScope.launch {
            _collectionsUiState.update {
                it.copy(isCreatingCollection = true, pickerErrorMessage = null)
            }
            try {
                val trimmedName = name.trim()
                createCollectionUseCase(name)
                _collectionsUiState.update { it.copy(isCreatingCollection = false) }
                _feedbackEvents.send("Coleção \"$trimmedName\" criada com sucesso!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: "Erro ao criar coleção."
                _collectionsUiState.update {
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
        val currentState = _collectionsUiState.value
        if (currentState.isSaving) return
        val article = (_uiState.value as? WikipediaArticleUiState.Success)?.article ?: return

        val savedIds = currentState.savedCollectionIds
        val selectedIds = currentState.selectedCollectionIds
        val toAdd = selectedIds - savedIds
        val toRemove = savedIds - selectedIds

        if (selectedIds.isEmpty() && savedIds.isEmpty()) {
            _collectionsUiState.update {
                it.copy(pickerErrorMessage = EMPTY_SELECTION_ERROR_MESSAGE)
            }
            return
        }

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            _collectionsUiState.update {
                it.copy(isPickerVisible = false, pickerErrorMessage = null)
            }
            return
        }

        viewModelScope.launch {
            _collectionsUiState.update {
                it.copy(isSaving = true, pickerErrorMessage = null)
            }
            try {
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

                _collectionsUiState.update {
                    it.copy(
                        isSaving = false,
                        isPickerVisible = false,
                        pickerErrorMessage = null
                    )
                }

                val feedbackMessage = when {
                    selectedIds.isEmpty() -> "Artigo removido das coleções selecionadas."
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
                _collectionsUiState.update {
                    it.copy(
                        isSaving = false,
                        pickerErrorMessage = errorMsg
                    )
                }
                _feedbackEvents.send(errorMsg)
            }
        }
    }

    private suspend fun findLocalArticle(wikiArticle: WikipediaArticle): Article? {
        val findIn: (List<Article>) -> Article? = { articles ->
            articles.find { it.wikipediaPageId == wikiArticle.pageId }
                ?: articles.find { it.wikipediaPageId == null && it.url == wikiArticle.url }
        }
        return findIn(cachedSavedArticles) ?: runCatching {
            val latest = getSavedArticlesUseCase().first()
            cachedSavedArticles = latest
            findIn(latest)
        }.getOrNull()
    }

    companion object {
        const val ARG_PAGE_ID = "pageId"
        private const val INVALID_PAGE_ID_MESSAGE = "Identificador de artigo inválido."
        private const val ARTICLE_NOT_FOUND_MESSAGE = "Artigo não encontrado na Wikipédia."
        private const val GENERIC_ERROR_MESSAGE =
            "Não foi possível carregar o artigo da Wikipédia. Tente novamente."
        private const val EMPTY_SELECTION_ERROR_MESSAGE =
            "Selecione pelo menos uma coleção para salvar o artigo."
        private const val SAVE_ERROR_MESSAGE =
            "Não foi possível atualizar as coleções do artigo."
    }
}
