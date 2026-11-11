package com.example.erudito.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun CreateEditCollectionDialog(
    initialName: String = "",
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    val isBlank = name.trim().isEmpty()
    val errorMessage = if (hasAttemptedSubmit && isBlank) {
        "O nome da coleção não pode ser vazio ou composto apenas por espaços."
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Renomear Coleção" else "Nova Coleção",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = if (isEditing) {
                        "Digite o novo nome para esta coleção."
                    } else {
                        "Organize seus artigos criando uma coleção personalizada."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = { Text("Nome da coleção") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection_name_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    hasAttemptedSubmit = true
                    if (!isBlank) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_collection_dialog_button")
            ) {
                Text(if (isEditing) "Salvar" else "Criar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_collection_dialog_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}
