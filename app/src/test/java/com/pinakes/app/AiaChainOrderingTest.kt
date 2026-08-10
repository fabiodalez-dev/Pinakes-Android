package com.pinakes.app

import com.pinakes.app.data.network.AiaCompletingTrustManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Regression for issue #16: a QNAP box whose reinstalled Let's Encrypt certificate serves
 * `[leaf, intermediate, retired DST Root CA X3]`. The stray, expired, self-signed root at the tail
 * made the AIA completer stop immediately (it saw a self-issued cert on top and assumed the chain
 * was complete), so the intermediate's real issuer was never fetched and Android rejected the
 * certificate as untrusted — while desktop browsers, already trusting the new ISRG root, ignored
 * the junk cert.
 *
 * [AiaCompletingTrustManager.orderedChainFromLeaf] is the fix: it re-links the presented
 * certificates into the single path rooted at the leaf, dropping anything not reachable from it,
 * so the subsequent AIA walk resumes from the genuine top of the chain. These tests pin that
 * structural behaviour with real (openssl-generated) certificates whose linkage is:
 *   leaf.example.org ── issued by ──▶ Test Intermediate I ── issued by ──▶ Test Root A (self-signed)
 * plus an unrelated self-signed "Junk Legacy Root X3" standing in for the DST Root CA X3.
 */
class AiaChainOrderingTest {

    private val factory: CertificateFactory = CertificateFactory.getInstance("X.509")

    private fun cert(base64Der: String): X509Certificate =
        factory.generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(base64Der)))
            as X509Certificate

    private val leaf = cert(LEAF)
    private val inter = cert(INTER)
    private val rootA = cert(ROOT_A)
    private val junk = cert(JUNK)

    private fun cn(c: X509Certificate) = c.subjectX500Principal.name

    @Test
    fun `drops an unrelated self-signed root appended after the intermediate`() {
        // The exact QNAP shape: leaf, intermediate, then a junk self-signed root.
        val result = AiaCompletingTrustManager.orderedChainFromLeaf(listOf(leaf, inter, junk))
        assertEquals(listOf(cn(leaf), cn(inter)), result.map(::cn))
    }

    @Test
    fun `reorders an out-of-order chain and still drops the junk root`() {
        val result = AiaCompletingTrustManager.orderedChainFromLeaf(listOf(leaf, junk, inter))
        assertEquals(listOf(cn(leaf), cn(inter)), result.map(::cn))
    }

    @Test
    fun `keeps a complete, correctly ordered chain untouched`() {
        val input = listOf(leaf, inter, rootA)
        val result = AiaCompletingTrustManager.orderedChainFromLeaf(input)
        assertEquals(input, result)
    }

    @Test
    fun `leaf-only chain is returned as-is for the AIA walk to complete`() {
        val result = AiaCompletingTrustManager.orderedChainFromLeaf(listOf(leaf))
        assertEquals(listOf(leaf), result)
    }

    @Test
    fun `a lone self-signed certificate terminates immediately`() {
        val result = AiaCompletingTrustManager.orderedChainFromLeaf(listOf(rootA))
        assertEquals(listOf(rootA), result)
    }

    @Test
    fun `an empty chain is returned unchanged`() {
        assertEquals(emptyList<X509Certificate>(), AiaCompletingTrustManager.orderedChainFromLeaf(emptyList()))
    }

    @Test
    fun `stops at the first self-issued root even if more certs follow it`() {
        // rootA is self-signed; anything after it (here the junk root) must not be pulled in.
        val result = AiaCompletingTrustManager.orderedChainFromLeaf(listOf(leaf, inter, rootA, junk))
        assertEquals(listOf(cn(leaf), cn(inter), cn(rootA)), result.map(::cn))
    }

    private companion object {
        const val LEAF =
            "MIIC2zCCAcOgAwIBAgIJAMpFcQyb+i5QMA0GCSqGSIb3DQEBCwUAMB4xHDAaBgNVBAMME1Rlc3QgSW50ZXJtZWRpYXRlIEkwHhcNMjYwODEwMDEwNTIwWhcNMzYwODA3MDEwNTIwWjAbMRkwFwYDVQQDDBBsZWFmLmV4YW1wbGUub3JnMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtIzK9GYKbrqbdGlo3t6kDcdCkhf/BPadab/9T3Mp9h1yJIxjeGjh8+GdFJPTrUfessW5Nj4AmE+HUQL+XP5014M9yrV7e2p69JD/7CgPOYOF8kUDBaSWrS9xKODymq6XrYyetHfhXt7BtrPDyPEj08DjundqikWgxRhInf0PJ9+mExG44tT0I+U4dgGIMBuu+OFa9239ut2crrDK3kHgvbF+I7JHPXQabFJJB+3HzkM2/b3rbDrYs1yoFLITvz1bR+7zmMbwJ+JwxUjbs7ACm5Jca9d5uLDt9xohknpyVHeIFxktdkzpNeZQjkdmmoN8HCPJOaFIk35fVh7vmABIUwIDAQABox8wHTAbBgNVHREEFDASghBsZWFmLmV4YW1wbGUub3JnMA0GCSqGSIb3DQEBCwUAA4IBAQCLTKBy8VefRZjG1BSGXgU//UgmvSPZ94mOXQ+EVlPmDDPS4IafDtlcAFR86BiVQQm+3gzX6qlVaiFfTiuYl5LOMGb3FyyzFfPKgk9EiuKnj2ztONklLYoTa/nxZtVadsket689d3Nf9Y8CFQ9P2zC/QDZzfNOmlfA8dbPRsuwKSiGRl0AqGxaSIiee4h68BLL2HhXMwQH5xYBKw4oBloe8NC/TDwk0Cb+RUx1o1ZYllFlykv2L18A+BUE+hNULapSL8G+HXTaNtpyj82P0g7I24oktxIdVuo/sawMS4TqxIpRj92iMYO4dvVz2Z4I/H2aYx6rUCfptJqnoIj0HeQrd"
        const val INTER =
            "MIIC1DCCAbygAwIBAgIJALl5UxANzXzTMA0GCSqGSIb3DQEBCwUAMBYxFDASBgNVBAMMC1Rlc3QgUm9vdCBBMB4XDTI2MDgxMDAxMDUyMFoXDTM2MDgwNzAxMDUyMFowHjEcMBoGA1UEAwwTVGVzdCBJbnRlcm1lZGlhdGUgSTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJ2M8CqR+niJFRoVqVLR65rtm3l5zZFnKRStV2Znj2tJ2ss4U5oZ5JhLxSemIIfJsl9m+lVbM80uB80ULjXXZcR8ASYXoJ0EeRtHSgjyTjwYTM6q8HP+fXmFrdKeXLo3vQpFe+CfuJQyGrV2uAtMT8kiLb1t/cLeCT9f96LFeXZD00nxt2kXcvT5h5EjmgVwsLn+msT40cQugZw8lp/pypiF7FEi0NaoJwgRC6CXNpc+wSSybGIazpxw1FCPpj6arf6fkxLY/s2YS15d80rLUIvUEAcieUkNvtMj1Fn81TqoheNotEY4nMKPdU1UAmoH9I9pnGOluX+UsxvMMckR6t0CAwEAAaMdMBswDAYDVR0TBAUwAwEB/zALBgNVHQ8EBAMCAgQwDQYJKoZIhvcNAQELBQADggEBAGO49rIemS/GZJUnwb2ezw+e2ugkHJ22grNE7EkWO7zdvNcesVb4DMKq4tW4ryJx4H8e7e2NbMbbP1P/1vmrjvu/HKhkEnZLMrP1dN0N1WMG3hG3/SoPc4IuBQug/jedICS65nLQdpj801rGCeQJbhet/expA+fdFnOlAHIaZUGOYvNqlxEthLcqkRhTTyQOy0Hq/V+qkK1jzLoreSCSg9yBl4bk4g0Tw+X3cT5W4HyT6CS7Yq+oR6rl/QdC3D5OD5U4MlZEFP4p/BgQuC85frOYO5unxJRCjfb9kr71RxtbYJ1G57WdWmB7cKwvm5Y317fuvjDPfec4hU+/qN3iiiM="
        const val ROOT_A =
            "MIICqDCCAZACCQD05fizjcT/LjANBgkqhkiG9w0BAQsFADAWMRQwEgYDVQQDDAtUZXN0IFJvb3QgQTAeFw0yNjA4MTAwMTA1MTlaFw0zNjA4MDcwMTA1MTlaMBYxFDASBgNVBAMMC1Rlc3QgUm9vdCBBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0ybn34RoFUWTerd/EqdvV69Xbzu8tHtGGnAnJemvbk5fYBlhn3C6XNnvFL9FWp2EeKpKD8x53BPpoF1BoXCBaYatFdNxCa1p7Zsr+Mio96k7pknFJ22SPv3Knklg3zuvZZcNxH3FDSb4z4eBAfWg+9N6Nhm/kA1eh9RTb9vA+V7ru+0ZbVpMVn57T/popvGaMh6JWbpAQfqfqA5cV/lIDQTPU0wVGpfQYMzG0LFo2Jpo2IEPtgGlGredEKvZzjBYph9oonxNbWEuHAfzeFElrfpaEZxkqaPXD3Ibt4PnvuknvTofdKUcdXHm3eXXUnWFo2fxON3IRv7IvU3Q5wCa9wIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQDPYY1prz/49u0DECuS5xeqMmE5T8OaEtEsNnA7VApsLoKAyd/JTshbBy2cJnmqelF1aEK/XWRD/LWEhM6ucUX8enQShNkLUsDuB/ECZKxcJ7FZ1V5ANCQa68wuN2pvZ6QuhQtFZB5QjclbeMsPzBwOQA9VyFs8ah5PaBxnnAOK+HT37ZC0WUKp6Mh/j2yvPg4bf2qv8X+QJOWi4GFzztSI8qyjMzZb2yvSzxteLLlwlFIvAuLqcRQNJjoPOHqwFXaMn1+Izzs+FaPmsAm6rkuUyAmsuA5E+neLNHE1EiqRt2egbE1LwKWwUxZz6AF53tcT560/rhIoRi/M67mgHo9P"
        const val JUNK =
            "MIICuDCCAaACCQCsozNW/QjQXTANBgkqhkiG9w0BAQsFADAeMRwwGgYDVQQDDBNKdW5rIExlZ2FjeSBSb290IFgzMB4XDTI2MDgxMDAxMDUyMFoXDTI2MDgxMTAxMDUyMFowHjEcMBoGA1UEAwwTSnVuayBMZWdhY3kgUm9vdCBYMzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALpvkwtXEyZaUWLtfzfMKPouvnrwcfxJayoXqeR4MtgK6yjcoYlwVs4cJLyzZgXwg2zawm8TfIENWJY2V9LSGvAr5tkpUgY99uJMohb8uc4+7XKIJ3YXGwiQtuY9lHV8XxA6fuNN+Vdr81mYzPLcqwOWIIv9vcLfpneQRWNNJDwmFDiyRgFZXUNuFUlVke8jgAKrdbdsKdjAk1L7fKecjG/LdSmDwNKnXRNphe/H7fwErqa14u/oikAdRmC9xfaZ2EWzviQHlaTL4p/SQNJ+8xb8Y4Kzv++7OwTAMB8iGK1W75pPf90Co0vf/kF+jU9iGi5CeaH8ojAKNXkA85rVMKcCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAlMqPKe8qGqxwwsjMt1ZJXpfMzzAyY3CyDHWxWiyqW+gJq4rxsskV3GFXaHU6A87DdgTDotik9jM54iieHtnCHs87EdIITFVxOcXIA6/ao4VETKBjRKqvtoDxNfkx9LX+Z+5sJ8Ibk3/5Qo+226+quYYXAQQcrLzJGelvn/25AkWwvEKsxzKOo3dFEryDrYHrcdkDJErv+ez+kbvj22G1iq+85E8NifrsZb6GsfsreXy+DTZWUOJbLogW0t4Tdw30l/3fFrCbEx4yt8Idxd0YAl8aj3K8sEs2QJT4ULRYGH4aUJQ86O4YbWWyWGuzKCm/VjnyFVGnpaeNOrEfJVi5Xg=="
    }
}
