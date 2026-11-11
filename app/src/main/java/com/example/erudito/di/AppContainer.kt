package com.example.erudito.di

import android.content.Context
import com.example.erudito.data.local.database.EruditoDatabase
import com.example.erudito.data.repository.ArticleRepositoryImpl
import com.example.erudito.data.repository.CollectionRepositoryImpl
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.CollectionRepository
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetArticleByIdUseCase
import com.example.erudito.domain.usecase.GetCollectionWithArticlesUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase

/**
 * Contêiner de Injeção de Dependências seguindo a Clean Architecture e as boas
 * práticas de injeção manual recomendadas pelo Android.
 */
interface AppContainer {
    val collectionRepository: CollectionRepository
    val articleRepository: ArticleRepository

    val createCollectionUseCase: CreateCollectionUseCase
    val renameCollectionUseCase: RenameCollectionUseCase
    val deleteCollectionUseCase: DeleteCollectionUseCase
    val getCollectionsUseCase: GetCollectionsUseCase
    val getCollectionWithArticlesUseCase: GetCollectionWithArticlesUseCase
    val getSavedArticlesUseCase: GetSavedArticlesUseCase
    val getArticleByIdUseCase: GetArticleByIdUseCase
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database: EruditoDatabase by lazy {
        EruditoDatabase.getInstance(context)
    }

    override val collectionRepository: CollectionRepository by lazy {
        CollectionRepositoryImpl(database.collectionDao())
    }

    override val articleRepository: ArticleRepository by lazy {
        ArticleRepositoryImpl(database.articleDao())
    }

    override val createCollectionUseCase: CreateCollectionUseCase by lazy {
        CreateCollectionUseCase(collectionRepository)
    }

    override val renameCollectionUseCase: RenameCollectionUseCase by lazy {
        RenameCollectionUseCase(collectionRepository)
    }

    override val deleteCollectionUseCase: DeleteCollectionUseCase by lazy {
        DeleteCollectionUseCase(collectionRepository)
    }

    override val getCollectionsUseCase: GetCollectionsUseCase by lazy {
        GetCollectionsUseCase(collectionRepository)
    }

    override val getCollectionWithArticlesUseCase: GetCollectionWithArticlesUseCase by lazy {
        GetCollectionWithArticlesUseCase(collectionRepository)
    }

    override val getSavedArticlesUseCase: GetSavedArticlesUseCase by lazy {
        GetSavedArticlesUseCase(articleRepository)
    }

    override val getArticleByIdUseCase: GetArticleByIdUseCase by lazy {
        GetArticleByIdUseCase(articleRepository)
    }
}
