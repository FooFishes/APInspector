package work.foofish.apinspector.openapi.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.ParameterSerialization
import work.foofish.apinspector.openapi.ParameterStyle

/** 用例取自 OpenAPI 规范 "Style Examples" 表格。 */
class SerializedParameterTest {
    private val primitive = ParameterValue.Text("blue")
    private val array = ParameterValue.Items(listOf("blue", "black", "brown"))
    private val obj = ParameterValue.Fields(listOf(Field("R", "100"), Field("G", "200"), Field("B", "150")))

    private fun parameter(
        location: ParameterLocation,
        style: ParameterStyle?,
        explode: Boolean = false,
    ) = Parameter(
        name = "color",
        location = location,
        description = null,
        required = false,
        deprecated = false,
        serialization = style?.let { ParameterSerialization(it, explode) },
        schema = null,
        content = emptyMap(),
        example = null,
        examples = emptyMap(),
    )

    private fun path(
        style: ParameterStyle,
        explode: Boolean,
        value: ParameterValue,
    ) = (parameter(ParameterLocation.PATH, style, explode).serialize(value) as SerializedParameter.Path).text

    private fun query(
        style: ParameterStyle,
        explode: Boolean,
        value: ParameterValue,
    ) = (parameter(ParameterLocation.QUERY, style, explode).serialize(value) as SerializedParameter.Query).pairs.joinToString("&")

    @Test
    fun `simple style`() {
        assertEquals("blue", path(ParameterStyle.SIMPLE, false, primitive))
        assertEquals("blue,black,brown", path(ParameterStyle.SIMPLE, false, array))
        assertEquals("R,100,G,200,B,150", path(ParameterStyle.SIMPLE, false, obj))
        assertEquals("R=100,G=200,B=150", path(ParameterStyle.SIMPLE, true, obj))
    }

    @Test
    fun `label style`() {
        assertEquals(".blue", path(ParameterStyle.LABEL, false, primitive))
        assertEquals(".blue,black,brown", path(ParameterStyle.LABEL, false, array))
        assertEquals(".blue.black.brown", path(ParameterStyle.LABEL, true, array))
        assertEquals(".R=100.G=200.B=150", path(ParameterStyle.LABEL, true, obj))
    }

    @Test
    fun `matrix style`() {
        assertEquals(";color=blue", path(ParameterStyle.MATRIX, false, primitive))
        assertEquals(";color", path(ParameterStyle.MATRIX, false, ParameterValue.Text("")))
        assertEquals(";color=blue,black,brown", path(ParameterStyle.MATRIX, false, array))
        assertEquals(";color=blue;color=black;color=brown", path(ParameterStyle.MATRIX, true, array))
        assertEquals(";color=R,100,G,200,B,150", path(ParameterStyle.MATRIX, false, obj))
        assertEquals(";R=100;G=200;B=150", path(ParameterStyle.MATRIX, true, obj))
    }

    @Test
    fun `path values are percent-encoded, including slashes`() {
        assertEquals("a%2Fb%20c%E4%B8%AD", path(ParameterStyle.SIMPLE, false, ParameterValue.Text("a/b c中")))
    }

    @Test
    fun `form style`() {
        assertEquals("color=blue", query(ParameterStyle.FORM, true, primitive))
        assertEquals("color=blue&color=black&color=brown", query(ParameterStyle.FORM, true, array))
        assertEquals("color=blue,black,brown", query(ParameterStyle.FORM, false, array))
        assertEquals("R=100&G=200&B=150", query(ParameterStyle.FORM, true, obj))
        assertEquals("color=R,100,G,200,B,150", query(ParameterStyle.FORM, false, obj))
        assertEquals("", query(ParameterStyle.FORM, true, ParameterValue.Items(emptyList())))
    }

    @Test
    fun `delimited styles and deepObject`() {
        assertEquals("color=blue%20black%20brown", query(ParameterStyle.SPACE_DELIMITED, false, array))
        assertEquals("color=blue|black|brown", query(ParameterStyle.PIPE_DELIMITED, false, array))
        assertEquals("color[R]=100&color[G]=200&color[B]=150", query(ParameterStyle.DEEP_OBJECT, true, obj))
    }

    @Test
    fun `query values are percent-encoded`() {
        assertEquals("color=a%26b%3Dc%20d", query(ParameterStyle.FORM, true, ParameterValue.Text("a&b=c d")))
    }

    @Test
    fun `headers use simple style without encoding`() {
        val header = parameter(ParameterLocation.HEADER, ParameterStyle.SIMPLE).serialize(ParameterValue.Items(listOf("a b", "c")))
        assertEquals(SerializedParameter.Header("a b,c"), header)
    }

    @Test
    fun `cookies use form style with encoding, cookie style verbatim`() {
        assertEquals(
            SerializedParameter.Cookies(listOf(Field("color", "a%20b"))),
            parameter(ParameterLocation.COOKIE, ParameterStyle.FORM, explode = true).serialize(ParameterValue.Text("a b")),
        )
        assertEquals(
            SerializedParameter.Cookies(listOf(Field("color", "blue"), Field("color", "black"))),
            parameter(
                ParameterLocation.COOKIE,
                ParameterStyle.COOKIE,
                explode = true,
            ).serialize(ParameterValue.Items(listOf("blue", "black"))),
        )
    }

    @Test
    fun `content parameters are encoded as a whole`() {
        val content = parameter(ParameterLocation.QUERY, style = null).serialize(ParameterValue.Text("""{"a":1}"""))
        assertEquals(SerializedParameter.Query(listOf("color=%7B%22a%22%3A1%7D")), content)
        val queryString = parameter(ParameterLocation.QUERY_STRING, style = null).serialize(ParameterValue.Text("a=1&b=2"))
        assertEquals(SerializedParameter.QueryString("a=1&b=2"), queryString)
    }
}
