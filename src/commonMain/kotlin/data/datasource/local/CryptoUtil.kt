package data.datasource.local

import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256/GCM 本地存储加密工具。
 *
 * 密钥派生后存入 Java Preferences（平台原生安全存储），
 * 不依赖外部密钥文件。
 */
object CryptoUtil {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val KEY_LENGTH = 256
    private const val KEY_PREF = "local_crypto_key"

    private val prefs = Preferences.userNodeForPackage(CryptoUtil::class.java)

    private fun getOrCreateKey(): SecretKey {
        val stored = prefs.get(KEY_PREF, null)
        if (stored != null) {
            return SecretKeySpec(Base64.getDecoder().decode(stored), "AES")
        }
        val keyGen = KeyGenerator.getInstance("AES").apply { init(KEY_LENGTH) }
        val key = keyGen.generateKey()
        prefs.put(KEY_PREF, Base64.getEncoder().encodeToString(key.encoded))
        return key
    }

    /**
     * 加密字节数组，输出 = IV + 密文。
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * 解密 [IV + 密文] 格式的数据。
     */
    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        require(data.size > IV_LENGTH) { "密文数据过短" }
        val iv = data.copyOfRange(0, IV_LENGTH)
        val ciphertext = data.copyOfRange(IV_LENGTH, data.size)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * 加密字符串为 Base64 编码的密文。
     */
    fun encryptString(plaintext: String): String =
        Base64.getEncoder().encodeToString(encrypt(plaintext.encodeToByteArray()))

    /**
     * 解密 Base64 编码的密文字符串。
     */
    fun decryptString(ciphertext: String): String =
        String(decrypt(Base64.getDecoder().decode(ciphertext)))
}
