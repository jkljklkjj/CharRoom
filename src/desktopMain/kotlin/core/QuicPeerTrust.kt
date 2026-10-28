package core

import io.netty.util.NetUtil
import java.net.IDN
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

/** The incubator QUIC engine cannot set SSLParameters. Verify SAN explicitly after JVM chain validation. */
internal class QuicPeerTrust(private val delegate: X509TrustManager, private val peer: String) : X509ExtendedTrustManager() {
    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = delegate.checkClientTrusted(chain, authType)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) = checkClientTrusted(chain, authType)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) = checkClientTrusted(chain, authType)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        delegate.checkServerTrusted(chain, authType)
        if (chain.isEmpty() || !matchesPeer(peer, chain[0].subjectAlternativeNames.orEmpty())) {
            throw CertificateException("QUIC certificate does not match the requested peer")
        }
    }
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {
        checkServerTrusted(chain, authType)
    }
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) {
        checkServerTrusted(chain, authType)
    }
    companion object {
        fun system(peer: String): QuicPeerTrust {
            require(peer.isNotBlank())
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return QuicPeerTrust(factory.trustManagers.filterIsInstance<X509TrustManager>().first(), peer)
        }
        fun matchesPeer(peer: String?, names: Collection<List<*>>): Boolean {
            if (peer.isNullOrBlank()) return false
            val host = peer.removePrefix("[").removeSuffix("]")
            val address = NetUtil.createByteArrayFromIpAddressString(host)
            if (address != null) return names.any { name ->
                name.getOrNull(0) == 7 && when (val value = name.getOrNull(1)) {
                    is String -> NetUtil.createByteArrayFromIpAddressString(value)?.contentEquals(address) == true
                    is ByteArray -> value.contentEquals(address)
                    else -> false
                }
            }
            fun dns(value: String): String = IDN.toASCII(value.removeSuffix("."), IDN.USE_STD3_ASCII_RULES).lowercase(java.util.Locale.ROOT)
            return try {
                val expected = dns(host)
                names.any { name ->
                    if (name.getOrNull(0) != 2 || name.getOrNull(1) !is String) false
                    else {
                        val value = name[1] as String
                        if (!value.startsWith("*.")) expected == dns(value)
                        else {
                            val suffix = dns(value.drop(2))
                            val first = expected.removeSuffix(".$suffix")
                            suffix.contains('.') && expected.endsWith(".$suffix") && first.isNotEmpty() && !first.contains('.')
                        }
                    }
                }
            } catch (_: IllegalArgumentException) { false }
        }
    }
}
