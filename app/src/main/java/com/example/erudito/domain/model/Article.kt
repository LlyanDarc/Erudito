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
    val lastSyncedAt: Long? = null,
    val contentFormat: ArticleContentFormat = ArticleContentFormat.PLAIN_TEXT,
    val extractedFormulaCount: Int = 0,
    val unextractableFormulaCount: Int = 0
) {
    val hasUsableContent: Boolean
        get() = !content.isNullOrBlank()

    val hasUnextractableFormulas: Boolean
        get() = unextractableFormulaCount > 0

    val isContentIntegrityComplete: Boolean
        get() = hasUsableContent && !hasUnextractableFormulas

    val isStructuredHtmlComplete: Boolean
        get() = contentFormat == ArticleContentFormat.STRUCTURED_HTML && isContentIntegrityComplete
}
