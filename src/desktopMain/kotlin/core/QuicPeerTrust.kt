package core

import io.netty.util.NetUtil
import java.net.IDN
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * The incubator QUIC engine cannot set SSLParameters. Verify SAN explicitly after JVM chain
 * validation.
 */
internal class QuicPeerTrust(private val delegate: X509TrustManager, private val peer: String) :
    X509ExtendedTrustManager() {
    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        delegate.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket,
    ) = checkClientTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine,
    ) = checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        delegate.checkServerTrusted(chain, authType)
        if (chain.isEmpty() || !matchesPeer(peer, chain[0].subjectAlternativeNames.orEmpty())) {
            throw CertificateException("QUIC certificate does not match the requested peer")
        }
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket,
    ) {
        checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine,
    ) {
        checkServerTrusted(chain, authType)
    }

    companion object {
        fun system(peer: String): QuicPeerTrust {
            require(peer.isNotBlank())
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return QuicPeerTrust(
                factory.trustManagers.filterIsInstance<X509TrustManager>().first(),
                peer,
            )
        }

        fun matchesPeer(peer: String?, names: Collection<List<*>>): Boolean {
            if (peer.isNullOrBlank()) return false
            val host = peer.removePrefix("[").removeSuffix("]")
            val address = NetUtil.createByteArrayFromIpAddressString(host)
            return if (address != null) matchesAddress(address, names) else matchesDns(host, names)
        }

        private fun matchesAddress(address: ByteArray, names: Collection<List<*>>): Boolean =
            names.any { name ->
                name.getOrNull(0) == 7 &&
                    when (val value = name.getOrNull(1)) {
                        is String ->
                            NetUtil.createByteArrayFromIpAddressString(value)
                                ?.contentEquals(address) == true
                        is ByteArray -> value.contentEquals(address)
                        else -> false
                    }
            }

        private fun dns(value: String): String =
            IDN.toASCII(value.removeSuffix("."), IDN.USE_STD3_ASCII_RULES)
                .lowercase(java.util.Locale.ROOT)

        private fun matchesDns(host: String, names: Collection<List<*>>): Boolean =
            try {
                val expected = dns(host)
                names.any { matchesDnsName(expected, it) }
            } catch (_: IllegalArgumentException) {
                false
            }

        private fun matchesDnsName(expected: String, name: List<*>): Boolean {
            val value = name.getOrNull(1)
            return if (name.getOrNull(0) != 2 || value !is String) false
            else if (!value.startsWith("*.")) expected == dns(value)
            else matchesWildcard(expected, dns(value.drop(2)))
        }

        private fun matchesWildcard(expected: String, suffix: String): Boolean {
            val first = expected.removeSuffix(".$suffix")
            val suffixMatches = suffix.contains('.') && expected.endsWith(".$suffix")
            val singleLabel = first.isNotEmpty() && !first.contains('.')
            return suffixMatches && singleLabel
        }
    }
}
