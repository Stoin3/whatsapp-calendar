package nl.stijn.nyprooster

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
        /** Elk uur een controle, voor het geval een melding gemist is. */
        fun planPeriodiek(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "periodiek",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ScanWorker>(1, TimeUnit.HOURS).build(),
            )
        }

        /**
         * Na een WhatsApp-melding: WhatsApp moet de PDF eerst downloaden,
         * dus een paar keer kijken (na 20 s, 1, 3 en 10 minuten).
         */
        fun planNaMelding(context: Context) {
            val wm = WorkManager.getInstance(context)
            listOf(20L, 60L, 180L, 600L).forEach { sec ->
                wm.enqueueUniqueWork(
                    "na-melding-$sec",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ScanWorker>().setInitialDelay(sec, TimeUnit.SECONDS).build(),
                )
            }
        }
    }
}
