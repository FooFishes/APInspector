package work.foofish.apinspector.openapi.parse

import work.foofish.apinspector.openapi.ApiKeyLocation
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.ParameterStyle
import work.foofish.apinspector.openapi.SchemaType

/** OpenAPI 3.0 / 3.1 中用到的固定字段名。 */
internal object Field {
    const val OPENAPI = "openapi"
    const val SWAGGER = "swagger"
    const val SELF = "\$self"
    const val INFO = "info"
    const val TITLE = "title"
    const val VERSION = "version"
    const val SUMMARY = "summary"
    const val DESCRIPTION = "description"
    const val SERVERS = "servers"
    const val URL = "url"
    const val VARIABLES = "variables"
    const val DEFAULT = "default"
    const val ENUM = "enum"
    const val TAGS = "tags"
    const val NAME = "name"
    const val PARENT = "parent"
    const val KIND = "kind"
    const val PATHS = "paths"
    const val COMPONENTS = "components"
    const val SECURITY_SCHEMES = "securitySchemes"
    const val SECURITY = "security"
    const val OPERATION_ID = "operationId"
    const val DEPRECATED = "deprecated"
    const val PARAMETERS = "parameters"
    const val REQUEST_BODY = "requestBody"
    const val RESPONSES = "responses"
    const val ADDITIONAL_OPERATIONS = "additionalOperations"
    const val IN = "in"
    const val REQUIRED = "required"
    const val STYLE = "style"
    const val EXPLODE = "explode"
    const val SCHEMA = "schema"
    const val ITEM_SCHEMA = "itemSchema"
    const val CONTENT = "content"
    const val EXAMPLE = "example"
    const val EXAMPLES = "examples"
    const val VALUE = "value"
    const val DATA_VALUE = "dataValue"
    const val SERIALIZED_VALUE = "serializedValue"
    const val EXTERNAL_VALUE = "externalValue"
    const val HEADERS = "headers"
    const val TYPE = "type"
    const val SCHEME = "scheme"
    const val BEARER_FORMAT = "bearerFormat"
    const val REF = "\$ref"
    const val FORMAT = "format"
    const val PROPERTIES = "properties"
    const val ADDITIONAL_PROPERTIES = "additionalProperties"
    const val ITEMS = "items"
    const val ALL_OF = "allOf"
    const val ONE_OF = "oneOf"
    const val ANY_OF = "anyOf"
    const val NOT = "not"
    const val CONST = "const"
    const val NULLABLE = "nullable"
    const val MINIMUM = "minimum"
    const val MAXIMUM = "maximum"
    const val EXCLUSIVE_MINIMUM = "exclusiveMinimum"
    const val EXCLUSIVE_MAXIMUM = "exclusiveMaximum"
    const val MIN_LENGTH = "minLength"
    const val MAX_LENGTH = "maxLength"
    const val PATTERN = "pattern"
    const val MIN_ITEMS = "minItems"
    const val MAX_ITEMS = "maxItems"
    const val UNIQUE_ITEMS = "uniqueItems"
    const val READ_ONLY = "readOnly"
    const val WRITE_ONLY = "writeOnly"
}

/** Security Scheme Object 的 `type` 取值。 */
internal object SecuritySchemeType {
    const val API_KEY = "apiKey"
    const val HTTP = "http"
    val UNSUPPORTED: Set<String> = setOf("oauth2", "openIdConnect", "mutualTLS")
}

internal val PARAMETER_LOCATIONS: Map<String, ParameterLocation> =
    mapOf(
        "path" to ParameterLocation.PATH,
        "query" to ParameterLocation.QUERY,
        "querystring" to ParameterLocation.QUERY_STRING,
        "header" to ParameterLocation.HEADER,
        "cookie" to ParameterLocation.COOKIE,
    )

internal val PARAMETER_STYLES: Map<String, ParameterStyle> =
    mapOf(
        "matrix" to ParameterStyle.MATRIX,
        "label" to ParameterStyle.LABEL,
        "form" to ParameterStyle.FORM,
        "simple" to ParameterStyle.SIMPLE,
        "spaceDelimited" to ParameterStyle.SPACE_DELIMITED,
        "pipeDelimited" to ParameterStyle.PIPE_DELIMITED,
        "deepObject" to ParameterStyle.DEEP_OBJECT,
        "cookie" to ParameterStyle.COOKIE,
    )

internal val API_KEY_LOCATIONS: Map<String, ApiKeyLocation> =
    mapOf(
        "header" to ApiKeyLocation.HEADER,
        "query" to ApiKeyLocation.QUERY,
        "cookie" to ApiKeyLocation.COOKIE,
    )

internal val SCHEMA_TYPES: Map<String, SchemaType> =
    mapOf(
        "string" to SchemaType.STRING,
        "number" to SchemaType.NUMBER,
        "integer" to SchemaType.INTEGER,
        "boolean" to SchemaType.BOOLEAN,
        "array" to SchemaType.ARRAY,
        "object" to SchemaType.OBJECT,
        "null" to SchemaType.NULL,
    )
