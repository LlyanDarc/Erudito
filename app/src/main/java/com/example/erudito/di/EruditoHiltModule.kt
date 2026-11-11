package com.example.erudito.di

import android.content.Context
import com.example.erudito.data.local.dao.ArticleDao
import com.example.erudito.data.local.dao.CollectionDao
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EruditoHiltModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EruditoDatabase {
        return EruditoDatabase.getInstance(context)
    }

    @Provides
    fun provideCollectionDao(database: EruditoDatabase): CollectionDao {
        return database.collectionDao()
    }

    @Provides
    fun provideArticleDao(database: EruditoDatabase): ArticleDao {
        return database.articleDao()
    }

    @Provides
    @Singleton
    fun provideCollectionRepository(collectionDao: CollectionDao): CollectionRepository {
        return CollectionRepositoryImpl(collectionDao)
    }

    @Provides
    @Singleton
    fun provideArticleRepository(articleDao: ArticleDao): ArticleRepository {
        return ArticleRepositoryImpl(articleDao)
    }

    @Provides
    fun provideCreateCollectionUseCase(repository: CollectionRepository): CreateCollectionUseCase {
        return CreateCollectionUseCase(repository)
    }

    @Provides
    fun provideRenameCollectionUseCase(repository: CollectionRepository): RenameCollectionUseCase {
        return RenameCollectionUseCase(repository)
    }

    @Provides
    fun provideDeleteCollectionUseCase(repository: CollectionRepository): DeleteCollectionUseCase {
        return DeleteCollectionUseCase(repository)
    }

    @Provides
    fun provideGetCollectionsUseCase(repository: CollectionRepository): GetCollectionsUseCase {
        return GetCollectionsUseCase(repository)
    }

    @Provides
    fun provideGetCollectionWithArticlesUseCase(repository: CollectionRepository): GetCollectionWithArticlesUseCase {
        return GetCollectionWithArticlesUseCase(repository)
    }

    @Provides
    fun provideGetSavedArticlesUseCase(repository: ArticleRepository): GetSavedArticlesUseCase {
        return GetSavedArticlesUseCase(repository)
    }

    @Provides
    fun provideGetArticleByIdUseCase(repository: ArticleRepository): GetArticleByIdUseCase {
        return GetArticleByIdUseCase(repository)
    }
}
