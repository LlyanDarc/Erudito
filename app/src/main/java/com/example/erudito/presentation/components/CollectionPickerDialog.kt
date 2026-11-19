package com.example.erudito.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.erudito.domain.model.Collection

@Composable
fun CollectionPickerDialog(
    collections: List<Collection>,
    selectedCollectionIds: Set<Long>,
    initiallyAssociatedIds: Set<Long>,
    onToggleCollection: (Long) -> Unit,
    onCreateCollection: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLoadingCollections: Boolean = false,
    isSaving: Boolean = false,
    isCreatingCollection: Boolean = false,
    errorMessage: String? = null,
    articleTitle: String? = null,
    title: String = "Salvar em coleções"
) {
    var showCreateCollectionDialog by remember { mutableStateOf(false) }

    val canConfirm = !isSaving &&
        !isLoadingCollections &&
        (selectedCollectionIds.isNotEmpty() || initiallyAssociatedIds.isNotEmpty())

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        modifier = modifier.testTag("collection_picker_dialog"),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!articleTitle.isNullOrBlank()) {
                    Text(
                        text = "Selecione onde deseja guardar \"$articleTitle\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                when {
                    isLoadingCollections -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("collection_picker_loading")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Carregando coleções...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    collections.isEmpty() -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collection_picker_empty")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Nenhuma coleção criada",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Crie sua primeira coleção para organizar seus artigos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .testTag("collection_picker_list")
                        ) {
                            items(
                                items = collections,
                                key = { it.id },
                                contentType = { "collection_picker_item" }
                            ) { collection ->
                                val isChecked = collection.id in selectedCollectionIds
                                val isPreviouslyAssociated = collection.id in initiallyAssociatedIds

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isChecked) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .toggleable(
                                            value = isChecked,
                                            enabled = !isSaving,
                                            role = Role.Checkbox,
                                            onValueChange = { onToggleCollection(collection.id) }
                                        )
                                        .testTag("collection_picker_item_${collection.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null,
                                            enabled = !isSaving,
                                            modifier = Modifier.testTag("collection_picker_checkbox_${collection.id}")
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = collection.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val articleLabel = if (collection.articleCount == 1) {
                                                    "1 artigo"
                                                } else {
                                                    "${collection.articleCount} artigos"
                                                }
                                                Text(
                                                    text = articleLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (isPreviouslyAssociated) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = "Já salvo",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.SemiBold
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                .testTag("collection_picker_saved_badge_${collection.id}")
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("collection_picker_error")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showCreateCollectionDialog = true },
                    enabled = !isSaving && !isCreatingCollection,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("collection_picker_new_collection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isCreatingCollection) "Criando coleção..." else "Nova coleção")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canConfirm,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("collection_picker_confirm_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salvando...")
                } else {
                    Text("Salvar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("collection_picker_cancel_button")
            ) {
                Text("Cancelar")
            }
        }
    )

    if (showCreateCollectionDialog) {
        CreateEditCollectionDialog(
            onDismiss = { showCreateCollectionDialog = false },
            onConfirm = { newName ->
                showCreateCollectionDialog = false
                onCreateCollection(newName)
            }
        )
    }
}
