package com.pinakes.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.store.SessionStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh of the offline catalog snapshot. Complements the
 * foreground refresh in [com.pinakes.app.PinakesApplication]: even if the user doesn't
 * open the app, WorkManager keeps the cached "Available now" shelf reasonably fresh so the
 * catalog works offline without hammering the server.
 *
 * No-ops when the user is logged out; asks WorkManager to retry on a network failure.
 * Uses the default WorkerFactory and reaches its dependencies through a Hilt [EntryPoint]
 * (the worker is created by WorkManager, not by Hilt, so it can't be constructor-injected).
 */
class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /** Bridge from WorkManager-created workers into the Hilt singleton graph. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun session(): SessionStore
        fun catalogRepository(): CatalogRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        // Nothing to sync for a signed-out user; succeed so the periodic chain continues.
        if (!deps.session().isLoggedIn()) return Result.success()

        return when (deps.catalogRepository().refreshCatalog()) {
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
