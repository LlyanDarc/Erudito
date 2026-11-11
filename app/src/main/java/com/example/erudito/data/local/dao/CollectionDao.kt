package com.example.erudito.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.erudito.data.local.entity.CollectionEntity
import com.example.erudito.data.local.entity.CollectionWithArticlesEntity
import com.example.erudito.data.local.entity.CollectionWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query(
        """
        SELECT c.*, COUNT(cr.articleId) AS articleCount
        FROM collections c
        LEFT JOIN collection_article_cross_ref cr ON c.id = cr.collectionId
        GROUP BY c.id
        ORDER BY c.createdAt DESC
        """
    )
    fun getAllCollectionsWithCount(): Flow<List<CollectionWithCount>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollectionById(id: Long): CollectionEntity?

    @Transaction
    @Query("SELECT * FROM collections WHERE id = :id")
    fun getCollectionWithArticles(id: Long): Flow<CollectionWithArticlesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollectionById(id: Long)
}
