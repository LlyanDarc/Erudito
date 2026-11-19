package com.example.erudito.presentation.components

import com.example.erudito.domain.usecase.CreateCollectionUseCase
import kotlinx.coroutines.CancellationException

internal data class CollectionPickerMessages(
    val emptySelectionError: String,
    val allRemovedFeedback: String,
    val saveError: String = DEFAULT_SAVE_ERROR
) {
    companion object {
        const val DEFAULT_CREATE_ERROR = "Erro ao criar coleção."
        const val DEFAULT_SAVE_ERROR = "Não foi possível atualizar as coleções do artigo."
    }
}

internal object CollectionPickerCoordinator {

    fun toggleSelection(
        currentSelection: Set<Long>,
        collectionId: Long
    ): Set<Long> {
        return if (collectionId in currentSelection) {
            currentSelection - collectionId
        } else {
            currentSelection + collectionId
        }
    }

    suspend fun createCollection(
        name: String,
        createCollectionUseCase: CreateCollectionUseCase,
        onStart: () -> Unit,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onCancelled: () -> Unit,
        emitFeedback: suspend (String) -> Unit
    ) {
        onStart()
        try {
            val trimmedName = name.trim()
            createCollectionUseCase(name)
            onSuccess()
            emitFeedback("Coleção \"$trimmedName\" criada com sucesso!")
        } catch (e: CancellationException) {
            onCancelled()
            throw e
        } catch (e: Exception) {
            val errorMsg = e.message?.takeIf { it.isNotBlank() }
                ?: CollectionPickerMessages.DEFAULT_CREATE_ERROR
            onFailure(errorMsg)
            emitFeedback(errorMsg)
        }
    }

    suspend fun confirmSelection(
        savedIds: Set<Long>,
        selectedIds: Set<Long>,
        messages: CollectionPickerMessages,
        onValidationError: (String) -> Unit,
        onNoChanges: () -> Unit,
        onStart: () -> Unit,
        persistChanges: suspend (toAdd: Set<Long>, toRemove: Set<Long>) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onCancelled: () -> Unit,
        emitFeedback: suspend (String) -> Unit
    ) {
        val toAdd = selectedIds - savedIds
        val toRemove = savedIds - selectedIds

        if (selectedIds.isEmpty() && savedIds.isEmpty()) {
            onValidationError(messages.emptySelectionError)
            return
        }

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            onNoChanges()
            return
        }

        onStart()
        try {
            persistChanges(toAdd, toRemove)
            onSuccess()
            val feedbackMessage = buildFeedbackMessage(
                selectedCount = selectedIds.size,
                hasAdditions = toAdd.isNotEmpty(),
                hasRemovals = toRemove.isNotEmpty(),
                allRemovedFeedback = messages.allRemovedFeedback
            )
            emitFeedback(feedbackMessage)
        } catch (e: CancellationException) {
            onCancelled()
            throw e
        } catch (e: Exception) {
            val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: messages.saveError
            onFailure(errorMsg)
            emitFeedback(errorMsg)
        }
    }

    private fun buildFeedbackMessage(
        selectedCount: Int,
        hasAdditions: Boolean,
        hasRemovals: Boolean,
        allRemovedFeedback: String
    ): String {
        return when {
            selectedCount == 0 -> allRemovedFeedback
            hasAdditions && !hasRemovals -> {
                if (selectedCount == 1) {
                    "Artigo salvo na coleção!"
                } else {
                    "Artigo salvo em $selectedCount coleções!"
                }
            }
            else -> "Coleções do artigo atualizadas!"
        }
    }
}
