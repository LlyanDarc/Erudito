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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
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
    modifier: Modifier = Modifier
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
        feedbackEvents = viewModel.feedbackEvents,
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
    feedbackEvents: Flow<String> = emptyFlow()
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
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "100% Offline",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
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
                item {
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
                                                imageVector = Icons.Default.MenuBook,
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
                            }
                        }
                    }
                }

                // Seção Coleções
                item {
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
                            modifier = Modifier.testTag("view_all_collections_button")
                        ) {
                            Text("Ver todas (${uiState.collections.size})")
                        }
                    }
                }

                if (uiState.collections.isEmpty()) {
                    item {
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
                        key = { "collection_${it.id}" }
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
                item(key = "section_recent_articles_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Artigos Armazenados Localmente",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (uiState.recentArticles.isEmpty()) {
                    item(key = "empty_state_recent_articles") {
                        EmptyStateView(
                            icon = Icons.Default.Book,
                            title = "Nenhum artigo salvo",
                            description = "Seus artigos salvos estarão disponíveis offline para leitura a qualquer momento."
                        )
                    }
                } else {
                    items(
                        items = uiState.recentArticles,
                        key = { "article_${it.id}" }
                    ) { article ->
                        ArticleCard(
                            article = article,
                            onClick = { onNavigateToArticleDetail(article.id) }
                        )
                    }
                }

                // Bottom spacer for FAB clearance
                item {
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
