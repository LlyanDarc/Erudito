package com.example.erudito.domain.sync

import com.example.erudito.domain.model.Article
import com.example.erudito.domain.model.ArticleContentFormat
import com.example.erudito.domain.model.WikipediaArticle

data class MergeResult(
    val article: Article,
    val hasContentChanged: Boolean
)

/**
 * Executa o merge defensivo entre o artigo persistido localmente e a resposta remota da Wikipédia.
 *
 * Política de formato e integridade de conteúdo:
 * 1. Resposta remota vazia, sem conteúdo extraído (`hasExtractedContent = false`) ou com contadores
 *    negativos nunca substitui conteúdo local válido.
 * 2. Local `STRUCTURED_HTML` íntegro (`unextractableFormulaCount == 0`) é preservado contra
 *    respostas remotas `PLAIN_TEXT`, vazias ou `STRUCTURED_HTML` com fórmulas indisponíveis;
 *    só pode ser atualizado por outro `STRUCTURED_HTML` íntegro.
 * 3. Local `STRUCTURED_HTML` incompleto (`unextractableFormulaCount > 0`) só é substituído por
 *    um `STRUCTURED_HTML` remoto íntegro (ou quando o conteúdo local estiver vazio).
 * 4. Local `PLAIN_TEXT` aproveitável pode evoluir para `STRUCTURED_HTML` íntegro ou ser
 *    atualizado por `PLAIN_TEXT` válido; um `STRUCTURED_HTML` incompleto não sobrescreve
 *    texto simples aproveitável para evitar perda silenciosa de expressões matemáticas.
 * 5. Quando não há conteúdo local aproveitável (`content` nulo ou em branco), qualquer resposta
 *    remota extraída e não vazia é aceita preservando seu formato e contadores reais.
 * 6. Metadados (título, URL, descrição, imagem) continuam sendo atualizados de forma defensiva e
 *    `lastSyncedAt` registra o timestamp da consulta remota bem-sucedida, enquanto `lastUpdated`
 *    só muda quando algum dado persistido relevante é alterado.
 */
object ArticleSyncMerger {

    fun merge(
        local: Article,
        remote: WikipediaArticle,
        syncedAtMs: Long,
        resolvedPageId: Long? = local.wikipediaPageId
    ): MergeResult {
        val mergedTitle = remote.title.trim().takeIf { it.isNotEmpty() } ?: local.title
        val mergedUrl = remote.url.trim().takeIf { it.isNotEmpty() } ?: local.url
        val mergedDescription = remote.description?.trim()?.takeIf { it.isNotEmpty() }
            ?: local.description
        val mergedImageUrl = remote.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: local.imageUrl

        val extractedRemoteContent = if (remote.hasExtractedContent) {
            remote.content.trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val hasValidFormulaCounts =
            remote.extractedFormulaCount >= 0 && remote.unextractableFormulaCount >= 0

        val shouldReplaceContent = shouldReplaceLocalContent(
            local = local,
            remote = remote,
            extractedRemoteContent = extractedRemoteContent,
            hasValidFormulaCounts = hasValidFormulaCounts
        )

        val mergedContent = if (shouldReplaceContent) extractedRemoteContent else local.content
        val mergedContentFormat = if (shouldReplaceContent) {
            remote.contentFormat
        } else {
            local.contentFormat
        }
        val mergedExtractedFormulaCount = if (shouldReplaceContent) {
            remote.extractedFormulaCount.coerceAtLeast(0)
        } else {
            local.extractedFormulaCount.coerceAtLeast(0)
        }
        val mergedUnextractableFormulaCount = if (shouldReplaceContent) {
            remote.unextractableFormulaCount.coerceAtLeast(0)
        } else {
            local.unextractableFormulaCount.coerceAtLeast(0)
        }

        val hasContentChanged =
            mergedTitle != local.title ||
                mergedUrl != local.url ||
                mergedDescription != local.description ||
                mergedContent != local.content ||
                mergedImageUrl != local.imageUrl ||
                mergedContentFormat != local.contentFormat ||
                mergedExtractedFormulaCount != local.extractedFormulaCount ||
                mergedUnextractableFormulaCount != local.unextractableFormulaCount

        val updatedArticle = local.copy(
            title = mergedTitle,
            url = mergedUrl,
            description = mergedDescription,
            content = mergedContent,
            imageUrl = mergedImageUrl,
            lastUpdated = if (hasContentChanged) syncedAtMs else local.lastUpdated,
            wikipediaPageId = resolvedPageId ?: local.wikipediaPageId,
            lastSyncedAt = syncedAtMs,
            contentFormat = mergedContentFormat,
            extractedFormulaCount = mergedExtractedFormulaCount,
            unextractableFormulaCount = mergedUnextractableFormulaCount
        )

        return MergeResult(
            article = updatedArticle,
            hasContentChanged = hasContentChanged
        )
    }

    private fun shouldReplaceLocalContent(
        local: Article,
        remote: WikipediaArticle,
        extractedRemoteContent: String?,
        hasValidFormulaCounts: Boolean
    ): Boolean {
        if (extractedRemoteContent == null || !hasValidFormulaCounts) {
            return false
        }

        if (!local.hasUsableContent) {
            return true
        }

        val remoteIsCompleteStructuredHtml =
            remote.contentFormat == ArticleContentFormat.STRUCTURED_HTML &&
                remote.unextractableFormulaCount == 0

        return when (local.contentFormat) {
            ArticleContentFormat.STRUCTURED_HTML -> remoteIsCompleteStructuredHtml
            ArticleContentFormat.PLAIN_TEXT -> when (remote.contentFormat) {
                ArticleContentFormat.STRUCTURED_HTML -> remoteIsCompleteStructuredHtml
                ArticleContentFormat.PLAIN_TEXT -> true
            }
        }
    }
}
