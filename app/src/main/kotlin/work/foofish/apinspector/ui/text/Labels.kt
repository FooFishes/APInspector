package work.foofish.apinspector.ui.text

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import work.foofish.apinspector.R
import work.foofish.apinspector.history.NetworkError
import work.foofish.apinspector.history.NetworkErrorKind
import work.foofish.apinspector.history.RequestSource
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.request.AssemblyProblem
import work.foofish.apinspector.openapi.request.JsonSyntaxError
import work.foofish.apinspector.request.BodyMode
import work.foofish.apinspector.request.SendProblem
import work.foofish.apinspector.request.body.JsonValueType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 枚举与领域错误的界面文案。

@StringRes
fun ParameterLocation.label(): Int =
    when (this) {
        ParameterLocation.PATH -> R.string.doc_location_path
        ParameterLocation.QUERY -> R.string.doc_location_query
        ParameterLocation.QUERY_STRING -> R.string.doc_location_query_string
        ParameterLocation.HEADER -> R.string.doc_location_header
        ParameterLocation.COOKIE -> R.string.doc_location_cookie
    }

@StringRes
fun BodyMode.label(): Int =
    when (this) {
        BodyMode.VISUAL -> R.string.request_mode_visual
        BodyMode.RAW -> R.string.request_mode_raw
    }

@StringRes
fun JsonValueType.label(): Int =
    when (this) {
        JsonValueType.STRING -> R.string.json_type_string
        JsonValueType.NUMBER -> R.string.json_type_number
        JsonValueType.BOOLEAN -> R.string.json_type_boolean
        JsonValueType.NULL -> R.string.json_type_null
        JsonValueType.OBJECT -> R.string.json_type_object
        JsonValueType.ARRAY -> R.string.json_type_array
        JsonValueType.VARIABLE -> R.string.json_type_variable
    }

@StringRes
fun JsonSyntaxError.label(): Int =
    when (this) {
        JsonSyntaxError.EMPTY -> R.string.json_error_empty
        JsonSyntaxError.UNEXPECTED_CHARACTER -> R.string.json_error_unexpected_character
        JsonSyntaxError.UNEXPECTED_END -> R.string.json_error_unexpected_end
        JsonSyntaxError.UNTERMINATED_STRING -> R.string.json_error_unterminated_string
        JsonSyntaxError.INVALID_ESCAPE -> R.string.json_error_invalid_escape
        JsonSyntaxError.CONTROL_CHARACTER -> R.string.json_error_control_character
        JsonSyntaxError.INVALID_NUMBER -> R.string.json_error_invalid_number
        JsonSyntaxError.TRAILING_CONTENT -> R.string.json_error_trailing_content
        JsonSyntaxError.TOO_DEEP -> R.string.json_error_too_deep
    }

@StringRes
fun RequestSource.label(): Int =
    when (this) {
        RequestSource.OPERATION -> R.string.history_source_operation
        RequestSource.PRESET -> R.string.history_source_preset
        RequestSource.REPLAY -> R.string.history_source_replay
        RequestSource.CUSTOM -> R.string.history_source_custom
    }

@Composable
fun SendProblem.message(): String =
    when (this) {
        SendProblem.NoEnvironment -> {
            stringResource(R.string.request_problem_no_environment)
        }

        is SendProblem.FileUnavailable -> {
            stringResource(R.string.request_problem_file_unavailable, name)
        }

        is SendProblem.Assembly -> {
            when (val problem = problem) {
                is AssemblyProblem.MissingVariables -> {
                    stringResource(R.string.request_problem_missing_variables, problem.names.joinToString("、"))
                }

                is AssemblyProblem.IncompleteBaseUrl -> {
                    stringResource(R.string.request_problem_incomplete_base_url, problem.baseUrl.ifEmpty { "∅" })
                }

                is AssemblyProblem.FileNotSelected -> {
                    problem.part?.let { stringResource(R.string.request_problem_part_file, it) }
                        ?: stringResource(R.string.request_problem_body_file)
                }
            }
        }
    }

@Composable
fun NetworkError.message(): String {
    val reason =
        stringResource(
            when (kind) {
                NetworkErrorKind.TIMEOUT -> R.string.network_error_timeout
                NetworkErrorKind.UNKNOWN_HOST -> R.string.network_error_unknown_host
                NetworkErrorKind.CONNECTION_FAILED -> R.string.network_error_connection
                NetworkErrorKind.TLS -> R.string.network_error_tls
                NetworkErrorKind.FILE_UNAVAILABLE -> R.string.network_error_file
                NetworkErrorKind.OTHER -> R.string.network_error_other
            },
        )
    return detail?.let { stringResource(R.string.network_error_with_detail, reason, it) } ?: reason
}

/** 文件大小，如"1.2 MB"。 */
fun formatFileSize(
    context: Context,
    bytes: Long,
): String = Formatter.formatShortFileSize(context, bytes)

/** 耗时：1 秒以内以毫秒显示，否则以秒显示（两位小数）。 */
fun formatDuration(duration: Duration): String {
    val millis = duration.toMillis()
    return if (millis < MILLIS_PER_SECOND) "$millis ms" else String.format(Locale.ROOT, "%.2f s", millis / MILLIS_PER_SECOND.toDouble())
}

/** 完整的日期与时间，如"2026-11-10 14:03:22"。 */
fun formatDateTime(instant: Instant): String = DateTimeFormat.format(instant.atZone(ZoneId.systemDefault()))

private const val MILLIS_PER_SECOND = 1_000L
private val DateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
