package com.chatlite.charroom.core.quic

import android.content.Context
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Export the platform's trusted public roots for quiche/BoringSSL; never disable peer verification. */
object AndroidQuicTrustRoots {
    @Synchronized fun pemFile(context: Context): String {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val roots = factory.trustManagers.filterIsInstance<X509TrustManager>().flatMap { it.acceptedIssuers.toList() }
        check(roots.isNotEmpty()) { "Android provides no trusted CA certificates" }
        val encoder = Base64.getMimeEncoder(64, byteArrayOf(10))
        val pem = roots.joinToString("\n") { certificate ->
            "-----BEGIN CERTIFICATE-----\n${encoder.encodeToString(certificate.encoded)}\n-----END CERTIFICATE-----\n"
        }
        val destination = File(context.noBackupFilesDir, "quic-trust-roots.pem")
        val temporary = File(context.noBackupFilesDir, "quic-trust-roots.tmp")
        temporary.writeText(pem, Charsets.US_ASCII)
        check(temporary.renameTo(destination)) { "Cannot prepare QUIC trust roots" }
        return destination.absolutePath
    }
}
