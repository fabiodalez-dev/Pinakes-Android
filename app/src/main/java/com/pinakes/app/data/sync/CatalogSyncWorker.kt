package com.pinakes.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pinakes.app.PinakesApplication
import com.pinakes.app.data.network.ApiResult
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh of the offline catalog snapshot. Complements the
 * foreground refresh in [PinakesApplication]: even if the user doesn't open the app,
 * WorkManager keeps the cached "Available now" shelf reasonably fresh so the catalog
 * works offline without hammering the server.
 *
 * No-ops when the user is logged out; asks WorkManager to retry on a network failure.
 * Uses the default WorkerFactory (this worker only needs the standard
 * `(Context, WorkerParameters)` constructor) and reaches dependencies through the
 * Application's [com.pinakes.app.di.ServiceLocator].
 */
class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PinakesApplication ?: return Result.success()
        // Nothing to sync for a signed-out user; succeed so the periodic chain continues.
        if (!app.services.session.isLoggedIn()) return Result.success()

        return when (app.services.catalogRepository.refreshCatalog()) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Failure -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "catalog-periodic-sync"

        /**
         * Enqueue the unique periodic sync (every 6h, only on a connected network).
         * [ExistingPeriodicWorkPolicy.KEEP] makes this idempotent — calling it on every
         * app start never resets the schedule or stacks duplicates.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
