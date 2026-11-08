package nl.stijn.nyprooster

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WhatsAppMap.scan(applicationContext)
        return Result.success()
    }

    companion object {
        /**
         * Elke 15 minuten (het minimum van Android) de WhatsApp-documentenmap controleren.
         * Bij een slapende telefoon kan Android dit wat uitstellen.
         */
        fun planPeriodiek(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "periodiek-15min",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES).build(),
            )
            WorkManager.getInstance(context).cancelUniqueWork("periodiek") // oude versie (elk uur)
        }
    }
}
