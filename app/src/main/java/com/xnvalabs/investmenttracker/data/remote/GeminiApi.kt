package com.xnvalabs.investmenttracker.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Google Gemini API (generateContent) — dipakai untuk analisis berita.
 * Key dikirim lewat header x-goog-api-key (cara resmi Google), bukan di
 * hardcode di kode: dibaca dari BuildConfig.GEMINI_API_KEY.
 * Model dipakai: gemini-2.5-flash-lite (tier gratis: 1.000 request/hari,
 * tanpa kartu kredit — cukup untuk pemakaian pribadi).
 */
interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)
