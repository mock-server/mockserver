package org.mockserver.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.model.*;
import org.mockserver.serialization.Base64Converter;

import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.character.Character.NEW_LINE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author jamesdbloom
 */
public class HttpRequestToJavaSerializerTest {

    private final Base64Converter base64Converter = new Base64Converter();

    @Test
    public void shouldSerializeArrayOfObjectsAsJava() {
        assertThat(
            new HttpRequestToJavaSerializer().serialize(
                Arrays.asList(
                    new HttpRequest()
                        .withMethod("GET")
                        .withPath("somePathOne")
                        .withBody(new StringBody("responseBodyOne")),
                    new HttpRequest()
                        .withMethod("GET")
                        .withPath("somePathTwo")
                        .withBody(new StringBody("responseBodyTwo"))
                )
            )
        , is(NEW_LINE +
                "request()" + NEW_LINE +
                "        .withMethod(\"GET\")" + NEW_LINE +
                "        .withPath(\"somePathOne\")" + NEW_LINE +
                "        .withBody(new StringBody(\"responseBodyOne\"));" + NEW_LINE +
                NEW_LINE +
                "request()" + NEW_LINE +
                "        .withMethod(\"GET\")" + NEW_LINE +
                "        .withPath(\"somePathTwo\")" + NEW_LINE +
                "        .withBody(new StringBody(\"responseBodyTwo\"));" + NEW_LINE));
    }

    @Test
    public void shouldSerializeFullObjectAsJava() {
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest()
                    .withMethod("GET")
                    .withPath("somePath")
                    .withQueryStringParameters(
                        new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                        new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                    )
                    .withHeaders(
                        new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
                    .withCookies(
                        new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
                    .withSecure(true)
                    .withKeepAlive(false)
                    .withProtocol(Protocol.HTTP_2)
                    .withSocketAddress(
                        new SocketAddress().withHost("someHost").withPort(1234).withScheme(SocketAddress.Scheme.HTTPS)
                    )
                    .withBody(new StringBody("responseBody"))
            )
        , is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withMethod(\"GET\")" + NEW_LINE +
                "                .withPath(\"somePath\")" + NEW_LINE +
                "                .withHeaders(" + NEW_LINE +
                "                        new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                "                        new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                "                )" + NEW_LINE +
                "                .withCookies(" + NEW_LINE +
                "                        new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                "                        new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                "                )" + NEW_LINE +
                "                .withQueryStringParameters(" + NEW_LINE +
                "                        new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                "                        new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                "                )" + NEW_LINE +
                "                .withSecure(true)" + NEW_LINE +
                "                .withKeepAlive(false)" + NEW_LINE +
                "                .withProtocol(Protocol.HTTP_2)" + NEW_LINE +
                "                .withSocketAddress(" + NEW_LINE +
                "                        new SocketAddress()" + NEW_LINE +
                "                                .withHost(\"someHost\")" + NEW_LINE +
                "                                .withPort(1234)" + NEW_LINE +
                "                                .withScheme(SocketAddress.Scheme.HTTPS)" + NEW_LINE +
                "                )" + NEW_LINE +
                "                .withBody(new StringBody(\"responseBody\"))"));
    }

    @Test
    public void shouldSerializeFullObjectWithParameterBodyRequestAsJava() {
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest()
                    .withBody(
                        new ParameterBody(
                            new Parameter("requestBodyParameterNameOne", "requestBodyParameterValueOneOne", "requestBodyParameterValueOneTwo"),
                            new Parameter("requestBodyParameterNameTwo", "requestBodyParameterValueTwo")
                        )
                    )
            )
        , is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withBody(" + NEW_LINE +
                "                        new ParameterBody(" + NEW_LINE +
                "                                new Parameter(\"requestBodyParameterNameOne\", \"requestBodyParameterValueOneOne\", \"requestBodyParameterValueOneTwo\")," + NEW_LINE +
                "                                new Parameter(\"requestBodyParameterNameTwo\", \"requestBodyParameterValueTwo\")" + NEW_LINE +
                "                        )" + NEW_LINE +
                "                )"));
    }

    @Test
    public void shouldSerializeFullObjectWithBinaryBodyRequestAsJava() {
        // when
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest()
                    .withBody(
                        new BinaryBody("responseBody".getBytes(UTF_8))
                    )
            ), is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withBody(new Base64Converter().base64StringToBytes(\"" + base64Converter.bytesToBase64String("responseBody".getBytes(UTF_8)) + "\"))"));
    }

    @Test
    public void shouldSerializeFullObjectWithFuzzyBodyRequestAsJava() {
        // when
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest()
                    .withBody(
                        new FuzzyBody("some fuzzy value", 0.8d, false)
                    )
            ), is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withBody(new FuzzyBody(\"some fuzzy value\", 0.8d, false))"));
    }

    @Test
    public void shouldSerializeJsonPathAndXPathBodiesWithDateTimeAsJava() {
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest().withBody(JsonPathBody.jsonPath("$.created", NottableDateTimeString.after("now -1 days").withZone("Europe/London")))
            ), is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withBody(new JsonPathBody(\"$.created\", NottableDateTimeString.after(\"now -1 days\").withZone(\"Europe/London\")))"));
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest().withBody(XPathBody.xpath("/order/@due", NottableDateTimeString.before("2026-01-01").withNot(true)))
            ), is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withBody(new XPathBody(\"/order/@due\", null, NottableDateTimeString.before(\"2026-01-01\").withNot(true)))"));
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest().withBody(XPathBody.xpath("/lib:due", java.util.Map.of("lib", "urn:library"), NottableDateTimeString.after("now")))
            ), is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withBody(new XPathBody(\"/lib:due\", java.util.Map.ofEntries(java.util.Map.entry(\"lib\", \"urn:library\")), NottableDateTimeString.after(\"now\")))"));
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1, new HttpRequest().withBody(JsonPathBody.jsonPath("$.created"))),
            is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withBody(new JsonPathBody(\"$.created\"))"));
    }

    @Test
    public void shouldEscapeJsonBodies() {
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest()
                    .withPath("somePath")
                    .withBody(new JsonBody("[" + NEW_LINE +
                        "    {" + NEW_LINE +
                        "        \"id\": \"1\"," + NEW_LINE +
                        "        \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\"," + NEW_LINE +
                        "        \"author\": \"James Tatum\"," + NEW_LINE +
                        "        \"isbn\": \"0691067570\"," + NEW_LINE +
                        "        \"publicationDate\": \"1989\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    {" + NEW_LINE +
                        "        \"id\": \"2\"," + NEW_LINE +
                        "        \"title\": \"You are here : personal geographies and other maps of the imagination\"," + NEW_LINE +
                        "        \"author\": \"Katharine A. Harmon\"," + NEW_LINE +
                        "        \"isbn\": \"1568984308\"," + NEW_LINE +
                        "        \"publicationDate\": \"2004\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    {" + NEW_LINE +
                        "        \"id\": \"3\"," + NEW_LINE +
                        "        \"title\": \"You just don't understand : women and men in conversation\"," + NEW_LINE +
                        "        \"author\": \"Deborah Tannen\"," + NEW_LINE +
                        "        \"isbn\": \"0345372050\"," + NEW_LINE +
                        "        \"publicationDate\": \"1990\"" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "]"))
            )
        , is("" + NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withPath(\"somePath\")" + NEW_LINE +
                "                .withBody(new JsonBody(\"[" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "        \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    }" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "]\", JsonBodyMatchType.ONLY_MATCHING_FIELDS))"));
    }

    @Test
    public void shouldEscapeJsonSchemaBodies() {
        String jsonSchema = "{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "    \"title\": \"Product\"," + NEW_LINE +
            "    \"description\": \"A product from Acme's catalog\"," + NEW_LINE +
            "    \"type\": \"object\"," + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"id\": {" + NEW_LINE +
            "                  \"description\": \"The unique identifier for a product\"," + NEW_LINE +
            "                  \"type\": \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\": {" + NEW_LINE +
            "                  \"description\": \"Name of the product\"," + NEW_LINE +
            "                  \"type\": \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"price\": {" + NEW_LINE +
            "                  \"type\": \"number\"," + NEW_LINE +
            "                  \"minimum\": 0," + NEW_LINE +
            "                  \"exclusiveMinimum\": true" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tags\": {" + NEW_LINE +
            "                  \"type\": \"array\"," + NEW_LINE +
            "                  \"items\": {" + NEW_LINE +
            "                      \"type\": \"string\"" + NEW_LINE +
            "                  }," + NEW_LINE +
            "                  \"minItems\": 1," + NEW_LINE +
            "                  \"uniqueItems\": true" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
            "}";
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest()
                    .withPath("somePath")
                    .withBody(new JsonSchemaBody(jsonSchema))
            )
        , is("" + NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withPath(\"somePath\")" + NEW_LINE +
                "                .withBody(new JsonSchemaBody(\"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"))"));
    }

    @Test
    public void shouldEscapeXmlSchemaBodies() {
        String xmlSchema = "{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "    \"title\": \"Product\"," + NEW_LINE +
            "    \"description\": \"A product from Acme's catalog\"," + NEW_LINE +
            "    \"type\": \"object\"," + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"id\": {" + NEW_LINE +
            "                  \"description\": \"The unique identifier for a product\"," + NEW_LINE +
            "                  \"type\": \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\": {" + NEW_LINE +
            "                  \"description\": \"Name of the product\"," + NEW_LINE +
            "                  \"type\": \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"price\": {" + NEW_LINE +
            "                  \"type\": \"number\"," + NEW_LINE +
            "                  \"minimum\": 0," + NEW_LINE +
            "                  \"exclusiveMinimum\": true" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tags\": {" + NEW_LINE +
            "                  \"type\": \"array\"," + NEW_LINE +
            "                  \"items\": {" + NEW_LINE +
            "                      \"type\": \"string\"" + NEW_LINE +
            "                  }," + NEW_LINE +
            "                  \"minItems\": 1," + NEW_LINE +
            "                  \"uniqueItems\": true" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
            "}";
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest()
                    .withPath("somePath")
                    .withBody(new XmlSchemaBody(xmlSchema))
            )
        , is("" + NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withPath(\"somePath\")" + NEW_LINE +
                "                .withBody(new XmlSchemaBody(\"" + StringEscapeUtils.escapeJava(xmlSchema) + "\"))"));
    }

    @Test
    public void shouldEscapeSpecialCharactersInMethodAndPathAsJava() {
        // given - a method and path containing characters that would otherwise produce
        // non-compiling or injected Java source (double quote, backslash, dollar, backtick)
        String method = "GET\"\\$`";
        String path = "/some\"path\\with$specials`";

        // when
        String java = new HttpRequestToJavaSerializer().serialize(1,
            new HttpRequest()
                .withMethod(method)
                .withPath(path)
        );

        // then - both literals are escaped uniformly with escapeJava
        assertThat(java, is(NEW_LINE +
            "        request()" + NEW_LINE +
            "                .withMethod(\"" + StringEscapeUtils.escapeJava(method) + "\")" + NEW_LINE +
            "                .withPath(\"" + StringEscapeUtils.escapeJava(path) + "\")"));
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() {
        assertThat(
            new HttpRequestToJavaSerializer().serialize(1,
                new HttpRequest()
                    .withPath("somePath")
                    .withBody("responseBody")
            )
        , is(NEW_LINE +
                "        request()" + NEW_LINE +
                "                .withPath(\"somePath\")" + NEW_LINE +
                "                .withBody(new StringBody(\"responseBody\"))"));
    }
}
