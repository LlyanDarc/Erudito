package com.example.erudito.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erudito.domain.connectivity.ConnectivityObserver
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.sync.SyncResult
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import com.example.erudito.domain.usecase.SyncSavedArticlesUseCase
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
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val syncSavedArticlesUseCase: SyncSavedArticlesUseCase,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private var hasLoadedInitialLibrary = false
    private var hasCompletedInitialAutoSync = false
    private var syncJob: Job? = null

    private val _uiState = MutableStateFlow(
        HomeUiState(isOnline = connectivityObserver.isCurrentlyOnline())
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<String>(Channel.BUFFERED)
    val feedbackEvents: Flow<String> = _feedbackEvents.receiveAsFlow()

    init {
        observeConnectivity()
        loadLibraryData()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { current ->
                    val nextSyncStatus = when {
                        online && current.syncStatus is HomeSyncStatus.Offline -> HomeSyncStatus.Idle
                        !online &&
                            !current.isSyncing &&
                            hasLoadedInitialLibrary &&
                            current.recentArticles.any { (it.wikipediaPageId ?: 0L) > 0L } ->
                            HomeSyncStatus.Offline
                        else -> current.syncStatus
                    }
                    current.copy(
                        isOnline = online,
                        syncStatus = nextSyncStatus
                    )
                }

                if (online && hasLoadedInitialLibrary && !hasCompletedInitialAutoSync) {
                    triggerAutoSyncIfNeeded(_uiState.value.recentArticles)
                }
            }
        }
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
                hasLoadedInitialLibrary = true
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        collections = collections,
                        recentArticles = articles,
                        totalCollectionsCount = collections.size,
                        totalSavedArticlesCount = articles.size
                    )
                }

                if (!hasCompletedInitialAutoSync) {
                    triggerAutoSyncIfNeeded(articles)
                }
            }
        }
    }

    private fun triggerAutoSyncIfNeeded(articles: List<Article>) {
        if (hasCompletedInitialAutoSync || syncJob?.isActive == true) return
        val hasSyncableArticles = articles.any { (it.wikipediaPageId ?: 0L) > 0L }
        if (!hasSyncableArticles) {
            return
        }

        if (!connectivityObserver.isCurrentlyOnline()) {
            _uiState.update { it.copy(syncStatus = HomeSyncStatus.Offline) }
            return
        }

        hasCompletedInitialAutoSync = true
        launchSync(forceRefresh = false, isAutomatic = true)
    }

    fun retrySync() {
        if (!hasLoadedInitialLibrary) return
        launchSync(forceRefresh = false, isAutomatic = false)
    }

    fun dismissSyncStatus() {
        _uiState.update { current ->
            if (current.isSyncing) current else current.copy(syncStatus = HomeSyncStatus.Idle)
        }
    }

    private fun launchSync(forceRefresh: Boolean, isAutomatic: Boolean) {
        if (syncJob?.isActive == true || _uiState.value.isSyncing) return

        if (!connectivityObserver.isCurrentlyOnline()) {
            if (isAutomatic) {
                hasCompletedInitialAutoSync = false
            }
            _uiState.update { it.copy(syncStatus = HomeSyncStatus.Offline) }
            if (!isAutomatic) {
                viewModelScope.launch {
                    _feedbackEvents.send("Dispositivo offline. Conecte-se à internet para sincronizar.")
                }
            }
            return
        }

        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(syncStatus = HomeSyncStatus.Syncing) }
            try {
                val result = syncSavedArticlesUseCase(forceRefresh = forceRefresh)
                handleSyncResult(result = result, isAutomatic = isAutomatic)
            } catch (e: CancellationException) {
                if (isAutomatic) {
                    hasCompletedInitialAutoSync = false
                }
                _uiState.update { current ->
                    if (current.syncStatus is HomeSyncStatus.Syncing) {
                        current.copy(syncStatus = HomeSyncStatus.Idle)
                    } else {
                        current
                    }
                }
                throw e
            } catch (_: Exception) {
                if (isAutomatic) {
                    hasCompletedInitialAutoSync = false
                }
                _uiState.update { it.copy(syncStatus = HomeSyncStatus.Failed(failedCount = 1)) }
                if (!isAutomatic) {
                    _feedbackEvents.send("Não foi possível concluir a sincronização dos artigos salvos.")
                }
            }
        }
    }

    private suspend fun handleSyncResult(result: SyncResult, isAutomatic: Boolean) {
        when {
            result.abortedDueToNoConnection -> {
                if (isAutomatic) {
                    hasCompletedInitialAutoSync = false
                }
                _uiState.update { it.copy(syncStatus = HomeSyncStatus.Offline) }
                if (!isAutomatic) {
                    _feedbackEvents.send("Dispositivo offline. Sincronização não executada.")
                }
            }

            result.failedCount > 0 && (result.updatedCount > 0 || result.unchangedCount > 0) -> {
                val status = HomeSyncStatus.PartialFailure(
                    updatedCount = result.updatedCount,
                    unchangedCount = result.unchangedCount,
                    failedCount = result.failedCount
                )
                _uiState.update { it.copy(syncStatus = status) }
                if (!isAutomatic) {
                    val message = if (result.updatedCount > 0) {
                        "Sincronização parcial: ${result.updatedCount} atualizado(s) e ${result.failedCount} falha(s)."
                    } else {
                        "Sincronização parcial: ${result.unchangedCount} verificado(s) e ${result.failedCount} falha(s)."
                    }
                    _feedbackEvents.send(message)
                }
            }

            result.failedCount > 0 -> {
                _uiState.update { it.copy(syncStatus = HomeSyncStatus.Failed(result.failedCount)) }
                if (!isAutomatic) {
                    _feedbackEvents.send("Não foi possível sincronizar os artigos salvos no momento.")
                }
            }

            result.updatedCount > 0 -> {
                _uiState.update {
                    it.copy(
                        syncStatus = HomeSyncStatus.CompletedWithUpdates(
                            updatedCount = result.updatedCount,
                            unchangedCount = result.unchangedCount
                        )
                    )
                }
                if (!isAutomatic) {
                    val message = if (result.updatedCount == 1) {
                        "1 artigo salvo foi atualizado."
                    } else {
                        "${result.updatedCount} artigos salvos foram atualizados."
                    }
                    _feedbackEvents.send(message)
                }
            }

            result.unchangedCount > 0 -> {
                _uiState.update {
                    it.copy(
                        syncStatus = HomeSyncStatus.CompletedNoChanges(
                            checkedCount = result.unchangedCount
                        )
                    )
                }
            }

            else -> {
                // Todos os artigos foram ignorados pela política de frescor ou não possuem pageId remoto
                _uiState.update { it.copy(syncStatus = HomeSyncStatus.Idle) }
            }
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            try {
                val trimmedName = name.trim()
                createCollectionUseCase(name)
                _feedbackEvents.send("Coleção \"$trimmedName\" criada com sucesso!")
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
