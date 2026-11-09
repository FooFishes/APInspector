package work.foofish.apinspector.ui.text

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.JsonType
import work.foofish.apinspector.openapi.OpenApiParseError
import work.foofish.apinspector.openapi.ParseWarning
import work.foofish.apinspector.openapi.UnresolvedReason
import work.foofish.apinspector.spec.ImportFailure

// 把领域层的结构化错误与警告转成界面文案。

@Composable
fun ImportFailure.message(): String =
    when (this) {
        ImportFailure.InvalidUrl -> {
            stringResource(R.string.failure_invalid_url)
        }

        is ImportFailure.HttpStatus -> {
            stringResource(R.string.failure_http_status, code)
        }

        is ImportFailure.Unreadable -> {
            stringResource(R.string.failure_unreadable, detail)
        }

        is ImportFailure.TooLarge -> {
            stringResource(
                R.string.failure_too_large,
                Formatter.formatShortFileSize(LocalContext.current, limitBytes),
            )
        }

        is ImportFailure.Unparseable -> {
            error.message()
        }

        ImportFailure.StoredContentMissing -> {
            stringResource(R.string.failure_stored_content_missing)
        }
    }

@Composable
fun OpenApiParseError.message(): String =
    when (this) {
        is OpenApiParseError.Syntax -> {
            position?.let { stringResource(R.string.parse_error_syntax_at, format.name, it.line, it.column, detail) }
                ?: stringResource(R.string.parse_error_syntax, format.name, detail)
        }

        is OpenApiParseError.DuplicateKey -> {
            position?.let { stringResource(R.string.parse_error_duplicate_key_at, key, it.line) }
                ?: stringResource(R.string.parse_error_duplicate_key, key)
        }

        is OpenApiParseError.RecursiveAlias -> {
            stringResource(R.string.parse_error_recursive_alias)
        }

        OpenApiParseError.RootNotObject -> {
            stringResource(R.string.parse_error_root_not_object)
        }

        OpenApiParseError.NotOpenApi -> {
            stringResource(R.string.parse_error_not_openapi)
        }

        is OpenApiParseError.Swagger2Unsupported -> {
            stringResource(R.string.parse_error_swagger2, version)
        }

        is OpenApiParseError.UnsupportedVersion -> {
            stringResource(R.string.parse_error_unsupported_version, version)
        }

        is OpenApiParseError.MissingRequiredField -> {
            stringResource(R.string.parse_error_missing_field, pointer)
        }
    }

/** 警告的说明，不含位置（位置由 [ParseWarning.pointer] 单独显示）。 */
@Composable
fun ParseWarning.message(): String =
    when (this) {
        is ParseWarning.UnexpectedType -> stringResource(R.string.warning_unexpected_type, expected.label())
        is ParseWarning.InvalidValue -> stringResource(R.string.warning_invalid_value, value)
        is ParseWarning.MissingField -> stringResource(R.string.warning_missing_field, field)
        is ParseWarning.UnresolvedReference -> stringResource(R.string.warning_unresolved_reference, ref, reason.label())
    }

@Composable
fun UnresolvedReason.label(): String =
    stringResource(
        when (this) {
            UnresolvedReason.EXTERNAL -> R.string.unresolved_external
            UnresolvedReason.INVALID_POINTER -> R.string.unresolved_invalid_pointer
            UnresolvedReason.NOT_FOUND -> R.string.unresolved_not_found
            UnresolvedReason.INVALID_TARGET -> R.string.unresolved_invalid_target
            UnresolvedReason.CIRCULAR -> R.string.unresolved_circular
        },
    )

@Composable
private fun JsonType.label(): String =
    stringResource(
        when (this) {
            JsonType.OBJECT -> R.string.json_type_object
            JsonType.ARRAY -> R.string.json_type_array
            JsonType.STRING -> R.string.json_type_string
            JsonType.NUMBER -> R.string.json_type_number
            JsonType.INTEGER -> R.string.json_type_integer
            JsonType.BOOLEAN -> R.string.json_type_boolean
        },
    )
