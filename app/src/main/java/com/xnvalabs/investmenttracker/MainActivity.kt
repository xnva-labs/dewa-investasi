package com.xnvalabs.investmenttracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.xnvalabs.investmenttracker.notification.NotificationHelper
import com.xnvalabs.investmenttracker.ui.AppRoot
import com.xnvalabs.investmenttracker.ui.InvestmentTrackerTheme
import com.xnvalabs.investmenttracker.worker.PriceSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Entry point aplikasi. Sengaja dibuat setipis mungkin:
 * - Setup notification channel & permission di sini.
 * - Jadwalkan background price sync (WorkManager) di sini.
 * - Semua UI & state didelegasikan ke AppRoot() (lihat package ui),
 *   yang di-backing PortfolioViewModel + Room (lihat package viewmodel & data).
 */
class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted atau tidak, app tetap jalan */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        schedulePriceSyncWorker()

        setContent {
            InvestmentTrackerTheme {
                AppRoot()
            }
        }
    }

    /**
     * Daftarkan job background yang refresh harga crypto tiap 15 menit
     * (minimum interval yang diizinkan WorkManager untuk periodic work),
     * hanya jalan kalau ada koneksi internet. Lihat PriceSyncWorker.kt.
     */
    private fun schedulePriceSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PriceSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PriceSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
