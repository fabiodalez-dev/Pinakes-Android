package com.pinakes.app.staffe2e

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.pinakes.app.data.connectivity.ConnectivityObserver
import com.pinakes.app.data.sync.CatalogSyncWorker
import com.pinakes.app.ui.screens.search.BookSort
import com.pinakes.app.ui.screens.search.SearchViewModel
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertEquals
import org.junit.Test

/** Search listens to connectivity once, however many searches, sorts and filter resets run. */
class ConnectivityCollectorTest {

    @Test fun searchKeepsASingleConnectivityCollector() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deps = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext as Application, CatalogSyncWorker.Deps::class.java)
        val connectivity = ConnectivityObserver.Fixed(true)
        lateinit var vm: SearchViewModel
        instrumentation.runOnMainSync { vm = SearchViewModel(deps.catalogRepository(), connectivity) }
        instrumentation.runOnMainSync {
            repeat(3) { vm.submitSearch() }
            vm.setSort(BookSort.TITLE_ASC)
            vm.setSort(BookSort.TITLE_DESC)
            vm.clearFilters()
        }
        instrumentation.waitForIdleSync()
        assertEquals(1, connectivity.subscribers)
    }
}
