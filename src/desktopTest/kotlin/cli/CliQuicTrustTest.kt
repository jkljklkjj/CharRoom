package cli

import core.QuicPeerTrust
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import kotlin.test.*

class CliQuicTrustTest {
    @Test fun nativeContextUsesExplicitPeerVerificationEvenWhenEngineHasNoPeerName() {
        val context = QuicSslContextBuilder.forClient().applicationProtocols("custom").trustManager(QuicPeerTrust.system("example.test")).build()
        val engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT, "example.test", 443)
        val acceptingChainValidator = object : javax.net.ssl.X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) {}
        }
        val cert = FixtureCertificate(listOf(listOf(2, "example.test")))
        QuicPeerTrust(acceptingChainValidator, "example.test").checkServerTrusted(arrayOf(cert), "RSA", engine)
        assertFailsWith<java.security.cert.CertificateException> {
            QuicPeerTrust(acceptingChainValidator, "wrong.test").checkServerTrusted(arrayOf(cert), "RSA", engine)
        }
    }
    @Test fun sanMatchingRejectsMissingNamesNestedWildcardsAndWrongAddresses() {
        assertTrue(QuicPeerTrust.matchesPeer("chat.example.test", listOf(listOf(2, "*.example.test"))))
        assertFalse(QuicPeerTrust.matchesPeer("nested.chat.example.test", listOf(listOf(2, "*.example.test"))))
        assertFalse(QuicPeerTrust.matchesPeer("example.test", listOf(listOf(2, "*.example.test"))))
        assertFalse(QuicPeerTrust.matchesPeer("chat.example.test", listOf(listOf(2, "other.example.test"))))
        assertFalse(QuicPeerTrust.matchesPeer("chat.example.test", emptyList()))
        assertTrue(QuicPeerTrust.matchesPeer("[::1]", listOf(listOf(7, "0:0:0:0:0:0:0:1"))))
        assertFalse(QuicPeerTrust.matchesPeer("127.0.0.1", listOf(listOf(2, "127.0.0.1"))))
        assertTrue(QuicPeerTrust.matchesPeer("127.0.0.1", listOf(listOf(7, "127.0.0.1"))))
    }
}

private class FixtureCertificate(private val names: Collection<List<*>>) : java.security.cert.X509Certificate() {
    override fun getSubjectAlternativeNames(): Collection<List<*>> = names
    override fun getEncoded() = byteArrayOf()
    override fun verify(key: java.security.PublicKey?) {}
    override fun verify(key: java.security.PublicKey?, provider: String?) {}
    override fun toString() = "fixture certificate"
    override fun getPublicKey(): java.security.PublicKey? = null
    override fun checkValidity() {}
    override fun checkValidity(date: java.util.Date?) {}
    override fun getVersion() = 3
    override fun getSerialNumber() = java.math.BigInteger.ONE
    override fun getIssuerDN(): java.security.Principal = javax.security.auth.x500.X500Principal("CN=fixture")
    override fun getSubjectDN(): java.security.Principal = issuerDN
    override fun getNotBefore() = java.util.Date(0)
    override fun getNotAfter() = java.util.Date(Long.MAX_VALUE)
    override fun getTBSCertificate() = byteArrayOf()
    override fun getSignature() = byteArrayOf()
    override fun getSigAlgName() = "SHA256withRSA"
    override fun getSigAlgOID() = "1.2.840.113549.1.1.11"
    override fun getSigAlgParams(): ByteArray? = null
    override fun getIssuerUniqueID(): BooleanArray? = null
    override fun getSubjectUniqueID(): BooleanArray? = null
    override fun getKeyUsage(): BooleanArray? = null
    override fun getBasicConstraints() = -1
    override fun getCriticalExtensionOIDs(): Set<String>? = null
    override fun getNonCriticalExtensionOIDs(): Set<String>? = null
    override fun getExtensionValue(oid: String?): ByteArray? = null
    override fun hasUnsupportedCriticalExtension() = false
}
