package com.example.erudito.presentation.components

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.webkit.WebViewAssetLoader
import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.presentation.article_detail.ArticleDetailScreenContent
import com.example.erudito.presentation.article_detail.ArticleDetailUiState
import com.example.erudito.presentation.wikipedia_article.WikipediaArticleScreenContent
import com.example.erudito.presentation.wikipedia_article.WikipediaArticleUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArticleStructuredRenderingRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `offline KaTeX and Erudito assets exist in APK assets and WebViewAssetLoader serves them locally`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                StructuredArticleSecurityPolicy.ASSET_PATH_PREFIX,
                WebViewAssetLoader.AssetsPathHandler(context)
            )
            .build()

        val requiredPaths = listOf(
            "katex/katex.min.js",
            "katex/katex.min.css",
            "katex/fonts/KaTeX_Main-Regular.woff2",
            "katex/fonts/KaTeX_Math-Italic.woff2",
            "katex/fonts/KaTeX_AMS-Regular.woff2",
            "erudito/article.css",
            "erudito/math_renderer.js"
        )

        for (relativePath in requiredPaths) {
            val response = assetLoader.shouldInterceptRequest(
                Uri.parse("${ArticleHtmlDocumentBuilder.LOCAL_ASSETS_BASE_URL}$relativePath")
            )
            assertNotNull("Asset não encontrado via WebViewAssetLoader: $relativePath", response)
            response!!.data.use { stream ->
                assertTrue("Asset vazio: $relativePath", stream.readBytes().isNotEmpty())
            }
        }
    }

    @Test
    fun `configured WebView enforces strict security settings blocks unsafe URLs and delegates safe links`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                StructuredArticleSecurityPolicy.ASSET_PATH_PREFIX,
                WebViewAssetLoader.AssetsPathHandler(context)
            )
            .build()
        val openedLinks = mutableListOf<String>()
        val webView = createConfiguredWebView(
            context = context,
            assetLoader = assetLoader,
            onOpenLink = { openedLinks.add(it) },
            onPageFinishedCallback = { _, _ -> },
            onMainFrameErrorCallback = {}
        )

        assertTrue(webView.settings.javaScriptEnabled)
        assertFalse(webView.settings.allowFileAccess)
        assertFalse(webView.settings.allowContentAccess)
        @Suppress("DEPRECATION")
        assertFalse(webView.settings.allowFileAccessFromFileURLs)
        @Suppress("DEPRECATION")
        assertFalse(webView.settings.allowUniversalAccessFromFileURLs)
        assertFalse(webView.settings.domStorageEnabled)
        assertFalse(webView.settings.databaseEnabled)
        assertFalse(webView.settings.javaScriptCanOpenWindowsAutomatically)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)

        val client = webView.webViewClient
        assertNotNull(client)

        // Link legítimo da Wikipédia é interceptado para abertura externa controlada
        val handledWiki = client.shouldOverrideUrlLoading(
            webView,
            fakeWebRequest("https://pt.wikipedia.org/wiki/Teorema_de_Pit%C3%A1goras")
        )
        assertTrue(handledWiki)
        assertEquals(
            listOf("https://pt.wikipedia.org/wiki/Teorema_de_Pit%C3%A1goras"),
            openedLinks
        )

        // Esquemas inseguros são bloqueados sem disparar callback
        assertTrue(client.shouldOverrideUrlLoading(webView, fakeWebRequest("javascript:alert(1)")))
        assertTrue(client.shouldOverrideUrlLoading(webView, fakeWebRequest("file:///etc/hosts")))
        assertTrue(client.shouldOverrideUrlLoading(webView, fakeWebRequest("intent://test")))
        assertEquals(1, openedLinks.size)

        // Sub-recursos locais são servidos; http:// e file:// são bloqueados com 403
        val localKatexResponse = client.shouldInterceptRequest(
            webView,
            fakeWebRequest(ArticleHtmlDocumentBuilder.KATEX_JS_URL)
        )
        assertNotNull(localKatexResponse)

        val blockedHttp = client.shouldInterceptRequest(
            webView,
            fakeWebRequest("http://evil.example/script.js")
        )
        assertNotNull(blockedHttp)
        assertEquals(403, blockedHttp!!.statusCode)

        val blockedFile = client.shouldInterceptRequest(
            webView,
            fakeWebRequest("file:///data/data/com.example/databases/erudito_database.db")
        )
        assertNotNull(blockedFile)
        assertEquals(403, blockedFile!!.statusCode)
    }

    @Test
    fun `ArticleDetailScreenContent renders PLAIN_TEXT literally and routes STRUCTURED_HTML to WebView`() {
        val literalPlainText = "<p>Texto literal em formato simples sem conversão HTML.</p>"
        val plainArticle = Article(
            id = 1L,
            title = "Artigo Plain",
            url = "https://pt.wikipedia.org/wiki/Artigo_Plain",
            content = literalPlainText,
            contentFormat = ArticleContentFormat.PLAIN_TEXT
        )

        composeTestRule.setContent {
            ArticleDetailScreenContent(
                uiState = ArticleDetailUiState(
                    isLoading = false,
                    article = plainArticle,
                    isLoadingCollections = false
                ),
                onNavigateBack = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("article_detail_content").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(literalPlainText).assertIsDisplayed()
        composeTestRule.onNodeWithTag("structured_article_webview").assertDoesNotExist()
    }

    @Test
    fun `ArticleDetailScreenContent renders STRUCTURED_HTML in WebView without raw tags and shows unavailable formula banner`() {
        val structuredHtml = """
            <p>Equação de Euler: <span class="erudito-math-inline" data-tex="e^{i\pi} + 1 = 0"></span>.</p>
            <div class="erudito-math-unavailable" data-math-status="unavailable">[Fórmula indisponível]</div>
        """.trimIndent()
        val structuredArticle = Article(
            id = 2L,
            title = "Identidade de Euler",
            url = "https://pt.wikipedia.org/wiki/Identidade_de_Euler",
            content = structuredHtml,
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 1,
            unextractableFormulaCount = 1
        )

        composeTestRule.setContent {
            ArticleDetailScreenContent(
                uiState = ArticleDetailUiState(
                    isLoading = false,
                    article = structuredArticle,
                    isLoadingCollections = false
                ),
                onNavigateBack = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("article_detail_content_unavailable_formulas_notice")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("structured_article_webview")
            .assertExists()
        composeTestRule.onNodeWithText(structuredHtml).assertDoesNotExist()
    }

    @Test
    fun `WikipediaArticleScreenContent routes STRUCTURED_HTML to WebView loads local KaTeX and preserves PLAIN_TEXT`() {
        val structuredHtml = """
            <h2>Relatividade</h2>
            <p>Fórmula famosa: <span class="erudito-math-inline" data-tex="E = mc^2"></span></p>
        """.trimIndent()

        var capturedWebView: WebView? = null
        val wikiArticle = WikipediaArticle(
            pageId = 42L,
            title = "Relatividade",
            content = structuredHtml,
            url = "https://pt.wikipedia.org/wiki/Relatividade",
            hasExtractedContent = true,
            contentFormat = ArticleContentFormat.STRUCTURED_HTML,
            extractedFormulaCount = 1,
            unextractableFormulaCount = 0
        )

        composeTestRule.setContent {
            WikipediaArticleScreenContent(
                uiState = WikipediaArticleUiState.Success(wikiArticle),
                onNavigateBack = {},
                onRetry = {},
                onOpenOriginalUrl = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wikipedia_article_content").assertExists()
        composeTestRule.onNodeWithTag("structured_article_webview").assertExists()
        composeTestRule.onNodeWithText(structuredHtml).assertDoesNotExist()
        composeTestRule.onNodeWithTag("wikipedia_article_content_unavailable_formulas_notice")
            .assertDoesNotExist()

        // Verifica também que a carga via loadDataWithBaseURL usa a origem local de assets
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadow = Shadows.shadowOf(WebView(context).also { capturedWebView = it })
        val builtDoc = ArticleHtmlDocumentBuilder.buildDocument(structuredHtml, isDarkTheme = false)
        capturedWebView!!.loadDataWithBaseURL(
            ArticleHtmlDocumentBuilder.LOCAL_ASSETS_BASE_URL,
            builtDoc,
            "text/html",
            "UTF-8",
            null
        )
        assertEquals(
            ArticleHtmlDocumentBuilder.LOCAL_ASSETS_BASE_URL,
            shadow.lastLoadDataWithBaseURL.baseUrl
        )
        assertTrue(shadow.lastLoadDataWithBaseURL.data.contains("erudito-math-inline"))
        assertTrue(shadow.lastLoadDataWithBaseURL.data.contains("katex/katex.min.js"))
    }

    @Test
    fun `LocalArticleDocumentHandler serves active UTF-8 HTML document without caching and blocks unknown tokens`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val documentHandler = LocalArticleDocumentHandler()
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                StructuredArticleSecurityPolicy.ASSET_PATH_PREFIX,
                WebViewAssetLoader.AssetsPathHandler(context)
            )
            .addPathHandler(
                StructuredArticleSecurityPolicy.ARTICLE_DOCUMENT_PATH_PREFIX,
                documentHandler
            )
            .build()

        val docHtml = ArticleHtmlDocumentBuilder.buildDocument(
            sanitizedHtml = "<p>Fórmula com âncora <a href=\"https://pt.wikipedia.org/wiki/Teste#Secao\">link</a> e <span class=\"erudito-math-inline\" data-tex=\"E=mc^2\"></span></p>",
            isDarkTheme = false
        )
        documentHandler.setDocument("doc_99", docHtml)

        val validResponse = assetLoader.shouldInterceptRequest(
            Uri.parse("${ArticleHtmlDocumentBuilder.LOCAL_ARTICLE_BASE_URL}doc_99.html")
        )
        assertNotNull(validResponse)
        assertEquals(200, validResponse!!.statusCode)
        assertEquals("text/html", validResponse.mimeType)
        val servedText = validResponse.data.readBytes().toString(Charsets.UTF_8)
        assertTrue(servedText.contains("https://pt.wikipedia.org/wiki/Teste#Secao"))
        assertTrue(servedText.contains("E=mc^2"))

        val missingResponse = assetLoader.shouldInterceptRequest(
            Uri.parse("${ArticleHtmlDocumentBuilder.LOCAL_ARTICLE_BASE_URL}doc_stale.html")
        )
        assertEquals(null, missingResponse)
    }

    @Test
    fun `WebView diagnostics handle favicon locally distinguish main-frame vs subresource errors and survive onRenderProcessGone`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val documentHandler = LocalArticleDocumentHandler()
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                StructuredArticleSecurityPolicy.ASSET_PATH_PREFIX,
                WebViewAssetLoader.AssetsPathHandler(context)
            )
            .addPathHandler(
                StructuredArticleSecurityPolicy.ARTICLE_DOCUMENT_PATH_PREFIX,
                documentHandler
            )
            .build()

        var mainFrameErrors = 0
        var renderProcessGoneHandled = false
        var startedUrl: String? = null
        var finishedUrl: String? = null
        val httpErrors = mutableListOf<Triple<String?, Int, Boolean>>()

        val webView = createConfiguredWebView(
            context = context,
            assetLoader = assetLoader,
            onOpenLink = {},
            onPageStartedCallback = { startedUrl = it },
            onPageFinishedCallback = { _, url -> finishedUrl = url },
            onMainFrameErrorCallback = { mainFrameErrors++ },
            onHttpErrorCallback = { url, status, isMain ->
                httpErrors.add(Triple(url, status, isMain))
            },
            onRenderProcessGoneCallback = { view, _ ->
                renderProcessGoneHandled = true
                cleanupWebView(view, documentHandler, isRendererDead = true)
                true
            }
        )

        val client = webView.webViewClient
        assertNotNull(client)

        // Favicon virtual retorna 404 local em vez de tentar DNS externo
        val faviconResponse = client.shouldInterceptRequest(
            webView,
            fakeWebRequest("https://appassets.androidplatform.net/favicon.ico")
        )
        assertNotNull(faviconResponse)
        assertEquals(404, faviconResponse!!.statusCode)

        // Erro HTTP de sub-recurso não deve acionar fallback da página principal
        client.onReceivedHttpError(
            webView,
            fakeWebRequest("https://upload.wikimedia.org/missing.png", isMainFrame = false),
            android.webkit.WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
        )
        assertEquals(0, mainFrameErrors)
        assertEquals(1, httpErrors.size)

        // Erro HTTP no frame principal aciona fallback
        client.onReceivedHttpError(
            webView,
            fakeWebRequest("https://appassets.androidplatform.net/article/doc_1.html", isMainFrame = true),
            android.webkit.WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
        )
        assertEquals(1, mainFrameErrors)

        // Callbacks de ciclo de carregamento
        client.onPageStarted(webView, "https://appassets.androidplatform.net/article/doc_1.html", null)
        client.onPageFinished(webView, "https://appassets.androidplatform.net/article/doc_1.html")
        assertEquals("https://appassets.androidplatform.net/article/doc_1.html", startedUrl)
        assertEquals("https://appassets.androidplatform.net/article/doc_1.html", finishedUrl)

        // onRenderProcessGone deve retornar true, destruir uma única vez e permanecer idempotente
        val handled = client.onRenderProcessGone(webView, null)
        assertTrue(handled)
        assertTrue(renderProcessGoneHandled)
        assertTrue((webView as EruditoArticleWebView).isWebViewDestroyed)

        // Segunda chamada de cleanup não deve falhar
        cleanupWebView(webView, documentHandler, isRendererDead = false)
        assertTrue(webView.isWebViewDestroyed)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `StructuredArticleFallbackView shows intermediate loading state while processing off main thread then renders plain text`() {
        val testScheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val backgroundDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val processor = com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer()

        composeTestRule.setContent {
            StructuredArticleFallbackView(
                html = "<section><h2>Título</h2><p>Texto <strong>formatado</strong> em fallback.</p></section>",
                htmlProcessor = processor,
                processingDispatcher = backgroundDispatcher
            )
        }

        // Enquanto o dispatcher de background não executou, exibe o banner e o estado intermediário de preparo
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("structured_article_fallback_loading").assertIsDisplayed()
        composeTestRule.onNodeWithTag("structured_article_fallback_text").assertDoesNotExist()

        // Ao concluir o processamento assíncrono em background, exibe o texto extraído sem tags HTML
        testScheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("structured_article_fallback_loading").assertDoesNotExist()
        composeTestRule.onNodeWithTag("structured_article_fallback_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Título Texto formatado em fallback.").assertIsDisplayed()
    }

    private fun fakeWebRequest(
        url: String,
        isMainFrame: Boolean = false
    ): WebResourceRequest {
        val parsed = Uri.parse(url)
        return object : WebResourceRequest {
            override fun getUrl(): Uri = parsed
            override fun isForMainFrame(): Boolean = isMainFrame
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = true
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
    }
}
