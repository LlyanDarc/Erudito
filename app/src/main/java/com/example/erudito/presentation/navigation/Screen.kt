package com.example.erudito.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Collections : Screen("collections")
    object CollectionDetail : Screen("collection/{collectionId}") {
        fun createRoute(collectionId: Long): String = "collection/$collectionId"
    }
    object ArticleDetail : Screen("article/{articleId}") {
        fun createRoute(articleId: Long): String = "article/$articleId"
    }
}
