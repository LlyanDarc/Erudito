package com.example.erudito.domain.model

data class WikipediaArticle(
    val pageId: Long,
    val title: String,
    val description: String? = null,
    val content: String,
    val imageUrl: String? = null,
    val url: String,
    val hasExtractedContent: Boolean = content.isNotBlank(),
    val contentFormat: ArticleContentFormat = ArticleContentFormat.PLAIN_TEXT,
    val extractedFormulaCount: Int = 0,
    val unextractableFormulaCount: Int = 0
) {
    val hasUnextractableFormulas: Boolean
        get() = unextractableFormulaCount > 0

    val isContentIntegrityComplete: Boolean
        get() = hasExtractedContent && !hasUnextractableFormulas
}
