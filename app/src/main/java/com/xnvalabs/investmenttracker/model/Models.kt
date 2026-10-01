package com.xnvalabs.investmenttracker.model

import androidx.compose.ui.graphics.Color
import java.math.BigDecimal

// ============================================================================
// ENUMS
// ============================================================================

enum class TrackerType(val label: String) {
    INVESTASI("Investasi"),
    TRADING("Trading")
}

enum class AssetClass(val label: String) {
    SAHAM("Saham"),
    ETF("ETF/Indeks"),
    CRYPTO("Crypto"),
    FOREX("Forex/Mata Uang"),
    CASH("Cash"),
    LAINNYA("Lainnya")
}

/** Mata uang tempat harga sebuah aset dinyatakan. */
enum class Currency(val code: String, val symbol: String) {
    IDR("IDR", "Rp"),
    USD("USD", "US$")
}

enum class Sentiment(val label: String, val color: Color) {
    BULLISH("Bullish", Color(0xFF22C55E)),
    BEARISH("Bearish", Color(0xFFEF4444)),
    NEUTRAL("Netral", Color(0xFFF59E0B))
}

// ============================================================================
// DATA CLASSES
// ============================================================================

/**
 * Satu aset di portfolio (saham, ETF, crypto, forex, cash, dll).
 *
 * Nilai uang (qty, avgPrice, currentPrice) pakai BigDecimal — BUKAN Double —
 * supaya tidak ada floating-point rounding error.
 *
 * [currency] = mata uang harga aset ini. marketValue & pnl selalu dalam
 * mata uang itu. Untuk menjumlahkan aset berbeda mata uang, pakai
 * [valueInIdr] / [pnlInIdr] dengan kurs USD/IDR.
 */
data class Asset(
    val id: Int,
    val ticker: String,
    val name: String,
    val assetClass: AssetClass,
    val trackerType: TrackerType,
    val qty: BigDecimal,
    val avgPrice: BigDecimal,
    var currentPrice: BigDecimal,
    val currency: Currency = Currency.USD
) {
    val marketValue: BigDecimal get() = qty.multiply(currentPrice)
    val pnl: BigDecimal get() = currentPrice.subtract(avgPrice).multiply(qty)

    /**
     * Persentase P/L. Sengaja Double: murni angka tampilan (bukan nilai uang
     * yang disimpan), dan aman dari ArithmeticException pembagian BigDecimal
     * tak berhingga.
     */
    val pnlPercent: Double
        get() = if (avgPrice.signum() == 0) 0.0
        else (currentPrice.toDouble() - avgPrice.toDouble()) / avgPrice.toDouble() * 100.0
}

/**
 * Nilai pasar dalam rupiah. Return null kalau aset USD tapi kurs belum
 * tersedia (jangan dianggap 0 — supaya total tidak diam-diam salah).
 */
fun Asset.valueInIdr(usdIdrRate: BigDecimal?): BigDecimal? = when (currency) {
    Currency.IDR -> marketValue
    Currency.USD -> usdIdrRate?.let { marketValue.multiply(it) }
}

/**
 * P/L dalam rupiah. Catatan: pakai kurs SAAT INI untuk modal dan nilai
 * sekarang, jadi untung/rugi akibat perubahan kurs sejak tanggal beli
 * tidak dihitung terpisah.
 */
fun Asset.pnlInIdr(usdIdrRate: BigDecimal?): BigDecimal? = when (currency) {
    Currency.IDR -> pnl
    Currency.USD -> usdIdrRate?.let { pnl.multiply(it) }
}

data class NewsItem(
    val handle: String,
    val name: String,
    val verified: Boolean,
    val time: String,
    val body: String,
    val tag: String,
    val sentiment: Sentiment
)

data class DictionaryTerm(
    val term: String,
    val category: String,
    val definition: String,
    val example: String
)
