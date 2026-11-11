package com.example.erudito.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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

@Composable
fun EruditoNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(route = Screen.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()

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
                }
            )
        }

        composable(route = Screen.Collections.route) {
            val collectionsViewModel: CollectionsViewModel = hiltViewModel()

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
        ) {
            val collectionDetailViewModel: CollectionDetailViewModel = hiltViewModel()

            CollectionDetailScreen(
                viewModel = collectionDetailViewModel,
                onNavigateBack = {
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
        ) {
            val articleDetailViewModel: ArticleDetailViewModel = hiltViewModel()

            ArticleDetailScreen(
                viewModel = articleDetailViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
