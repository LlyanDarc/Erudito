package com.example.erudito.data.remote.api

import com.example.erudito.data.remote.dto.WikipediaApiResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface WikipediaApiService {

    @GET("w/api.php")
    suspend fun searchArticles(
        @Query("gsrsearch") query: String,
        @Query("gsrlimit") limit: Int = 20,
        @Query("gsrnamespace") namespace: Int = 0,
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
        @Query("generator") generator: String = "search",
        @Query("prop") prop: String = "pageimages|description|info|extracts",
        @Query("piprop") piProp: String = "thumbnail",
        @Query("pithumbsize") piThumbSize: Int = 320,
        @Query("pilimit") piLimit: Int = 20,
        @Query("inprop") inProp: String = "url",
        @Query("exintro") exIntro: Int = 1,
        @Query("explaintext") explainText: Int = 1,
        @Query("exsentences") exSentences: Int = 2,
        @Query("exlimit") exLimit: Int = 20,
        @Query("redirects") redirects: Int = 1
    ): WikipediaApiResponseDto

    @GET("w/api.php")
    suspend fun getArticleByPageId(
        @Query("pageids") pageId: Long,
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
        @Query("prop") prop: String = "pageimages|description|info",
        @Query("piprop") piProp: String = "original|thumbnail",
        @Query("pithumbsize") piThumbSize: Int = 800,
        @Query("inprop") inProp: String = "url",
        @Query("redirects") redirects: Int = 1
    ): WikipediaApiResponseDto

    @Headers("Accept: text/html; charset=utf-8")
    @GET("w/rest.php/v1/page/{title}/html")
    suspend fun getArticleHtml(
        @Path("title") title: String
    ): Response<ResponseBody>
}
