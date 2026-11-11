package work.foofish.apinspector.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件的可选密码加密（PRD 8）。
 *
 * 格式：魔数 ‖ 盐 ‖ nonce ‖ 密文与认证标签。密钥由 PBKDF2-HMAC-SHA256 从密码派生，内容以 AES-256-GCM 加密，
 * 魔数作为附加认证数据。
 */
object BackupCipher {
    /** 用 [password] 加密 [plain]；输出自带魔数、盐与 nonce。 */
    fun encrypt(
        plain: ByteArray,
        password: CharArray,
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        return MAGIC + salt + nonce + cipher(Cipher.ENCRYPT_MODE, password, salt, nonce).doFinal(plain)
    }

    /** [data] 是否是加密过的备份（看魔数）。 */
    fun isEncrypted(data: ByteArray): Boolean = data.size >= MAGIC.size && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** 密码错误或数据损坏时返回 `null`。 */
    fun decrypt(
        data: ByteArray,
        password: CharArray,
    ): ByteArray? {
        val saltEnd = MAGIC.size + SALT_BYTES
        val nonceEnd = saltEnd + NONCE_BYTES
        return if (!isEncrypted(data) || data.size < nonceEnd + TAG_BITS / Byte.SIZE_BITS) {
            null
        } else {
            try {
                cipher(Cipher.DECRYPT_MODE, password, data.copyOfRange(MAGIC.size, saltEnd), data.copyOfRange(saltEnd, nonceEnd))
                    .doFinal(data, nonceEnd, data.size - nonceEnd)
            } catch (_: GeneralSecurityException) {
                // 认证标签不符：密码错误或内容被改动。
                null
            }
        }
    }

    private fun cipher(
        mode: Int,
        password: CharArray,
        salt: ByteArray,
        nonce: ByteArray,
    ): Cipher {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val key =
            try {
                SecretKeySpec(SecretKeyFactory.getInstance(KEY_DERIVATION).generateSecret(spec).encoded, KEY_ALGORITHM)
            } finally {
                spec.clearPassword()
            }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(MAGIC)
        }
    }

    private val random = SecureRandom()
    private val MAGIC = "APIBAK1\n".encodeToByteArray()
    private const val KEY_DERIVATION = "PBKDF2WithHmacSHA256"
    private const val KEY_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** OWASP 对 PBKDF2-HMAC-SHA256 的建议量级。 */
    private const val ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
}
