package work.foofish.apinspector.backup

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCipherTest {
    private val plain = """{"specs":[],"note":"中文"}""".encodeToByteArray()

    @Test
    fun `round trips with the right password`() {
        val encrypted = BackupCipher.encrypt(plain, "pass word".toCharArray())

        assertTrue(BackupCipher.isEncrypted(encrypted))
        assertArrayEquals(plain, BackupCipher.decrypt(encrypted, "pass word".toCharArray()))
    }

    @Test
    fun `plain data is not recognised as encrypted`() {
        assertFalse(BackupCipher.isEncrypted(plain))
        assertFalse(BackupCipher.isEncrypted(ByteArray(0)))
        assertNull(BackupCipher.decrypt(plain, "x".toCharArray()))
    }

    @Test
    fun `wrong password, tampering and truncation fail`() {
        val encrypted = BackupCipher.encrypt(plain, "right".toCharArray())
        val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertNull(BackupCipher.decrypt(encrypted, "wrong".toCharArray()))
        assertNull(BackupCipher.decrypt(tampered, "right".toCharArray()))
        assertNull(BackupCipher.decrypt(encrypted.copyOf(TRUNCATED_BYTES), "right".toCharArray()))
    }

    @Test
    fun `each encryption uses a fresh salt and nonce`() {
        val first = BackupCipher.encrypt(plain, "p".toCharArray())
        val second = BackupCipher.encrypt(plain, "p".toCharArray())

        assertFalse(first.contentEquals(second))
    }

    private companion object {
        const val TRUNCATED_BYTES = 20
    }
}
