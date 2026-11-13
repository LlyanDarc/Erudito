package com.example.erudito.di

import android.content.Context
import com.example.BuildConfig
import com.example.erudito.data.local.dao.ArticleDao
import com.example.erudito.data.local.dao.CollectionDao
import com.example.erudito.data.local.database.EruditoDatabase
import com.example.erudito.data.remote.api.WikipediaApiService
import com.example.erudito.data.repository.ArticleRepositoryImpl
import com.example.erudito.data.repository.CollectionRepositoryImpl
import com.example.erudito.data.repository.WikipediaRepositoryImpl
import com.example.erudito.domain.repository.ArticleRepository
import com.example.erudito.domain.repository.CollectionRepository
import com.example.erudito.domain.repository.WikipediaRepository
import com.example.erudito.domain.usecase.CreateCollectionUseCase
import com.example.erudito.domain.usecase.DeleteCollectionUseCase
import com.example.erudito.domain.usecase.GetArticleByIdUseCase
import com.example.erudito.domain.usecase.GetCollectionWithArticlesUseCase
import com.example.erudito.domain.usecase.GetCollectionsUseCase
import com.example.erudito.domain.usecase.GetSavedArticlesUseCase
import com.example.erudito.domain.usecase.GetWikipediaArticleUseCase
import com.example.erudito.domain.usecase.RenameCollectionUseCase
import com.example.erudito.domain.usecase.SearchWikipediaArticlesUseCase
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object EruditoHiltModule {

    internal const val WIKIPEDIA_BASE_URL = "https://pt.wikipedia.org/"
    internal val WIKIPEDIA_USER_AGENT =
        "Erudito/${BuildConfig.VERSION_NAME} (https://github.com/LlyanDarc/Erudito; 881881um@gmail.com) OkHttp/4.10.0"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder().build()
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestWithUserAgent = chain.request()
                    .newBuilder()
                    .header("User-Agent", WIKIPEDIA_USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(WIKIPEDIA_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideWikipediaApiService(retrofit: Retrofit): WikipediaApiService {
        return retrofit.create(WikipediaApiService::class.java)
    }

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

    @Provides
    @Singleton
    fun provideWikipediaRepository(apiService: WikipediaApiService): WikipediaRepository {
        return WikipediaRepositoryImpl(apiService)
    }

    @Provides
    fun provideSearchWikipediaArticlesUseCase(repository: WikipediaRepository): SearchWikipediaArticlesUseCase {
        return SearchWikipediaArticlesUseCase(repository)
    }

    @Provides
    fun provideGetWikipediaArticleUseCase(repository: WikipediaRepository): GetWikipediaArticleUseCase {
        return GetWikipediaArticleUseCase(repository)
    }
}
