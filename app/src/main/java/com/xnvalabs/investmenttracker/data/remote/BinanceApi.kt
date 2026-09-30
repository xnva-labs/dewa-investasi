package com.xnvalabs.investmenttracker.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Binance Public Market Data — harga crypto terakhir, TANPA API key.
 * Base URL pakai data-api.binance.vision (endpoint resmi Binance khusus
 * data pasar publik). Pair yang dipakai selalu <TICKER>USDT (USDT ~ USD).
 * Kalau ticker tidak ada di Binance, MarketDataRepository otomatis
 * fallback ke CoinGecko.
 */
interface BinanceApi {
    @GET("ticker/price")
    suspend fun getTickerPrice(@Query("symbol") symbol: String): BinancePriceResponse
}

/** price dikirim Binance sebagai String supaya presisi penuh -> langsung ke BigDecimal. */
data class BinancePriceResponse(
    val symbol: String = "",
    val price: String = ""
)
