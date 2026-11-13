package com.example.erudito.presentation.wikipedia_article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WikipediaArticleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWikipediaArticleUseCase: GetWikipediaArticleUseCase
) : ViewModel() {

    private val hasPageIdArg: Boolean = savedStateHandle.contains(ARG_PAGE_ID)

    val pageId: Long = savedStateHandle.get<Long>(ARG_PAGE_ID)
        ?: savedStateHandle.get<String>(ARG_PAGE_ID)?.toLongOrNull()
        ?: 0L

    private var activePageId: Long = pageId
    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow<WikipediaArticleUiState>(WikipediaArticleUiState.Initial)
    val uiState: StateFlow<WikipediaArticleUiState> = _uiState.asStateFlow()

    init {
        if (hasPageIdArg) {
            loadArticle(pageId)
        }
    }

    fun loadArticle(targetPageId: Long = activePageId) {
        if (targetPageId <= 0L) {
            loadJob?.cancel()
            loadJob = null
            activePageId = targetPageId
            _uiState.value = WikipediaArticleUiState.Error(INVALID_PAGE_ID_MESSAGE)
            return
        }

        if (loadJob?.isActive == true && activePageId == targetPageId) {
            return
        }

        activePageId = targetPageId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = WikipediaArticleUiState.Loading
            try {
                val article = getWikipediaArticleUseCase(targetPageId)
                _uiState.value = if (article != null) {
                    WikipediaArticleUiState.Success(article)
                } else {
                    WikipediaArticleUiState.Error(ARTICLE_NOT_FOUND_MESSAGE)
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

    companion object {
        const val ARG_PAGE_ID = "pageId"
        private const val INVALID_PAGE_ID_MESSAGE = "Identificador de artigo inválido."
        private const val ARTICLE_NOT_FOUND_MESSAGE = "Artigo não encontrado na Wikipédia."
        private const val GENERIC_ERROR_MESSAGE =
            "Não foi possível carregar o artigo da Wikipédia. Tente novamente."
    }
}
