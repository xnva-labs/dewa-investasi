package com.xnvalabs.investmenttracker.data

import com.xnvalabs.investmenttracker.model.DictionaryTerm

// ============================================================================
// KAMUS ISTILAH INVESTASI & TRADING — LENGKAP DARI DASAR SAMPAI DEWA
// Tambah istilah baru kapan saja: tinggal tambah baris DictionaryTerm baru
// di list ini, tidak perlu ubah UI (DictionaryScreen.kt auto-mengelompokkan
// berdasarkan field `category`).
// ============================================================================

fun dictionaryData(): List<DictionaryTerm> = listOf(
    // LEVEL 1 — DASAR
    DictionaryTerm("Asset", "1. Dasar", "Sesuatu yang memiliki nilai ekonomi, misalnya saham, obligasi, ETF, emas, properti, atau crypto.", "Rumah, emas batangan, dan saham NVDA semuanya termasuk asset."),
    DictionaryTerm("Stock / Saham", "1. Dasar", "Bukti kepemilikan sebagian kecil dari sebuah perusahaan.", "Membeli 1 lembar saham NVDA berarti memiliki sebagian kecil NVIDIA Corp."),
    DictionaryTerm("Share", "1. Dasar", "Satuan dari saham, satu lembar/unit kepemilikan.", "1 share NVDA = 1 lembar kepemilikan NVIDIA."),
    DictionaryTerm("Ticker", "1. Dasar", "Kode singkat yang mewakili suatu saham/aset di bursa.", "NVIDIA = NVDA, Apple = AAPL, Bitcoin = BTC."),
    DictionaryTerm("Broker", "1. Dasar", "Perantara yang memungkinkan investor membeli/menjual aset di pasar.", "Kamu membeli saham lewat aplikasi broker seperti Ajaib atau Stockbit."),
    DictionaryTerm("Exchange / Bursa", "1. Dasar", "Tempat resmi terjadinya transaksi jual-beli aset.", "NASDAQ, NYSE, dan IDX adalah contoh bursa."),
    DictionaryTerm("Portfolio", "1. Dasar", "Kumpulan seluruh investasi yang dimiliki seseorang.", "Portfolio: 30% NVDA, 30% ETF kesehatan, 20% ETF indeks, 20% cash."),
    DictionaryTerm("Return", "1. Dasar", "Keuntungan atau kerugian dari sebuah investasi, biasanya dalam persen.", "Modal Rp1 juta jadi Rp1,2 juta → return +20%."),
    DictionaryTerm("Capital Gain", "1. Dasar", "Keuntungan karena harga aset naik dibanding harga beli.", "Beli di Rp100, jual di Rp150 → capital gain Rp50."),
    DictionaryTerm("Capital Loss", "1. Dasar", "Kerugian karena harga aset turun dibanding harga beli.", "Beli di Rp100, jual di Rp70 → capital loss -30%."),
    DictionaryTerm("Dividend", "1. Dasar", "Pembagian sebagian keuntungan perusahaan kepada pemegang saham.", "Perusahaan membagikan dividen Rp5 per lembar saham."),
    DictionaryTerm("Yield", "1. Dasar", "Pendapatan yang dihasilkan sebuah aset dibanding harga/investasinya.", "Dividen Rp5 dari saham Rp100 → dividend yield 5%."),

    // LEVEL 2 — HARGA & ORDER
    DictionaryTerm("Bid", "2. Harga & Order", "Harga tertinggi yang sedang ditawarkan oleh pembeli.", "Bid NVDA saat ini $180.00."),
    DictionaryTerm("Ask", "2. Harga & Order", "Harga terendah yang diminta oleh penjual.", "Ask NVDA saat ini $180.10."),
    DictionaryTerm("Spread", "2. Harga & Order", "Selisih antara harga Ask dan Bid.", "Ask $100.10 - Bid $100.00 = spread $0.10."),
    DictionaryTerm("Market Order", "2. Harga & Order", "Order untuk beli/jual segera pada harga pasar yang tersedia saat itu.", "Klik 'Buy' tanpa set harga → order dieksekusi di harga pasar saat itu."),
    DictionaryTerm("Limit Order", "2. Harga & Order", "Order dengan harga yang ditentukan sendiri oleh trader.", "\"Beli NVDA hanya kalau harganya turun ke $180 atau di bawah.\""),
    DictionaryTerm("Stop Order", "2. Harga & Order", "Order yang baru aktif ketika harga mencapai level tertentu.", "Stop order jual aktif kalau harga menyentuh $170."),
    DictionaryTerm("Stop-Loss", "2. Harga & Order", "Batas kerugian yang sudah direncanakan sebelumnya untuk membatasi loss.", "Set stop-loss di $170 supaya kerugian maksimal terbatas."),
    DictionaryTerm("Take Profit (TP)", "2. Harga & Order", "Level harga untuk merealisasikan keuntungan secara otomatis.", "Set TP di $220 untuk mengunci profit."),
    DictionaryTerm("Position", "2. Harga & Order", "Aset/kontrak yang sedang dipegang oleh trader/investor.", "Posisi long 5 lembar NVDA."),
    DictionaryTerm("Entry", "2. Harga & Order", "Harga atau titik saat pertama kali masuk posisi.", "Entry di harga $180."),
    DictionaryTerm("Exit", "2. Harga & Order", "Titik saat keluar dari sebuah posisi.", "Exit di harga $210 dengan profit."),
    DictionaryTerm("Long", "2. Harga & Order", "Posisi yang untung ketika harga naik (beli lalu jual lebih mahal).", "Long NVDA di $180, berharap harga naik."),
    DictionaryTerm("Short / Short Selling", "2. Harga & Order", "Posisi yang untung ketika harga turun; menjual aset pinjaman lalu membeli kembali lebih murah. Risikonya besar karena harga secara teori bisa naik tanpa batas.", "Short selling saham X di $50, berharap bisa beli kembali di $40."),

    // LEVEL 3 — FUNDAMENTAL ANALYSIS
    DictionaryTerm("Fundamental", "3. Fundamental", "Kondisi ekonomi dan bisnis suatu perusahaan: revenue, profit, cash flow, debt, growth, valuasi.", "Analisis fundamental NVDA melihat pertumbuhan revenue chip AI-nya."),
    DictionaryTerm("Revenue", "3. Fundamental", "Total pendapatan perusahaan sebelum dikurangi berbagai biaya.", "Revenue tahunan perusahaan $100 miliar."),
    DictionaryTerm("Gross Profit", "3. Fundamental", "Revenue dikurangi biaya pokok produksi (Cost of Goods Sold).", "Revenue $100M - COGS $30M = Gross Profit $70M."),
    DictionaryTerm("Gross Margin", "3. Fundamental", "Gross Profit dibagi Revenue, dalam persen.", "Gross profit $70M / Revenue $100M = 70%."),
    DictionaryTerm("Operating Income", "3. Fundamental", "Keuntungan dari kegiatan operasional bisnis setelah biaya operasional.", "Setelah biaya gaji dan sewa, operating income = $40M."),
    DictionaryTerm("Operating Margin", "3. Fundamental", "Operating Income dibagi Revenue.", "$40M / $100M = 40% operating margin."),
    DictionaryTerm("Net Income", "3. Fundamental", "Laba bersih setelah semua biaya, bunga, dan pajak dikurangkan.", "Net income tahun ini $25 miliar."),
    DictionaryTerm("Net Margin", "3. Fundamental", "Net Income dibagi Revenue, dalam persen.", "$25M / $100M = 25% net margin."),
    DictionaryTerm("EBITDA", "3. Fundamental", "Laba sebelum bunga, pajak, depresiasi, dan amortisasi — sering dipakai melihat kemampuan operasional inti bisnis.", "EBITDA dipakai membandingkan profitabilitas antar perusahaan dengan struktur modal berbeda."),
    DictionaryTerm("EPS (Earnings Per Share)", "3. Fundamental", "Laba bersih dibagi jumlah saham beredar.", "Net income $10B / 1B saham = EPS $10."),
    DictionaryTerm("P/E Ratio", "3. Fundamental", "Price-to-Earnings: harga saham dibagi EPS, mengukur berapa kali investor membayar earnings tahunan.", "Harga $200, EPS $10 → P/E = 20x."),
    DictionaryTerm("PEG Ratio", "3. Fundamental", "P/E dibagi tingkat pertumbuhan earnings, menghubungkan valuasi dengan growth.", "P/E 20 dengan growth 20%/tahun → PEG = 1."),
    DictionaryTerm("Balance Sheet", "3. Fundamental", "Laporan yang berisi Assets, Liabilities, dan Equity perusahaan pada satu titik waktu.", "Assets = Liabilities + Equity."),
    DictionaryTerm("Liabilities", "3. Fundamental", "Kewajiban atau hutang perusahaan.", "Hutang bank, obligasi yang diterbitkan, hutang supplier."),
    DictionaryTerm("Equity", "3. Fundamental", "Nilai kepemilikan pemegang saham atas perusahaan.", "Assets $500M - Liabilities $200M = Equity $300M."),
    DictionaryTerm("Operating Cash Flow (CFO)", "3. Fundamental", "Kas yang dihasilkan dari kegiatan operasional bisnis sehari-hari.", "CFO positif menandakan bisnis inti menghasilkan uang tunai."),
    DictionaryTerm("CapEx (Capital Expenditure)", "3. Fundamental", "Pengeluaran untuk aset jangka panjang seperti pabrik, mesin, atau data center.", "Perusahaan chip membangun pabrik baru senilai $5B sebagai CapEx."),
    DictionaryTerm("FCF (Free Cash Flow)", "3. Fundamental", "Operating Cash Flow dikurangi CapEx; kas bebas yang tersisa untuk perusahaan.", "CFO $50M - CapEx $10M = FCF $40M."),
    DictionaryTerm("Market Cap", "3. Fundamental", "Harga saham dikali jumlah saham beredar; nilai total perusahaan di pasar.", "1 miliar saham x $100 = market cap $100 miliar."),
    DictionaryTerm("Enterprise Value (EV)", "3. Fundamental", "Nilai perusahaan yang lebih luas dari market cap, memperhitungkan debt dan cash.", "EV sering dipakai bersama EBITDA untuk rasio EV/EBITDA."),
    DictionaryTerm("DCF (Discounted Cash Flow)", "3. Fundamental", "Metode valuasi dengan memperkirakan arus kas masa depan lalu mendiskontokannya ke nilai sekarang.", "Analis memakai DCF untuk menentukan harga wajar saham."),

    // LEVEL 4 — TECHNICAL ANALYSIS
    DictionaryTerm("Candlestick / OHLC", "4. Technical", "Grafik yang menunjukkan Open, High, Low, Close harga dalam satu periode waktu.", "Candle hijau harian menunjukkan Close lebih tinggi dari Open."),
    DictionaryTerm("Timeframe", "4. Technical", "Rentang waktu satu candle/periode pada chart.", "1m, 5m, 15m, 1H, 4H, 1D, 1W."),
    DictionaryTerm("Uptrend", "4. Technical", "Tren naik, ditandai dengan Higher High dan Higher Low secara berturut-turut.", "Harga membentuk puncak dan lembah yang makin tinggi."),
    DictionaryTerm("Downtrend", "4. Technical", "Tren turun, ditandai dengan Lower High dan Lower Low.", "Harga membentuk puncak dan lembah yang makin rendah."),
    DictionaryTerm("Sideways", "4. Technical", "Harga bergerak dalam rentang tertentu tanpa tren jelas.", "Harga NVDA bergerak antara $180-$190 selama dua minggu."),
    DictionaryTerm("Support", "4. Technical", "Area harga tempat tekanan beli cenderung muncul, menahan penurunan.", "Support kuat di area $170."),
    DictionaryTerm("Resistance", "4. Technical", "Area harga tempat tekanan jual cenderung muncul, menahan kenaikan.", "Resistance di area $200 sudah diuji tiga kali."),
    DictionaryTerm("Breakout", "4. Technical", "Harga menembus level resistance.", "NVDA breakout dari resistance $200 dengan volume tinggi."),
    DictionaryTerm("Breakdown", "4. Technical", "Harga menembus level support.", "Saham breakdown di bawah support $170."),
    DictionaryTerm("Retest", "4. Technical", "Harga kembali menguji area yang baru saja ditembus.", "Setelah breakout $200, harga retest ke $200 sebelum lanjut naik."),
    DictionaryTerm("Volume", "4. Technical", "Jumlah unit aset yang diperdagangkan dalam suatu periode.", "Breakout dengan volume tinggi dianggap lebih meyakinkan."),
    DictionaryTerm("Moving Average (MA)", "4. Technical", "Rata-rata harga selama periode tertentu, membantu melihat arah tren.", "MA20 = rata-rata harga 20 hari/periode terakhir."),
    DictionaryTerm("EMA", "4. Technical", "Exponential Moving Average; MA yang memberi bobot lebih besar pada harga terbaru.", "EMA lebih cepat merespons perubahan harga dibanding SMA."),
    DictionaryTerm("RSI", "4. Technical", "Relative Strength Index; indikator momentum untuk melihat kondisi overbought/oversold.", "RSI di atas 70 sering dianggap overbought, di bawah 30 oversold."),
    DictionaryTerm("MACD", "4. Technical", "Moving Average Convergence Divergence; indikator untuk membaca momentum dan perubahan tren.", "Garis MACD memotong ke atas garis sinyal bisa jadi sinyal bullish."),

    // LEVEL 5 — TRADING & RISK
    DictionaryTerm("Scalping", "5. Trading & Risk", "Gaya trading dengan transaksi sangat singkat, hitungan menit.", "Buka dan tutup posisi dalam 5-10 menit."),
    DictionaryTerm("Day Trading", "5. Trading & Risk", "Masuk dan keluar posisi pada hari yang sama.", "Semua posisi ditutup sebelum market tutup."),
    DictionaryTerm("Swing Trading", "5. Trading & Risk", "Menahan posisi dari beberapa hari hingga beberapa minggu.", "Beli NVDA, tahan 2 minggu, lalu jual saat target tercapai."),
    DictionaryTerm("Position Trading", "5. Trading & Risk", "Menahan posisi berminggu-minggu hingga berbulan-bulan.", "Trader menahan posisi berdasarkan tren jangka menengah-panjang."),
    DictionaryTerm("Investing", "5. Trading & Risk", "Horizon jangka panjang, berbasis tesis fundamental bisnis/aset.", "Membeli dan menahan ETF indeks selama 10 tahun."),
    DictionaryTerm("Risk/Reward Ratio", "5. Trading & Risk", "Perbandingan potensi kerugian terhadap potensi keuntungan dalam satu transaksi.", "Risiko Rp10.000 vs potensi profit Rp30.000 → R/R 1:3."),
    DictionaryTerm("Win Rate", "5. Trading & Risk", "Persentase transaksi yang menghasilkan profit dari total transaksi.", "55 dari 100 trade profit → win rate 55%."),
    DictionaryTerm("Expectancy", "5. Trading & Risk", "Ekspektasi hasil rata-rata per transaksi berdasarkan win rate dan rata-rata untung/rugi.", "(50% x Rp30k) - (50% x Rp20k) = expectancy +Rp5k/trade."),
    DictionaryTerm("Drawdown", "5. Trading & Risk", "Penurunan nilai portfolio dari titik tertinggi ke titik terendah berikutnya.", "Rp10 juta turun ke Rp7 juta → max drawdown 30%."),
    DictionaryTerm("Position Sizing", "5. Trading & Risk", "Menentukan besar dana yang dialokasikan pada satu transaksi.", "Hanya mengalokasikan 2% dari total modal per posisi."),

    // LEVEL 6 — MARGIN & LEVERAGE
    DictionaryTerm("Margin", "6. Margin & Leverage", "Fasilitas dana pinjaman dari broker untuk memperbesar daya beli, sekaligus memperbesar risiko.", "Modal Rp10 juta bisa membeli Rp20 juta aset lewat margin."),
    DictionaryTerm("Leverage", "6. Margin & Leverage", "Menggunakan modal relatif kecil untuk mengendalikan eksposur yang jauh lebih besar.", "Modal Rp1 juta dengan leverage 5x → eksposur Rp5 juta."),
    DictionaryTerm("Margin Call", "6. Margin & Leverage", "Peringatan dari broker saat equity akun turun drastis, meminta tambahan dana; jika tidak dipenuhi posisi bisa dilikuidasi paksa.", "Akun kena margin call setelah harga bergerak melawan posisi secara tajam."),

    // LEVEL 7 — ETF & INDEX
    DictionaryTerm("ETF (Exchange-Traded Fund)", "7. ETF & Index", "Produk investasi berisi kumpulan aset yang diperdagangkan di bursa seperti saham.", "ETF S&P 500 berisi Apple, Microsoft, NVIDIA, dll dalam satu produk."),
    DictionaryTerm("Index / Indeks", "7. ETF & Index", "Keranjang yang mewakili sekumpulan aset sebagai acuan pasar.", "S&P 500, IHSG, Nasdaq 100."),
    DictionaryTerm("Benchmark", "7. ETF & Index", "Standar pembanding untuk menilai performa suatu portfolio.", "Portfolio +12% vs benchmark S&P 500 +15% → tertinggal 3 poin persen."),

    // LEVEL 8 — BONDS
    DictionaryTerm("Bond / Obligasi", "8. Bonds", "Instrumen utang; investor meminjamkan uang kepada penerbit dan menerima bunga.", "Membeli obligasi pemerintah dengan tenor 10 tahun."),
    DictionaryTerm("Coupon", "8. Bonds", "Pembayaran bunga berkala dari sebuah obligasi.", "Obligasi dengan coupon 6% per tahun."),
    DictionaryTerm("Maturity", "8. Bonds", "Tanggal jatuh tempo saat pokok obligasi dikembalikan.", "Obligasi jatuh tempo 10 tahun dari tanggal terbit."),
    DictionaryTerm("Credit Risk", "8. Bonds", "Risiko penerbit obligasi gagal membayar kewajibannya.", "Obligasi korporasi umumnya punya credit risk lebih tinggi dari obligasi negara."),
    DictionaryTerm("Duration", "8. Bonds", "Ukuran sensitivitas harga obligasi terhadap perubahan suku bunga.", "Saat suku bunga naik, harga obligasi yang beredar cenderung turun."),

    // LEVEL 9 — OPTIONS & GREEKS
    DictionaryTerm("Call Option", "9. Options & Greeks", "Kontrak yang memberi hak (bukan kewajiban) untuk membeli aset di harga tertentu.", "Beli call NVDA strike $200 berharap harga naik di atas itu."),
    DictionaryTerm("Put Option", "9. Options & Greeks", "Kontrak yang memberi hak untuk menjual aset di harga tertentu.", "Beli put untuk melindungi portfolio dari penurunan harga."),
    DictionaryTerm("Strike Price", "9. Options & Greeks", "Harga yang ditentukan dalam kontrak option.", "Strike price $200 pada contoh call option di atas."),
    DictionaryTerm("Premium", "9. Options & Greeks", "Harga yang dibayar untuk membeli sebuah kontrak option.", "Premium call option $5 per lembar."),
    DictionaryTerm("Delta / Gamma / Theta / Vega / Rho (Greeks)", "9. Options & Greeks", "Ukuran sensitivitas harga option terhadap harga underlying (Delta), kecepatan perubahan Delta (Gamma), peluruhan waktu (Theta), volatilitas (Vega), dan suku bunga (Rho).", "Theta tinggi berarti option kehilangan nilai lebih cepat seiring waktu."),
    DictionaryTerm("Implied Volatility (IV)", "9. Options & Greeks", "Volatilitas yang tersirat dari harga option di pasar.", "IV tinggi membuat premium option lebih mahal."),
    DictionaryTerm("0DTE", "9. Options & Greeks", "Zero Days To Expiration; option yang jatuh tempo pada hari yang sama, sangat sensitif dan berisiko tinggi.", "Trading 0DTE membutuhkan manajemen risiko sangat ketat."),

    // LEVEL 10 — PSIKOLOGI PASAR
    DictionaryTerm("Bull Market", "10. Psikologi Pasar", "Periode pasar secara umum mengalami tren naik berkepanjangan.", "Bull market saham teknologi berlangsung beberapa tahun."),
    DictionaryTerm("Bear Market", "10. Psikologi Pasar", "Periode pasar mengalami tren turun berkepanjangan.", "Bear market ditandai penurunan luas lebih dari 20% dari puncak."),
    DictionaryTerm("Correction", "10. Psikologi Pasar", "Penurunan harga yang lebih moderat dari level tertinggi, tanpa berubah jadi bear market penuh.", "Indeks turun 10% dari puncak lalu stabil kembali."),
    DictionaryTerm("FOMO", "10. Psikologi Pasar", "Fear Of Missing Out; membeli aset karena takut ketinggalan kenaikan harga.", "\"Harganya naik terus, buru-buru beli sekarang!\""),
    DictionaryTerm("Panic Selling", "10. Psikologi Pasar", "Menjual aset karena rasa takut berlebihan saat harga turun.", "Investor menjual semua posisi saat market crash tanpa analisis."),
    DictionaryTerm("Capitulation", "10. Psikologi Pasar", "Titik di mana investor menyerah dan menjual besar-besaran setelah tekanan panjang.", "Volume jual meledak di titik terendah tren turun panjang."),
    DictionaryTerm("Risk-On / Risk-Off", "10. Psikologi Pasar", "Kecenderungan pasar mengambil aset berisiko (Risk-On) atau berpindah ke aset defensif (Risk-Off).", "Saat data ekonomi buruk, pasar cenderung Risk-Off ke obligasi dan emas."),

    // LEVEL 11 — MAKROEKONOMI
    DictionaryTerm("GDP", "11. Makroekonomi", "Gross Domestic Product; ukuran total output ekonomi suatu negara.", "GDP AS tumbuh 2.5% year-over-year."),
    DictionaryTerm("CPI", "11. Makroekonomi", "Consumer Price Index; ukuran perubahan harga barang dan jasa konsumen, indikator inflasi.", "CPI naik lebih tinggi dari ekspektasi, yield obligasi ikut naik."),
    DictionaryTerm("Interest Rate", "11. Makroekonomi", "Suku bunga acuan yang memengaruhi biaya pinjaman di seluruh ekonomi.", "Fed menaikkan suku bunga acuan 25 basis poin."),
    DictionaryTerm("The Fed / FOMC", "11. Makroekonomi", "Bank sentral AS (Federal Reserve) dan komitenya (FOMC) yang menentukan kebijakan moneter.", "FOMC mengumumkan keputusan suku bunga setiap beberapa minggu."),
    DictionaryTerm("Hawkish / Dovish", "11. Makroekonomi", "Hawkish = cenderung kebijakan ketat melawan inflasi; Dovish = cenderung kebijakan longgar mendukung pertumbuhan.", "Pernyataan hawkish Fed biasanya menekan harga saham dan obligasi."),
    DictionaryTerm("QE / QT", "11. Makroekonomi", "Quantitative Easing (bank sentral membeli aset untuk stimulus) dan Quantitative Tightening (mengurangi neraca bank sentral).", "QE cenderung mendukung aset berisiko, QT cenderung menekannya."),

    // LEVEL 12 — METRIK PORTFOLIO
    DictionaryTerm("CAGR", "12. Metrik Portfolio", "Compound Annual Growth Rate; tingkat pertumbuhan tahunan majemuk suatu investasi.", "Investasi tumbuh dari Rp10 juta ke Rp20 juta dalam 5 tahun → CAGR sekitar 14.9%."),
    DictionaryTerm("Sharpe Ratio", "12. Metrik Portfolio", "Mengukur return tambahan yang diperoleh untuk setiap unit risiko/volatilitas yang diambil.", "Sharpe ratio lebih tinggi berarti return per unit risiko lebih baik."),
    DictionaryTerm("Alpha", "12. Metrik Portfolio", "Return tambahan suatu portfolio relatif terhadap benchmark-nya.", "Alpha positif berarti mengalahkan benchmark setelah penyesuaian tertentu."),
    DictionaryTerm("Beta", "12. Metrik Portfolio", "Sensitivitas pergerakan suatu aset terhadap pergerakan pasar/benchmark.", "Beta 1.5 berarti aset cenderung bergerak 1.5x lebih besar dari pasar."),
    DictionaryTerm("Correlation", "12. Metrik Portfolio", "Seberapa erat dua aset bergerak bersama, penting untuk diversifikasi.", "Korelasi +1 = searah sempurna, -1 = berlawanan sempurna, 0 = tidak berkorelasi."),

    // LEVEL 13 — ADVANCED / DEWA
    DictionaryTerm("WACC", "13. Advanced/Dewa", "Weighted Average Cost of Capital; rata-rata tertimbang biaya modal perusahaan (utang + ekuitas).", "Dipakai sebagai discount rate dalam perhitungan DCF."),
    DictionaryTerm("CAPM", "13. Advanced/Dewa", "Capital Asset Pricing Model; model untuk memperkirakan return yang diharapkan dari sebuah aset berdasarkan risikonya (beta).", "CAPM membantu menentukan cost of equity dalam WACC."),
    DictionaryTerm("NPV / IRR", "13. Advanced/Dewa", "Net Present Value (nilai sekarang dari arus kas masa depan) dan Internal Rate of Return (tingkat diskonto yang membuat NPV = 0).", "Proyek dengan NPV positif umumnya dianggap layak dijalankan."),
    DictionaryTerm("ROE / ROA / ROIC", "13. Advanced/Dewa", "Return on Equity, Return on Assets, Return on Invested Capital — mengukur efisiensi perusahaan menghasilkan laba dari modal, aset, atau modal yang diinvestasikan.", "ROE tinggi konsisten sering dianggap tanda kualitas bisnis yang baik."),
    DictionaryTerm("D/E Ratio", "13. Advanced/Dewa", "Debt-to-Equity; rasio total hutang terhadap ekuitas perusahaan.", "D/E rendah biasanya menandakan struktur modal lebih konservatif."),
    DictionaryTerm("Current Ratio / Quick Ratio", "13. Advanced/Dewa", "Ukuran kemampuan perusahaan memenuhi kewajiban jangka pendek; Quick Ratio versi lebih ketat (tanpa inventory).", "Current ratio di atas 1 menandakan aset lancar melebihi kewajiban lancar."),
    DictionaryTerm("Algorithmic Trading", "13. Advanced/Dewa", "Trading yang dijalankan otomatis oleh program/algoritma berdasarkan aturan matematis.", "Strategi mean-reversion dijalankan otomatis oleh bot trading."),
    DictionaryTerm("Backtesting", "13. Advanced/Dewa", "Menguji strategi trading terhadap data historis sebelum dipakai secara live.", "Backtest strategi RSI pada data 5 tahun terakhir sebelum dipakai nyata."),
    DictionaryTerm("Mean Reversion", "13. Advanced/Dewa", "Asumsi bahwa harga cenderung kembali ke rata-rata setelah bergerak ekstrem.", "Strategi mean reversion membeli saat harga jauh di bawah MA."),
    DictionaryTerm("Monte Carlo Simulation", "13. Advanced/Dewa", "Teknik simulasi statistik dengan ribuan skenario acak untuk memperkirakan probabilitas hasil investasi.", "Dipakai memperkirakan probabilitas portfolio mencapai target dana pensiun.")
)
