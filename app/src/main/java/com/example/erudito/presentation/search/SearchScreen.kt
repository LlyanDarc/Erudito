package com.example.erudito.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erudito.presentation.components.EmptyStateView
import com.example.erudito.presentation.components.EruditoTopAppBar
import com.example.erudito.presentation.components.SearchResultCard

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onArticleSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        onNavigateBack()
    }

    SearchScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onQueryChange = { viewModel.onQueryChange(it) },
        onSearch = { viewModel.search() },
        onRetry = { viewModel.retry() },
        onClearSearch = { viewModel.clearSearch() },
        onArticleSelected = onArticleSelected,
        modifier = modifier
    )
}

@Composable
fun SearchScreenContent(
    uiState: SearchUiState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onClearSearch: () -> Unit,
    onArticleSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            EruditoTopAppBar(
                title = "Pesquisar na Wikipédia",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = onQueryChange,
                        placeholder = {
                            Text("Buscar tema, biografia ou conceito...")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearSearch,
                                    modifier = Modifier.testTag("search_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpar pesquisa"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSearch()
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_query_input")
                    )

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onSearch()
                        },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("search_submit_button")
                    ) {
                        Text(
                            text = "Buscar",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !uiState.isOnline) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("search_offline_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sem conexão com a internet. A pesquisa online requer rede, mas seus artigos salvos permanecem disponíveis offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val content = uiState.contentState) {
                    SearchContentState.Initial -> {
                        EmptyStateView(
                            icon = Icons.Default.TravelExplore,
                            title = "Explore a Wikipédia",
                            description = "Digite um assunto, personalidade ou conceito acima para consultar artigos diretamente da Wikipédia em português.",
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("search_initial_state")
                        )
                    }

                    SearchContentState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                                .testTag("search_loading_state")
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Pesquisando na Wikipédia...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is SearchContentState.Success -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("search_results_list"),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "search_results_header") {
                                val countLabel = if (content.results.size == 1) {
                                    "1 resultado para \"${uiState.lastSearchedQuery}\""
                                } else {
                                    "${content.results.size} resultados para \"${uiState.lastSearchedQuery}\""
                                }
                                Text(
                                    text = countLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            items(
                                items = content.results,
                                key = { "wiki_${it.pageId}" }
                            ) { result ->
                                SearchResultCard(
                                    result = result,
                                    onClick = { onArticleSelected(result.pageId) }
                                )
                            }
                        }
                    }

                    is SearchContentState.Empty -> {
                        EmptyStateView(
                            icon = Icons.Default.SearchOff,
                            title = "Nenhum resultado encontrado",
                            description = "Não encontramos artigos na Wikipédia para \"${content.query}\". Tente usar outros termos ou verifique a ortografia.",
                            actionButtonText = "Limpar pesquisa",
                            onActionClick = onClearSearch,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("search_empty_state")
                        )
                    }

                    is SearchContentState.Error -> {
                        EmptyStateView(
                            icon = Icons.Default.WifiOff,
                            title = "Erro ao pesquisar",
                            description = content.message,
                            actionButtonText = "Tentar novamente",
                            onActionClick = onRetry,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("search_error_state")
                        )
                    }
                }
            }
        }
    }
}
