package com.example.erudito.domain.model

data class Article(
    val id: Long = 0,
    val title: String,
    val url: String,
    val description: String? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val wikipediaPageId: Long? = null,
    val lastSyncedAt: Long? = null
)
