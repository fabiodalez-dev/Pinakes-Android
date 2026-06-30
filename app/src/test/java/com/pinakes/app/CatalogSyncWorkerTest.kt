package com.pinakes.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.pinakes.app.data.sync.CatalogSyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The periodic catalog-sync worker (touched: WorkManager #11). Under a plain test
 * Application (not PinakesApplication) the worker degrades to a successful no-op
 * instead of crashing — so a missing/odd Application context never fails the chain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogSyncWorkerTest {

    @Test fun succeedsAsNoOpWhenContextIsNotPinakesApplication() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<CatalogSyncWorker>(ctx).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
