package com.example.erudito.domain.model

data class WikipediaArticle(
    val pageId: Long,
    val title: String,
    val description: String? = null,
    val content: String,
    val imageUrl: String? = null,
    val url: String
)
