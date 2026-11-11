package work.foofish.apinspector.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import work.foofish.apinspector.custom.data.CustomRequestEntity
import work.foofish.apinspector.extraction.data.ExtractionRuleEntity
import work.foofish.apinspector.settings.ProxyConfig
import work.foofish.apinspector.settings.ProxyType
import work.foofish.apinspector.spec.data.SpecEntity
import work.foofish.apinspector.spec.data.SpecOriginType

class BackupCodecTest {
    private val backup =
        Backup(
            schema = SCHEMA,
            createdAt = 1_700_000_000_000,
            specs =
                listOf(
                    SpecBackup(
                        SpecEntity(
                            "spec",
                            "宠物店",
                            "1.0",
                            SpecOriginType.URL,
                            "https://example.com/openapi.json",
                            3,
                            null,
                            1_700_000_000_000,
                        ),
                        content = """{"openapi":"3.1.0"}""",
                    ),
                ),
            favorites = emptyList(),
            environments = emptyList(),
            presets = emptyList(),
            working = emptyList(),
            customRequests = listOf(CustomRequestEntity("custom", null, "登录", "POST", "https://example.com/login", "{}", "[]", 1L)),
            rules = listOf(ExtractionRuleEntity(1, null, null, null, "custom", "$.token", "token")),
            proxy = ProxyConfig(ProxyType.SOCKS5, "127.0.0.1", 1080, password = "secret"),
        )

    @Test
    fun `plain backup is readable json and round trips`() {
        val data = BackupCodec.encode(backup, password = null)

        assertFalse(BackupCipher.isEncrypted(data))
        assertTrue(data.decodeToString().contains("宠物店"))
        assertEquals(BackupRead.Ready(backup), BackupCodec.decode(data, password = null, schema = SCHEMA))
    }

    @Test
    fun `empty password means no encryption`() {
        assertFalse(BackupCipher.isEncrypted(BackupCodec.encode(backup, CharArray(0))))
    }

    @Test
    fun `encrypted backup hides its content and needs the right password`() {
        val data = BackupCodec.encode(backup, "pw".toCharArray())

        assertFalse(data.decodeToString().contains("secret"))
        assertEquals(BackupRead.PasswordRequired, BackupCodec.decode(data, password = null, schema = SCHEMA))
        assertEquals(BackupRead.WrongPassword, BackupCodec.decode(data, "nope".toCharArray(), SCHEMA))
        assertEquals(BackupRead.Ready(backup), BackupCodec.decode(data, "pw".toCharArray(), SCHEMA))
    }

    @Test
    fun `rejects other files and backups from a newer app`() {
        assertEquals(BackupRead.Invalid, BackupCodec.decode("not a backup".encodeToByteArray(), null, SCHEMA))
        assertEquals(BackupRead.Invalid, BackupCodec.decode("""{"schema":3}""".encodeToByteArray(), null, SCHEMA))
        assertEquals(BackupRead.TooNew, BackupCodec.decode(BackupCodec.encode(backup, null), null, SCHEMA - 1))
    }

    private companion object {
        const val SCHEMA = 3
    }
}
