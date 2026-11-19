package com.example.erudito.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erudito.presentation.components.ArticleCard
import com.example.erudito.presentation.components.CollectionCard
import com.example.erudito.presentation.components.CreateEditCollectionDialog
import com.example.erudito.presentation.components.DeleteConfirmationDialog
import com.example.erudito.presentation.components.EmptyStateView
import com.example.erudito.presentation.components.EruditoTopAppBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCollections: () -> Unit,
    onNavigateToCollectionDetail: (Long) -> Unit,
    onNavigateToArticleDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onNavigateToCollections = onNavigateToCollections,
        onNavigateToCollectionDetail = onNavigateToCollectionDetail,
        onNavigateToArticleDetail = onNavigateToArticleDetail,
        onCreateCollection = { viewModel.createCollection(it) },
        onRenameCollection = { id, name -> viewModel.renameCollection(id, name) },
        onDeleteCollection = { viewModel.deleteCollection(it) },
        onClearError = { viewModel.clearError() },
        onRetrySync = { viewModel.retrySync() },
        onDismissSyncStatus = { viewModel.dismissSyncStatus() },
        feedbackEvents = viewModel.feedbackEvents,
        onNavigateToSearch = onNavigateToSearch,
        modifier = modifier
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onNavigateToCollections: () -> Unit,
    onNavigateToCollectionDetail: (Long) -> Unit,
    onNavigateToArticleDetail: (Long) -> Unit,
    onCreateCollection: (String) -> Unit,
    onRenameCollection: (Long, String) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onClearError: () -> Unit = {},
    onRetrySync: () -> Unit = {},
    onDismissSyncStatus: () -> Unit = {},
    feedbackEvents: Flow<String> = emptyFlow(),
    onNavigateToSearch: () -> Unit = {}
) {
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var collectionToRename by remember { mutableStateOf<com.example.erudito.domain.model.Collection?>(null) }
    var collectionToDelete by remember { mutableStateOf<com.example.erudito.domain.model.Collection?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackEvents) {
        var activeSnackbarJob: Job? = null
        feedbackEvents.collect { message ->
            activeSnackbarJob?.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
            activeSnackbarJob = launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            try {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(error)
            } finally {
                onClearError()
            }
        }
    }

    Scaffold(
        topBar = {
            EruditoTopAppBar(
                title = "Erudito",
                actions = {
                    val badgeContainerColor = if (uiState.isOnline) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                    val badgeContentColor = if (uiState.isOnline) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = badgeContainerColor,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("home_connectivity_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isOnline) {
                                    Icons.Default.CloudDone
                                } else {
                                    Icons.Default.CloudOff
                                },
                                contentDescription = null,
                                tint = badgeContentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.isOnline) "Online" else "Modo Offline",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = badgeContentColor
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCollections,
                icon = {
                    Icon(
                        imageVector = Icons.Default.CollectionsBookmark,
                        contentDescription = null
                    )
                },
                text = { Text("Gerenciar Coleções") },
                modifier = Modifier.testTag("manage_collections_fab")
            )
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Banner
                item(
                    key = "home_hero_banner",
                    contentType = "hero_banner"
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Biblioteca de Conhecimento",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text(
                                            text = "Acesso completo aos seus artigos salvos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    StatItem(
                                        count = uiState.totalCollectionsCount.toString(),
                                        label = "Coleções",
                                        onPrimaryColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                    StatItem(
                                        count = uiState.totalSavedArticlesCount.toString(),
                                        label = "Artigos Salvos",
                                        onPrimaryColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                    StatItem(
                                        count = "Offline",
                                        label = "Disponibilidade",
                                        onPrimaryColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Surface(
                                    onClick = onNavigateToSearch,
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("home_search_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Pesquisar na Wikipédia...",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (uiState.isOnline) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            modifier = Modifier.testTag("home_search_connectivity_badge")
                                        ) {
                                            Text(
                                                text = if (uiState.isOnline) "Online" else "Offline",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = if (uiState.isOnline) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.syncStatus !is HomeSyncStatus.Idle) {
                    item(
                        key = "home_sync_status_item",
                        contentType = "sync_status"
                    ) {
                        HomeSyncStatusBanner(
                            syncStatus = uiState.syncStatus,
                            onRetry = onRetrySync,
                            onDismiss = onDismissSyncStatus
                        )
                    }
                }

                // Seção Coleções
                item(
                    key = "home_collections_header",
                    contentType = "section_header"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Coleções Personalizadas",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = onNavigateToCollections,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("view_all_collections_button")
                        ) {
                            Text("Ver todas (${uiState.collections.size})")
                        }
                    }
                }

                if (uiState.collections.isEmpty()) {
                    item(
                        key = "empty_state_collections",
                        contentType = "empty_state"
                    ) {
                        EmptyStateView(
                            icon = Icons.Default.Folder,
                            title = "Nenhuma coleção criada",
                            description = "Crie sua primeira coleção para organizar artigos por temas de estudo.",
                            actionButtonText = "Criar Coleção",
                            onActionClick = { isCreateDialogOpen = true }
                        )
                    }
                } else {
                    items(
                        items = uiState.collections.take(3),
                        key = { "collection_${it.id}" },
                        contentType = { "collection_card" }
                    ) { collection ->
                        CollectionCard(
                            collection = collection,
                            onClick = { onNavigateToCollectionDetail(collection.id) },
                            onRenameClick = { collectionToRename = collection },
                            onDeleteClick = { collectionToDelete = collection }
                        )
                    }
                }

                // Seção Artigos Salvos na Biblioteca
                item(
                    key = "section_recent_articles_header",
                    contentType = "section_header"
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Artigos Armazenados Localmente",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (uiState.recentArticles.isEmpty()) {
                    item(
                        key = "empty_state_recent_articles",
                        contentType = "empty_state"
                    ) {
                        EmptyStateView(
                            icon = Icons.Default.Book,
                            title = "Nenhum artigo salvo",
                            description = "Seus artigos salvos estarão disponíveis offline para leitura a qualquer momento."
                        )
                    }
                } else {
                    items(
                        items = uiState.recentArticles,
                        key = { "article_${it.id}" },
                        contentType = { "article_card" }
                    ) { article ->
                        ArticleCard(
                            article = article,
                            onClick = { onNavigateToArticleDetail(article.id) }
                        )
                    }
                }

                // Bottom spacer for FAB clearance
                item(
                    key = "home_bottom_spacer",
                    contentType = "spacer"
                ) {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    if (isCreateDialogOpen) {
        CreateEditCollectionDialog(
            initialName = "",
            isEditing = false,
            onDismiss = { isCreateDialogOpen = false },
            onConfirm = { name ->
                isCreateDialogOpen = false
                onCreateCollection(name)
            }
        )
    }

    collectionToRename?.let { col ->
        CreateEditCollectionDialog(
            initialName = col.name,
            isEditing = true,
            onDismiss = { collectionToRename = null },
            onConfirm = { newName ->
                collectionToRename = null
                onRenameCollection(col.id, newName)
            }
        )
    }

    collectionToDelete?.let { col ->
        DeleteConfirmationDialog(
            collectionName = col.name,
            onDismiss = { collectionToDelete = null },
            onConfirm = {
                collectionToDelete = null
                onDeleteCollection(col.id)
            }
        )
    }
}

@Composable
private fun HomeSyncStatusBanner(
    syncStatus: HomeSyncStatus,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val containerColor = when (syncStatus) {
        HomeSyncStatus.Syncing -> MaterialTheme.colorScheme.primaryContainer
        is HomeSyncStatus.CompletedWithUpdates -> MaterialTheme.colorScheme.secondaryContainer
        is HomeSyncStatus.CompletedNoChanges -> MaterialTheme.colorScheme.surfaceVariant
        is HomeSyncStatus.PartialFailure -> MaterialTheme.colorScheme.tertiaryContainer
        is HomeSyncStatus.Failed -> MaterialTheme.colorScheme.errorContainer
        HomeSyncStatus.Offline -> MaterialTheme.colorScheme.surfaceVariant
        HomeSyncStatus.Idle -> return
    }
    val contentColor = when (syncStatus) {
        HomeSyncStatus.Syncing -> MaterialTheme.colorScheme.onPrimaryContainer
        is HomeSyncStatus.CompletedWithUpdates -> MaterialTheme.colorScheme.onSecondaryContainer
        is HomeSyncStatus.CompletedNoChanges -> MaterialTheme.colorScheme.onSurfaceVariant
        is HomeSyncStatus.PartialFailure -> MaterialTheme.colorScheme.onTertiaryContainer
        is HomeSyncStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
        HomeSyncStatus.Offline -> MaterialTheme.colorScheme.onSurfaceVariant
        HomeSyncStatus.Idle -> return
    }

    val message = when (syncStatus) {
        HomeSyncStatus.Syncing -> "Sincronizando artigos salvos..."
        is HomeSyncStatus.CompletedWithUpdates -> {
            if (syncStatus.updatedCount == 1) {
                "Sincronização concluída: 1 artigo atualizado."
            } else {
                "Sincronização concluída: ${syncStatus.updatedCount} artigos atualizados."
            }
        }
        is HomeSyncStatus.CompletedNoChanges ->
            "Artigos verificados: nenhuma alteração encontrada."
        is HomeSyncStatus.PartialFailure -> {
            if (syncStatus.updatedCount > 0) {
                "Sincronização parcial: ${syncStatus.updatedCount} atualizado(s) e ${syncStatus.failedCount} falha(s)."
            } else {
                "Sincronização parcial: ${syncStatus.unchangedCount} verificado(s) e ${syncStatus.failedCount} falha(s)."
            }
        }
        is HomeSyncStatus.Failed ->
            "Falha ao sincronizar os artigos salvos."
        HomeSyncStatus.Offline ->
            "Dispositivo offline. Exibindo artigos salvos localmente."
        HomeSyncStatus.Idle -> ""
    }

    val showRetry = syncStatus is HomeSyncStatus.PartialFailure || syncStatus is HomeSyncStatus.Failed
    val showDismiss = syncStatus !is HomeSyncStatus.Syncing && syncStatus !is HomeSyncStatus.Offline

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_sync_status_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (syncStatus is HomeSyncStatus.Syncing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = contentColor,
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("home_sync_progress")
                    )
                } else {
                    val icon = when (syncStatus) {
                        is HomeSyncStatus.CompletedWithUpdates -> Icons.Default.CloudDone
                        is HomeSyncStatus.CompletedNoChanges -> Icons.Default.CheckCircle
                        is HomeSyncStatus.PartialFailure -> Icons.Default.Warning
                        is HomeSyncStatus.Failed -> Icons.Default.ErrorOutline
                        HomeSyncStatus.Offline -> Icons.Default.CloudOff
                        HomeSyncStatus.Syncing, HomeSyncStatus.Idle -> Icons.Default.CloudDone
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = contentColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_sync_status_text")
                )

                if (showDismiss) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_sync_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dispensar aviso de sincronização",
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showRetry) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("home_sync_retry_button")
                    ) {
                        Text(
                            text = "Tentar novamente",
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    count: String,
    label: String,
    onPrimaryColor: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = onPrimaryColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = onPrimaryColor.copy(alpha = 0.8f)
        )
    }
}
