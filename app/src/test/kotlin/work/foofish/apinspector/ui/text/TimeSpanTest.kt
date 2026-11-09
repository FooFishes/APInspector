package work.foofish.apinspector.ui.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class TimeSpanTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")

    private fun ago(duration: Duration) = timeSpan(now - duration, now)

    @Test
    fun `rounds down to the coarsest fitting unit`() {
        assertEquals(TimeSpan.JustNow, ago(Duration.ofSeconds(59)))
        assertEquals(TimeSpan.Minutes(1), ago(Duration.ofSeconds(60)))
        assertEquals(TimeSpan.Minutes(59), ago(Duration.ofMinutes(59).plusSeconds(59)))
        assertEquals(TimeSpan.Hours(23), ago(Duration.ofHours(23).plusMinutes(59)))
        assertEquals(TimeSpan.Days(29), ago(Duration.ofDays(29)))
    }

    @Test
    fun `a month or more shows the date and the future counts as just now`() {
        assertEquals(TimeSpan.Absolute(now - Duration.ofDays(30)), ago(Duration.ofDays(30)))
        assertEquals(TimeSpan.JustNow, timeSpan(now + Duration.ofHours(1), now))
    }
}
