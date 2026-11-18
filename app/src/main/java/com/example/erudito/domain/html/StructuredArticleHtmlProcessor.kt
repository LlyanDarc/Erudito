package com.example.erudito.domain.html

interface StructuredArticleHtmlProcessor {
    fun sanitizeForDisplay(rawHtml: String): String
    fun extractPlainText(html: String): String
}
