package com.example.erudito.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.erudito.domain.model.ArticleContentFormat

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
    val wikipediaPageId: Long? = null,
    val lastSyncedAt: Long? = null,
    @ColumnInfo(defaultValue = "PLAIN_TEXT")
    val contentFormat: String = ArticleContentFormat.PLAIN_TEXT.name,
    @ColumnInfo(defaultValue = "0")
    val extractedFormulaCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val unextractableFormulaCount: Int = 0
)
