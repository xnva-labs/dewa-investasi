package com.xnvalabs.investmenttracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xnvalabs.investmenttracker.data.AssetRepository
import com.xnvalabs.investmenttracker.data.initialAssets
import com.xnvalabs.investmenttracker.data.local.AppDatabase
import com.xnvalabs.investmenttracker.data.remote.MarketDataRepository
import com.xnvalabs.investmenttracker.model.Asset
import com.xnvalabs.investmenttracker.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * Satu-satunya pemilik state portfolio untuk seluruh UI.
 * - Data PERSISTEN lewat Room (via AssetRepository).
 * - Harga live lewat MarketDataRepository (crypto: Binance/CoinGecko,
 *   saham & ETF US: Finnhub) — hanya untuk aset berdenominasi USD.
 * - Kurs USD/IDR disimpan di SharedPreferences supaya total saldo tetap
 *   bisa dihitung saat offline (pakai kurs terakhir yang berhasil diambil).
 */
class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AssetRepository(AppDatabase.getInstance(application).assetDao())
    private val marketDataRepository = MarketDataRepository()
    private val prefs = application.getSharedPreferences("xnva_prefs", Context.MODE_PRIVATE)

    val assets: StateFlow<List<Asset>> = repository.observeAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _usdIdrRate = MutableStateFlow(prefs.getString(KEY_USD_IDR, null)?.toBigDecimalOrNull())
    /** Kurs 1 USD dalam rupiah. Null = belum pernah berhasil diambil. */
    val usdIdrRate: StateFlow<BigDecimal?> = _usdIdrRate.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfEmpty(initialAssets())
            updateUsdIdrRate()
        }
    }

    fun addAsset(newAsset: Asset) {
        viewModelScope.launch {
            val nextId = (assets.value.maxOfOrNull { it.id } ?: 0) + 1
            repository.upsert(newAsset.copy(id = nextId))
        }
    }

    fun deleteAsset(asset: Asset) {
        viewModelScope.launch { repository.delete(asset) }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private suspend fun updateUsdIdrRate(): Boolean {
        val rate = marketDataRepository.fetchUsdIdrRate() ?: return false
        _usdIdrRate.value = rate
        prefs.edit().putString(KEY_USD_IDR, rate.toPlainString()).apply()
        return true
    }

    /** Demo/simulasi: ubah harga 1 aset acak lalu kirim notifikasi. */
    fun simulateRandomPriceChange() {
        val current = assets.value
        if (current.isEmpty()) return
        val asset = current[Random.nextInt(current.size)]
        val changePercent = Random.nextDouble(-8.0, 8.0)
        val factor = BigDecimal.valueOf(1 + changePercent / 100.0)
        val newPrice = asset.currentPrice.multiply(factor).setScale(8, RoundingMode.HALF_UP)

        viewModelScope.launch {
            repository.upsert(asset.copy(currentPrice = newPrice))
        }

        val arrow = if (changePercent >= 0) "\u2B06\uFE0F" else "\u2B07\uFE0F"
        val pctText = String.format(Locale.US, "%.2f", changePercent)
        NotificationHelper.showNotification(
            getApplication(),
            "$arrow Price Alert (Simulasi): ${asset.ticker}",
            "${asset.name} bergerak $pctText% menjadi ${newPrice.stripTrailingZeros().toPlainString()}"
        )
    }

    /**
     * Refresh harga NYATA untuk aset USD yang punya sumber live (Crypto,
     * Saham, ETF) + kurs USD/IDR. Kalau perubahan harga melewati
     * [alertThresholdPercent], kirim notifikasi sungguhan. Ticker/kurs
     * yang gagal dilaporkan ke UI.
     */
    fun refreshLivePrices(alertThresholdPercent: Double = 3.0) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val rateOk = updateUsdIdrRate()

            val candidates = assets.value.filter { marketDataRepository.isLiveSupported(it) }
            if (candidates.isEmpty()) {
                _isRefreshing.value = false
                _errorMessage.value = if (marketDataRepository.isFinnhubConfigured) {
                    "Tidak ada aset Crypto/Saham/ETF berdenominasi USD untuk di-refresh."
                } else {
                    "Tidak ada aset Crypto USD untuk di-refresh. Saham/ETF butuh API key Finnhub di build."
                }
                return@launch
            }

            val failed = mutableListOf<String>()
            for (asset in candidates) {
                val livePrice = marketDataRepository.fetchLivePrice(asset)
                if (livePrice == null) {
                    failed += asset.ticker
                    continue
                }
                val prevPrice = asset.currentPrice
                repository.upsert(asset.copy(currentPrice = livePrice))

                if (prevPrice.signum() != 0) {
                    val changePercent = livePrice.subtract(prevPrice)
                        .divide(prevPrice, MathContext(10))
                        .multiply(BigDecimal(100)).toDouble()
                    if (abs(changePercent) >= alertThresholdPercent) {
                        val arrow = if (changePercent >= 0) "\u2B06\uFE0F" else "\u2B07\uFE0F"
                        val pctText = String.format(Locale.US, "%.2f", changePercent)
                        NotificationHelper.showNotification(
                            getApplication(),
                            "$arrow Price Alert: ${asset.ticker}",
                            "${asset.name} bergerak $pctText% menjadi US\$${livePrice.toPlainString()} (live)"
                        )
                    }
                }
            }
            _isRefreshing.value = false

            val notes = mutableListOf<String>()
            if (failed.isNotEmpty()) {
                notes += "Gagal update harga: ${failed.joinToString(", ")} (cek koneksi, ticker — Finnhub cuma saham/ETF US — atau limit API)."
            }
            if (!rateOk) {
                notes += if (_usdIdrRate.value != null) {
                    "Kurs USD/IDR gagal diperbarui, memakai kurs terakhir."
                } else {
                    "Kurs USD/IDR gagal diambil."
                }
            }
            if (notes.isNotEmpty()) _errorMessage.value = notes.joinToString(" ")
        }
    }

    private companion object {
        const val KEY_USD_IDR = "usd_idr_rate"
    }
}
