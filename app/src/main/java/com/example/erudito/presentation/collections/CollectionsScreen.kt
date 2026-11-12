package com.example.erudito.presentation.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erudito.presentation.components.CollectionCard
import com.example.erudito.presentation.components.CreateEditCollectionDialog
import com.example.erudito.presentation.components.DeleteConfirmationDialog
import com.example.erudito.presentation.components.EmptyStateView
import com.example.erudito.presentation.components.EruditoTopAppBar

@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCollectionDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedbackMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        topBar = {
            EruditoTopAppBar(
                title = "Minhas Coleções",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_collection_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Criar nova coleção"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.collections.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.FolderOpen,
                title = "Nenhuma coleção encontrada",
                description = "Você ainda não possui coleções personalizadas. Crie uma para agrupar seus artigos salvos.",
                actionButtonText = "Criar Coleção",
                onActionClick = { viewModel.openCreateDialog() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.collections,
                    key = { "collection_${it.id}" }
                ) { collection ->
                    CollectionCard(
                        collection = collection,
                        onClick = { onNavigateToCollectionDetail(collection.id) },
                        onRenameClick = { viewModel.openRenameDialog(collection) },
                        onDeleteClick = { viewModel.openDeleteDialog(collection) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    if (uiState.isCreateDialogOpen) {
        CreateEditCollectionDialog(
            initialName = "",
            isEditing = false,
            onDismiss = { viewModel.closeCreateDialog() },
            onConfirm = { name ->
                viewModel.createCollection(name)
            }
        )
    }

    uiState.collectionToRename?.let { col ->
        CreateEditCollectionDialog(
            initialName = col.name,
            isEditing = true,
            onDismiss = { viewModel.closeRenameDialog() },
            onConfirm = { newName ->
                viewModel.renameCollection(col.id, newName)
            }
        )
    }

    uiState.collectionToDelete?.let { col ->
        DeleteConfirmationDialog(
            collectionName = col.name,
            onDismiss = { viewModel.closeDeleteDialog() },
            onConfirm = {
                viewModel.deleteCollection(col.id)
            }
        )
    }
}
