package com.example.erudito.data.repository

import com.example.erudito.data.local.dao.CollectionDao
import com.example.erudito.data.local.entity.CollectionEntity
import com.example.erudito.data.mapper.toDomain
import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.model.CollectionWithArticles
import com.example.erudito.domain.repository.CollectionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CollectionRepositoryImpl @Inject constructor(
    private val collectionDao: CollectionDao
) : CollectionRepository {

    override fun getCollections(): Flow<List<Collection>> {
        return collectionDao.getAllCollectionsWithCount().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getCollectionWithArticles(collectionId: Long): Flow<CollectionWithArticles?> {
        return collectionDao.getCollectionWithArticles(collectionId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getCollectionById(collectionId: Long): Collection? {
        return collectionDao.getCollectionById(collectionId)?.toDomain()
    }

    override suspend fun createCollection(name: String): Long {
        val entity = CollectionEntity(name = name)
        return collectionDao.insertCollection(entity)
    }

    override suspend fun renameCollection(id: Long, newName: String) {
        val existing = collectionDao.getCollectionById(id)
        if (existing != null) {
            collectionDao.updateCollection(existing.copy(name = newName))
        }
    }

    override suspend fun deleteCollection(id: Long) {
        collectionDao.deleteCollectionById(id)
    }
}
