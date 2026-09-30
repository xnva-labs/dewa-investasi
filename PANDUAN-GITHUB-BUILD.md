# PANDUAN-GITHUB-BUILD — API key jalan otomatis di APK hasil GitHub

## 1. Peta penempatan file di repo GitHub

```
repo/
├── .github/workflows/build-apk.yml
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/xnvalabs/investmenttracker/
            ├── MainActivity.kt
            ├── model/                  (Models.kt)
            ├── data/                   (AssetRepository, MockData, DictionaryData)
            │   ├── local/              (AssetEntity, AssetDao, AppDatabase, Converters)
            │   └── remote/             (BinanceApi, CoinGeckoApi, FinnhubApi,
            │                            ExchangeRateApi, MarketDataRepository, NetworkModule)
            ├── notification/           (NotificationHelper)
            ├── viewmodel/              (PortfolioViewModel)
            ├── worker/                 (PriceSyncWorker)
            └── ui/                     (Theme, AppRoot, PortfolioScreen,
                                         SentimentScreen, ValuationScreen, DictionaryScreen)
```

File di zip ini SUDAH ada di path yang benar — tinggal push seluruh isi
folder ini apa adanya ke root repo GitHub kamu.

## 2. Pasang API key (Finnhub) sebagai GitHub Secret

1. Buka repo di GitHub -> Settings -> Secrets and variables -> Actions
2. New repository secret
3. Name  : FINNHUB_API_KEY   (harus persis begini)
4. Secret: paste key Finnhub kamu
5. Add secret

Key TIDAK ditulis di kode. Saat build, workflow mengoper secret itu ke Gradle,
lalu Gradle memasukkannya ke BuildConfig.FINNHUB_API_KEY.

## 3. Build

Push ke branch main -> tab Actions -> "Build APK" -> tunggu hijau ->
download artifact "xnva-investment-debug-apk" -> install di HP.

## 4. Sumber harga yang aktif di app

| Aset          | Sumber                                | Butuh key?              |
|---------------|----------------------------------------|--------------------------|
| Crypto        | Binance -> CoinGecko                   | Tidak                    |
| Saham/ETF US  | Finnhub                                | Ya (FINNHUB_API_KEY)     |
| Kurs USD/IDR  | Binance (USDTIDR) -> open.er-api.com   | Tidak                    |
| Saham IDX     | belum ada                              | harga diisi manual       |
| Forex lain, Cash | belum ada                            | harga diisi manual       |

Harga live dari Binance/CoinGecko/Finnhub dalam USD. Tiap aset punya field
mata uang (USD/IDR) — total saldo dikonversi ke rupiah pakai kurs USD/IDR
sebelum dijumlahkan.

## 5. Tes key Finnhub dulu (Termux)

    curl "https://finnhub.io/api/v1/quote?symbol=AAPL&token=KEY_KAMU"

- Key benar   : keluar JSON {"c":...,"h":...} dengan c lebih dari 0
- Key salah   : {"error":"Invalid API key"}

Dashboard Finnhub menampilkan "API Key" DAN "Webhook secret". Pakai yang berlabel
API Key. Webhook secret tidak dipakai app ini.

## 6. Keamanan

- Key di dalam APK bisa diekstrak siapa pun yang memegang APK-nya. Untuk pemakaian
  pribadi dengan key gratis ini risikonya kecil, tapi JANGAN bagikan APK-nya.
- Pakai repo PRIVATE. Di repo publik, artifact APK bisa diunduh user GitHub lain.
- Kalau key bocor: Finnhub dashboard -> generate ulang -> update GitHub Secret.
- Jangan tempel key di chat/kode/README.
