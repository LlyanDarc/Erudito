package com.example.erudito.presentation.article_detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.erudito.domain.html.StructuredArticleHtmlProcessor
import com.example.erudito.presentation.components.ArticleBodyContent
import com.example.erudito.presentation.components.CollectionPickerDialog
import com.example.erudito.presentation.components.EmptyStateView
import com.example.erudito.presentation.components.EruditoTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun ArticleDetailScreen(
    viewModel: ArticleDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    BackHandler {
        onNavigateBack()
    }

    ArticleDetailScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onOpenOriginalUrl = { url ->
            runCatching { uriHandler.openUri(url) }
        },
        onRefreshArticle = { viewModel.refreshArticle() },
        onOpenCollectionPicker = { viewModel.openCollectionPicker() },
        onDismissCollectionPicker = { viewModel.dismissCollectionPicker() },
        onToggleCollectionSelection = { viewModel.toggleCollectionSelection(it) },
        onCreateCollection = { viewModel.createCollection(it) },
        onConfirmCollectionSelection = { viewModel.confirmCollectionSelection() },
        feedbackEvents = viewModel.feedbackEvents,
        htmlProcessor = viewModel.htmlProcessor,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleDetailScreenContent(
    uiState: ArticleDetailUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenOriginalUrl: (String) -> Unit = {},
    onRefreshArticle: () -> Unit = {},
    onOpenCollectionPicker: () -> Unit = {},
    onDismissCollectionPicker: () -> Unit = {},
    onToggleCollectionSelection: (Long) -> Unit = {},
    onCreateCollection: (String) -> Unit = {},
    onConfirmCollectionSelection: () -> Unit = {},
    feedbackEvents: Flow<String> = emptyFlow(),
    htmlProcessor: StructuredArticleHtmlProcessor? = null
) {
    val scrollState = rememberScrollState()
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

    Scaffold(
        topBar = {
            EruditoTopAppBar(
                title = "Leitura Offline",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("article_detail_loading"),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.article == null) {
            EmptyStateView(
                icon = Icons.AutoMirrored.Filled.Article,
                title = "Artigo não encontrado",
                description = uiState.errorMessage ?: "Este artigo não está disponível na biblioteca local.",
                actionButtonText = "Voltar",
                onActionClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("article_detail_error")
            )
        } else {
            val article = uiState.article
            val hasImage = !article.imageUrl.isNullOrBlank()
            var imageLoadFailed by remember(article.imageUrl) { mutableStateOf(false) }

            val formattedDate = remember(article.lastUpdated) {
                val sdf = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
                sdf.format(Date(article.lastUpdated))
            }

            val formattedSyncDate = remember(article.lastSyncedAt) {
                article.lastSyncedAt?.let { syncedAt ->
                    val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
                    sdf.format(Date(syncedAt))
                }
            }

            val associatedCollectionNames = remember(
                uiState.availableCollections,
                uiState.savedCollectionIds
            ) {
                uiState.availableCollections
                    .filter { it.id in uiState.savedCollectionIds }
                    .map { it.name }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Badges de status offline e conectividade
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Salvo na Biblioteca",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Leitura Offline",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (uiState.isOnline) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            modifier = Modifier.testTag("article_detail_connectivity_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isOnline) {
                                        Icons.Default.CloudDone
                                    } else {
                                        Icons.Default.WifiOff
                                    },
                                    contentDescription = null,
                                    tint = if (uiState.isOnline) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isOnline) "Online" else "Sem conexão",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (uiState.isOnline) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }
                        }
                    }

                    if (hasImage) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .testTag("article_detail_image")
                        ) {
                            if (!imageLoadFailed) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(article.imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Imagem de ${article.title}",
                                    contentScale = ContentScale.Crop,
                                    onError = { imageLoadFailed = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Título
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("article_detail_title")
                    )

                    if (!article.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = article.description,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("article_detail_description")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Armazenado localmente em $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    if (formattedSyncDate != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Última sincronização em $formattedSyncDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("article_detail_last_synced_at")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val collectionsStatusText = when (uiState.savedCollectionsCount) {
                        0 -> "Nenhuma coleção associada"
                        1 -> "Salvo em 1 coleção"
                        else -> "Salvo em ${uiState.savedCollectionsCount} coleções"
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (uiState.isSavedInAnyCollection) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("article_detail_collections_status")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isSavedInAnyCollection) {
                                    Icons.Default.BookmarkAdded
                                } else {
                                    Icons.Default.CollectionsBookmark
                                },
                                contentDescription = null,
                                tint = if (uiState.isSavedInAnyCollection) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = collectionsStatusText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (uiState.isSavedInAnyCollection) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (associatedCollectionNames.isNotEmpty()) {
                                    Text(
                                        text = associatedCollectionNames.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FilledTonalButton(
                        onClick = onOpenCollectionPicker,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("article_detail_manage_collections_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gerenciar coleções")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (uiState.canRefreshRemotely) {
                        OutlinedButton(
                            onClick = onRefreshArticle,
                            enabled = !uiState.isRefreshing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("article_detail_refresh_button")
                        ) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .testTag("article_detail_refresh_loading")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Atualizando artigo...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Atualizar artigo")
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("article_detail_no_remote_update_notice")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Artigo local sem vínculo remoto — atualização pela Wikipédia indisponível.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(20.dp))

                    // Conteúdo do artigo
                    ArticleBodyContent(
                        content = article.content,
                        contentFormat = article.contentFormat,
                        unextractableFormulaCount = article.unextractableFormulaCount,
                        onOpenLink = onOpenOriginalUrl,
                        testTag = "article_detail_content",
                        emptyContentText = "Nenhum conteúdo salvo para este artigo.",
                        htmlProcessor = htmlProcessor
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Card de referência ao artigo original
                    val originalUrl = article.url.trim()
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("article_detail_url_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Referência Original",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = article.url,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            textDecoration = if (originalUrl.isNotEmpty()) {
                                                TextDecoration.Underline
                                            } else {
                                                TextDecoration.None
                                            }
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = originalUrl.isNotEmpty()) {
                                                onOpenOriginalUrl(originalUrl)
                                            }
                                            .padding(vertical = 4.dp)
                                            .testTag("article_detail_url_text")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = { onOpenOriginalUrl(originalUrl) },
                                enabled = originalUrl.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("article_detail_open_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Abrir na Wikipédia")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (uiState.isPickerVisible) {
                CollectionPickerDialog(
                    title = "Gerenciar coleções",
                    collections = uiState.availableCollections,
                    selectedCollectionIds = uiState.selectedCollectionIds,
                    initiallyAssociatedIds = uiState.savedCollectionIds,
                    onToggleCollection = onToggleCollectionSelection,
                    onCreateCollection = onCreateCollection,
                    onConfirm = onConfirmCollectionSelection,
                    onDismiss = onDismissCollectionPicker,
                    isLoadingCollections = uiState.isLoadingCollections,
                    isSaving = uiState.isSavingCollections,
                    isCreatingCollection = uiState.isCreatingCollection,
                    errorMessage = uiState.pickerErrorMessage,
                    articleTitle = article.title
                )
            }
        }
    }
}
