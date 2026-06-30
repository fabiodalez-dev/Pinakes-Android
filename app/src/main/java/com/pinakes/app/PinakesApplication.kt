package com.pinakes.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.pinakes.app.data.sync.CatalogSyncWorker
import com.pinakes.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application entry point; owns the single [ServiceLocator] and the Coil image loader. */
class PinakesApplication : Application(), ImageLoaderFactory {

    lateinit var services: ServiceLocator
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        services = ServiceLocator(this)

        // Refresh the cached catalog every time the app comes to the foreground, so the
        // offline catalog stays current without a network round-trip on every screen.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (!services.session.isLoggedIn()) return
                appScope.launch { services.catalogRepository.refreshCatalog() }
            }
        })

        // Keep the offline snapshot fresh even when the app isn't opened (every 6h, on a
        // connected network). Idempotent (KEEP) — safe to call on every process start.
        CatalogSyncWorker.schedule(this)
    }

    /**
     * App-wide Coil loader with a persistent 256 MB disk cache that ignores server cache
     * headers, so book covers are downloaded once and reused across sessions instead of
     * being re-fetched on every screen / app open.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}
