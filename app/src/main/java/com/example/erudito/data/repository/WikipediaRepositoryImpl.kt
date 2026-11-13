package com.example.erudito.data.repository

import com.example.erudito.data.mapper.toSearchResults
import com.example.erudito.data.mapper.toWikipediaArticleOrNull
import com.example.erudito.data.remote.api.WikipediaApiService
import com.example.erudito.data.remote.dto.WikipediaApiResponseDto
import com.example.erudito.domain.model.WikipediaArticle
import com.example.erudito.domain.model.WikipediaException
import com.example.erudito.domain.model.WikipediaSearchResult
import com.example.erudito.domain.repository.WikipediaRepository
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class WikipediaRepositoryImpl @Inject constructor(
    private val apiService: WikipediaApiService
) : WikipediaRepository {

    override suspend fun searchArticles(query: String): List<WikipediaSearchResult> {
        val response = executeApiCall {
            apiService.searchArticles(query = query)
        }
        return response.toSearchResults()
    }

    override suspend fun getArticleByPageId(pageId: Long): WikipediaArticle? {
        val response = executeApiCall {
            apiService.getArticleByPageId(pageId = pageId)
        }
        return response.toWikipediaArticleOrNull()
    }

    private suspend fun executeApiCall(
        block: suspend () -> WikipediaApiResponseDto
    ): WikipediaApiResponseDto {
        val response = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: WikipediaException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw WikipediaException.Timeout(cause = e)
        } catch (e: IOException) {
            throw WikipediaException.NoConnection(cause = e)
        } catch (e: HttpException) {
            throw WikipediaException.HttpError(
                statusCode = e.code(),
                message = mapHttpErrorMessage(e.code()),
                cause = e
            )
        } catch (e: JsonDataException) {
            throw WikipediaException.InvalidResponse(cause = e)
        } catch (e: JsonEncodingException) {
            throw WikipediaException.InvalidResponse(cause = e)
        }

        response.error?.let { apiError ->
            val detail = apiError.info?.trim()?.takeIf { it.isNotEmpty() }
            val message = if (detail != null) {
                "A Wikipédia retornou um erro: $detail"
            } else {
                "Não foi possível concluir a consulta na Wikipédia."
            }
            throw WikipediaException.ApiError(
                code = apiError.code,
                message = message
            )
        }

        return response
    }

    private fun mapHttpErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            403 -> "Acesso recusado pela Wikipédia (erro 403)."
            404 -> "Recurso não encontrado na Wikipédia (erro 404)."
            429 -> "Muitas requisições em sequência. Aguarde um instante e tente novamente."
            in 500..599 -> "Os servidores da Wikipédia estão temporariamente indisponíveis (erro $statusCode)."
            else -> "Erro ao comunicar com a Wikipédia (código HTTP $statusCode)."
        }
    }
}
