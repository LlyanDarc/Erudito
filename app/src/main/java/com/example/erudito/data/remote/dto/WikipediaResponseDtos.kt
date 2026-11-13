package com.example.erudito.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WikipediaApiResponseDto(
    @param:Json(name = "batchcomplete")
    val batchComplete: Boolean? = null,
    @param:Json(name = "query")
    val query: WikipediaQueryDto? = null,
    @param:Json(name = "error")
    val error: WikipediaApiErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaQueryDto(
    @param:Json(name = "pages")
    val pages: List<WikipediaPageDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WikipediaPageDto(
    @param:Json(name = "pageid")
    val pageId: Long? = null,
    @param:Json(name = "ns")
    val namespace: Int? = null,
    @param:Json(name = "title")
    val title: String? = null,
    @param:Json(name = "index")
    val index: Int? = null,
    @param:Json(name = "missing")
    val missing: Boolean? = null,
    @param:Json(name = "description")
    val description: String? = null,
    @param:Json(name = "descriptionsource")
    val descriptionSource: String? = null,
    @param:Json(name = "extract")
    val extract: String? = null,
    @param:Json(name = "thumbnail")
    val thumbnail: WikipediaImageDto? = null,
    @param:Json(name = "original")
    val original: WikipediaImageDto? = null,
    @param:Json(name = "fullurl")
    val fullUrl: String? = null,
    @param:Json(name = "canonicalurl")
    val canonicalUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaImageDto(
    @param:Json(name = "source")
    val source: String,
    @param:Json(name = "width")
    val width: Int? = null,
    @param:Json(name = "height")
    val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaApiErrorDto(
    @param:Json(name = "code")
    val code: String? = null,
    @param:Json(name = "info")
    val info: String? = null
)
