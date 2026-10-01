package com.xnvalabs.investmenttracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xnvalabs.investmenttracker.data.AssetRepository
import com.xnvalabs.investmenttracker.data.local.AppDatabase
import com.xnvalabs.investmenttracker.data.remote.MarketDataRepository
import com.xnvalabs.investmenttracker.notification.NotificationHelper
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.abs

/**
 * Background job: refresh harga live (Crypto, Saham, ETF) secara berkala
 * walau app tidak dibuka, lalu kirim notifikasi kalau ada pergerakan
 * signifikan. Jadwal didaftarkan di MainActivity (minimum 15 menit —
 * batas WorkManager untuk periodic work).
 */
class PriceSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "price_sync_worker"
        const val ALERT_THRESHOLD_PERCENT = 3.0
    }

    override suspend fun doWork(): Result {
        return try {
            val repository = AssetRepository(AppDatabase.getInstance(applicationContext).assetDao())
            val marketDataRepository = MarketDataRepository()

            val candidates = repository.observeAssets().first()
                .filter { marketDataRepository.isLiveSupported(it) }

            for (asset in candidates) {
                val livePrice = marketDataRepository.fetchLivePrice(asset) ?: continue
                val prevPrice = asset.currentPrice
                repository.upsert(asset.copy(currentPrice = livePrice))

                if (prevPrice.signum() != 0) {
                    val changePercent = livePrice.subtract(prevPrice)
                        .divide(prevPrice, MathContext(10))
                        .multiply(BigDecimal(100)).toDouble()
                    if (abs(changePercent) >= ALERT_THRESHOLD_PERCENT) {
                        val arrow = if (changePercent >= 0) "\u2B06\uFE0F" else "\u2B07\uFE0F"
                        val pctText = String.format(Locale.US, "%.2f", changePercent)
                        NotificationHelper.showNotification(
                            applicationContext,
                            "$arrow Price Alert: ${asset.ticker}",
                            "${asset.name} bergerak $pctText% menjadi US\$${livePrice.toPlainString()} (background sync)"
                        )
                    }
                }
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
