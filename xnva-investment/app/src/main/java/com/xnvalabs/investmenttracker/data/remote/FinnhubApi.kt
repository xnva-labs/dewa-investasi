package com.xnvalabs.investmenttracker.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Finnhub REST API — harga saham/ETF US.
 * Butuh API key (gratis, https://finnhub.io). Key TIDAK ditulis di kode:
 * dibaca dari BuildConfig.FINNHUB_API_KEY, yang diisi saat build dari
 * environment variable FINNHUB_API_KEY (GitHub Secrets) atau dari
 * local.properties (finnhub_api_key=...) untuk build lokal.
 *
 * Catatan: tier gratis hanya mencakup saham/ETF US. Ticker yang tidak
 * didukung (mis. saham IDX) dibalas c = 0.0 -> dianggap "tidak ada harga".
 */
interface FinnhubApi {
    @GET("quote")
    suspend fun getQuote(
        @Query("symbol") symbol: String,
        @Query("token") token: String
    ): FinnhubQuoteResponse
}

data class FinnhubQuoteResponse(
    @SerializedName("c") val current: Double = 0.0,        // harga terkini
    @SerializedName("h") val high: Double = 0.0,
    @SerializedName("l") val low: Double = 0.0,
    @SerializedName("o") val open: Double = 0.0,
    @SerializedName("pc") val previousClose: Double = 0.0,
    @SerializedName("t") val timestamp: Long = 0L
)
