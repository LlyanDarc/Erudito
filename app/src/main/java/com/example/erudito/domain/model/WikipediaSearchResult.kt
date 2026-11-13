package com.example.erudito.domain.model

data class WikipediaSearchResult(
    val pageId: Long,
    val title: String,
    val description: String? = null,
    val snippet: String? = null,
    val thumbnailUrl: String? = null,
    val url: String
)
