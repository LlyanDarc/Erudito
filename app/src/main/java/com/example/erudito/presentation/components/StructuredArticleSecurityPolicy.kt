package com.example.erudito.presentation.components

import java.net.URI

object StructuredArticleSecurityPolicy {

    const val ASSET_HOST = "appassets.androidplatform.net"
    const val ASSET_PATH_PREFIX = "/assets/"
    const val ARTICLE_DOCUMENT_PATH_PREFIX = "/article/"
    const val FAVICON_PATH = "/favicon.ico"

    fun isLocalAssetUri(
        scheme: String?,
        host: String?,
        path: String?
    ): Boolean {
        return scheme.equals("https", ignoreCase = true) &&
            host.equals(ASSET_HOST, ignoreCase = true) &&
            path?.startsWith(ASSET_PATH_PREFIX) == true
    }

    fun isLocalDocumentUri(
        scheme: String?,
        host: String?,
        path: String?
    ): Boolean {
        return scheme.equals("https", ignoreCase = true) &&
            host.equals(ASSET_HOST, ignoreCase = true) &&
            path?.startsWith(ARTICLE_DOCUMENT_PATH_PREFIX) == true
    }

    fun isLocalFaviconUri(
        scheme: String?,
        host: String?,
        path: String?
    ): Boolean {
        return scheme.equals("https", ignoreCase = true) &&
            host.equals(ASSET_HOST, ignoreCase = true) &&
            path.equals(FAVICON_PATH, ignoreCase = true)
    }

    fun isAllowedExternalNavigationUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = parsed.host?.lowercase() ?: return false
        if (host.isBlank() || host == ASSET_HOST) return false
        return true
    }

    fun isAllowedSubresourceUri(
        scheme: String?,
        host: String?,
        path: String?
    ): Boolean {
        if (isLocalAssetUri(scheme = scheme, host = host, path = path) ||
            isLocalDocumentUri(scheme = scheme, host = host, path = path)
        ) {
            return true
        }
        if (host.equals(ASSET_HOST, ignoreCase = true)) {
            return false
        }
        return scheme.equals("https", ignoreCase = true) && !host.isNullOrBlank()
    }
}
