package com.pinakes.app.staffe2e

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A fake Pinakes Mobile API on the device's loopback: every route answers a JSON fixture from
 * `androidTest/assets/fixtures`, unknown routes answer the API's 404 envelope, and every
 * request is recorded so a test can assert what the app actually sent.
 *
 * Routes are keyed `"METHOD /path"` with the path relative to `/api/v1/` and without the
 * query string, e.g. `"GET staff/lookups"`.
 */
class FakePinakes {
    val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, () -> MockResponse>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    init {
        // Everything the app asks for in the background, so screens never block on a 404.
        json("GET health", "health_staff")
        json("GET me", "me_admin")
        json("GET me/devices", "empty_list")
        json("GET me/notifications", "empty_list")
        json("GET me/loans", "loans_empty")
        json("GET me/reservations", "empty_list")
        json("GET me/wishlist", "empty_list")
        json("GET me/calendar", "calendar_disabled")
        json("GET catalog/search", "search_books")
        json("GET catalog/sync", "sync_full")
        json("GET catalog/genres", "empty_list")
        json("GET catalog/languages", "empty_list")
        json("GET catalog/books/1", "book_1")
        json("GET staff/lookups", "staff_lookups")
        json("GET staff/genres", "staff_genres")
        json("GET staff/shelves", "staff_shelves")
        json("GET staff/books", "staff_books")
        json("GET staff/books/5", "staff_book_5")
        json("GET staff/authors", "empty_list")
        json("GET staff/publishers", "empty_list")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.requestUrl?.encodedPath.orEmpty().removePrefix("/api/v1/").trimEnd('/')
                val route = routes["${request.method} $path"]
                return route?.invoke() ?: notFound()
            }
        }
        server.start()
    }

    /** Base API URL the app is pointed at (`http://localhost:port/api/v1/`). */
    val apiBaseUrl: String get() = server.url("/api/v1/").toString()
    val origin: String get() = apiBaseUrl.removeSuffix("/api/v1/")

    fun json(route: String, fixture: String, status: Int = 200) {
        routes[route] = { MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(fixture)) }
    }

    fun body(route: String, status: Int, body: String) {
        routes[route] = { MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body) }
    }

    fun recorded(method: String, path: String): List<RecordedRequest> =
        requests.filter { it.method == method && it.requestUrl?.encodedPath == "/api/v1/$path" }

    fun shutdown() = runCatching { server.shutdown() }

    private fun notFound() = MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
        .setBody("""{"data":null,"meta":{},"error":{"code":"not_found","message":"Not found."}}""")

    companion object {
        fun fixture(name: String): String =
            InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name.json").bufferedReader().use { it.readText() }
    }
}
