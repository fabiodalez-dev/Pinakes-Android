package com.pinakes.app.data.network

import android.util.Log
import com.pinakes.app.BuildConfig
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * An [X509ExtendedTrustManager] that completes an incomplete server certificate chain by fetching
 * the missing intermediate CA certificate(s) from the leaf's Authority Information Access (AIA)
 * "CA Issuers" URL — exactly what a browser does, and what OkHttp / Android's default TLS stack
 * does NOT.
 *
 * Why: self-hosted Pinakes instances behind QNAP / Synology reverse proxies routinely serve an
 * incomplete chain (leaf only, or leaf + wrong intermediate). Browsers — and Firefox with its own
 * store — paper over this by fetching the intermediate from the AIA URL; the native app, using the
 * system trust store with no AIA fetching, fails the handshake with
 * "Trust anchor for certification path not found" even though the certificate is a perfectly valid
 * Let's Encrypt one whose root Android already trusts. (Reproduced against a real user's QNAP
 * instance in the emulator: `letsencrypt.org` validates, the user's host does not.)
 *
 * It extends [X509ExtendedTrustManager] (not the plain [X509TrustManager]) because an app with a
 * domain-specific network-security-config makes Android's platform trust manager reject the
 * hostname-unaware two-arg `checkServerTrusted`, demanding the socket/engine-aware overloads.
 *
 * Security: this NEVER weakens validation. The final decision is always made by the platform
 * default trust manager (with the real peer hostname, via the socket/engine overloads) against the
 * system trust store. We only ADD intermediate certificates fetched via AIA and re-validate; a
 * fetched certificate that does not cryptographically chain to a trusted root is rejected exactly
 * as before, and on any failure we re-throw the ORIGINAL exception so genuinely untrusted certs
 * fail identically. No new trust anchors are introduced.
 */
class AiaCompletingTrustManager(
    private val delegate: X509ExtendedTrustManager,
) : X509ExtendedTrustManager() {

    private val certFactory = CertificateFactory.getInstance("X.509")

    /** Fetched intermediates cached by AIA URL for the process lifetime (handshake-hot path). */
    private val cache = ConcurrentHashMap<String, X509Certificate>()

    // ── Server trust: complete the chain via AIA, then defer to the platform (hostname-aware). ──

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) =
        completeThenValidate(chain) { delegate.checkServerTrusted(it, authType, socket) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) =
        completeThenValidate(chain) { delegate.checkServerTrusted(it, authType, engine) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
        completeThenValidate(chain) { delegate.checkServerTrusted(it, authType) }

    // ── Client trust + accepted issuers: straight delegation, never touched. ──

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) =
        delegate.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) =
        delegate.checkClientTrusted(chain, authType, engine)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        delegate.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    /**
     * Run [validate] on the server-supplied chain; if it throws, try again on an AIA-completed chain.
     * On any completion/re-validation failure the ORIGINAL exception is surfaced, so the behaviour
     * for genuinely untrusted certificates is byte-for-byte unchanged.
     */
    private inline fun completeThenValidate(
        chain: Array<out X509Certificate>,
        validate: (Array<X509Certificate>) -> Unit,
    ) {
        try {
            @Suppress("UNCHECKED_CAST")
            validate(chain as Array<X509Certificate>)
            return
        } catch (original: CertificateException) {
            val completed = runCatching { completeChain(chain.toList()) }.getOrNull()
            if (completed == null || completed.size == chain.size) throw original
            try {
                validate(completed.toTypedArray())
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "AIA-completed chain (${chain.size} -> ${completed.size}) validated.")
                }
            } catch (stillUntrusted: CertificateException) {
                throw original
            }
        }
    }

    /** Walk up from the server-supplied chain, fetching each missing issuer via AIA, until a
     *  self-issued cert is reached, the issuer is already present, or the depth cap is hit. */
    private fun completeChain(initial: List<X509Certificate>): List<X509Certificate> {
        val chain = ArrayList(initial)
        var depth = 0
        while (depth++ < MAX_FETCH) {
            val top = chain.last()
            if (top.subjectX500Principal == top.issuerX500Principal) break // self-issued root
            if (chain.any { it.subjectX500Principal == top.issuerX500Principal }) break // issuer already here
            val issuer = fetchIssuer(top) ?: break
            if (issuer.subjectX500Principal != top.issuerX500Principal) break // fetched wrong cert
            chain.add(issuer)
        }
        return chain
    }

    private fun fetchIssuer(cert: X509Certificate): X509Certificate? {
        for (url in caIssuerUrls(cert)) {
            cache[url]?.let { return it }
            val fetched = runCatching { download(url) }.getOrNull() ?: continue
            cache[url] = fetched
            return fetched
        }
        return null
    }

    private fun download(url: String): X509Certificate? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = FETCH_TIMEOUT_MS
            readTimeout = FETCH_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        return try {
            val bytes = conn.inputStream.use { it.readBytes() }
            // AIA "CA Issuers" is usually a single DER certificate; CertificateFactory also parses
            // PEM. A non-certificate body (e.g. an OCSP responder answering a bare GET) throws, and
            // the caller simply tries the next URL.
            certFactory.generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
        } catch (t: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Every ASCII http(s) URL embedded in the AIA extension (OID 1.3.6.1.5.5.7.1.1). We skip a
     *  full ASN.1 dependency: OCSP URLs are naturally filtered out downstream because [download]
     *  returns null for anything that doesn't parse as a certificate. */
    private fun caIssuerUrls(cert: X509Certificate): List<String> {
        val ext = cert.getExtensionValue(AIA_OID) ?: return emptyList()
        val text = String(ext, Charsets.ISO_8859_1)
        return URL_REGEX.findAll(text).map { it.value }.distinct().toList()
    }

    companion object {
        private const val TAG = "AiaTrust"
        private const val AIA_OID = "1.3.6.1.5.5.7.1.1"
        private const val MAX_FETCH = 4
        private const val FETCH_TIMEOUT_MS = 5_000
        private val URL_REGEX = Regex("""https?://[A-Za-z0-9._~:/?#\[\]@!${'$'}&'()*+,;=%-]+""")

        /**
         * Platform default trust manager wrapped so incomplete chains are completed via AIA. Returns
         * the socket factory + trust manager pair OkHttp's `sslSocketFactory(factory, tm)` needs.
         */
        fun sslSocketFactory(): Pair<SSLSocketFactory, X509TrustManager> {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            val system = tmf.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()
            val aia = AiaCompletingTrustManager(system)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(aia), null)
            return ctx.socketFactory to aia
        }
    }
}
