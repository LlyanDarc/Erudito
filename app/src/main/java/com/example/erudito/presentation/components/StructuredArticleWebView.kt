package com.example.erudito.presentation.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

private const val LOG_TAG = "StructuredArticleWebView"
private val MIN_WEBVIEW_HEIGHT_DP = 180.dp
private val INITIAL_WEBVIEW_HEIGHT_DP = 240.dp
private val MAX_WEBVIEW_HEIGHT_DP = 540.dp
private const val HEIGHT_UPDATE_THRESHOLD_DP = 4f
private val DOCUMENT_SEQUENCE = AtomicLong(0L)

private data class PreparedArticleDocument(
    val token: String,
    val htmlDocument: String
)

private class WebViewRenderTracker(
    var loadedToken: String? = null,
    var lastDarkTheme: Boolean? = null,
    var isPageReady: Boolean = false
)

internal class LocalArticleDocumentHandler : WebViewAssetLoader.PathHandler {
    @Volatile
    private var activeToken: String? = null

    @Volatile
    private var activeHtmlBytes: ByteArray? = null

    fun setDocument(token: String, documentHtml: String) {
        activeToken = token
        activeHtmlBytes = documentHtml.toByteArray(Charsets.UTF_8)
    }

    fun clear() {
        activeToken = null
        activeHtmlBytes = null
    }

    override fun handle(path: String): WebResourceResponse? {
        val expectedFile = activeToken?.let { "$it.html" } ?: return null
        if (path != expectedFile) return null
        val bytes = activeHtmlBytes ?: return null
        return WebResourceResponse(
            "text/html",
            "utf-8",
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store, no-cache, must-revalidate",
                "X-Content-Type-Options" to "nosniff"
            ),
            ByteArrayInputStream(bytes)
        )
    }
}

internal class EruditoArticleWebView(context: Context) : WebView(context) {
    private val destroyedFlag = AtomicBoolean(false)
    private var pendingHeightRunnable: Runnable? = null
    private var lastTouchY = 0f

    val isWebViewDestroyed: Boolean
        get() = destroyedFlag.get()

    fun markDestroyed(): Boolean = destroyedFlag.compareAndSet(false, true)

    fun scheduleDelayedHeightMeasurement(delayMs: Long, action: (WebView) -> Unit) {
        cancelPendingHeightMeasurement()
        if (destroyedFlag.get()) return
        val weakSelf = WeakReference(this)
        val runnable = Runnable {
            pendingHeightRunnable = null
            val view = weakSelf.get() ?: return@Runnable
            if (!view.isWebViewDestroyed && view.isAttachedToWindow) {
                action(view)
            }
        }
        pendingHeightRunnable = runnable
        postDelayed(runnable, delayMs)
    }

    fun cancelPendingHeightMeasurement() {
        pendingHeightRunnable?.let { removeCallbacks(it) }
        pendingHeightRunnable = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                val canScroll = canScrollVertically(-1) || canScrollVertically(1)
                parent?.requestDisallowInterceptTouchEvent(canScroll)
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = lastTouchY - event.y
                lastTouchY = event.y
                val scrollingDown = deltaY > 0f && canScrollVertically(1)
                val scrollingUp = deltaY < 0f && canScrollVertically(-1)
                if (scrollingDown || scrollingUp) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else if (abs(deltaY) > 4f) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

@Composable
fun StructuredArticleWebView(
    html: String,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    val currentDarkTheme by rememberUpdatedState(isDarkTheme)
    val renderTracker = remember { WebViewRenderTracker() }
    val documentHandler = remember { LocalArticleDocumentHandler() }

    val assetLoader = remember(appContext, documentHandler) {
        WebViewAssetLoader.Builder()
            .addPathHandler(
                StructuredArticleSecurityPolicy.ASSET_PATH_PREFIX,
                WebViewAssetLoader.AssetsPathHandler(appContext)
            )
            .addPathHandler(
                StructuredArticleSecurityPolicy.ARTICLE_DOCUMENT_PATH_PREFIX,
                documentHandler
            )
            .build()
    }

    var isLoading by remember(html) { mutableStateOf(true) }
    var loadError by remember(html) { mutableStateOf(false) }
    var measuredHeightDp by remember(html) { mutableStateOf<Dp?>(null) }
    val initialDarkTheme = remember(html) { isDarkTheme }

    val preparedDocState by produceState<PreparedArticleDocument?>(
        initialValue = null,
        key1 = html
    ) {
        value = withContext(Dispatchers.Default) {
            val builtDoc = ArticleHtmlDocumentBuilder.buildDocument(
                sanitizedHtml = html,
                isDarkTheme = initialDarkTheme
            )
            PreparedArticleDocument(
                token = "doc_${DOCUMENT_SEQUENCE.incrementAndGet()}",
                htmlDocument = builtDoc
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (loadError) {
            StructuredArticleFallbackView(
                html = html,
                modifier = Modifier.testTag("structured_article_webview_fallback")
            )
        } else {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("structured_article_webview_loading")
                )
            }

            val boundedHeight = (measuredHeightDp ?: INITIAL_WEBVIEW_HEIGHT_DP)
                .coerceIn(MIN_WEBVIEW_HEIGHT_DP, MAX_WEBVIEW_HEIGHT_DP)

            Box(modifier = Modifier.fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(boundedHeight)
                        .testTag("structured_article_webview"),
                    factory = { viewContext ->
                        createConfiguredWebView(
                            context = viewContext,
                            assetLoader = assetLoader,
                            onOpenLink = { url -> currentOnOpenLink(url) },
                            onPageStartedCallback = { url ->
                                safeLogDebug("onPageStarted url=$url")
                            },
                            onPageFinishedCallback = { webView, url ->
                                if (url == "about:blank") return@createConfiguredWebView
                                safeLogDebug("onPageFinished url=$url")
                                renderTracker.isPageReady = true
                                isLoading = false

                                if (renderTracker.lastDarkTheme != currentDarkTheme) {
                                    renderTracker.lastDarkTheme = currentDarkTheme
                                    applyWebViewTheme(webView, currentDarkTheme)
                                }

                                val onHeightMeasured: (Dp) -> Unit = { heightDp ->
                                    val clamped = heightDp.coerceIn(
                                        MIN_WEBVIEW_HEIGHT_DP,
                                        MAX_WEBVIEW_HEIGHT_DP
                                    )
                                    val previous = measuredHeightDp?.value
                                    if (previous == null ||
                                        abs(previous - clamped.value) >= HEIGHT_UPDATE_THRESHOLD_DP
                                    ) {
                                        measuredHeightDp = clamped
                                    }
                                }

                                updateMeasuredContentHeight(webView, onHeightMeasured)
                                (webView as? EruditoArticleWebView)?.scheduleDelayedHeightMeasurement(
                                    delayMs = 180L
                                ) { attachedView ->
                                    updateMeasuredContentHeight(attachedView, onHeightMeasured)
                                }
                            },
                            onMainFrameErrorCallback = { errorSummary ->
                                safeLogError("Main-frame load error: $errorSummary")
                                isLoading = false
                                loadError = true
                            },
                            onRenderProcessGoneCallback = { webView, didCrash ->
                                safeLogError("Renderer process gone (didCrash=$didCrash). Switching to fallback.")
                                cleanupWebView(
                                    webView = webView,
                                    documentHandler = documentHandler,
                                    isRendererDead = true
                                )
                                isLoading = false
                                loadError = true
                                true
                            }
                        )
                    },
                    update = { webView ->
                        val doc = preparedDocState
                        if (doc != null && renderTracker.loadedToken != doc.token) {
                            renderTracker.loadedToken = doc.token
                            renderTracker.lastDarkTheme = initialDarkTheme
                            renderTracker.isPageReady = false
                            documentHandler.setDocument(doc.token, doc.htmlDocument)
                            val documentUrl = "${ArticleHtmlDocumentBuilder.LOCAL_ARTICLE_BASE_URL}${doc.token}.html"
                            safeLogDebug("Loading structured document url=$documentUrl bytes=${doc.htmlDocument.length}")
                            webView.loadUrl(documentUrl)
                        } else if (renderTracker.isPageReady &&
                            renderTracker.lastDarkTheme != isDarkTheme
                        ) {
                            renderTracker.lastDarkTheme = isDarkTheme
                            applyWebViewTheme(webView, isDarkTheme)
                        }
                    },
                    onRelease = { webView ->
                        cleanupWebView(
                            webView = webView,
                            documentHandler = documentHandler,
                            isRendererDead = false
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun StructuredArticleFallbackView(
    html: String,
    modifier: Modifier = Modifier
) {
    val plainFallback = remember(html) {
        Jsoup.parseBodyFragment(html).text().trim()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Exibindo versão textual simplificada porque a visualização formatada não pôde ser iniciada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        if (plainFallback.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = plainFallback,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
internal fun createConfiguredWebView(
    context: Context,
    assetLoader: WebViewAssetLoader,
    onOpenLink: (String) -> Unit,
    onPageStartedCallback: (String?) -> Unit = {},
    onPageFinishedCallback: (WebView, String?) -> Unit = { _, _ -> },
    onMainFrameErrorCallback: (String) -> Unit = {},
    onHttpErrorCallback: (url: String?, statusCode: Int, isMainFrame: Boolean) -> Unit = { _, _, _ -> },
    onRenderProcessGoneCallback: (webView: WebView, didCrash: Boolean) -> Boolean = { _, _ -> true },
    onConsoleMessageCallback: (ConsoleMessage) -> Unit = {}
): WebView {
    return EruditoArticleWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        configureArticleWebSettings(settings)

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    val formatted =
                        "[JS:${consoleMessage.messageLevel()}] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} - ${consoleMessage.message()}"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> safeLogError(formatted)
                        ConsoleMessage.MessageLevel.WARNING -> safeLogWarn(formatted)
                        else -> safeLogDebug(formatted)
                    }
                    onConsoleMessageCallback(consoleMessage)
                }
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url ?: return blockedResourceResponse()
                val scheme = uri.scheme
                val host = uri.host
                val path = uri.path

                if (StructuredArticleSecurityPolicy.isLocalFaviconUri(scheme, host, path)) {
                    return emptyNotFoundResponse()
                }

                if (StructuredArticleSecurityPolicy.isLocalAssetUri(scheme, host, path) ||
                    StructuredArticleSecurityPolicy.isLocalDocumentUri(scheme, host, path)
                ) {
                    return assetLoader.shouldInterceptRequest(uri) ?: run {
                        safeLogWarn("Local asset/document missing for uri=$uri")
                        blockedResourceResponse()
                    }
                }

                if (!StructuredArticleSecurityPolicy.isAllowedSubresourceUri(scheme, host, path)) {
                    safeLogWarn("Blocked disallowed subresource uri=$uri")
                    return blockedResourceResponse()
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val rawUrl = request?.url?.toString().orEmpty()
                if (StructuredArticleSecurityPolicy.isAllowedExternalNavigationUrl(rawUrl)) {
                    onOpenLink(rawUrl)
                } else {
                    safeLogWarn("Blocked external navigation url=$rawUrl")
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onPageStartedCallback(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null) {
                    onPageFinishedCallback(view, url)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val isMainFrame = request?.isForMainFrame == true
                val errorCode = error?.errorCode
                val description = error?.description?.toString()
                val failedUrl = request?.url?.toString()
                safeLogError(
                    "onReceivedError mainFrame=$isMainFrame code=$errorCode desc=$description url=$failedUrl"
                )
                if (isMainFrame) {
                    onMainFrameErrorCallback("code=$errorCode desc=$description url=$failedUrl")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val isMainFrame = request?.isForMainFrame == true
                val statusCode = errorResponse?.statusCode ?: -1
                val failedUrl = request?.url?.toString()
                safeLogWarn(
                    "onReceivedHttpError mainFrame=$isMainFrame status=$statusCode reason=${errorResponse?.reasonPhrase} url=$failedUrl"
                )
                onHttpErrorCallback(failedUrl, statusCode, isMainFrame)
                if (isMainFrame && statusCode >= 400) {
                    onMainFrameErrorCallback("httpStatus=$statusCode url=$failedUrl")
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                val didCrash = detail?.didCrash() == true
                val priority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detail?.rendererPriorityAtExit()
                } else {
                    null
                }
                safeLogError(
                    "onRenderProcessGone didCrash=$didCrash rendererPriorityAtExit=$priority"
                )
                val targetView = view ?: this@apply
                return onRenderProcessGoneCallback(targetView, didCrash)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
internal fun configureArticleWebSettings(webSettings: WebSettings) {
    webSettings.apply {
        // Necessário exclusivamente para o KaTeX offline (restrito via CSP e WebViewAssetLoader).
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        domStorageEnabled = false
        databaseEnabled = false
        @Suppress("DEPRECATION")
        setGeolocationEnabled(false)
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        loadsImagesAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = true
        }
    }
}

private fun applyWebViewTheme(webView: WebView, isDarkTheme: Boolean) {
    if ((webView as? EruditoArticleWebView)?.isWebViewDestroyed == true) return
    val themeArg = if (isDarkTheme) "dark" else "light"
    runCatching {
        webView.evaluateJavascript(
            "window.EruditoMath && window.EruditoMath.setTheme('$themeArg');",
            null
        )
    }
}

private fun updateMeasuredContentHeight(
    webView: WebView,
    onMeasuredDp: (Dp) -> Unit
) {
    if ((webView as? EruditoArticleWebView)?.isWebViewDestroyed == true) return
    runCatching {
        webView.evaluateJavascript(
            "(function(){ return window.EruditoMath ? window.EruditoMath.getContentHeight() : (document.documentElement ? document.documentElement.scrollHeight : 0); })();"
        ) { rawValue ->
            val cssHeight = rawValue?.trim()?.trim('"')?.toFloatOrNull()
            if (cssHeight != null && cssHeight > 0f) {
                val coercedHeight = cssHeight.coerceAtLeast(MIN_WEBVIEW_HEIGHT_DP.value)
                onMeasuredDp(coercedHeight.dp)
            }
        }
    }
}

private fun blockedResourceResponse(): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Forbidden",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )
}

private fun emptyNotFoundResponse(): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Not Found",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )
}

internal fun cleanupWebView(
    webView: WebView,
    documentHandler: LocalArticleDocumentHandler? = null,
    isRendererDead: Boolean = false
) {
    val managed = webView as? EruditoArticleWebView
    if (managed != null) {
        if (!managed.markDestroyed()) return
        managed.cancelPendingHeightMeasurement()
    }
    documentHandler?.clear()
    runCatching {
        if (!isRendererDead) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
        }
        webView.webChromeClient = null
        webView.removeAllViews()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}

private fun safeLogDebug(message: String) {
    runCatching { Log.d(LOG_TAG, message) }
}

private fun safeLogWarn(message: String) {
    runCatching { Log.w(LOG_TAG, message) }
}

private fun safeLogError(message: String) {
    runCatching { Log.e(LOG_TAG, message) }
}
