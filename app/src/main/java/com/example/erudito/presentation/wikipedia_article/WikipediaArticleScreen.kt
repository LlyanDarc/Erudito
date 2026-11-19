package com.example.erudito.presentation.wikipedia_article

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.erudito.domain.html.StructuredArticleHtmlProcessor
import com.example.erudito.presentation.components.ArticleBodyContent
import com.example.erudito.presentation.components.CollectionPickerDialog
import com.example.erudito.presentation.components.EmptyStateView
import com.example.erudito.presentation.components.EruditoTopAppBar
import com.example.erudito.presentation.components.StructuredArticleSecurityPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun WikipediaArticleScreen(
    viewModel: WikipediaArticleViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collectionsUiState by viewModel.collectionsUiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    BackHandler {
        onNavigateBack()
    }

    WikipediaArticleScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.retry() },
        onOpenOriginalUrl = { url ->
            runCatching { uriHandler.openUri(url) }
                .onFailure { viewModel.onOpenExternalLinkFailed() }
        },
        collectionsUiState = collectionsUiState,
        onOpenCollectionPicker = { viewModel.openCollectionPicker() },
        onDismissCollectionPicker = { viewModel.dismissCollectionPicker() },
        onToggleCollectionSelection = { viewModel.toggleCollectionSelection(it) },
        onCreateCollection = { viewModel.createCollection(it) },
        onConfirmCollectionSelection = { viewModel.confirmCollectionSelection() },
        onRefreshSavedCopy = { viewModel.refreshSavedCopy() },
        feedbackEvents = viewModel.feedbackEvents,
        htmlProcessor = viewModel.htmlProcessor,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WikipediaArticleScreenContent(
    uiState: WikipediaArticleUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenOriginalUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    collectionsUiState: WikipediaArticleCollectionsUiState = WikipediaArticleCollectionsUiState(
        isLoadingCollections = false
    ),
    onOpenCollectionPicker: () -> Unit = {},
    onDismissCollectionPicker: () -> Unit = {},
    onToggleCollectionSelection: (Long) -> Unit = {},
    onCreateCollection: (String) -> Unit = {},
    onConfirmCollectionSelection: () -> Unit = {},
    onRefreshSavedCopy: () -> Unit = {},
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

    val topBarTitle = if (uiState is WikipediaArticleUiState.Success && uiState.isLocalFallback) {
        "Leitura Local (Offline)"
    } else {
        "Leitura Online"
    }

    Scaffold(
        topBar = {
            EruditoTopAppBar(
                title = topBarTitle,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                WikipediaArticleUiState.Initial,
                WikipediaArticleUiState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                            .testTag("wikipedia_article_loading")
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Carregando artigo da Wikipédia...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is WikipediaArticleUiState.Error -> {
                    EmptyStateView(
                        icon = Icons.Default.WifiOff,
                        title = "Não foi possível carregar o artigo",
                        description = uiState.message,
                        actionButtonText = "Tentar novamente",
                        onActionClick = onRetry,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("wikipedia_article_error")
                    )
                }

                is WikipediaArticleUiState.Success -> {
                    val article = uiState.article
                    val safeImageUrl = article.imageUrl?.takeIf {
                        StructuredArticleSecurityPolicy.isAllowedRemoteImageUrl(it)
                    }
                    var imageLoadFailed by remember(safeImageUrl) { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (uiState.isLocalFallback) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    modifier = Modifier.testTag("wikipedia_article_source_badge")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isLocalFallback) {
                                                Icons.Default.CloudOff
                                            } else {
                                                Icons.Default.Public
                                            },
                                            contentDescription = null,
                                            tint = if (uiState.isLocalFallback) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (uiState.isLocalFallback) {
                                                "Cópia Local Offline"
                                            } else {
                                                "Consulta Online"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (uiState.isLocalFallback) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Wikipédia PT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                if (!collectionsUiState.isOnline && !uiState.isLocalFallback) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.testTag("wikipedia_article_connectivity_badge")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WifiOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Sem conexão",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            if (uiState.isLocalFallback) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("wikipedia_article_offline_fallback_banner")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Exibindo a versão salva localmente porque não foi possível carregar a página online.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        if (collectionsUiState.isOnline) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextButton(
                                                onClick = onRetry,
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("wikipedia_article_reload_online_button")
                                            ) {
                                                Text("Atualizar leitura online")
                                            }
                                        }
                                    }
                                }
                            }

                            if (safeImageUrl != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    tonalElevation = 1.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .testTag("wikipedia_article_image")
                                ) {
                                    if (!imageLoadFailed) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(safeImageUrl)
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

                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("wikipedia_article_title")
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
                                    modifier = Modifier.testTag("wikipedia_article_description")
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (collectionsUiState.isSaved) {
                                val savedLabel = if (collectionsUiState.savedCollectionsCount == 1) {
                                    "Salvo em 1 coleção"
                                } else {
                                    "Salvo em ${collectionsUiState.savedCollectionsCount} coleções"
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("wikipedia_article_saved_status")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkAdded,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = savedLabel,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = onOpenCollectionPicker,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("wikipedia_article_save_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdded,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gerenciar coleções")
                                }

                                if (collectionsUiState.canRefreshSavedCopy) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = onRefreshSavedCopy,
                                        enabled = !collectionsUiState.isRefreshingSavedCopy,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("wikipedia_article_refresh_saved_button")
                                    ) {
                                        if (collectionsUiState.isRefreshingSavedCopy) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .testTag("wikipedia_article_refresh_saved_loading")
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Atualizando cópia salva...")
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Atualizar cópia salva")
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onOpenCollectionPicker,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("wikipedia_article_save_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Salvar em coleções")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(20.dp))

                            ArticleBodyContent(
                                content = article.content,
                                contentFormat = article.contentFormat,
                                unextractableFormulaCount = article.unextractableFormulaCount,
                                onOpenLink = onOpenOriginalUrl,
                                testTag = "wikipedia_article_content",
                                emptyContentText = "Este artigo não possui conteúdo textual disponível para exibição.",
                                htmlProcessor = htmlProcessor
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wikipedia_article_url_card")
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
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = article.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.testTag("wikipedia_article_url_text")
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedButton(
                                        onClick = { onOpenOriginalUrl(article.url) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("wikipedia_article_open_url_button")
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

                    if (collectionsUiState.isPickerVisible) {
                        CollectionPickerDialog(
                            collections = collectionsUiState.availableCollections,
                            selectedCollectionIds = collectionsUiState.selectedCollectionIds,
                            initiallyAssociatedIds = collectionsUiState.savedCollectionIds,
                            onToggleCollection = onToggleCollectionSelection,
                            onCreateCollection = onCreateCollection,
                            onConfirm = onConfirmCollectionSelection,
                            onDismiss = onDismissCollectionPicker,
                            isLoadingCollections = collectionsUiState.isLoadingCollections,
                            isSaving = collectionsUiState.isSaving,
                            isCreatingCollection = collectionsUiState.isCreatingCollection,
                            errorMessage = collectionsUiState.pickerErrorMessage,
                            articleTitle = article.title
                        )
                    }
                }
            }
        }
    }
}
