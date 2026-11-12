package com.example.erudito.presentation.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.Collection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeScreenDuplicateKeyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `HomeScreen renders without crashing when collections and articles share the same IDs`() {
        // Arrange: collection with ID = 3 and article with ID = 3 (as seeded in Room database)
        val testCollections = listOf(
            Collection(id = 1L, name = "Coleção 1", articleCount = 1, createdAt = System.currentTimeMillis()),
            Collection(id = 2L, name = "Coleção 2", articleCount = 1, createdAt = System.currentTimeMillis()),
            Collection(id = 3L, name = "Coleção 3", articleCount = 1, createdAt = System.currentTimeMillis())
        )
        val testArticles = listOf(
            Article(
                id = 1L,
                title = "Artigo 1",
                url = "https://example.com/1",
                description = "Desc 1",
                content = "Conteúdo 1",
                imageUrl = null,
                lastUpdated = System.currentTimeMillis()
            ),
            Article(
                id = 2L,
                title = "Artigo 2",
                url = "https://example.com/2",
                description = "Desc 2",
                content = "Conteúdo 2",
                imageUrl = null,
                lastUpdated = System.currentTimeMillis()
            ),
            Article(
                id = 3L,
                title = "Artigo 3",
                url = "https://example.com/3",
                description = "Desc 3",
                content = "Conteúdo 3",
                imageUrl = null,
                lastUpdated = System.currentTimeMillis()
            )
        )

        val state = HomeUiState(
            isLoading = false,
            collections = testCollections,
            recentArticles = testArticles,
            totalCollectionsCount = testCollections.size,
            totalSavedArticlesCount = testArticles.size
        )

        // Act: Compor o HomeScreenContent com o estado contendo IDs coincidentes (ID 3 compartilhado entre coleção e artigo)
        composeTestRule.setContent {
            HomeScreenContent(
                uiState = state,
                onNavigateToCollections = {},
                onNavigateToCollectionDetail = {},
                onNavigateToArticleDetail = {},
                onCreateCollection = {},
                onRenameCollection = { _, _ -> },
                onDeleteCollection = {}
            )
        }

        // Assert: A tela compõe perfeitamente sem IllegalArgumentException ("Key 3 was already used")
        composeTestRule.onNodeWithText("Biblioteca de Conhecimento", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Coleções Personalizadas", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Coleção 1", useUnmergedTree = true).assertExists()
    }
}
