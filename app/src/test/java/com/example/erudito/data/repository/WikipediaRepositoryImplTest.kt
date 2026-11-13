package com.example.erudito.data.repository

import com.example.erudito.data.remote.api.WikipediaApiService
import com.example.erudito.data.remote.dto.WikipediaApiErrorDto
import com.example.erudito.data.remote.dto.WikipediaApiResponseDto
import com.example.erudito.data.remote.dto.WikipediaImageDto
import com.example.erudito.data.remote.dto.WikipediaPageDto
import com.example.erudito.data.remote.dto.WikipediaQueryDto
import com.example.erudito.domain.model.WikipediaException
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class WikipediaRepositoryImplTest {

    @Test
    fun `searchArticles maps pages ordered by search index and handles missing optional fields`() = runTest {
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
    fun `getArticleByPageId maps complete article preferring original image and handles optional fields`() = runTest {
        val fakeService = FakeWikipediaApiService(
            articleByIdHandler = {
                WikipediaApiResponseDto(
                    batchComplete = true,
                    query = WikipediaQueryDto(
                        pages = listOf(
                            WikipediaPageDto(
                                pageId = 55L,
                                title = "Albert Einstein",
                                description = null,
                                extract = "Albert Einstein foi um físico teórico nascido na Alemanha.",
                                original = WikipediaImageDto("https://upload.wikimedia.org/einstein_orig.jpg"),
                                thumbnail = WikipediaImageDto("https://upload.wikimedia.org/einstein_thumb.jpg"),
                                fullUrl = "https://pt.wikipedia.org/wiki/Albert_Einstein"
                            )
                        )
                    )
                )
            }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        val article = repository.getArticleByPageId(55L)

        assertNotNull(article)
        assertEquals(55L, article?.pageId)
        assertEquals("Albert Einstein", article?.title)
        assertNull(article?.description)
        assertEquals("Albert Einstein foi um físico teórico nascido na Alemanha.", article?.content)
        assertEquals("https://upload.wikimedia.org/einstein_orig.jpg", article?.imageUrl)
        assertEquals("https://pt.wikipedia.org/wiki/Albert_Einstein", article?.url)
    }

    @Test
    fun `getArticleByPageId returns null when article is marked as missing`() = runTest {
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
            }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        val article = repository.getArticleByPageId(9999L)

        assertNull(article)
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
    fun `searchArticles converts HttpException into HttpError domain exception`() = runTest {
        val httpErrorResponse = Response.error<WikipediaApiResponseDto>(
            429,
            "Too Many Requests".toResponseBody("text/plain".toMediaType())
        )
        val fakeService = FakeWikipediaApiService(
            searchHandler = { throw HttpException(httpErrorResponse) }
        )
        val repository = WikipediaRepositoryImpl(fakeService)

        try {
            repository.searchArticles("Física")
            fail("Deveria ter lançado WikipediaException.HttpError")
        } catch (e: WikipediaException.HttpError) {
            assertEquals(429, e.statusCode)
            assertTrue(e.message.contains("Muitas requisições"))
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

    private class FakeWikipediaApiService(
        private val searchHandler: suspend (String) -> WikipediaApiResponseDto = {
            WikipediaApiResponseDto()
        },
        private val articleByIdHandler: suspend (Long) -> WikipediaApiResponseDto = {
            WikipediaApiResponseDto()
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
            explainText: Int,
            piProp: String,
            piThumbSize: Int,
            inProp: String,
            redirects: Int
        ): WikipediaApiResponseDto = articleByIdHandler(pageId)

        override suspend fun getArticleByTitle(
            title: String,
            action: String,
            format: String,
            formatVersion: Int,
            prop: String,
            explainText: Int,
            piProp: String,
            piThumbSize: Int,
            inProp: String,
            redirects: Int
        ): WikipediaApiResponseDto = WikipediaApiResponseDto()
    }
}
