package com.example.erudito.domain.repository

import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaSearchResult

interface WikipediaRepository {
    suspend fun searchArticles(query: String): List<WikipediaSearchResult>
    suspend fun getArticleByPageId(pageId: Long): WikipediaArticle?
}
