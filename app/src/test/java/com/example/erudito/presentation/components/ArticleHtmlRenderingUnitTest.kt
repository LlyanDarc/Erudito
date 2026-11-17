package com.example.erudito.presentation.components

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlRenderingUnitTest {

    @Test
    fun `buildDocument uses exclusively local appassets URLs and strict CSP without unsafe-inline scripts`() {
        val html = "<p>Texto com <strong>negrito</strong> e fórmula <span class=\"erudito-math-inline\" data-tex=\"E = mc^2\"></span>.</p>"

        val docHtml = ArticleHtmlDocumentBuilder.buildDocument(
            sanitizedHtml = html,
            isDarkTheme = false
        )

        assertTrue(docHtml.contains("https://appassets.androidplatform.net/assets/katex/katex.min.css"))
        assertTrue(docHtml.contains("https://appassets.androidplatform.net/assets/erudito/article.css"))
        assertTrue(docHtml.contains("https://appassets.androidplatform.net/assets/katex/katex.min.js"))
        assertTrue(docHtml.contains("https://appassets.androidplatform.net/assets/erudito/math_renderer.js"))

        // Sem CDN ou scripts externos
        assertFalse(docHtml.contains("cdn.jsdelivr.net"))
        assertFalse(docHtml.contains("unpkg.com"))
        assertFalse(docHtml.contains("cdnjs.cloudflare.com"))

        // CSP estrita para script-src sem unsafe-inline ou unsafe-eval
        assertTrue(docHtml.contains("http-equiv=\"Content-Security-Policy\""))
        assertTrue(docHtml.contains("script-src https://appassets.androidplatform.net;"))
        assertFalse(docHtml.contains("script-src https://appassets.androidplatform.net 'unsafe-inline'"))
        assertFalse(docHtml.contains("'unsafe-eval'"))
    }

    @Test
    fun `buildDocument preserves headings paragraphs lists tables links math and unavailable placeholders`() {
        val richHtml = """
            <h2>Seção Principal</h2>
            <p>Parágrafo com <a href="https://pt.wikipedia.org/wiki/%C3%81lgebra" rel="noopener noreferrer">Álgebra</a>, x<sup>2</sup> e H<sub>2</sub>O.</p>
            <ul><li>Item 1</li><li>Item 2</li></ul>
            <table>
              <thead><tr><th scope="col">Conjunto</th><th scope="col">Símbolo</th></tr></thead>
              <tbody><tr><td>Reais</td><td><span class="erudito-math-inline" data-tex="\R"></span></td></tr></tbody>
            </table>
            <div class="erudito-math-block" data-tex="\begin{aligned} a &= b + c \\ d &= e \end{aligned}"></div>
            <span class="erudito-math-unavailable" data-math-status="unavailable">[Fórmula indisponível]</span>
        """.trimIndent()

        val docHtml = ArticleHtmlDocumentBuilder.buildDocument(
            sanitizedHtml = richHtml,
            isDarkTheme = true
        )
        val parsed = Jsoup.parse(docHtml)

        assertTrue(parsed.body().hasClass("erudito-dark"))
        assertEquals("dark", parsed.body().attr("data-theme"))
        assertEquals("Seção Principal", parsed.selectFirst("h2")?.text())
        assertEquals(2, parsed.select("ul > li").size)
        assertEquals(1, parsed.select("table").size)
        assertEquals("https://pt.wikipedia.org/wiki/%C3%81lgebra", parsed.selectFirst("a")?.attr("href"))
        assertEquals("\\R", parsed.selectFirst(".erudito-math-inline")?.attr("data-tex"))
        assertEquals(
            "\\begin{aligned} a &= b + c \\\\ d &= e \\end{aligned}",
            parsed.selectFirst(".erudito-math-block")?.attr("data-tex")
        )
        assertEquals("[Fórmula indisponível]", parsed.selectFirst(".erudito-math-unavailable")?.text())
    }

    @Test
    fun `sanitizeFragmentDefensively strips executable tags event handlers and javascript URLs`() {
        val maliciousFragment = """
            <p onclick="alert(1)">Texto legítimo</p>
            <script>window.evil = true;</script>
            <iframe src="https://evil.example/frame"></iframe>
            <a href="javascript:alert(2)">Link malicioso</a>
            <a href="https://pt.wikipedia.org/wiki/F%C3%ADsica">Link válido</a>
            <img src="http://insecure.example/a.png" alt="http">
            <img src="https://upload.wikimedia.org/b.png" alt="https">
            <span class="erudito-math-inline" data-tex="x \in \Z" onmouseover="hack()"></span>
        """.trimIndent()

        val cleaned = ArticleHtmlDocumentBuilder.sanitizeFragmentDefensively(maliciousFragment)
        val parsed = Jsoup.parseBodyFragment(cleaned)

        assertTrue(parsed.select("script, iframe").isEmpty())
        assertFalse(cleaned.contains("onclick"))
        assertFalse(cleaned.contains("onmouseover"))
        assertFalse(cleaned.contains("javascript:"))
        assertEquals(1, parsed.select("a").size)
        assertEquals("https://pt.wikipedia.org/wiki/F%C3%ADsica", parsed.selectFirst("a")?.attr("href"))
        assertEquals(1, parsed.select("img").size)
        assertEquals("https://upload.wikimedia.org/b.png", parsed.selectFirst("img")?.attr("src"))
        assertEquals("x \\in \\Z", parsed.selectFirst(".erudito-math-inline")?.attr("data-tex"))
    }

    @Test
    fun `security policy restricts external navigation and local or HTTPS subresources`() {
        assertTrue(StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl("https://pt.wikipedia.org/wiki/Matem%C3%A1tica"))
        assertTrue(StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl("http://pt.wikipedia.org/wiki/Matem%C3%A1tica"))
        assertFalse(StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl("javascript:alert(1)"))
        assertFalse(StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl("file:///data/data/com.example/files/secret"))
        assertFalse(StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl("content://media/external/images/1"))
        assertFalse(StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl("intent://scan/#Intent;end"))
        assertFalse(StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl("https://appassets.androidplatform.net/assets/katex/katex.min.js"))
        assertFalse(StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl(""))

        assertTrue(
            StructuredArticleSecurityPolicy.isLocalAssetUri(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = "/assets/katex/katex.min.js"
            )
        )
        assertFalse(
            StructuredArticleSecurityPolicy.isLocalAssetUri(
                scheme = "file",
                host = null,
                path = "/android_asset/katex/katex.min.js"
            )
        )
        assertTrue(
            StructuredArticleSecurityPolicy.isAllowedSubresourceUri(
                scheme = "https",
                host = "upload.wikimedia.org",
                path = "/wikipedia/commons/a.png"
            )
        )
        assertFalse(
            StructuredArticleSecurityPolicy.isAllowedSubresourceUri(
                scheme = "http",
                host = "insecure.example.com",
                path = "/a.png"
            )
        )
        assertFalse(
            StructuredArticleSecurityPolicy.isAllowedSubresourceUri(
                scheme = "file",
                host = "",
                path = "/etc/passwd"
            )
        )
        assertTrue(
            StructuredArticleSecurityPolicy.isLocalDocumentUri(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = "/article/doc_1.html"
            )
        )
        assertTrue(
            StructuredArticleSecurityPolicy.isLocalFaviconUri(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = "/favicon.ico"
            )
        )
        assertFalse(
            StructuredArticleSecurityPolicy.isAllowedSubresourceUri(
                scheme = "https",
                host = "appassets.androidplatform.net",
                path = "/unknown/resource.js"
            )
        )
    }

    @Test
    fun `progressive documents 1 to 5 produce valid CSP-compliant documents including complex Parsoid HTML`() {
        val sanitizer = com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer()

        val doc1Minimal = ArticleHtmlDocumentBuilder.buildDocument(
            sanitizedHtml = "<p>Teste simples</p>",
            isDarkTheme = false
        )
        assertTrue(doc1Minimal.contains("<p>Teste simples</p>"))
        assertTrue(doc1Minimal.contains(ArticleHtmlDocumentBuilder.CONTENT_SECURITY_POLICY))

        val doc2Css = ArticleHtmlDocumentBuilder.buildDocument(
            sanitizedHtml = "<h2>Título</h2><p>Parágrafo com <strong>negrito</strong>.</p>",
            isDarkTheme = true
        )
        assertTrue(doc2Css.contains(ArticleHtmlDocumentBuilder.ARTICLE_CSS_URL))
        assertTrue(doc2Css.contains(ArticleHtmlDocumentBuilder.KATEX_CSS_URL))
        assertTrue(doc2Css.contains("erudito-dark"))

        val doc3Js = ArticleHtmlDocumentBuilder.buildDocument(
            sanitizedHtml = "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>",
            isDarkTheme = false
        )
        assertTrue(doc3Js.contains(ArticleHtmlDocumentBuilder.MATH_RENDERER_JS_URL))
        assertTrue(doc3Js.contains("<table>"))

        val doc4Katex = ArticleHtmlDocumentBuilder.buildDocument(
            sanitizedHtml = """
                <p>Energia: <span class="erudito-math-inline" data-tex="E = mc^2"></span></p>
                <div class="erudito-math-block" data-tex="\int_0^1 x^2\,dx = \frac{1}{3}"></div>
            """.trimIndent(),
            isDarkTheme = false
        )
        assertTrue(doc4Katex.contains(ArticleHtmlDocumentBuilder.KATEX_JS_URL))
        assertTrue(doc4Katex.contains("data-tex=\"E = mc^2\""))

        val rawParsoidSample = """
            <section>
              <h2>Relatividade geral</h2>
              <p>Veja <a href="./Espa%C3%A7o-tempo#Curvatura">curvatura</a> e a equação de Einstein
              <math class="mwe-math-element" typeof="mw:Extension/math" data-mw='{"name":"math","attrs":{},"body":{"extsrc":"G_{\\mu\\nu} + \\Lambda g_{\\mu\\nu} = \\frac{8\\pi G}{c^4} T_{\\mu\\nu}"}}'></math>.</p>
              <dl><dd><span typeof="mw:Extension/math" data-mw='{"name":"math","attrs":{"display":"block"},"body":{"extsrc":"ds^2 = g_{\\mu\\nu} dx^\\mu dx^\\nu"}}'></span></dd></dl>
              <figure><img src="//thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Test.png" alt="Teste"/></figure>
              <div class="navbox">Ruído editorial</div>
            </section>
        """.trimIndent()
        val sanitized = sanitizer.sanitizeWithMetadata(rawParsoidSample)
        assertEquals(2, sanitized.extractedFormulaCount)
        assertEquals(0, sanitized.unextractableFormulaCount)
        assertFalse(sanitized.html.contains("Ruído editorial"))
        assertTrue(sanitized.html.contains("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Test.png"))

        val doc5Parsoid = ArticleHtmlDocumentBuilder.buildDocument(
            sanitizedHtml = sanitized.html,
            isDarkTheme = false
        )
        assertTrue(doc5Parsoid.contains("https://pt.wikipedia.org/wiki/Espa%C3%A7o-tempo#Curvatura"))
        assertTrue(doc5Parsoid.contains("erudito-math-inline"))
        assertTrue(doc5Parsoid.contains("erudito-math-block"))
        assertTrue(doc5Parsoid.contains("https://thumb.wikimedia.org/"))
    }
}
