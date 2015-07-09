package org.mockserver.client.serialization.java;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.BinaryBody.binary;

/**
 * @author jamesdbloom
 */
public class HttpResponseToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithResponseAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        response()" + System.getProperty("line.separator") +
                        "                .withStatusCode(304)" + System.getProperty("line.separator") +
                        "                .withHeaders(" + System.getProperty("line.separator") +
                        "                        new Header(\"responseHeaderNameOne\", \"responseHeaderValueOneOne\", \"responseHeaderValueOneTwo\")," + System.getProperty("line.separator") +
                        "                        new Header(\"responseHeaderNameTwo\", \"responseHeaderValueTwo\")" + System.getProperty("line.separator") +
                        "                )" + System.getProperty("line.separator") +
                        "                .withCookies(" + System.getProperty("line.separator") +
                        "                        new Cookie(\"responseCookieNameOne\", \"responseCookieValueOne\")," + System.getProperty("line.separator") +
                        "                        new Cookie(\"responseCookieNameTwo\", \"responseCookieValueTwo\")" + System.getProperty("line.separator") +
                        "                )" + System.getProperty("line.separator") +
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
        assertEquals(System.getProperty("line.separator") +
                        "        response()" + System.getProperty("line.separator") +
                        "                .withStatusCode(200)" + System.getProperty("line.separator") +
                        "                .withBody(Base64Converter.base64StringToBytes(\"" + Base64Converter.bytesToBase64String("responseBody".getBytes()) + "\"))",
                new HttpResponseToJavaSerializer().serializeAsJava(1,
                        new HttpResponse()
                                .withBody(binary("responseBody".getBytes()))
                )
        );
    }

    @Test
    public void shouldEscapeJSONBodies() throws IOException {
        assertEquals("" + System.getProperty("line.separator") +
                        "        response()" + System.getProperty("line.separator") +
                        "                .withStatusCode(304)" + System.getProperty("line.separator") +
                        "                .withBody(\"[" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "          {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "          }," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "          {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "          }," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "          {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "              \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "          }" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "]\")",
                new HttpResponseToJavaSerializer().serializeAsJava(1,

                        new HttpResponse()
                                .withStatusCode(304)
                                .withBody("[" + System.getProperty("line.separator") +
                                        "          {" + System.getProperty("line.separator") +
                                        "              \"id\": \"1\"," + System.getProperty("line.separator") +
                                        "              \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\"," + System.getProperty("line.separator") +
                                        "              \"author\": \"James Tatum\"," + System.getProperty("line.separator") +
                                        "              \"isbn\": \"0691067570\"," + System.getProperty("line.separator") +
                                        "              \"publicationDate\": \"1989\"" + System.getProperty("line.separator") +
                                        "          }," + System.getProperty("line.separator") +
                                        "          {" + System.getProperty("line.separator") +
                                        "              \"id\": \"2\"," + System.getProperty("line.separator") +
                                        "              \"title\": \"You are here : personal geographies and other maps of the imagination\"," + System.getProperty("line.separator") +
                                        "              \"author\": \"Katharine A. Harmon\"," + System.getProperty("line.separator") +
                                        "              \"isbn\": \"1568984308\"," + System.getProperty("line.separator") +
                                        "              \"publicationDate\": \"2004\"" + System.getProperty("line.separator") +
                                        "          }," + System.getProperty("line.separator") +
                                        "          {" + System.getProperty("line.separator") +
                                        "              \"id\": \"3\"," + System.getProperty("line.separator") +
                                        "              \"title\": \"You just don't understand : women and men in conversation\"," + System.getProperty("line.separator") +
                                        "              \"author\": \"Deborah Tannen\"," + System.getProperty("line.separator") +
                                        "              \"isbn\": \"0345372050\"," + System.getProperty("line.separator") +
                                        "              \"publicationDate\": \"1990\"" + System.getProperty("line.separator") +
                                        "          }" + System.getProperty("line.separator") +
                                        "]")
                )
        );
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        response()" + System.getProperty("line.separator") +
                        "                .withStatusCode(304)",
                new HttpResponseToJavaSerializer().serializeAsJava(1,
                        new HttpResponse()
                                .withStatusCode(304)
                )
        );
    }
}
