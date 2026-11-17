package com.example.erudito.data.repository

import com.example.erudito.data.remote.api.WikipediaApiService
import com.example.erudito.data.remote.dto.WikipediaApiErrorDto
import com.example.erudito.data.remote.dto.WikipediaApiResponseDto
import com.example.erudito.data.remote.dto.WikipediaImageDto
import com.example.erudito.data.remote.dto.WikipediaPageDto
import com.example.erudito.data.remote.dto.WikipediaQueryDto
import com.example.erudito.data.remote.parser.SanitizedWikipediaHtml
import com.example.erudito.data.remote.parser.WikipediaHtmlSanitizer
import com.example.erudito.di.EruditoHiltModule
import com.example.erudito.domain.model.ArticleContentFormat
import com.example.erudito.domain.model.WikipediaException
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class WikipediaRepositoryImplTest {

    @Test
    fun `searchArticles maps pages ordered by search index as plain text snippets`() = runTest {
        val fakeService = FakeWikipediaApiService(
            searchHandler = {
                WikipediaApiResponseDto(
                    batchComplete = true,
                    query = WikipediaQueryDto(
                        pages = listOf(
                            WikipediaPageDto(
                                pageId = 20L,
                                title = "História da Filosofia",
                                index = 2,
                                description = null,
                                extract = null,
                                thumbnail = null,
                                fullUrl = null
                            ),
                            WikipediaPageDto(
                                pageId = 10L,
                                title = "Filosofia",
                                index = 1,
                                description = "Estudo de questões fundamentais",
                                extract = "Filosofia é o estudo de questões gerais e fundamentais.",
                                thumbnail = WikipediaImageDto("https://upload.wikimedia.org/filosofia.jpg"),
                                fullUrl = "https://pt.wikipedia.org/wiki/Filosofia"
                            ),
                            WikipediaPageDto(
                                pageId = 30L,
                                title = "Página Removida",
                                index = 3,
                                missing = true
                            )
                        )
                    )
                )
            }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        val results = repository.searchArticles("Filosofia")

        assertEquals(2, results.size)
        assertEquals(10L, results[0].pageId)
        assertEquals("Filosofia", results[0].title)
        assertEquals("Estudo de questões fundamentais", results[0].description)
        assertEquals("Filosofia é o estudo de questões gerais e fundamentais.", results[0].snippet)
        assertFalse(results[0].snippet.orEmpty().contains("<span"))
        assertEquals("https://upload.wikimedia.org/filosofia.jpg", results[0].thumbnailUrl)
        assertEquals("https://pt.wikipedia.org/wiki/Filosofia", results[0].url)

        assertEquals(20L, results[1].pageId)
        assertEquals("História da Filosofia", results[1].title)
        assertNull(results[1].description)
        assertNull(results[1].snippet)
        assertNull(results[1].thumbnailUrl)
        assertTrue(results[1].url.startsWith("https://pt.wikipedia.org/wiki/"))
    }

    @Test
    fun `searchArticles returns empty list when query payload is absent`() = runTest {
        val fakeService = FakeWikipediaApiService(
            searchHandler = { WikipediaApiResponseDto(batchComplete = true, query = null) }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        val results = repository.searchArticles("xyzabc")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `getArticleByPageId fetches metadata then Parsoid HTML, sanitizes formulas, and preserves metadata`() = runTest {
        var requestedParsoidTitle: String? = null
        val parsoidHtml = """
            <section>
              <script>alert('xss')</script>
              <div class="hatnote">Desambiguação</div>
              <p>A energia relativística satisfaz
                <math class="mwe-math-element mwe-math-element-inline" typeof="mw:Extension/math"
                  data-mw='{"name":"math","attrs":{},"body":{"extsrc":"E = \\gamma m c^2"}}'>
                  <mi>E</mi>
                </math>.
              </p>
              <math class="mwe-math-element"><mrow><mi>z</mi></mrow></math>
            </section>
        """.trimIndent()

        val fakeService = FakeWikipediaApiService(
            articleByIdHandler = {
                WikipediaApiResponseDto(
                    batchComplete = true,
                    query = WikipediaQueryDto(
                        pages = listOf(
                            WikipediaPageDto(
                                pageId = 9541L,
                                title = "Relatividade restrita",
                                description = "Teoria física de Albert Einstein",
                                original = WikipediaImageDto("https://upload.wikimedia.org/einstein_orig.jpg"),
                                thumbnail = WikipediaImageDto("https://upload.wikimedia.org/einstein_thumb.jpg"),
                                fullUrl = "https://pt.wikipedia.org/wiki/Relatividade_restrita"
                            )
                        )
                    )
                )
            },
            articleHtmlHandler = { title ->
                requestedParsoidTitle = title
                Response.success(parsoidHtml.toResponseBody("text/html; charset=utf-8".toMediaType()))
            }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        val article = repository.getArticleByPageId(9541L)

        assertEquals("Relatividade_restrita", requestedParsoidTitle)
        assertNotNull(article)
        assertEquals(9541L, article?.pageId)
        assertEquals("Relatividade restrita", article?.title)
        assertEquals("Teoria física de Albert Einstein", article?.description)
        assertEquals("https://upload.wikimedia.org/einstein_orig.jpg", article?.imageUrl)
        assertEquals("https://pt.wikipedia.org/wiki/Relatividade_restrita", article?.url)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, article?.contentFormat)
        assertTrue(article?.hasExtractedContent == true)
        assertEquals(1, article?.extractedFormulaCount)
        assertEquals(1, article?.unextractableFormulaCount)
        assertTrue(article?.hasUnextractableFormulas == true)
        assertFalse(article?.isContentIntegrityComplete == true)

        val content = article!!.content
        assertFalse(content.contains("<script"))
        assertFalse(content.contains("Desambiguação"))
        val doc = Jsoup.parseBodyFragment(content)
        assertEquals(
            """E = \gamma m c^2""",
            doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_INLINE}")?.attr("data-tex")
        )
        assertNotNull(doc.selectFirst("span.${WikipediaHtmlSanitizer.CLASS_MATH_UNAVAILABLE}"))
    }

    @Test
    fun `getArticleByPageId constructs Parsoid URL without double encoding spaces, accents and special characters`() = runTest {
        val capturedRequests = mutableListOf<Request>()
        val loggingInterceptor = EruditoHiltModule.provideHttpLoggingInterceptor()
        val baseClient = EruditoHiltModule.provideOkHttpClient(loggingInterceptor)
        val moshi = EruditoHiltModule.provideMoshi()

        val testClient = baseClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedRequests.add(request)
                val path = request.url.encodedPath
                val body = when {
                    path.endsWith("/w/api.php") -> {
                        val requestedId = request.url.queryParameter("pageids")?.toLongOrNull() ?: 1L
                        val resolvedTitle = when (requestedId) {
                            6321L -> "Equação diferencial"
                            7001L -> "TCP/IP & C++"
                            else -> "100% Álgebra"
                        }
                        """
                        {
                          "batchcomplete": true,
                          "query": {
                            "pages": [
                              {
                                "pageid": $requestedId,
                                "title": "$resolvedTitle",
                                "description": "Artigo científico",
                                "fullurl": "https://pt.wikipedia.org/wiki/Test"
                              }
                            ]
                          }
                        }
                        """.trimIndent().toResponseBody("application/json; charset=utf-8".toMediaType())
                    }
                    path.contains("/w/rest.php/v1/page/") -> {
                        "<p>Conteúdo HTML válido</p>".toResponseBody("text/html; charset=utf-8".toMediaType())
                    }
                    else -> fail("Caminho inesperado: $path").let { "".toResponseBody(null) }
                }
                OkHttpResponse.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()

        val retrofit = EruditoHiltModule.provideRetrofit(testClient, moshi)
        val service = EruditoHiltModule.provideWikipediaApiService(retrofit)
        val repository = WikipediaRepositoryImpl(service, WikipediaHtmlSanitizer(moshi))

        val eqArticle = repository.getArticleByPageId(6321L)
        assertNotNull(eqArticle)
        assertEquals(ArticleContentFormat.STRUCTURED_HTML, eqArticle?.contentFormat)
        assertTrue(eqArticle?.isContentIntegrityComplete == true)

        repository.getArticleByPageId(7001L)
        repository.getArticleByPageId(8001L)

        val parsoidRequests = capturedRequests.filter { it.url.encodedPath.contains("/w/rest.php/v1/page/") }
        assertEquals(3, parsoidRequests.size)

        assertEquals("/w/rest.php/v1/page/Equa%C3%A7%C3%A3o_diferencial/html", parsoidRequests[0].url.encodedPath)
        assertFalse(parsoidRequests[0].url.encodedPath.contains("%25C3"))
        assertEquals("text/html; charset=utf-8", parsoidRequests[0].header("Accept"))
        assertTrue(parsoidRequests[0].header("User-Agent").orEmpty().contains("Erudito/"))

        assertEquals("/w/rest.php/v1/page/TCP%2FIP_&_C++/html", parsoidRequests[1].url.encodedPath)
        assertEquals("/w/rest.php/v1/page/100%25_%C3%81lgebra/html", parsoidRequests[2].url.encodedPath)
        assertFalse(parsoidRequests[2].url.encodedPath.contains("%2525"))

        val apiRequests = capturedRequests.filter { it.url.encodedPath.endsWith("/w/api.php") }
        assertEquals(3, apiRequests.size)
        assertEquals("application/json", apiRequests[0].header("Accept"))
        assertNull(apiRequests[0].url.queryParameter("explaintext"))
        assertEquals("pageimages|description|info", apiRequests[0].url.queryParameter("prop"))
    }

    @Test
    fun `getArticleByPageId returns null when article is missing without requesting Parsoid HTML`() = runTest {
        var parsoidCalled = false
        val fakeService = FakeWikipediaApiService(
            articleByIdHandler = {
                WikipediaApiResponseDto(
                    batchComplete = true,
                    query = WikipediaQueryDto(
                        pages = listOf(
                            WikipediaPageDto(pageId = 9999L, missing = true)
                        )
                    )
                )
            },
            articleHtmlHandler = {
                parsoidCalled = true
                Response.success("<p>Não deveria chamar</p>".toResponseBody("text/html".toMediaType()))
            }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        val article = repository.getArticleByPageId(9999L)

        assertNull(article)
        assertFalse(parsoidCalled)
    }

    @Test
    fun `getArticleByPageId throws HttpError when Parsoid returns non-2xx HTTP status`() = runTest {
        val fakeService = FakeWikipediaApiService(
            articleByIdHandler = { metadataResponse(55L, "Albert Einstein") },
            articleHtmlHandler = {
                Response.error(
                    503,
                    "Service Unavailable".toResponseBody("text/plain".toMediaType())
                )
            }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        try {
            repository.getArticleByPageId(55L)
            fail("Deveria ter lançado WikipediaException.HttpError")
        } catch (e: WikipediaException.HttpError) {
            assertEquals(503, e.statusCode)
            assertTrue(e.message.contains("503"))
        }
    }

    @Test
    fun `getArticleByPageId throws EmptyContent when Parsoid returns blank HTML or only unsafe tags`() = runTest {
        val blankService = FakeWikipediaApiService(
            articleByIdHandler = { metadataResponse(1L, "Artigo Vazio") },
            articleHtmlHandler = {
                Response.success("   \n  ".toResponseBody("text/html".toMediaType()))
            }
        )
        val blankRepo = WikipediaRepositoryImpl(blankService)
        try {
            blankRepo.getArticleByPageId(1L)
            fail("Deveria ter lançado WikipediaException.EmptyContent")
        } catch (_: WikipediaException.EmptyContent) {
            // esperado
        }

        val onlyUnsafeService = FakeWikipediaApiService(
            articleByIdHandler = { metadataResponse(2L, "Somente Script") },
            articleHtmlHandler = {
                Response.success("<script>alert(1)</script><style>.a{}</style>".toResponseBody("text/html".toMediaType()))
            }
        )
        val onlyUnsafeRepo = WikipediaRepositoryImpl(onlyUnsafeService)
        try {
            onlyUnsafeRepo.getArticleByPageId(2L)
            fail("Deveria ter lançado WikipediaException.EmptyContent")
        } catch (_: WikipediaException.EmptyContent) {
            // esperado
        }
    }

    @Test
    fun `getArticleByPageId throws InvalidResponse when Parsoid returns non-HTML content type`() = runTest {
        val fakeService = FakeWikipediaApiService(
            articleByIdHandler = { metadataResponse(15L, "Física") },
            articleHtmlHandler = {
                Response.success(
                    """{"error":"unexpected"}""".toResponseBody("application/json".toMediaType())
                )
            }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        try {
            repository.getArticleByPageId(15L)
            fail("Deveria ter lançado WikipediaException.InvalidResponse")
        } catch (e: WikipediaException.InvalidResponse) {
            assertTrue(e.message.contains("formato inesperado"))
        }
    }

    @Test
    fun `getArticleByPageId throws NoConnection and Timeout when Parsoid network call fails`() = runTest {
        val noConnRepo = WikipediaRepositoryImpl(
            FakeWikipediaApiService(
                articleByIdHandler = { metadataResponse(10L, "Química") },
                articleHtmlHandler = { throw IOException("Network unreachable") }
            )
        )
        try {
            noConnRepo.getArticleByPageId(10L)
            fail("Deveria ter lançado WikipediaException.NoConnection")
        } catch (_: WikipediaException.NoConnection) {
            // esperado
        }

        val timeoutRepo = WikipediaRepositoryImpl(
            FakeWikipediaApiService(
                articleByIdHandler = { metadataResponse(10L, "Química") },
                articleHtmlHandler = { throw SocketTimeoutException("Parsoid read timed out") }
            )
        )
        try {
            timeoutRepo.getArticleByPageId(10L)
            fail("Deveria ter lançado WikipediaException.Timeout")
        } catch (_: WikipediaException.Timeout) {
            // esperado
        }
    }

    @Test
    fun `getArticleByPageId throws SanitizationError when sanitizer fails unexpectedly`() = runTest {
        val fakeService = FakeWikipediaApiService(
            articleByIdHandler = { metadataResponse(33L, "Cálculo") },
            articleHtmlHandler = {
                Response.success("<p>Conteúdo</p>".toResponseBody("text/html".toMediaType()))
            }
        )
        val failingSanitizer = object : WikipediaHtmlSanitizer() {
            override fun sanitizeWithMetadata(rawHtml: String): SanitizedWikipediaHtml {
                throw IllegalStateException("Falha simulada do parser")
            }
        }
        val repository = WikipediaRepositoryImpl(fakeService, failingSanitizer)

        try {
            repository.getArticleByPageId(33L)
            fail("Deveria ter lançado WikipediaException.SanitizationError")
        } catch (e: WikipediaException.SanitizationError) {
            assertTrue(e.cause is IllegalStateException)
        }
    }

    @Test
    fun `searchArticles converts IOException into NoConnection domain exception`() = runTest {
        val fakeService = FakeWikipediaApiService(
            searchHandler = { throw IOException("Unable to resolve host") }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        try {
            repository.searchArticles("Ciência")
            fail("Deveria ter lançado WikipediaException.NoConnection")
        } catch (e: WikipediaException.NoConnection) {
            assertEquals("Sem conexão com a internet. Verifique sua rede e tente novamente.", e.message)
        }
    }

    @Test
    fun `getArticleByPageId converts SocketTimeoutException into Timeout domain exception`() = runTest {
        val fakeService = FakeWikipediaApiService(
            articleByIdHandler = { throw SocketTimeoutException("timeout") }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        try {
            repository.getArticleByPageId(12L)
            fail("Deveria ter lançado WikipediaException.Timeout")
        } catch (e: WikipediaException.Timeout) {
            assertEquals("A consulta à Wikipédia demorou mais que o esperado. Tente novamente.", e.message)
        }
    }

    @Test
    fun `searchArticles converts MediaWiki API error payload into ApiError domain exception`() = runTest {
        val fakeService = FakeWikipediaApiService(
            searchHandler = {
                WikipediaApiResponseDto(
                    error = WikipediaApiErrorDto(
                        code = "badquery",
                        info = "Search term rejected"
                    )
                )
            }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        try {
            repository.searchArticles("???")
            fail("Deveria ter lançado WikipediaException.ApiError")
        } catch (e: WikipediaException.ApiError) {
            assertEquals("badquery", e.code)
            assertTrue(e.message.contains("Search term rejected"))
        }
    }

    @Test
    fun `getArticleByPageId converts JsonDataException into InvalidResponse domain exception`() = runTest {
        val fakeService = FakeWikipediaApiService(
            articleByIdHandler = { throw JsonDataException("Malformed JSON") }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        try {
            repository.getArticleByPageId(10L)
            fail("Deveria ter lançado WikipediaException.InvalidResponse")
        } catch (e: WikipediaException.InvalidResponse) {
            assertEquals("Não foi possível interpretar a resposta da Wikipédia.", e.message)
        }
    }

    private fun metadataResponse(pageId: Long, title: String): WikipediaApiResponseDto {
        return WikipediaApiResponseDto(
            batchComplete = true,
            query = WikipediaQueryDto(
                pages = listOf(
                    WikipediaPageDto(
                        pageId = pageId,
                        title = title,
                        description = "Descrição de $title",
                        fullUrl = "https://pt.wikipedia.org/wiki/${title.replace(' ', '_')}"
                    )
                )
            )
        )
    }

    private class FakeWikipediaApiService(
        private val searchHandler: suspend (String) -> WikipediaApiResponseDto = {
            WikipediaApiResponseDto()
        },
        private val articleByIdHandler: suspend (Long) -> WikipediaApiResponseDto = {
            WikipediaApiResponseDto()
        },
        private val articleHtmlHandler: suspend (String) -> Response<ResponseBody> = {
            Response.success("<p>Conteúdo padrão</p>".toResponseBody("text/html".toMediaType()))
        }
    ) : WikipediaApiService {

        override suspend fun searchArticles(
            query: String,
            limit: Int,
            namespace: Int,
            action: String,
            format: String,
            formatVersion: Int,
            generator: String,
            prop: String,
            piProp: String,
            piThumbSize: Int,
            piLimit: Int,
            inProp: String,
            exIntro: Int,
            explainText: Int,
            exSentences: Int,
            exLimit: Int,
            redirects: Int
        ): WikipediaApiResponseDto = searchHandler(query)

        override suspend fun getArticleByPageId(
            pageId: Long,
            action: String,
            format: String,
            formatVersion: Int,
            prop: String,
            piProp: String,
            piThumbSize: Int,
            inProp: String,
            redirects: Int
        ): WikipediaApiResponseDto = articleByIdHandler(pageId)

        override suspend fun getArticleHtml(title: String): Response<ResponseBody> =
            articleHtmlHandler(title)

        override suspend fun getArticleByTitle(
            title: String,
            action: String,
            format: String,
            formatVersion: Int,
            prop: String,
            piProp: String,
            piThumbSize: Int,
            inProp: String,
            redirects: Int
        ): WikipediaApiResponseDto = WikipediaApiResponseDto()
    }
}
