package com.pinakes.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.pinakes.app.data.sync.CatalogSyncWorker
import com.pinakes.app.data.network.CleartextGuardInterceptor
import com.pinakes.app.data.network.NetworkEntryPoint
import dagger.hilt.android.EntryPointAccessors
import okhttp3.OkHttpClient
import dagger.hilt.android.HiltAndroidApp
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point (`@HiltAndroidApp`) plus the Coil image loader. All dependencies
 * live in Hilt ([com.pinakes.app.di.AppModule]); code that can't be constructor-injected
 * (this Application and the WorkManager worker) reaches them through a Hilt EntryPoint.
 */
@HiltAndroidApp
class PinakesApplication : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        // Crash reporting (Sentry — vendor-neutral, not a Google service). Init as
        // early as possible so failures during the rest of onCreate() are captured.
        // The DSN is a public ingest endpoint; no PII or perf tracing is sent.
        SentryAndroid.init(this) { options ->
            options.dsn =
                "https://a51e7537271cdf25767251d18d2e4ffa@o4511654498926592.ingest.de.sentry.io/4511654504300624"
            options.environment = if (BuildConfig.DEBUG) "debug" else "production"
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isDebug = false
            options.isSendDefaultPii = false   // no IP / user data attached by default
            options.tracesSampleRate = 0.0     // crash reporting only — no performance tracing

            // Sentry's OkHttp auto-instrumentation reports every backend HTTP error as an
            // error-level SentryHttpClientException. This app talks to the user's OWN
            // self-hosted server, so a backend outage is the server's state, not an app
            // bug. Drop the two clearly-not-our-fault cases so they don't create noise:
            // the health probe (whose whole job is to detect a down server) and transient
            // upstream 5xx (502 bad gateway / 503 unavailable / 504 timeout). A real 500
            // or a 4xx (which can point at an app-side request bug) still comes through.
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                if (isExpectedBackendHttpFailure(event)) null else event
            }
        }

        // Refresh the cached catalog every time the app comes to the foreground, so the
        // offline catalog stays current without a network round-trip on every screen.
        // Reaches the Hilt singletons via the worker's EntryPoint (same session + repository).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val deps = EntryPointAccessors.fromApplication(
                    this@PinakesApplication, CatalogSyncWorker.Deps::class.java,
                )
                if (!deps.session().isLoggedIn()) return
                appScope.launch { deps.catalogRepository().refreshCatalog() }
            }
        })

        // Keep the offline snapshot fresh even when the app isn't opened (every 6h, on a
        // connected network). Idempotent (KEEP) — safe to call on every process start.
        CatalogSyncWorker.schedule(this)
    }

    /**
     * True when a Sentry event is a backend HTTP failure that reflects the user's own
     * server being unavailable rather than a bug in this app: a failed `/health` probe,
     * or a transient upstream 5xx (502/503/504). Such events are pure noise for an app
     * that points at self-hosted instances, so [onCreate]'s `beforeSend` drops them.
     */
    private fun isExpectedBackendHttpFailure(event: SentryEvent): Boolean {
        val detail = buildString {
            event.throwable?.let { append(it.toString()) }
            event.exceptions?.forEach { append(' ').append(it.type).append(' ').append(it.value) }
        }
        val isHttpClientError = detail.contains("SentryHttpClientException") ||
            detail.contains("HTTP Client Error with status code:")
        if (!isHttpClientError) return false

        // The health probe's job is to detect a down server — a failure there is expected.
        val path = event.request?.url?.substringBefore('?')?.trimEnd('/').orEmpty()
        if (path.endsWith("/health")) return true

        // Transient upstream unavailability, not an app bug.
        val status = Regex("""status code:\s*(\d{3})""").find(detail)?.groupValues?.get(1)?.toIntOrNull()
        return status == 502 || status == 503 || status == 504
    }

    /**
     * App-wide Coil loader with a persistent 256 MB disk cache that ignores server cache
     * headers, so book covers are downloaded once and reused across sessions instead of
     * being re-fetched on every screen / app open.
     */
    override fun newImageLoader(): ImageLoader {
        // Route Coil through the same cleartext gate as the API client, so a book cover
        // served over plain HTTP can't silently downgrade the connection on an HTTPS
        // instance (only allowed for loopback or when the user opted into insecure HTTP).
        val session = EntryPointAccessors
            .fromApplication(this, NetworkEntryPoint::class.java)
            .sessionStore()
        val guardedClient = OkHttpClient.Builder()
            .addInterceptor(CleartextGuardInterceptor { session.allowInsecureHttp })
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(guardedClient)
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
}
