# xnva investment — Investment Tracker & Sentiment Dashboard

Android native (Kotlin + Jetpack Compose, Material3) untuk pemakaian pribadi.
Package: `com.xnvalabs.investmenttracker`.

**Cara build: lihat PANDUAN-GITHUB-BUILD.md.** Dokumen itu panduan utama —
peta penempatan file di repo, cara pasang API key sebagai GitHub Secret,
dan cara jalankan build lewat GitHub Actions.

## Struktur

```
model/          Asset, AssetClass, Currency, TrackerType, dll (Models.kt)
data/           AssetRepository, MockData (seed awal), DictionaryData
  local/        Room: AssetEntity, AssetDao, AppDatabase, Converters
  remote/       BinanceApi, CoinGeckoApi, FinnhubApi, ExchangeRateApi,
                NetworkModule, MarketDataRepository
notification/   NotificationHelper
viewmodel/      PortfolioViewModel (StateFlow, kurs, refresh harga)
worker/         PriceSyncWorker (background sync tiap 15 menit)
ui/             Theme, AppRoot, PortfolioScreen, SentimentScreen,
                ValuationScreen, DictionaryScreen
```

## Sumber harga live

| Aset | Sumber | Butuh key? |
|---|---|---|
| Crypto | Binance (utama) → CoinGecko (cadangan) | Tidak |
| Saham/ETF US | Finnhub | Ya — `FINNHUB_API_KEY` |
| Kurs USD/IDR | Binance (USDTIDR) → open.er-api.com (cadangan) | Tidak |
| Saham IDX, forex lain, cash | belum ada sumber live | harga diisi manual |

Semua harga live dalam USD. Tiap aset punya field `currency` (USD/IDR) —
total saldo portfolio dikonversi ke rupiah pakai kurs USD/IDR sebelum
dijumlahkan, supaya aset USD dan IDR tidak tercampur apa adanya.

## Fitur

- Portfolio: tambah/hapus aset (validasi ticker & angka), filter
  Investasi/Trading, refresh harga live + kurs, notifikasi saat harga
  bergerak signifikan (live maupun simulasi manual).
- Sentiment: feed mock ala X/Twitter dengan badge Bullish/Bearish/Netral.
- Valuation: kalkulator P/E Ratio dengan status murah/wajar/mahal.
- Kamus: ~90 istilah investasi & trading, level 1 (dasar) sampai 13
  (advanced/dewa), bisa dicari.

## Batas yang disengaja (bukan bug)

- Bukan aplikasi eksekusi order — murni tracker/monitoring.
- Saham IDX tidak punya sumber harga live gratis yang andal — harga diisi
  manual atau dibiarkan seperti data awal.
- Kurs USD/IDR sekali refresh dipakai untuk modal dan nilai sekarang aset
  USD, jadi P/L akibat perubahan kurs sejak tanggal beli tidak dipisah.
