package com.example.erudito.domain.repository

import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCollectionRepository : CollectionRepository {
    private val collections = mutableListOf<Collection>()
    private val collectionsFlow = MutableStateFlow<List<Collection>>(emptyList())
    private val collectionsWithArticlesMap = mutableMapOf<Long, CollectionWithArticles>()

    override fun getCollections(): Flow<List<Collection>> = collectionsFlow

    override fun getCollectionWithArticles(collectionId: Long): Flow<CollectionWithArticles?> {
        return collectionsFlow.map {
            collectionsWithArticlesMap[collectionId]
        }
    }

    override suspend fun getCollectionById(collectionId: Long): Collection? {
        return collections.find { it.id == collectionId }
    }

    override suspend fun createCollection(name: String): Long {
        val newId = (collections.maxOfOrNull { it.id } ?: 0L) + 1L
        val newCollection = Collection(id = newId, name = name, articleCount = 0)
        collections.add(newCollection)
        collectionsFlow.value = collections.toList()
        return newId
    }

    override suspend fun renameCollection(id: Long, newName: String) {
        val index = collections.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = collections[index].copy(name = newName)
            collections[index] = updated
            collectionsFlow.value = collections.toList()
        }
    }

    override suspend fun deleteCollection(id: Long) {
        collections.removeAll { it.id == id }
        collectionsWithArticlesMap.remove(id)
        collectionsFlow.value = collections.toList()
    }

    fun setCollectionWithArticles(collectionId: Long, data: CollectionWithArticles) {
        collectionsWithArticlesMap[collectionId] = data
    }
}
