package com.example.erudito.presentation.components

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

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
            "img-src https: data:; " +
            "object-src 'none'; " +
            "frame-src 'none'; " +
            "base-uri 'none'; " +
            "form-action 'none';"

    private const val WIKIPEDIA_BASE_URL = "https://pt.wikipedia.org/wiki/"

    private val ALLOWED_MATH_AND_TYPOGRAPHY_CLASSES = setOf(
        "erudito-math-inline",
        "erudito-math-block",
        "erudito-math-unavailable",
        "texhtml",
        "sfrac"
    )

    private val DEFENSE_IN_DEPTH_SAFELIST: Safelist = Safelist()
        .addTags(
            "section", "h1", "h2", "h3", "h4",
            "p", "br", "hr",
            "b", "strong", "i", "em", "u",
            "sub", "sup",
            "blockquote", "code", "pre",
            "ul", "ol", "li",
            "dl", "dt", "dd",
            "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
            "figure", "figcaption",
            "span", "div",
            "a", "img"
        )
        .addAttributes("span", "class", "data-tex", "data-math-status")
        .addAttributes("div", "class", "data-tex", "data-math-status")
        .addAttributes("a", "href", "title", "rel")
        .addProtocols("a", "href", "http", "https")
        .addAttributes("img", "src", "alt", "width", "height")
        .addProtocols("img", "src", "https")
        .addAttributes("th", "colspan", "rowspan", "scope")
        .addAttributes("td", "colspan", "rowspan")
        .addAttributes("ol", "start", "type")
        .preserveRelativeLinks(false)

    fun sanitizeFragmentDefensively(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""
        val parsed = Jsoup.parseBodyFragment(rawHtml, WIKIPEDIA_BASE_URL)
        parsed.outputSettings()
            .prettyPrint(false)
            .charset(Charsets.UTF_8)

        parsed.body()
            .select("script, iframe, object, embed, applet, frame, frameset, style, link, noscript, meta, base, template")
            .remove()

        for (element in parsed.body().select("[class]")) {
            val keptClasses = element.classNames().filter { it in ALLOWED_MATH_AND_TYPOGRAPHY_CLASSES }
            if (keptClasses.isEmpty()) {
                element.removeAttr("class")
            } else {
                element.attr("class", keptClasses.joinToString(" "))
            }
        }

        val cleaned: Document = Cleaner(DEFENSE_IN_DEPTH_SAFELIST).clean(parsed)
        for (link in cleaned.body().select("a").toList()) {
            val href = link.attr("href").trim()
            if (!href.startsWith("https://", ignoreCase = true) &&
                !href.startsWith("http://", ignoreCase = true)
            ) {
                link.unwrap()
            }
        }
        for (image in cleaned.body().select("img").toList()) {
            val src = image.attr("src").trim()
            if (!src.startsWith("https://", ignoreCase = true)) {
                image.remove()
            }
        }
        cleaned.outputSettings()
            .prettyPrint(false)
            .charset(Charsets.UTF_8)
        return cleaned.body().html().trim()
    }

    fun buildDocument(
        sanitizedHtml: String,
        isDarkTheme: Boolean
    ): String {
        val safeBody = sanitizeFragmentDefensively(sanitizedHtml)
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
