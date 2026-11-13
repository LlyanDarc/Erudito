package com.example.erudito.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.erudito.presentation.article_detail.ArticleDetailScreen
import com.example.erudito.presentation.article_detail.ArticleDetailViewModel
import com.example.erudito.presentation.collection_detail.CollectionDetailScreen
import com.example.erudito.presentation.collection_detail.CollectionDetailViewModel
import com.example.erudito.presentation.collections.CollectionsScreen
import com.example.erudito.presentation.collections.CollectionsViewModel
import com.example.erudito.presentation.home.HomeScreen
import com.example.erudito.presentation.home.HomeViewModel
import com.example.erudito.presentation.search.SearchScreen
import com.example.erudito.presentation.search.SearchViewModel
import com.example.erudito.presentation.wikipedia_article.WikipediaArticleScreen
import com.example.erudito.presentation.wikipedia_article.WikipediaArticleViewModel

private const val COLLECTION_FEEDBACK_RESULT_KEY = "collection_feedback_result"

@Composable
fun EruditoNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    homeViewModelProvider: @Composable (NavBackStackEntry) -> HomeViewModel = { hiltViewModel() },
    collectionsViewModelProvider: @Composable (NavBackStackEntry) -> CollectionsViewModel = { hiltViewModel() },
    collectionDetailViewModelProvider: @Composable (NavBackStackEntry) -> CollectionDetailViewModel = { hiltViewModel() },
    articleDetailViewModelProvider: @Composable (NavBackStackEntry) -> ArticleDetailViewModel = { hiltViewModel() },
    searchViewModelProvider: @Composable (NavBackStackEntry) -> SearchViewModel = { hiltViewModel() },
    wikipediaArticleViewModelProvider: @Composable (NavBackStackEntry) -> WikipediaArticleViewModel = { hiltViewModel() }
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(route = Screen.Home.route) { backStackEntry ->
            val homeViewModel = homeViewModelProvider(backStackEntry)

            LaunchedEffect(backStackEntry) {
                backStackEntry.savedStateHandle
                    .getStateFlow<String?>(COLLECTION_FEEDBACK_RESULT_KEY, null)
                    .collect { message ->
                        if (message != null) {
                            backStackEntry.savedStateHandle.remove<String>(COLLECTION_FEEDBACK_RESULT_KEY)
                            homeViewModel.showFeedbackMessage(message)
                        }
                    }
            }

            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToCollections = {
                    navController.navigate(Screen.Collections.route)
                },
                onNavigateToCollectionDetail = { collectionId ->
                    navController.navigate(Screen.CollectionDetail.createRoute(collectionId))
                },
                onNavigateToArticleDetail = { articleId ->
                    navController.navigate(Screen.ArticleDetail.createRoute(articleId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(route = Screen.Collections.route) { backStackEntry ->
            val collectionsViewModel = collectionsViewModelProvider(backStackEntry)

            LaunchedEffect(backStackEntry) {
                backStackEntry.savedStateHandle
                    .getStateFlow<String?>(COLLECTION_FEEDBACK_RESULT_KEY, null)
                    .collect { message ->
                        if (message != null) {
                            backStackEntry.savedStateHandle.remove<String>(COLLECTION_FEEDBACK_RESULT_KEY)
                            collectionsViewModel.showFeedbackMessage(message)
                        }
                    }
            }

            CollectionsScreen(
                viewModel = collectionsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCollectionDetail = { collectionId ->
                    navController.navigate(Screen.CollectionDetail.createRoute(collectionId))
                }
            )
        }

        composable(
            route = Screen.CollectionDetail.route,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val collectionDetailViewModel = collectionDetailViewModelProvider(backStackEntry)

            CollectionDetailScreen(
                viewModel = collectionDetailViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onCollectionDeleted = { deletedMessage ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(COLLECTION_FEEDBACK_RESULT_KEY, deletedMessage)
                    navController.popBackStack()
                },
                onNavigateToArticleDetail = { articleId ->
                    navController.navigate(Screen.ArticleDetail.createRoute(articleId))
                }
            )
        }

        composable(
            route = Screen.ArticleDetail.route,
            arguments = listOf(
                navArgument("articleId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val articleDetailViewModel = articleDetailViewModelProvider(backStackEntry)

            ArticleDetailScreen(
                viewModel = articleDetailViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(route = Screen.Search.route) { backStackEntry ->
            val searchViewModel = searchViewModelProvider(backStackEntry)

            SearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onArticleSelected = { pageId ->
                    navController.navigate(Screen.WikipediaArticle.createRoute(pageId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.WikipediaArticle.route,
            arguments = listOf(
                navArgument(WikipediaArticleViewModel.ARG_PAGE_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val wikipediaArticleViewModel = wikipediaArticleViewModelProvider(backStackEntry)

            WikipediaArticleScreen(
                viewModel = wikipediaArticleViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
