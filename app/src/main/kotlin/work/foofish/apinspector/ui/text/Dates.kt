package work.foofish.apinspector.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import work.foofish.apinspector.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 一天之内的时刻，如"14:03:22"。 */
fun formatTime(instant: Instant): String = TimeFormat.format(instant.atZone(ZoneId.systemDefault()))

/** 日期标题：今天、昨天，更早的显示月日，不是今年的再带上年份。 */
@Composable
fun dayLabel(
    day: LocalDate,
    today: LocalDate = LocalDate.now(),
): String =
    when {
        day == today -> stringResource(R.string.time_today)
        day == today.minusDays(1) -> stringResource(R.string.time_yesterday)
        day.year == today.year -> MonthDayFormat.format(day)
        else -> YearMonthDayFormat.format(day)
    }

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA)
private val MonthDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val YearMonthDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
