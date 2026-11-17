package com.example.erudito.data.remote.parser

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import okio.Buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

data class SanitizedWikipediaHtml(
    val html: String,
    val extractedFormulaCount: Int,
    val unextractableFormulaCount: Int
)

@Singleton
open class WikipediaHtmlSanitizer @Inject constructor(
    moshi: Moshi
) {
    constructor() : this(Moshi.Builder().build())

    private val dataMwAdapter = moshi.adapter(ParsoidDataMwDto::class.java)

    fun sanitize(rawHtml: String): String {
        return sanitizeWithMetadata(rawHtml).html
    }

    open fun sanitizeWithMetadata(rawHtml: String): SanitizedWikipediaHtml {
        if (rawHtml.isBlank()) {
            return SanitizedWikipediaHtml(
                html = "",
                extractedFormulaCount = 0,
                unextractableFormulaCount = 0
            )
        }

        val document = Jsoup.parse(rawHtml, WIKIPEDIA_WIKI_BASE_URL)
        document.outputSettings()
            .prettyPrint(false)
            .charset(StandardCharsets.UTF_8)

        val body = document.body()

        // 1. Remove elementos executáveis e metadados externos antes de processar o DOM.
        body.select(EXECUTABLE_AND_EXTERNAL_TAGS_SELECTOR).remove()

        // 2. Remove ruídos editoriais e de navegação da Wikipédia sem tocar em explicações do corpo.
        body.select(WIKIPEDIA_NOISE_SELECTOR).remove()

        // 3. Extrai e converte fórmulas matemáticas (Parsoid data-mw e Mathoid legado)
        //    antes da remoção de atributos/data-mw e da aplicação da Safelist.
        val (extractedCount, unextractableCount) = extractAndConvertMathElements(document, body)

        // 4. Remove eventuais imagens fallback matemáticas órfãs para evitar duplicação.
        body.select(MATH_FALLBACK_IMAGE_SELECTOR).remove()

        // 5. Promove <dl><dd> que contenham exclusivamente uma fórmula para bloco centralizado.
        promoteSingleFormulaDefinitionLists(document, body)

        // 6. Normaliza e valida URLs de links e imagens; filtra classes permitidas.
        sanitizeLinksAndImages(body)
        filterAllowedClasses(body)

        // 7. Aplica limpeza defensiva final via Safelist explícita (preservando data-tex e classes neutras).
        val cleanedDocument = Cleaner(WIKIPEDIA_SAFELIST).clean(document)
        cleanedDocument.outputSettings()
            .prettyPrint(false)
            .charset(StandardCharsets.UTF_8)

        return SanitizedWikipediaHtml(
            html = cleanedDocument.body().html().trim(),
            extractedFormulaCount = extractedCount,
            unextractableFormulaCount = unextractableCount
        )
    }

    private fun extractAndConvertMathElements(
        document: Document,
        body: Element
    ): Pair<Int, Int> {
        val allCandidates = body.select(MATH_CONTAINER_SELECTOR)
        val candidateSet = allCandidates.toSet()

        // Processa apenas o contêiner externo de cada fórmula para evitar duplicação
        // quando <span class="mwe-math-element"> envolve <math> e <img> fallback.
        val topLevelMathNodes = allCandidates.filter { candidate ->
            candidate.parents().none { parent -> parent in candidateSet }
        }

        var extractedCount = 0
        var unextractableCount = 0

        for (node in topLevelMathNodes) {
            val extracted = extractFormulaData(node)
            if (extracted != null) {
                val normalizedTex = normalizeLatex(extracted.rawTex)
                val tagName = if (extracted.isBlock) "div" else "span"
                val className = if (extracted.isBlock) CLASS_MATH_BLOCK else CLASS_MATH_INLINE

                val placeholder = document.createElement(tagName)
                    .addClass(className)
                    .attr(ATTR_DATA_TEX, normalizedTex)

                node.replaceWith(placeholder)
                extractedCount++
            } else {
                val isBlock = isBlockMathNode(node, parsoidDisplay = null)
                val tagName = if (isBlock) "div" else "span"
                val unavailablePlaceholder = document.createElement(tagName)
                    .addClass(CLASS_MATH_UNAVAILABLE)
                    .attr(ATTR_DATA_MATH_STATUS, STATUS_UNAVAILABLE)
                    .text(UNAVAILABLE_FORMULA_TEXT)

                node.replaceWith(unavailablePlaceholder)
                unextractableCount++
            }
        }

        return extractedCount to unextractableCount
    }

    private fun extractFormulaData(node: Element): ExtractedFormula? {
        var parsoidDisplay: String? = null
        var rawTex: String? = null

        val rawDataMw = node.attr(ATTR_DATA_MW).trim()
        if (rawDataMw.isNotEmpty()) {
            val parsed = runCatching { dataMwAdapter.fromJson(rawDataMw) }.getOrNull()
                ?: parseParsoidDataMwFallback(rawDataMw)
            parsoidDisplay = parsed?.attrs?.display?.trim()?.lowercase()
            rawTex = parsed?.body?.extsrc?.trim()?.takeIf { it.isNotEmpty() }
        }

        if (rawTex == null) {
            rawTex = node.selectFirst(ANNOTATION_TEX_SELECTOR)
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        if (rawTex == null) {
            rawTex = node.attr(ATTR_ALTTEXT).trim().takeIf { it.isNotEmpty() }
                ?: node.selectFirst("[$ATTR_ALTTEXT]")
                    ?.attr(ATTR_ALTTEXT)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
        }

        val resolvedTex = rawTex ?: return null
        val isBlock = isBlockMathNode(node, parsoidDisplay)
        return ExtractedFormula(rawTex = resolvedTex, isBlock = isBlock)
    }

    private fun parseParsoidDataMwFallback(rawJson: String): ParsoidDataMwDto? {
        return runCatching {
            val reader = JsonReader.of(Buffer().writeUtf8(rawJson))
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
            var display: String? = null
            var extsrc: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "attrs" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                if (reader.nextName() == "display" &&
                                    reader.peek() == JsonReader.Token.STRING
                                ) {
                                    display = reader.nextString()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "body" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                if (reader.nextName() == "extsrc" &&
                                    reader.peek() == JsonReader.Token.STRING
                                ) {
                                    extsrc = reader.nextString()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            ParsoidDataMwDto(
                attrs = display?.let { ParsoidMathAttrsDto(display = it) },
                body = extsrc?.let { ParsoidMathBodyDto(extsrc = it) }
            )
        }.getOrNull()
    }

    private fun isBlockMathNode(node: Element, parsoidDisplay: String?): Boolean {
        if (parsoidDisplay == DISPLAY_BLOCK) return true
        if (node.hasClass(WIKI_CLASS_MATH_BLOCK)) return true
        if (node.attr(ATTR_DISPLAY).equals(DISPLAY_BLOCK, ignoreCase = true)) return true
        return node.selectFirst(
            ".mwe-math-mathml-display, .mwe-math-fallback-image-display, math[display=block]"
        ) != null
    }

    internal fun normalizeLatex(rawLatex: String): String {
        var normalized = rawLatex.trim()
        normalized = ALIGN_BEGIN_REGEX.replace(normalized, "\\\\begin{aligned}")
        normalized = ALIGN_END_REGEX.replace(normalized, "\\\\end{aligned}")
        normalized = EMPTY_RM_REGEX.replace(normalized, "")
        normalized = BRACED_RM_REGEX.replace(normalized) { matchResult ->
            "\\mathrm{${matchResult.groupValues[1]}}"
        }
        return normalized.trim()
    }

    private fun promoteSingleFormulaDefinitionLists(document: Document, body: Element) {
        val definitionLists = body.select("dl").toList()
        for (dl in definitionLists) {
            val children = dl.children()
            if (children.isEmpty()) continue

            val hasDefinitionTerms = children.any { it.tagName().equals("dt", ignoreCase = true) }
            val ddElements = children.filter { it.tagName().equals("dd", ignoreCase = true) }
            if (ddElements.isEmpty()) continue

            val promotedBlocks = mutableMapOf<Element, Element>()
            for (dd in ddElements) {
                val singleFormula = extractSingleFormulaFromDd(dd)
                if (singleFormula != null) {
                    val tex = singleFormula.attr(ATTR_DATA_TEX)
                    val blockDiv = document.createElement("div")
                        .addClass(CLASS_MATH_BLOCK)
                        .attr(ATTR_DATA_TEX, tex)
                    promotedBlocks[dd] = blockDiv
                }
            }

            if (promotedBlocks.isEmpty()) continue

            if (!hasDefinitionTerms &&
                promotedBlocks.size == ddElements.size &&
                children.size == ddElements.size &&
                dl.ownText().isBlank()
            ) {
                val blocks = ddElements.mapNotNull { promotedBlocks[it] }
                for (block in blocks) {
                    dl.before(block)
                }
                dl.remove()
            } else {
                for ((dd, blockDiv) in promotedBlocks) {
                    dd.empty().appendChild(blockDiv)
                }
            }
        }
    }

    private fun extractSingleFormulaFromDd(dd: Element): Element? {
        if (dd.ownText().isNotBlank()) return null

        val mathElements = dd.select(".$CLASS_MATH_INLINE, .$CLASS_MATH_BLOCK")
        if (mathElements.size != 1) return null

        val mathEl = mathElements.first() ?: return null
        val clone = dd.clone()
        clone.select(".$CLASS_MATH_INLINE, .$CLASS_MATH_BLOCK").remove()
        if (clone.text().isNotBlank()) return null

        val nonBrRemaining = clone.select("*").any { element ->
            element != clone &&
                !element.tagName().equals("br", ignoreCase = true) &&
                !element.tagName().equals("span", ignoreCase = true)
        }
        return if (nonBrRemaining) null else mathEl
    }

    private fun sanitizeLinksAndImages(body: Element) {
        for (link in body.select("a").toList()) {
            val rawHref = link.attr("href").trim()
            if (rawHref.isEmpty() || rawHref.startsWith("#")) {
                link.unwrap()
                continue
            }

            val resolvedHref = link.absUrl("href").trim()
            if (isSafeHttpUrl(resolvedHref)) {
                link.attr("href", resolvedHref)
                link.attr("rel", "noopener noreferrer")
            } else {
                link.unwrap()
            }
        }

        for (image in body.select("img").toList()) {
            val resolvedSrc = image.absUrl("src").trim()
            if (isSafeHttpUrl(resolvedSrc)) {
                image.attr("src", resolvedSrc)
            } else {
                image.remove()
            }
        }
    }

    private fun filterAllowedClasses(body: Element) {
        for (element in body.select("[class]")) {
            val keptClasses = element.classNames().filter { it in ALLOWED_CLASSES }
            if (keptClasses.isEmpty()) {
                element.removeAttr("class")
            } else {
                element.attr("class", keptClasses.joinToString(" "))
            }
        }
    }

    private fun isSafeHttpUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)
    }

    private data class ExtractedFormula(
        val rawTex: String,
        val isBlock: Boolean
    )

    @JsonClass(generateAdapter = true)
    internal data class ParsoidDataMwDto(
        @param:Json(name = "name")
        val name: String? = null,
        @param:Json(name = "attrs")
        val attrs: ParsoidMathAttrsDto? = null,
        @param:Json(name = "body")
        val body: ParsoidMathBodyDto? = null
    )

    @JsonClass(generateAdapter = true)
    internal data class ParsoidMathAttrsDto(
        @param:Json(name = "display")
        val display: String? = null
    )

    @JsonClass(generateAdapter = true)
    internal data class ParsoidMathBodyDto(
        @param:Json(name = "extsrc")
        val extsrc: String? = null
    )

    companion object {
        const val CLASS_MATH_INLINE = "erudito-math-inline"
        const val CLASS_MATH_BLOCK = "erudito-math-block"
        const val CLASS_MATH_UNAVAILABLE = "erudito-math-unavailable"
        const val ATTR_DATA_TEX = "data-tex"
        const val ATTR_DATA_MATH_STATUS = "data-math-status"
        const val STATUS_UNAVAILABLE = "unavailable"
        const val UNAVAILABLE_FORMULA_TEXT = "[Fórmula indisponível]"

        private const val WIKIPEDIA_WIKI_BASE_URL = "https://pt.wikipedia.org/wiki/"
        private const val ATTR_DATA_MW = "data-mw"
        private const val ATTR_ALTTEXT = "alttext"
        private const val ATTR_DISPLAY = "display"
        private const val DISPLAY_BLOCK = "block"
        private const val WIKI_CLASS_MATH_BLOCK = "mwe-math-element-block"

        private const val MATH_CONTAINER_SELECTOR =
            "[typeof=mw:Extension/math], .mwe-math-element, math"
        private const val ANNOTATION_TEX_SELECTOR = "annotation[encoding=application/x-tex]"
        private const val MATH_FALLBACK_IMAGE_SELECTOR =
            "img.mwe-math-fallback-image-inline, img.mwe-math-fallback-image-display"

        private const val EXECUTABLE_AND_EXTERNAL_TAGS_SELECTOR =
            "script, iframe, object, embed, applet, frame, frameset, style, link, noscript, meta, base, template"

        private const val WIKIPEDIA_NOISE_SELECTOR =
            ".mw-editsection, .navbox, .vertical-navbox, .sidebar, .reflist, .mw-references-wrap, " +
                ".noprint, .ambox, .tmbox, .ombox, .cmbox, .fmbox, .mbox-small, .hatnote"

        private val ALLOWED_CLASSES = setOf(
            CLASS_MATH_INLINE,
            CLASS_MATH_BLOCK,
            CLASS_MATH_UNAVAILABLE,
            "texhtml",
            "sfrac"
        )

        private val ALIGN_BEGIN_REGEX = Regex("""\\begin\{align\*?\}""")
        private val ALIGN_END_REGEX = Regex("""\\end\{align\*?\}""")
        private val EMPTY_RM_REGEX = Regex("""\\rm\s*\{\s*\}\s*""")
        private val BRACED_RM_REGEX = Regex("""\\rm\s*\{([^{}]+)\}""")

        private val WIKIPEDIA_SAFELIST: Safelist = Safelist()
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
            .addAttributes("span", "class", ATTR_DATA_TEX, ATTR_DATA_MATH_STATUS)
            .addAttributes("div", "class", ATTR_DATA_TEX, ATTR_DATA_MATH_STATUS)
            .addAttributes("a", "href", "title", "rel")
            .addProtocols("a", "href", "http", "https")
            .addAttributes("img", "src", "alt", "width", "height")
            .addProtocols("img", "src", "http", "https")
            .addAttributes("th", "colspan", "rowspan", "scope")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("ol", "start", "type")
            .preserveRelativeLinks(false)
    }
}
