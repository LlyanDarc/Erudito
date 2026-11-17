package com.example.erudito.domain.model

sealed class WikipediaException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    class NoConnection(
        message: String = "Sem conexão com a internet. Verifique sua rede e tente novamente.",
        cause: Throwable? = null
    ) : WikipediaException(message, cause)

    class Timeout(
        message: String = "A consulta à Wikipédia demorou mais que o esperado. Tente novamente.",
        cause: Throwable? = null
    ) : WikipediaException(message, cause)

    class HttpError(
        val statusCode: Int,
        message: String,
        cause: Throwable? = null
    ) : WikipediaException(message, cause)

    class ApiError(
        val code: String?,
        message: String,
        cause: Throwable? = null
    ) : WikipediaException(message, cause)

    class InvalidResponse(
        message: String = "Não foi possível interpretar a resposta da Wikipédia.",
        cause: Throwable? = null
    ) : WikipediaException(message, cause)

    class EmptyContent(
        message: String = "A Wikipédia retornou conteúdo vazio para este artigo.",
        cause: Throwable? = null
    ) : WikipediaException(message, cause)

    class SanitizationError(
        message: String = "Não foi possível processar com segurança o conteúdo do artigo.",
        cause: Throwable? = null
    ) : WikipediaException(message, cause)
}
