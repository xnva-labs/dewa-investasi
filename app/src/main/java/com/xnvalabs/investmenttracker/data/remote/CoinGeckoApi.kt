package com.xnvalabs.investmenttracker.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * CoinGecko Public API — gratis, TIDAK butuh API key, dipakai sebagai
 * CADANGAN kalau Binance gagal/tidak mengenali ticker-nya. Ada rate
 * limit di jalur publik kalau dipanggil terlalu sering.
 * Dokumentasi: https://www.coingecko.com/en/api/documentation
 */
interface CoinGeckoApi {
    @GET("simple/price")
    suspend fun getSimplePrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "usd"
    ): Map<String, Map<String, Double>>
}

/** Pemetaan ticker umum -> CoinGecko coin id. Tambah sendiri kalau butuh coin lain. */
object CoinGeckoIds {
    private val map = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "SOL" to "solana",
        "BNB" to "binancecoin",
        "XRP" to "ripple",
        "ADA" to "cardano",
        "DOGE" to "dogecoin",
        "USDT" to "tether",
        "USDC" to "usd-coin",
        "AVAX" to "avalanche-2",
        "LINK" to "chainlink"
    )

    fun idFor(ticker: String): String? = map[ticker.uppercase()]
}
