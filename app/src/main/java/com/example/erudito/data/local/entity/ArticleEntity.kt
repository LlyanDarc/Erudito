package com.example.erudito.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["wikipediaPageId"], unique = true)
    ]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val description: String?,
    val content: String?,
    val imageUrl: String?,
    val lastUpdated: Long = System.currentTimeMillis(),
    val wikipediaPageId: Long? = null
)
