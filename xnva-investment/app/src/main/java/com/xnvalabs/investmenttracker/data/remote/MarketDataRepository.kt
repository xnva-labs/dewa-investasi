package com.xnvalabs.investmenttracker.data.remote

import com.xnvalabs.investmenttracker.BuildConfig
import com.xnvalabs.investmenttracker.model.Asset
import com.xnvalabs.investmenttracker.model.AssetClass
import com.xnvalabs.investmenttracker.model.Currency
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal

/**
 * Satu pintu untuk semua harga live:
 * - CRYPTO      : Binance (utama, tanpa key) -> CoinGecko (cadangan, tanpa key)
 * - SAHAM / ETF : Finnhub (butuh key dari BuildConfig.FINNHUB_API_KEY)
 * - Kurs USD/IDR: Binance USDTIDR (utama) -> open.er-api.com (cadangan)
 * - lainnya     : belum ada sumber live -> harga tetap manual
 *
 * Semua fungsi return null kalau gagal (ticker tidak didukung, tidak ada
 * internet, kena rate limit, key kosong/salah) — tidak pernah melempar
 * exception ke pemanggil.
 *
 * PENTING: semua sumber harga ini dalam USD, jadi harga live HANYA
 * diterapkan ke aset yang currency-nya USD.
 */
class MarketDataRepository(
    private val binanceApi: BinanceApi = NetworkModule.binanceApi,
    private val coinGeckoApi: CoinGeckoApi = NetworkModule.coinGeckoApi,
    private val finnhubApi: FinnhubApi = NetworkModule.finnhubApi,
    private val exchangeRateApi: ExchangeRateApi = NetworkModule.exchangeRateApi,
    private val finnhubKey: String = BuildConfig.FINNHUB_API_KEY
) {
    val isFinnhubConfigured: Boolean get() = finnhubKey.isNotBlank()

    /** Apakah asset ini punya sumber harga live (dan mata uangnya cocok, yaitu USD). */
    fun isLiveSupported(asset: Asset): Boolean {
        if (asset.currency != Currency.USD) return false
        return when (asset.assetClass) {
            AssetClass.CRYPTO -> true
            AssetClass.SAHAM, AssetClass.ETF -> isFinnhubConfigured
            else -> false
        }
    }

    suspend fun fetchLivePrice(asset: Asset): BigDecimal? = when (asset.assetClass) {
        AssetClass.CRYPTO -> fetchCryptoPriceUsd(asset.ticker)
        AssetClass.SAHAM, AssetClass.ETF -> fetchStockPriceUsd(asset.ticker)
        else -> null
    }

    suspend fun fetchCryptoPriceUsd(ticker: String): BigDecimal? =
        fetchBinancePrice(ticker.trim().uppercase() + "USDT") ?: fetchFromCoinGecko(ticker)

    suspend fun fetchStockPriceUsd(ticker: String): BigDecimal? {
        if (!isFinnhubConfigured) return null
        return safeCall {
            val quote = finnhubApi.getQuote(symbol = ticker.trim().uppercase(), token = finnhubKey)
            // Finnhub membalas c = 0.0 untuk ticker yang tidak dikenal/tidak didukung.
            if (quote.current > 0.0) BigDecimal.valueOf(quote.current) else null
        }
    }

    /**
     * Kurs 1 USD dalam rupiah. Utama: Binance USDTIDR (USDT ~ 1 USD, realtime).
     * Cadangan: open.er-api.com (update harian). Null kalau dua-duanya gagal.
     */
    suspend fun fetchUsdIdrRate(): BigDecimal? {
        val fromBinance = fetchBinancePrice("USDTIDR")
        if (fromBinance != null) return fromBinance
        return safeCall {
            val response = exchangeRateApi.latestUsd()
            val idr = response.rates["IDR"]
            if (response.result == "success" && idr != null && idr > 0.0) BigDecimal.valueOf(idr) else null
        }
    }

    private suspend fun fetchBinancePrice(symbol: String): BigDecimal? = safeCall {
        val price = binanceApi.getTickerPrice(symbol).price.toBigDecimalOrNull()
        if (price != null && price.signum() > 0) price else null
    }

    private suspend fun fetchFromCoinGecko(ticker: String): BigDecimal? {
        val coinId = CoinGeckoIds.idFor(ticker) ?: return null
        return safeCall {
            coinGeckoApi.getSimplePrice(ids = coinId, vsCurrencies = "usd")[coinId]
                ?.get("usd")
                ?.let { BigDecimal.valueOf(it) }
        }
    }

    /** try/catch yang TIDAK menelan CancellationException (biar coroutine bisa dibatalkan normal). */
    private inline fun <T> safeCall(block: () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
