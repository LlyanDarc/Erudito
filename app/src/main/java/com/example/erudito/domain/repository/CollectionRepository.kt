package com.example.erudito.domain.repository

import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    fun getCollections(): Flow<List<Collection>>
    fun getCollectionWithArticles(collectionId: Long): Flow<CollectionWithArticles?>
    suspend fun getCollectionById(collectionId: Long): Collection?
    suspend fun createCollection(name: String): Long
    suspend fun renameCollection(id: Long, newName: String)
    suspend fun deleteCollection(id: Long)
}
