package com.xnvalabs.investmenttracker.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Satu tempat untuk semua instance Retrofit di app ini. */
object NetworkModule {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    /** Crypto utama + kurs USDT/IDR — tanpa API key. */
    val binanceApi: BinanceApi by lazy {
        retrofit("https://data-api.binance.vision/api/v3/").create(BinanceApi::class.java)
    }

    /** Crypto cadangan — tanpa API key (ada rate limit ketat di jalur publik). */
    val coinGeckoApi: CoinGeckoApi by lazy {
        retrofit("https://api.coingecko.com/api/v3/").create(CoinGeckoApi::class.java)
    }

    /** Saham/ETF US — butuh API key (BuildConfig.FINNHUB_API_KEY). */
    val finnhubApi: FinnhubApi by lazy {
        retrofit("https://finnhub.io/api/v1/").create(FinnhubApi::class.java)
    }

    /** Kurs USD/IDR cadangan — tanpa API key, update ~harian. */
    val exchangeRateApi: ExchangeRateApi by lazy {
        retrofit("https://open.er-api.com/v6/").create(ExchangeRateApi::class.java)
    }

    /** Analisis berita AI — butuh API key (BuildConfig.GEMINI_API_KEY). */
    val geminiApi: GeminiApi by lazy {
        retrofit("https://generativelanguage.googleapis.com/").create(GeminiApi::class.java)
    }
}
