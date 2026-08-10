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
 * It also tolerates a server that appends an unrelated certificate to the chain — notably a QNAP
 * box whose reinstalled Let's Encrypt cert ships [leaf, intermediate, retired DST Root CA X3]. The
 * completion re-links the chain from the leaf first (see [orderedChainFromLeaf]), drops the stray
 * root, and then walks up via AIA to the intermediate's real issuer; desktop browsers paper over
 * this because they already trust the new ISRG root, but Android must be handed the path down to a
 * root it ships.
 *
 * Security: this NEVER weakens validation. The final decision is always made by the platform
 * default trust manager (with the real peer hostname, via the socket/engine overloads) against the
 * system trust store. We only re-link the presented certificates and ADD intermediates fetched via
 * AIA, then re-validate; dropping a stray certificate is purely structural and can only remove a
 * dead end, never introduce a trust anchor. A fetched certificate that does not cryptographically
 * chain to a trusted root is rejected exactly as before, and on any failure we re-throw the
 * ORIGINAL exception so genuinely untrusted certs fail identically.
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
            val presented = chain.toList()
            val completed = runCatching { completeChain(presented) }.getOrNull()
            // Re-validate only when completion actually changed the chain. A size check is not
            // enough: dropping an unrelated appended root and fetching the genuine issuer can
            // leave the count identical while the contents differ (X509Certificate.equals compares
            // the encoded form, so a plain list comparison catches this).
            if (completed == null || completed == presented) throw original
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
     *  self-issued cert is reached, the issuer is already present, or the depth cap is hit.
     *
     *  The walk starts from the issuer-linked chain rooted at the leaf (see [orderedChainFromLeaf]),
     *  NOT from the raw presented list: a misconfigured server can append a certificate that is not
     *  part of the leaf's path — typically an unrelated, expired, self-signed root such as the
     *  retired DST Root CA X3 — and walking up from that junk cert would stop immediately (it is
     *  self-issued) instead of fetching the real intermediate's issuer. Reproduced against a QNAP
     *  instance whose reinstalled Let's Encrypt cert served [leaf, intermediate, DST Root CA X3];
     *  desktop browsers already trust the new ISRG root and ignore the junk, but Android needs the
     *  chain completed down to a root it ships. */
    private fun completeChain(initial: List<X509Certificate>): List<X509Certificate> {
        val chain = ArrayList(orderedChainFromLeaf(initial))
        if (chain.isEmpty()) return chain
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
         * Reorder the presented certificates into the single issuer-linked chain that starts at
         * the leaf (`initial[0]`, always the end-entity certificate in a TLS handshake), following
         * subject→issuer links and drawing the rest from the remaining certificates as a pool.
         * Anything not reachable from the leaf is discarded — an out-of-order intermediate is still
         * picked up, but an unrelated appended root (e.g. an expired self-signed DST Root CA X3) is
         * dropped so the AIA walk resumes from the genuine top of the chain.
         *
         * Purely structural: it inspects only subject/issuer distinguished names, never signatures
         * or validity. It can therefore never turn an untrusted certificate into a trusted one —
         * the final decision still belongs to the platform trust manager against the system store,
         * and dropping a stray certificate can only remove a dead end, not add a trust anchor.
         */
        @JvmStatic
        internal fun orderedChainFromLeaf(initial: List<X509Certificate>): List<X509Certificate> {
            if (initial.isEmpty()) return initial
            val pool = ArrayList(initial.drop(1))
            val ordered = ArrayList<X509Certificate>()
            ordered.add(initial[0])
            while (true) {
                val top = ordered.last()
                if (top.subjectX500Principal == top.issuerX500Principal) break // reached a self-issued root
                val issuer = pool.firstOrNull { it.subjectX500Principal == top.issuerX500Principal } ?: break
                pool.remove(issuer)
                ordered.add(issuer)
            }
            return ordered
        }

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
