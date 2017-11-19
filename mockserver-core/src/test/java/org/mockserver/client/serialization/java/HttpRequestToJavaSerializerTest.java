package org.mockserver.client.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.*;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpRequestToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectAsJava() throws IOException {
        assertEquals(NEW_LINE +
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
                        "                .withBody(new StringBody(\"responseBody\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(1,
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
                                .withBody(new StringBody("responseBody"))
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithParameterBodyRequestAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        request()" + NEW_LINE +
                        "                .withBody(" + NEW_LINE +
                        "                        new ParameterBody(" + NEW_LINE +
                        "                                new Parameter(\"requestBodyParameterNameOne\", \"requestBodyParameterValueOneOne\", \"requestBodyParameterValueOneTwo\")," + NEW_LINE +
                        "                                new Parameter(\"requestBodyParameterNameTwo\", \"requestBodyParameterValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                )",
                new HttpRequestToJavaSerializer().serializeAsJava(1,
                        new HttpRequest()
                                .withBody(
                                        new ParameterBody(
                                                new Parameter("requestBodyParameterNameOne", "requestBodyParameterValueOneOne", "requestBodyParameterValueOneTwo"),
                                                new Parameter("requestBodyParameterNameTwo", "requestBodyParameterValueTwo")
                                        )
                                )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithBinaryBodyRequestAsJava() throws IOException {
        // when
        assertEquals(NEW_LINE +
                        "        request()" + NEW_LINE +
                        "                .withBody(Base64Converter.base64StringToBytes(\"" + Base64Converter.bytesToBase64String("responseBody".getBytes()) + "\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(1,
                        new HttpRequest()
                                .withBody(
                                        new BinaryBody("responseBody".getBytes())
                                )
                ));
    }

    @Test
    public void shouldEscapeJsonBodies() throws IOException {
        assertEquals("" + NEW_LINE +
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
                        "]\", JsonBodyMatchType.ONLY_MATCHING_FIELDS))",
                new HttpRequestToJavaSerializer().serializeAsJava(1,
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
        );
    }

    @Test
    public void shouldEscapeJsonSchemaBodies() throws IOException {
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
        assertEquals("" + NEW_LINE +
                        "        request()" + NEW_LINE +
                        "                .withPath(\"somePath\")" + NEW_LINE +
                        "                .withBody(new JsonSchemaBody(\"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(1,
                        new HttpRequest()
                                .withPath("somePath")
                                .withBody(new JsonSchemaBody(jsonSchema))
                )
        );
    }

    @Test
    public void shouldEscapeXmlSchemaBodies() throws IOException {
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
        assertEquals("" + NEW_LINE +
                        "        request()" + NEW_LINE +
                        "                .withPath(\"somePath\")" + NEW_LINE +
                        "                .withBody(new XmlSchemaBody(\"" + StringEscapeUtils.escapeJava(xmlSchema) + "\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(1,
                        new HttpRequest()
                                .withPath("somePath")
                                .withBody(new XmlSchemaBody(xmlSchema))
                )
        );
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        request()" + NEW_LINE +
                        "                .withPath(\"somePath\")" + NEW_LINE +
                        "                .withBody(new StringBody(\"responseBody\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(1,
                        new HttpRequest()
                                .withPath("somePath")
                                .withBody("responseBody")
                )
        );
    }
}
