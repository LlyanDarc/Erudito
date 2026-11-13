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
    object Search : Screen("search")
    object WikipediaArticle : Screen("wikipedia_article/{pageId}") {
        fun createRoute(pageId: Long): String = "wikipedia_article/$pageId"
    }
}
