package work.foofish.apinspector.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import work.foofish.apinspector.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 相对于现在的时间跨度，按最粗的合适单位取整。 */
sealed interface TimeSpan {
    data object JustNow : TimeSpan

    data class Minutes(
        val count: Int,
    ) : TimeSpan

    data class Hours(
        val count: Int,
    ) : TimeSpan

    data class Days(
        val count: Int,
    ) : TimeSpan

    /** 超过一个月，直接显示日期。 */
    data class Absolute(
        val instant: Instant,
    ) : TimeSpan
}

/** 未来的时间（时钟回拨）视为"刚刚"。 */
fun timeSpan(
    then: Instant,
    now: Instant,
): TimeSpan {
    val elapsed = Duration.between(then, now)
    return when {
        elapsed < Duration.ofMinutes(1) -> TimeSpan.JustNow
        elapsed < Duration.ofHours(1) -> TimeSpan.Minutes(elapsed.toMinutes().toInt())
        elapsed < Duration.ofDays(1) -> TimeSpan.Hours(elapsed.toHours().toInt())
        elapsed < Duration.ofDays(DAYS_BEFORE_ABSOLUTE) -> TimeSpan.Days(elapsed.toDays().toInt())
        else -> TimeSpan.Absolute(then)
    }
}

/** 中文的相对时间，例如"3 天前"。不随系统语言变化：界面只有中文。 */
@Composable
fun relativeTime(
    then: Instant,
    now: Instant = Instant.now(),
): String =
    when (val span = timeSpan(then, now)) {
        TimeSpan.JustNow -> stringResource(R.string.time_just_now)
        is TimeSpan.Minutes -> pluralStringResource(R.plurals.time_minutes_ago, span.count, span.count)
        is TimeSpan.Hours -> pluralStringResource(R.plurals.time_hours_ago, span.count, span.count)
        is TimeSpan.Days -> pluralStringResource(R.plurals.time_days_ago, span.count, span.count)
        is TimeSpan.Absolute -> DateFormatter.format(span.instant.atZone(ZoneId.systemDefault()))
    }

private const val DAYS_BEFORE_ABSOLUTE = 30L
private val DateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.CHINA)
