package com.example.erudito.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.erudito.domain.model.Collection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CollectionPickerDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays loading state and disables confirm button while loading collections`() {
        composeTestRule.setContent {
            CollectionPickerDialog(
                collections = emptyList(),
                selectedCollectionIds = emptySet(),
                initiallyAssociatedIds = emptySet(),
                onToggleCollection = {},
                onCreateCollection = {},
                onConfirm = {},
                onDismiss = {},
                isLoadingCollections = true
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("collection_picker_loading").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carregando coleções...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("collection_picker_confirm_button").assertIsNotEnabled()
    }

    @Test
    fun `displays empty state and allows creating a collection when none exist`() {
        var createdName: String? = null

        composeTestRule.setContent {
            CollectionPickerDialog(
                collections = emptyList(),
                selectedCollectionIds = emptySet(),
                initiallyAssociatedIds = emptySet(),
                onToggleCollection = {},
                onCreateCollection = { createdName = it },
                onConfirm = {},
                onDismiss = {},
                isLoadingCollections = false
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("collection_picker_empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nenhuma coleção criada").assertIsDisplayed()
        composeTestRule.onNodeWithTag("collection_picker_confirm_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("collection_picker_new_collection_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("collection_name_input").performTextInput("Astronomia")
        composeTestRule.onNodeWithTag("confirm_collection_dialog_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Astronomia", createdName)
    }

    @Test
    fun `preselects associated collections shows badge and supports multi-selection and confirmation`() {
        var confirmedSelection: Set<Long>? = null
        val collections = listOf(
            Collection(id = 1L, name = "Filosofia", articleCount = 2),
            Collection(id = 2L, name = "História", articleCount = 1),
            Collection(id = 3L, name = "Ciências", articleCount = 0)
        )

        composeTestRule.setContent {
            var selected by remember { mutableStateOf(setOf(1L)) }
            CollectionPickerDialog(
                collections = collections,
                selectedCollectionIds = selected,
                initiallyAssociatedIds = setOf(1L),
                onToggleCollection = { id ->
                    selected = if (id in selected) selected - id else selected + id
                },
                onCreateCollection = {},
                onConfirm = { confirmedSelection = selected },
                onDismiss = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("collection_picker_item_1").assertIsOn()
        composeTestRule.onNodeWithTag("collection_picker_item_2").assertIsOff()
        composeTestRule.onNodeWithTag("collection_picker_item_3").assertIsOff()
        composeTestRule.onNodeWithTag("collection_picker_checkbox_1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("collection_picker_saved_badge_1", useUnmergedTree = true).assertIsDisplayed()

        // Marca coleção 2 e 3
        composeTestRule.onNodeWithTag("collection_picker_item_2").performClick()
        composeTestRule.onNodeWithTag("collection_picker_item_3").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("collection_picker_item_1").assertIsOn()
        composeTestRule.onNodeWithTag("collection_picker_item_2").assertIsOn()
        composeTestRule.onNodeWithTag("collection_picker_item_3").assertIsOn()

        composeTestRule.onNodeWithTag("collection_picker_confirm_button")
            .assertIsEnabled()
            .performClick()

        assertEquals(setOf(1L, 2L, 3L), confirmedSelection)
    }

    @Test
    fun `allows unchecking all previously associated collections to remove links and supports cancel`() {
        var dismissed = false
        var confirmed = false
        val collections = listOf(
            Collection(id = 1L, name = "Filosofia", articleCount = 1)
        )

        composeTestRule.setContent {
            var selected by remember { mutableStateOf(setOf(1L)) }
            CollectionPickerDialog(
                collections = collections,
                selectedCollectionIds = selected,
                initiallyAssociatedIds = setOf(1L),
                onToggleCollection = { id ->
                    selected = if (id in selected) selected - id else selected + id
                },
                onCreateCollection = {},
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.waitForIdle()
        // Desmarca a única coleção já associada: Salvar continua habilitado para permitir remoção
        composeTestRule.onNodeWithTag("collection_picker_item_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("collection_picker_item_1").assertIsOff()
        composeTestRule.onNodeWithTag("collection_picker_confirm_button").assertIsEnabled()

        composeTestRule.onNodeWithTag("collection_picker_cancel_button").performClick()
        assertTrue(dismissed)
        assertTrue(!confirmed)
    }

    @Test
    fun `displays error message and saving indicator when saving`() {
        val collections = listOf(Collection(id = 1L, name = "Filosofia", articleCount = 1))

        composeTestRule.setContent {
            CollectionPickerDialog(
                collections = collections,
                selectedCollectionIds = setOf(1L),
                initiallyAssociatedIds = emptySet(),
                onToggleCollection = {},
                onCreateCollection = {},
                onConfirm = {},
                onDismiss = {},
                isSaving = true,
                errorMessage = "Falha simulada ao salvar"
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("collection_picker_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Falha simulada ao salvar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Salvando...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("collection_picker_confirm_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("collection_picker_cancel_button").assertIsNotEnabled()
    }
}
