package org.mockserver.client.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.BinaryBody.binary;

/**
 * @author jamesdbloom
 */
public class HttpResponseToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithResponseAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        response()" + NEW_LINE +
                        "                .withStatusCode(304)" + NEW_LINE +
                        "                .withHeaders(" + NEW_LINE +
                        "                        new Header(\"responseHeaderNameOne\", \"responseHeaderValueOneOne\", \"responseHeaderValueOneTwo\")," + NEW_LINE +
                        "                        new Header(\"responseHeaderNameTwo\", \"responseHeaderValueTwo\")" + NEW_LINE +
                        "                )" + NEW_LINE +
                        "                .withCookies(" + NEW_LINE +
                        "                        new Cookie(\"responseCookieNameOne\", \"responseCookieValueOne\")," + NEW_LINE +
                        "                        new Cookie(\"responseCookieNameTwo\", \"responseCookieValueTwo\")" + NEW_LINE +
                        "                )" + NEW_LINE +
                        "                .withBody(\"responseBody\")",
                new HttpResponseToJavaSerializer().serializeAsJava(1,
                        new HttpResponse()
                                .withStatusCode(304)
                                .withHeaders(
                                        new Header("responseHeaderNameOne", "responseHeaderValueOneOne", "responseHeaderValueOneTwo"),
                                        new Header("responseHeaderNameTwo", "responseHeaderValueTwo")
                                )
                                .withCookies(
                                        new Cookie("responseCookieNameOne", "responseCookieValueOne"),
                                        new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                                )
                                .withBody("responseBody")
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithBinaryBodyResponseAsJava() throws IOException {
        // when
        assertEquals(NEW_LINE +
                        "        response()" + NEW_LINE +
                        "                .withBody(Base64Converter.base64StringToBytes(\"" + Base64Converter.bytesToBase64String("responseBody".getBytes()) + "\"))",
                new HttpResponseToJavaSerializer().serializeAsJava(1,
                        new HttpResponse()
                                .withBody(binary("responseBody".getBytes()))
                )
        );
    }

    @Test
    public void shouldEscapeJSONBodies() throws IOException {
        assertEquals("" + NEW_LINE +
                        "        response()" + NEW_LINE +
                        "                .withStatusCode(304)" + NEW_LINE +
                        "                .withBody(\"[" + StringEscapeUtils.escapeJava(NEW_LINE) + "          {" + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "          }," + StringEscapeUtils.escapeJava(NEW_LINE) + "          {" + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "          }," + StringEscapeUtils.escapeJava(NEW_LINE) + "          {" + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "              \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "          }" + StringEscapeUtils.escapeJava(NEW_LINE) + "]\")",
                new HttpResponseToJavaSerializer().serializeAsJava(1,

                        new HttpResponse()
                                .withStatusCode(304)
                                .withBody("[" + NEW_LINE +
                                        "          {" + NEW_LINE +
                                        "              \"id\": \"1\"," + NEW_LINE +
                                        "              \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\"," + NEW_LINE +
                                        "              \"author\": \"James Tatum\"," + NEW_LINE +
                                        "              \"isbn\": \"0691067570\"," + NEW_LINE +
                                        "              \"publicationDate\": \"1989\"" + NEW_LINE +
                                        "          }," + NEW_LINE +
                                        "          {" + NEW_LINE +
                                        "              \"id\": \"2\"," + NEW_LINE +
                                        "              \"title\": \"You are here : personal geographies and other maps of the imagination\"," + NEW_LINE +
                                        "              \"author\": \"Katharine A. Harmon\"," + NEW_LINE +
                                        "              \"isbn\": \"1568984308\"," + NEW_LINE +
                                        "              \"publicationDate\": \"2004\"" + NEW_LINE +
                                        "          }," + NEW_LINE +
                                        "          {" + NEW_LINE +
                                        "              \"id\": \"3\"," + NEW_LINE +
                                        "              \"title\": \"You just don't understand : women and men in conversation\"," + NEW_LINE +
                                        "              \"author\": \"Deborah Tannen\"," + NEW_LINE +
                                        "              \"isbn\": \"0345372050\"," + NEW_LINE +
                                        "              \"publicationDate\": \"1990\"" + NEW_LINE +
                                        "          }" + NEW_LINE +
                                        "]")
                )
        );
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        response()" + NEW_LINE +
                        "                .withStatusCode(304)",
                new HttpResponseToJavaSerializer().serializeAsJava(1,
                        new HttpResponse()
                                .withStatusCode(304)
                )
        );
    }
}
