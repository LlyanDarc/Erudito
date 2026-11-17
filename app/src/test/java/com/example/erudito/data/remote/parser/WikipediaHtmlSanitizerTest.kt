package com.example.erudito.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaHtmlSanitizerTest {

    private val sanitizer = WikipediaHtmlSanitizer()

    @Test
    fun `extracts LaTeX from Parsoid data-mw body extsrc`() {
        val rawHtml = """
            <section>
              <p>A <a href="./Quinta_justa">quinta justa</a> multiplica por
                <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                  data-mw='{"name":"math","attrs":{},"body":{"extsrc":"\\frac{3}{2}"}}'>
                  <mrow><mfrac><mn>3</mn><mn>2</mn></mfrac></mrow>
                </math>.
              </p>
            </section>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)

        assertEquals(1, result.extractedFormulaCount)
        assertEquals(0, result.unextractableFormulaCount)
        val doc = Jsoup.parseBodyFragment(result.html)
        val mathSpan = doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")
        assertNotNull(mathSpan)
        assertEquals("""\frac{3}{2}""", mathSpan?.attr(WikipediaHtmlSanitizer.ATTR_DATA_TEX))
        assertFalse(result.html.contains("data-mw"))
        assertFalse(result.html.contains("<math"))

        val phpArrayAttrsHtml = """
            <p><math typeof="mw:Extension/math" data-mw='{"name":"math","attrs":[],"body":{"extsrc":"x^2"}}'><mi>x</mi></math></p>
        """.trimIndent()
        val arrayAttrsResult = sanitizer.sanitizeWithMetadata(phpArrayAttrsHtml)
        assertEquals(1, arrayAttrsResult.extractedFormulaCount)
        assertEquals(0, arrayAttrsResult.unextractableFormulaCount)
        assertEquals(
            "x^2",
            Jsoup.parseBodyFragment(arrayAttrsResult.html)
                .selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")
                ?.attr(WikipediaHtmlSanitizer.ATTR_DATA_TEX)
        )
    }

    @Test
    fun `extracts LaTeX from legacy Mathoid annotation and decodes HTML entities`() {
        val rawHtml = """
            <p>Sistema:
              <span class="mwe-math-element mwe-math-element-inline">
                <span class="mwe-math-mathml-a11y" style="display: none;">
                  <math xmlns="http://www.w3.org/1998/Math/MathML" alttext="{\displaystyle fallback}">
                    <semantics>
                      <mrow><mi>A</mi></mrow>
                      <annotation encoding="application/x-tex">{\displaystyle {\binom {x}{y}}'={\begin{pmatrix}1&amp;0\\0&amp;0.5\end{pmatrix}}}</annotation>
                    </semantics>
                  </math>
                </span>
                <img src="https://wikimedia.org/api/rest_v1/media/math/render/svg/abc" class="mwe-math-fallback-image-inline" alt="fallback">
              </span>
            </p>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)

        assertEquals(1, result.extractedFormulaCount)
        assertEquals(0, result.unextractableFormulaCount)
        val doc = Jsoup.parseBodyFragment(result.html)
        val mathSpan = doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")
        assertNotNull(mathSpan)
        assertEquals(
            """{\displaystyle {\binom {x}{y}}'={\begin{pmatrix}1&0\\0&0.5\end{pmatrix}}}""",
            mathSpan?.attr(WikipediaHtmlSanitizer.ATTR_DATA_TEX)
        )
    }

    @Test
    fun `falls back to alttext when annotation and data-mw are absent`() {
        val rawHtml = """
            <p>Energia:
              <span class="mwe-math-element mwe-math-element-inline">
                <span class="mwe-math-mathml-a11y" style="display: none;">
                  <math xmlns="http://www.w3.org/1998/Math/MathML" alttext="{\displaystyle E = mc^2}">
                    <semantics><mrow><mi>E</mi></mrow></semantics>
                  </math>
                </span>
              </span>
            </p>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)

        assertEquals(1, result.extractedFormulaCount)
        assertEquals(0, result.unextractableFormulaCount)
        val doc = Jsoup.parseBodyFragment(result.html)
        val mathSpan = doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")
        assertEquals("""{\displaystyle E = mc^2}""", mathSpan?.attr(WikipediaHtmlSanitizer.ATTR_DATA_TEX))
    }

    @Test
    fun `identifies inline math formula accurately`() {
        val rawHtml = """
            <p>O fator
              <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                data-mw='{"name":"math","attrs":{"display":"inline"},"body":{"extsrc":"\\gamma"}}'>
                <mi>γ</mi>
              </math>
              aparece na equação.
            </p>
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)
        val doc = Jsoup.parseBodyFragment(html)

        assertEquals(1, doc.select("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}").size)
        assertEquals(0, doc.select("div.${WikipediaHtmlSanitizer.CLASS_MATH_BLOCK}").size)
        assertEquals("""\gamma""", doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")?.attr("data-tex"))
    }

    @Test
    fun `identifies block math formula from Parsoid display block and Mathoid block classes`() {
        val rawHtml = """
            <p>
              <span class="mwe-math-element mwe-math-element-block" typeof="mw:Extension/math"
                data-mw='{"name":"math","attrs":{"display":"block"},"body":{"extsrc":"x^2 \\frac{d^2 y}{dx^2} + x \\frac{dy}{dx} + (x^2)y = 0"}}'>
                <span class="mwe-math-mathml-a11y"><math display="block"><mi>x</mi></math></span>
                <img src="https://wikimedia.org/api/rest_v1/media/math/render/svg/xyz" class="mwe-math-fallback-image-display">
              </span>
            </p>
            <div class="mwe-math-element mwe-math-element-block">
              <math display="block" alttext="a^2 + b^2 = c^2"><mi>a</mi></math>
            </div>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)
        val doc = Jsoup.parseBodyFragment(result.html)
        val blocks = doc.select("div.${WikipediaHtmlSanitizer.CLASS_MATH_BLOCK}")

        assertEquals(2, result.extractedFormulaCount)
        assertEquals(2, blocks.size)
        assertEquals(0, doc.select("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}").size)
        assertEquals(
            """x^2 \frac{d^2 y}{dx^2} + x \frac{dy}{dx} + (x^2)y = 0""",
            blocks[0].attr("data-tex")
        )
        assertEquals("a^2 + b^2 = c^2", blocks[1].attr("data-tex"))
    }

    @Test
    fun `converts Mathoid element without duplicating formula or retaining fallback image`() {
        val rawHtml = """
            <p>Equação:
              <span class="mwe-math-element mwe-math-element-inline">
                <span class="mwe-math-mathml-a11y" style="display: none;">
                  <math xmlns="http://www.w3.org/1998/Math/MathML" alttext="{\displaystyle \vec{F} = m\vec{a}}">
                    <semantics>
                      <mrow><mi>F</mi></mrow>
                      <annotation encoding="application/x-tex">{\displaystyle \vec{F} = m\vec{a}}</annotation>
                    </semantics>
                  </math>
                </span>
                <img src="https://wikimedia.org/api/rest_v1/media/math/render/svg/12345"
                     class="mwe-math-fallback-image-inline"
                     alt="{\displaystyle \vec{F} = m\vec{a}}">
              </span>
            </p>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)
        val doc = Jsoup.parseBodyFragment(result.html)

        assertEquals(1, result.extractedFormulaCount)
        assertEquals(1, doc.select("[data-tex]").size)
        assertEquals(0, doc.select("img").size)
    }

    @Test
    fun `preserves sub and sup tags in chemical and algebraic expressions`() {
        val rawHtml = """
            <p>H<sub>2(g)</sub> + O<sub>2(g)</sub> → H<sub>2</sub>O<sub>(l)</sub></p>
            <p>a<sub>1</sub>x<sub>1</sub> + x<sup>2</sup> = b</p>
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)
        val doc = Jsoup.parseBodyFragment(html)

        assertEquals(6, doc.select("sub").size)
        assertEquals(1, doc.select("sup").size)
        assertTrue(doc.text().contains("H2(g) + O2(g) → H2O(l)"))
        assertTrue(doc.text().contains("a1x1 + x2 = b"))
    }

    @Test
    fun `preserves texhtml and sfrac classes while stripping arbitrary Wikipedia classes`() {
        val rawHtml = """
            <p class="mw-paragraph custom-wiki-cls">
              Operadores <span class="texhtml mw-inline-symbol">×</span> e fração
              <span class="sfrac nowrap"><span class="num">3</span>/<span class="den">2</span></span>.
            </p>
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)
        val doc = Jsoup.parseBodyFragment(html)

        val texhtml = doc.selectFirst("span.texhtml")
        val sfrac = doc.selectFirst("span.sfrac")
        assertNotNull(texhtml)
        assertNotNull(sfrac)
        assertEquals("texhtml", texhtml?.attr("class"))
        assertEquals("sfrac", sfrac?.attr("class"))
        assertFalse(html.contains("mw-paragraph"))
        assertFalse(html.contains("custom-wiki-cls"))
        assertFalse(html.contains("mw-inline-symbol"))
        assertFalse(html.contains("nowrap"))
    }

    @Test
    fun `preserves tables, lists, headings and removes Wikipedia navigation and admin noise`() {
        val rawHtml = """
            <section>
              <div class="hatnote">Para outros significados, veja Matemática (desambiguação).</div>
              <table class="ambox"><tr><td>Este artigo precisa de fontes.</td></tr></table>
              <h2>Propriedades <span class="mw-editsection"><a href="/w/index.php?edit">editar</a></span></h2>
              <p>Texto científico importante.</p>
              <ul><li>Item 1</li><li>Item 2</li></ul>
              <ol start="3"><li>Item 3</li></ol>
              <table>
                <thead><tr><th scope="col">Grandeza</th><th scope="col">Unidade</th></tr></thead>
                <tbody><tr><td>Massa</td><td>kg</td></tr></tbody>
              </table>
              <div class="sidebar">Portal da Matemática</div>
              <div class="navbox">Caixa de navegação</div>
              <div class="mw-references-wrap"><ol class="references"><li>Ref 1</li></ol></div>
              <div class="reflist">Lista de referências</div>
              <div class="noprint">Imprimir</div>
            </section>
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)
        val doc = Jsoup.parseBodyFragment(html)

        assertNotNull(doc.selectFirst("section"))
        assertEquals("Propriedades", doc.selectFirst("h2")?.text()?.trim())
        assertEquals(2, doc.select("ul > li").size)
        assertEquals("3", doc.selectFirst("ol")?.attr("start"))
        assertEquals("Massa", doc.selectFirst("tbody td")?.text())
        assertFalse(html.contains("desambiguação"))
        assertFalse(html.contains("precisa de fontes"))
        assertFalse(html.contains("editar"))
        assertFalse(html.contains("Portal da Matemática"))
        assertFalse(html.contains("Caixa de navegação"))
        assertFalse(html.contains("Ref 1"))
        assertFalse(html.contains("Lista de referências"))
        assertFalse(html.contains("Imprimir"))
    }

    @Test
    fun `promotes single-formula dl dd to block and keeps multi-content dl dd inline`() {
        val rawHtml = """
            <dl>
              <dd>
                <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                  data-mw='{"name":"math","attrs":{},"body":{"extsrc":"H_2 + O_2 \\longrightarrow H_2O"}}'>
                  <mi>H</mi>
                </math>
              </dd>
            </dl>
            <dl>
              <dd>
                <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                  data-mw='{"name":"math","attrs":{},"body":{"extsrc":"E = \\gamma m c^2"}}'>
                  <mi>E</mi>
                </math>, onde
                <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                  data-mw='{"name":"math","attrs":{},"body":{"extsrc":"\\gamma = \\frac{1}{\\sqrt{1 - v^2/c^2}}"}}'>
                  <mi>γ</mi>
                </math>
              </dd>
            </dl>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)
        val doc = Jsoup.parseBodyFragment(result.html)

        assertEquals(3, result.extractedFormulaCount)
        val blocks = doc.select("div.${WikipediaHtmlSanitizer.CLASS_MATH_BLOCK}")
        val inlines = doc.select("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")
        assertEquals(1, blocks.size)
        assertEquals("""H_2 + O_2 \longrightarrow H_2O""", blocks.first()?.attr("data-tex"))
        assertEquals(2, inlines.size)
        assertEquals(1, doc.select("dl").size)
        assertTrue(doc.selectFirst("dl dd")?.text()?.contains("onde") == true)
    }

    @Test
    fun `removes script, iframe, object, embed, style and link tags`() {
        val rawHtml = """
            <link rel="stylesheet" href="https://pt.wikipedia.org/style.css">
            <style>.hack { color: red; }</style>
            <p>Conteúdo seguro.</p>
            <script>alert('xss')</script>
            <iframe src="https://evil.example.com"></iframe>
            <object data="evil.swf"></object>
            <embed src="evil.swf">
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)

        assertTrue(html.contains("Conteúdo seguro."))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("<iframe"))
        assertFalse(html.contains("<object"))
        assertFalse(html.contains("<embed"))
        assertFalse(html.contains("<style"))
        assertFalse(html.contains("<link"))
        assertFalse(html.contains("alert"))
    }

    @Test
    fun `removes inline event handlers and style attributes`() {
        val rawHtml = """
            <p style="color: red;" onclick="alert(1)" onmouseover="steal()">Texto limpo</p>
            <img src="https://upload.wikimedia.org/img.png" alt="Figura" onerror="alert(2)" style="width:100px">
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)
        val doc = Jsoup.parseBodyFragment(html)

        assertFalse(html.contains("onclick"))
        assertFalse(html.contains("onmouseover"))
        assertFalse(html.contains("onerror"))
        assertFalse(html.contains("style="))
        assertEquals("Texto limpo", doc.selectFirst("p")?.text())
        assertEquals("https://upload.wikimedia.org/img.png", doc.selectFirst("img")?.attr("src"))
    }

    @Test
    fun `neutralizes javascript and data URLs while preserving link text`() {
        val rawHtml = """
            <p>Veja <a href="javascript:alert('xss')">este aviso</a> e
            <a href="  JAVASCRIPT:void(0)">outro link</a>.</p>
            <img src="javascript:alert(1)" alt="Maliciosa">
            <img src="data:text/html,<script>alert(1)</script>" alt="Data">
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)
        val doc = Jsoup.parseBodyFragment(html)

        assertFalse(html.lowercase().contains("javascript:"))
        assertFalse(html.lowercase().contains("data:text/html"))
        assertEquals(0, doc.select("a").size)
        assertEquals(0, doc.select("img").size)
        assertTrue(doc.text().contains("este aviso"))
        assertTrue(doc.text().contains("outro link"))
    }

    @Test
    fun `preserves safe HTTPS links and resolves relative Wikipedia links`() {
        val rawHtml = """
            <p>
              Consulte <a href="https://pt.wikipedia.org/wiki/C%C3%A1lculo">Cálculo</a>,
              <a href="/wiki/Integral">Integral</a> e
              <a href="./Derivada">Derivada</a>.
            </p>
            <figure>
              <img src="//upload.wikimedia.org/wikipedia/commons/a/ab/Graph.png" alt="Gráfico">
              <figcaption>Curva ilustrativa (CC BY-SA)</figcaption>
            </figure>
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)
        val doc = Jsoup.parseBodyFragment(html)
        val links = doc.select("a")

        assertEquals(3, links.size)
        assertEquals("https://pt.wikipedia.org/wiki/C%C3%A1lculo", links[0].attr("href"))
        assertEquals("https://pt.wikipedia.org/wiki/Integral", links[1].attr("href"))
        assertEquals("https://pt.wikipedia.org/wiki/Derivada", links[2].attr("href"))
        links.forEach { link ->
            assertEquals("noopener noreferrer", link.attr("rel"))
        }
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/a/ab/Graph.png",
            doc.selectFirst("img")?.attr("src")
        )
        assertEquals("Curva ilustrativa (CC BY-SA)", doc.selectFirst("figcaption")?.text())
    }

    @Test
    fun `preserves Wikipedia macros Z, Q, R, N and C intact for KaTeX configuration`() {
        val rawHtml = """
            <p>Conjuntos:
              <math typeof="mw:Extension/math" data-mw='{"name":"math","attrs":{},"body":{"extsrc":"(\\Z), (\\Q), (\\R), (\\N), (\\C)"}}'><mi>Z</mi></math>
            </p>
        """.trimIndent()

        val html = sanitizer.sanitize(rawHtml)
        val doc = Jsoup.parseBodyFragment(html)
        val tex = doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")?.attr("data-tex")

        assertEquals("""(\Z), (\Q), (\R), (\N), (\C)""", tex)
    }

    @Test
    fun `normalizes align environment and legacy rm syntax without altering other commands`() {
        val rawHtml = """
            <p>
              <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                data-mw='{"name":"math","attrs":{},"body":{"extsrc":"\n\\begin{align}\n  &2x + 2y y\u0027 = 0 \\\\\n  &y\u0027 = -\\frac{x}{y}\n\\end{align}\n"}}'>
              </math>
              e
              <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                data-mw='{"name":"math","attrs":{},"body":{"extsrc":"\\rm{} \\frac{2.00 \\ g \\ NaCl}{58.44 \\ g \\ NaCl \\ \\rm{mol}^{-1}} = 0.034 \\ mol"}}'>
              </math>
            </p>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)
        val doc = Jsoup.parseBodyFragment(result.html)
        val formulas = doc.select("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")

        assertEquals(2, formulas.size)
        val alignNormalized = formulas[0].attr("data-tex")
        assertTrue(alignNormalized.contains("""\begin{aligned}"""))
        assertTrue(alignNormalized.contains("""\end{aligned}"""))
        assertFalse(alignNormalized.contains("""\begin{align}"""))
        assertTrue(alignNormalized.contains("""\frac{x}{y}"""))

        val rmNormalized = formulas[1].attr("data-tex")
        assertFalse(rmNormalized.contains("""\rm{}"""))
        assertTrue(rmNormalized.startsWith("""\frac{2.00"""))
        assertTrue(rmNormalized.contains("""\mathrm{mol}^{-1}"""))
    }

    @Test
    fun `handles invalid JSON in data-mw gracefully and marks formula unavailable`() {
        val rawHtml = """
            <p>Fórmula quebrada:
              <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                data-mw='{not-valid-json'>
                <mi>x</mi>
              </math>
            </p>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)
        val doc = Jsoup.parseBodyFragment(result.html)
        val marker = doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_UNAVAILABLE}")

        assertEquals(0, result.extractedFormulaCount)
        assertEquals(1, result.unextractableFormulaCount)
        assertNotNull(marker)
        assertEquals(WikipediaHtmlSanitizer.STATUS_UNAVAILABLE, marker?.attr(WikipediaHtmlSanitizer.ATTR_DATA_MATH_STATUS))
        assertEquals(WikipediaHtmlSanitizer.UNAVAILABLE_FORMULA_TEXT, marker?.text())
    }

    @Test
    fun `handles math element without extsrc, annotation or alttext without inventing LaTeX`() {
        val rawHtml = """
            <p>Sem anotação:
              <math class="mwe-math-element mwe-math-element-inline" xmlns="http://www.w3.org/1998/Math/MathML">
                <mrow><mfrac><mn>3</mn><mn>2</mn></mfrac></mrow>
              </math>
            </p>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)
        val doc = Jsoup.parseBodyFragment(result.html)
        val marker = doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_UNAVAILABLE}")

        assertEquals(0, result.extractedFormulaCount)
        assertEquals(1, result.unextractableFormulaCount)
        assertNotNull(marker)
        assertEquals(0, doc.select("[data-tex]").size)
    }

    @Test
    fun `handles empty, blank and malformed HTML safely`() {
        val emptyResult = sanitizer.sanitizeWithMetadata("")
        assertEquals("", emptyResult.html)
        assertEquals(0, emptyResult.extractedFormulaCount)
        assertEquals(0, emptyResult.unextractableFormulaCount)

        val blankResult = sanitizer.sanitizeWithMetadata("   \n\t  ")
        assertEquals("", blankResult.html)

        val malformed = sanitizer.sanitize("<p>Texto <b>sem fechar <i>aninhado")
        val doc = Jsoup.parseBodyFragment(malformed)
        assertTrue(doc.text().contains("Texto sem fechar aninhado"))
    }

    @Test
    fun `prevents silent loss of formulas and safely escapes HTML operators inside data-tex`() {
        val rawHtml = """
            <section>
              <p>Desigualdade
                <math typeof="mw:Extension/math"
                  data-mw='{"name":"math","attrs":{},"body":{"extsrc":"a < b & c > \"d\""}}'>
                </math>
                e integral
                <math typeof="mw:Extension/math"
                  data-mw='{"name":"math","attrs":{"display":"block"},"body":{"extsrc":"\\int_0^\\infty e^{-x} dx = 1"}}'>
                </math>
              </p>
              <math class="mwe-math-element"><mrow><mi>y</mi></mrow></math>
            </section>
        """.trimIndent()

        val result = sanitizer.sanitizeWithMetadata(rawHtml)
        val totalPlaceholders = result.extractedFormulaCount + result.unextractableFormulaCount

        assertEquals(3, totalPlaceholders)
        assertEquals(2, result.extractedFormulaCount)
        assertEquals(1, result.unextractableFormulaCount)

        // Verifica que operadores HTML na expressão são escapados no HTML serializado
        // e recuperados com exatidão no atributo data-tex.
        assertFalse(result.html.contains("a < b & c >"))
        val doc = Jsoup.parseBodyFragment(result.html)
        assertEquals(
            """a < b & c > "d"""",
            doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")?.attr("data-tex")
        )
        assertEquals(
            """\int_0^\infty e^{-x} dx = 1""",
            doc.selectFirst("div.${WikipediaHtmlSanitizer.CLASS_MATH_BLOCK}")?.attr("data-tex")
        )
    }
}
