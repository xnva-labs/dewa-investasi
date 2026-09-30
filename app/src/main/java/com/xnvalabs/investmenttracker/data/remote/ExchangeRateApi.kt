package com.xnvalabs.investmenttracker.data.remote

import retrofit2.http.GET

/**
 * Kurs cadangan USD -> mata uang lain, TANPA API key
 * (endpoint terbuka ExchangeRate-API, https://open.er-api.com).
 * Datanya diperbarui sekitar sekali sehari, jadi cuma dipakai kalau
 * kurs realtime dari Binance (USDTIDR) gagal diambil.
 */
interface ExchangeRateApi {
    @GET("latest/USD")
    suspend fun latestUsd(): ExchangeRateResponse
}

data class ExchangeRateResponse(
    val result: String = "",
    val rates: Map<String, Double> = emptyMap()
)
