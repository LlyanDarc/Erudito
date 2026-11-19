package com.example.erudito.presentation.components

object ArticleHtmlDocumentBuilder {

    const val LOCAL_ASSETS_ORIGIN = "https://appassets.androidplatform.net"
    const val LOCAL_ASSETS_BASE_URL = "$LOCAL_ASSETS_ORIGIN/assets/"
    const val LOCAL_ARTICLE_BASE_URL =
        "$LOCAL_ASSETS_ORIGIN${StructuredArticleSecurityPolicy.ARTICLE_DOCUMENT_PATH_PREFIX}"

    const val KATEX_CSS_URL = "${LOCAL_ASSETS_BASE_URL}katex/katex.min.css"
    const val ARTICLE_CSS_URL = "${LOCAL_ASSETS_BASE_URL}erudito/article.css"
    const val KATEX_JS_URL = "${LOCAL_ASSETS_BASE_URL}katex/katex.min.js"
    const val MATH_RENDERER_JS_URL = "${LOCAL_ASSETS_BASE_URL}erudito/math_renderer.js"

    const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; " +
            "script-src $LOCAL_ASSETS_ORIGIN; " +
            "style-src $LOCAL_ASSETS_ORIGIN 'unsafe-inline'; " +
            "font-src $LOCAL_ASSETS_ORIGIN; " +
            "img-src https:; " +
            "object-src 'none'; " +
            "frame-src 'none'; " +
            "base-uri 'none'; " +
            "form-action 'none';"

    fun buildDocument(
        sanitizedHtml: String,
        isDarkTheme: Boolean
    ): String {
        val safeBody = sanitizedHtml.trim()
        val themeClass = if (isDarkTheme) "erudito-dark" else "erudito-light"
        val themeAttr = if (isDarkTheme) "dark" else "light"

        return buildString(safeBody.length + 1024) {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"pt-BR\">\n")
            append("<head>\n")
            append("  <meta charset=\"utf-8\">\n")
            append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n")
            append("  <meta http-equiv=\"Content-Security-Policy\" content=\"$CONTENT_SECURITY_POLICY\">\n")
            append("  <link rel=\"stylesheet\" href=\"$KATEX_CSS_URL\">\n")
            append("  <link rel=\"stylesheet\" href=\"$ARTICLE_CSS_URL\">\n")
            append("</head>\n")
            append("<body class=\"erudito-article $themeClass\" data-theme=\"$themeAttr\">\n")
            append("  <main class=\"erudito-content\">\n")
            append(safeBody)
            append("\n  </main>\n")
            append("  <script defer src=\"$KATEX_JS_URL\"></script>\n")
            append("  <script defer src=\"$MATH_RENDERER_JS_URL\"></script>\n")
            append("</body>\n")
            append("</html>")
        }
    }
}
