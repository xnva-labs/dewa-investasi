package com.xnvalabs.investmenttracker.data

import com.xnvalabs.investmenttracker.model.Asset
import com.xnvalabs.investmenttracker.model.AssetClass
import com.xnvalabs.investmenttracker.model.Currency
import com.xnvalabs.investmenttracker.model.NewsItem
import com.xnvalabs.investmenttracker.model.Sentiment
import com.xnvalabs.investmenttracker.model.TrackerType
import java.math.BigDecimal

// ============================================================================
// DATA AWAL (SEED)
// Dipakai sekali oleh PortfolioViewModel lewat AssetRepository.seedIfEmpty()
// saat database Room masih kosong (instal pertama kali). Setelah itu semua
// perubahan disimpan permanen di Room. Ini cuma contoh awal — hapus/ganti
// lewat UI dengan aset milik kamu sendiri.
//
// Mata uang: NVDA, VOO, BTC dalam USD. USD/IDR dan CASH dalam IDR
// (nilai USD/IDR = jumlah USD yang dipegang x harga dalam rupiah).
// ============================================================================

fun initialAssets(): List<Asset> = listOf(
    Asset(1, "NVDA", "NVIDIA Corp", AssetClass.SAHAM, TrackerType.INVESTASI, BigDecimal("5"), BigDecimal("850.00"), BigDecimal("870.00"), Currency.USD),
    Asset(2, "VOO", "Vanguard S&P 500 ETF", AssetClass.ETF, TrackerType.INVESTASI, BigDecimal("10"), BigDecimal("420.00"), BigDecimal("431.00"), Currency.USD),
    Asset(3, "BTC", "Bitcoin", AssetClass.CRYPTO, TrackerType.TRADING, BigDecimal("0.05"), BigDecimal("62000.00"), BigDecimal("64500.00"), Currency.USD),
    Asset(4, "USD/IDR", "Dolar AS / Rupiah", AssetClass.FOREX, TrackerType.TRADING, BigDecimal("1000"), BigDecimal("15600.00"), BigDecimal("15750.00"), Currency.IDR),
    Asset(5, "CASH", "Kas / Rekening", AssetClass.CASH, TrackerType.INVESTASI, BigDecimal("1"), BigDecimal("5000000.00"), BigDecimal("5000000.00"), Currency.IDR)
)

fun mockNews(): List<NewsItem> = listOf(
    NewsItem("@xnva_market", "xnva Market Feed", true, "2m", "NVDA melanjutkan reli setelah laporan permintaan chip AI datacenter tetap kuat kuartal ini. Analis menaikkan target harga.", "NVDA", Sentiment.BULLISH),
    NewsItem("@fed_watch", "Fed Watch", true, "18m", "FOMC memberi sinyal bernuansa hawkish, pasar merevisi ekspektasi pemangkasan suku bunga tahun ini menjadi lebih konservatif.", "Fed Rate", Sentiment.BEARISH),
    NewsItem("@crypto_pulse", "Crypto Pulse", false, "35m", "BTC konsolidasi di area resistance utama, volume menurun menjelang rilis data inflasi AS pekan ini.", "Crypto", Sentiment.NEUTRAL),
    NewsItem("@idx_daily", "IDX Daily", true, "1h", "IHSG ditutup menguat ditopang sektor perbankan dan energi, asing tercatat net buy signifikan.", "IHSG", Sentiment.BULLISH),
    NewsItem("@etf_flows", "ETF Flow Tracker", true, "2h", "Inflow ke ETF S&P 500 melambat, investor mulai rotasi ke sektor defensif menjelang musim laporan keuangan.", "ETF", Sentiment.NEUTRAL),
    NewsItem("@macro_desk", "Macro Desk", true, "3h", "Data CPI AS keluar sedikit di atas ekspektasi, yield obligasi 10-tahun naik tajam merespons rilis tersebut.", "Macro", Sentiment.BEARISH),
    NewsItem("@nvda_watch", "NVDA Watchlist", false, "4h", "Rumor kemitraan baru di sektor robotika mendorong sentimen positif jangka pendek pada saham chip AI.", "NVDA", Sentiment.BULLISH)
)
