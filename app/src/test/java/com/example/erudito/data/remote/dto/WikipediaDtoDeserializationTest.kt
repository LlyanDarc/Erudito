package com.example.erudito.data.remote.dto

import com.example.erudito.di.EruditoHiltModule
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaDtoDeserializationTest {

    private val moshi: Moshi = EruditoHiltModule.provideMoshi()
    private val adapter = moshi.adapter(WikipediaApiResponseDto::class.java)

    @Test
    fun `deserializes full search response with optional images and descriptions`() {
        val json = """
            {
              "batchcomplete": true,
              "query": {
                "pages": [
                  {
                    "pageid": 12345,
                    "ns": 0,
                    "title": "Renascimento",
                    "index": 1,
                    "thumbnail": {
                      "source": "https://upload.wikimedia.org/wikipedia/commons/thumb/renascimento.jpg",
                      "width": 320,
                      "height": 240
                    },
                    "description": "Movimento cultural europeu",
                    "descriptionsource": "central",
                    "fullurl": "https://pt.wikipedia.org/wiki/Renascimento",
                    "extract": "O Renascimento foi um movimento cultural..."
                  },
                  {
                    "pageid": 67890,
                    "ns": 0,
                    "title": "Artigo Sem Imagem Nem Descricao",
                    "index": 2,
                    "fullurl": "https://pt.wikipedia.org/wiki/Artigo_Sem_Imagem_Nem_Descricao"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertEquals(true, result?.batchComplete)
        assertNull(result?.error)

        val pages = result?.query?.pages.orEmpty()
        assertEquals(2, pages.size)

        val first = pages[0]
        assertEquals(12345L, first.pageId)
        assertEquals("Renascimento", first.title)
        assertEquals("Movimento cultural europeu", first.description)
        assertEquals("O Renascimento foi um movimento cultural...", first.extract)
        assertEquals("https://pt.wikipedia.org/wiki/Renascimento", first.fullUrl)
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/thumb/renascimento.jpg",
            first.thumbnail?.source
        )
        assertEquals(320, first.thumbnail?.width)
        assertEquals(240, first.thumbnail?.height)

        val second = pages[1]
        assertEquals(67890L, second.pageId)
        assertEquals("Artigo Sem Imagem Nem Descricao", second.title)
        assertNull(second.thumbnail)
        assertNull(second.original)
        assertNull(second.description)
        assertNull(second.extract)
    }

    @Test
    fun `deserializes empty search result when query object is omitted by MediaWiki`() {
        val json = """
            {
              "batchcomplete": true
            }
        """.trimIndent()

        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertEquals(true, result?.batchComplete)
        assertNull(result?.query)
        assertNull(result?.error)
    }

    @Test
    fun `deserializes empty pages list when query contains no pages`() {
        val json = """
            {
              "batchcomplete": true,
              "query": {
                "pages": []
              }
            }
        """.trimIndent()

        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertNotNull(result?.query)
        assertTrue(result?.query?.pages.orEmpty().isEmpty())
    }

    @Test
    fun `deserializes article detail response with original image and full extract`() {
        val json = """
            {
              "batchcomplete": true,
              "query": {
                "pages": [
                  {
                    "pageid": 9988,
                    "ns": 0,
                    "title": "Teoria da relatividade",
                    "description": "Conjunto de teorias físicas de Albert Einstein",
                    "extract": "A teoria da relatividade engloba a relatividade restrita e a relatividade geral.",
                    "original": {
                      "source": "https://upload.wikimedia.org/wikipedia/commons/einstein.jpg",
                      "width": 1200,
                      "height": 900
                    },
                    "thumbnail": {
                      "source": "https://upload.wikimedia.org/wikipedia/commons/thumb/einstein.jpg",
                      "width": 800,
                      "height": 600
                    },
                    "fullurl": "https://pt.wikipedia.org/wiki/Teoria_da_relatividade",
                    "canonicalurl": "https://pt.wikipedia.org/wiki/Teoria_da_relatividade"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = adapter.fromJson(json)
        val page = result?.query?.pages?.singleOrNull()

        assertNotNull(page)
        assertEquals(9988L, page?.pageId)
        assertEquals("Teoria da relatividade", page?.title)
        assertEquals("Conjunto de teorias físicas de Albert Einstein", page?.description)
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/einstein.jpg",
            page?.original?.source
        )
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/thumb/einstein.jpg",
            page?.thumbnail?.source
        )
        assertEquals("https://pt.wikipedia.org/wiki/Teoria_da_relatividade", page?.fullUrl)
        assertEquals("https://pt.wikipedia.org/wiki/Teoria_da_relatividade", page?.canonicalUrl)
    }

    @Test
    fun `deserializes missing article page without throwing`() {
        val json = """
            {
              "batchcomplete": true,
              "query": {
                "pages": [
                  {
                    "pageid": 999999999,
                    "missing": true
                  }
                ]
              }
            }
        """.trimIndent()

        val result = adapter.fromJson(json)
        val page = result?.query?.pages?.singleOrNull()

        assertNotNull(page)
        assertEquals(999999999L, page?.pageId)
        assertEquals(true, page?.missing)
        assertNull(page?.title)
        assertNull(page?.extract)
        assertNull(page?.thumbnail)
    }

    @Test
    fun `deserializes MediaWiki API error payload`() {
        val json = """
            {
              "error": {
                "code": "badquery",
                "info": "Invalid search query."
              }
            }
        """.trimIndent()

        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertNull(result?.query)
        assertEquals("badquery", result?.error?.code)
        assertEquals("Invalid search query.", result?.error?.info)
    }

    @Test
    fun `network module configures Retrofit baseUrl and Wikimedia User-Agent header`() {
        val loggingInterceptor = EruditoHiltModule.provideHttpLoggingInterceptor()
        val okHttpClient = EruditoHiltModule.provideOkHttpClient(loggingInterceptor)
        val retrofit = EruditoHiltModule.provideRetrofit(okHttpClient, moshi)
        val service = EruditoHiltModule.provideWikipediaApiService(retrofit)

        assertEquals("https://pt.wikipedia.org/", retrofit.baseUrl().toString())
        assertNotNull(service)

        var capturedRequest: Request? = null
        val testClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{\"batchcomplete\":true}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val request = Request.Builder()
            .url("https://pt.wikipedia.org/w/api.php?action=query&format=json")
            .build()

        testClient.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }

        val userAgent = capturedRequest?.header("User-Agent")
        assertNotNull(userAgent)
        assertTrue(userAgent!!.contains("Erudito/"))
        assertEquals("application/json", capturedRequest?.header("Accept"))
    }
}
