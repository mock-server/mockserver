package org.mockserver.client.serialization.java;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.model.*;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpRequestToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithResponseAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  request()" + System.getProperty("line.separator") +
                        "          .withMethod(\"GET\")" + System.getProperty("line.separator") +
                        "          .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "          .withHeaders(" + System.getProperty("line.separator") +
                        "                  new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + System.getProperty("line.separator") +
                        "                  new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withCookies(" + System.getProperty("line.separator") +
                        "                  new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + System.getProperty("line.separator") +
                        "                  new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withQueryStringParameters(" + System.getProperty("line.separator") +
                        "                  new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + System.getProperty("line.separator") +
                        "                  new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withBody(new StringBody(\"responseBody\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(2,
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
                                .withBody(new StringBody("responseBody"))
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithParameterBodyResponseAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  request()" + System.getProperty("line.separator") +
                        "          .withBody(" + System.getProperty("line.separator") +
                        "                  new ParameterBody(" + System.getProperty("line.separator") +
                        "                          new Parameter(\"requestBodyParameterNameOne\", \"requestBodyParameterValueOneOne\", \"requestBodyParameterValueOneTwo\")," + System.getProperty("line.separator") +
                        "                          new Parameter(\"requestBodyParameterNameTwo\", \"requestBodyParameterValueTwo\")" + System.getProperty("line.separator") +
                        "                  )" + System.getProperty("line.separator") +
                        "          )",
                new HttpRequestToJavaSerializer().serializeAsJava(2,
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
    public void shouldSerializeFullObjectWithBinaryBodyResponseAsJava() throws IOException {
        // when
        assertEquals(System.getProperty("line.separator") +
                        "  request()" + System.getProperty("line.separator") +
                        "          .withBody(new byte[0]) /* note: not possible to generate code for binary data */",
                new HttpRequestToJavaSerializer().serializeAsJava(2,
                        new HttpRequest()
                                .withBody(
                                        new BinaryBody(new byte[0])
                                )
                ));
    }

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  request()" + System.getProperty("line.separator") +
                        "          .withMethod(\"GET\")" + System.getProperty("line.separator") +
                        "          .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "          .withHeaders(" + System.getProperty("line.separator") +
                        "                  new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + System.getProperty("line.separator") +
                        "                  new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withCookies(" + System.getProperty("line.separator") +
                        "                  new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + System.getProperty("line.separator") +
                        "                  new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withQueryStringParameters(" + System.getProperty("line.separator") +
                        "                  new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + System.getProperty("line.separator") +
                        "                  new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withBody(new StringBody(\"responseBody\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(2,
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
                                .withBody("responseBody")
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithCallbackAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  request()" + System.getProperty("line.separator") +
                        "          .withMethod(\"GET\")" + System.getProperty("line.separator") +
                        "          .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "          .withHeaders(" + System.getProperty("line.separator") +
                        "                  new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + System.getProperty("line.separator") +
                        "                  new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withCookies(" + System.getProperty("line.separator") +
                        "                  new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + System.getProperty("line.separator") +
                        "                  new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withQueryStringParameters(" + System.getProperty("line.separator") +
                        "                  new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + System.getProperty("line.separator") +
                        "                  new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + System.getProperty("line.separator") +
                        "          )" + System.getProperty("line.separator") +
                        "          .withBody(new StringBody(\"responseBody\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(2,
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
                                .withBody("responseBody")
                )
        );
    }

    @Test
    public void shouldEscapeJSONBodies() throws IOException {
        assertEquals("" + System.getProperty("line.separator") +
                        "  request()" + System.getProperty("line.separator") +
                        "          .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "          .withBody(new JsonBody(\"[" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "]\", JsonBodyMatchType.ONLY_MATCHING_FIELDS))",
                new HttpRequestToJavaSerializer().serializeAsJava(2,
                        new HttpRequest()
                                .withPath("somePath")
                                .withBody(new JsonBody("[" + System.getProperty("line.separator") +
                                        "    {" + System.getProperty("line.separator") +
                                        "        \"id\": \"1\"," + System.getProperty("line.separator") +
                                        "        \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\"," + System.getProperty("line.separator") +
                                        "        \"author\": \"James Tatum\"," + System.getProperty("line.separator") +
                                        "        \"isbn\": \"0691067570\"," + System.getProperty("line.separator") +
                                        "        \"publicationDate\": \"1989\"" + System.getProperty("line.separator") +
                                        "    }," + System.getProperty("line.separator") +
                                        "    {" + System.getProperty("line.separator") +
                                        "        \"id\": \"2\"," + System.getProperty("line.separator") +
                                        "        \"title\": \"You are here : personal geographies and other maps of the imagination\"," + System.getProperty("line.separator") +
                                        "        \"author\": \"Katharine A. Harmon\"," + System.getProperty("line.separator") +
                                        "        \"isbn\": \"1568984308\"," + System.getProperty("line.separator") +
                                        "        \"publicationDate\": \"2004\"" + System.getProperty("line.separator") +
                                        "    }," + System.getProperty("line.separator") +
                                        "    {" + System.getProperty("line.separator") +
                                        "        \"id\": \"3\"," + System.getProperty("line.separator") +
                                        "        \"title\": \"You just don't understand : women and men in conversation\"," + System.getProperty("line.separator") +
                                        "        \"author\": \"Deborah Tannen\"," + System.getProperty("line.separator") +
                                        "        \"isbn\": \"0345372050\"," + System.getProperty("line.separator") +
                                        "        \"publicationDate\": \"1990\"" + System.getProperty("line.separator") +
                                        "    }" + System.getProperty("line.separator") +
                                        "]"))
                )
        );
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  request()" + System.getProperty("line.separator") +
                        "          .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "          .withBody(new StringBody(\"responseBody\"))",
                new HttpRequestToJavaSerializer().serializeAsJava(2,
                        new HttpRequest()
                                .withPath("somePath")
                                .withBody("responseBody")
                )
        );
    }
}
