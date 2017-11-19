package org.mockserver.client.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpError.error;

/**
 * @author jamesdbloom
 */
public class ExpectationToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithResponseAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withMethod(\"GET\")" + NEW_LINE +
                        "                        .withPath(\"somePath\")" + NEW_LINE +
                        "                        .withHeaders(" + NEW_LINE +
                        "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                        "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withCookies(" + NEW_LINE +
                        "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                        "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withQueryStringParameters(" + NEW_LINE +
                        "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                        "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .respond(" + NEW_LINE +
                        "                response()" + NEW_LINE +
                        "                        .withStatusCode(304)" + NEW_LINE +
                        "                        .withHeaders(" + NEW_LINE +
                        "                                new Header(\"responseHeaderNameOne\", \"responseHeaderValueOneOne\", \"responseHeaderValueOneTwo\")," + NEW_LINE +
                        "                                new Header(\"responseHeaderNameTwo\", \"responseHeaderValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withCookies(" + NEW_LINE +
                        "                                new Cookie(\"responseCookieNameOne\", \"responseCookieValueOne\")," + NEW_LINE +
                        "                                new Cookie(\"responseCookieNameTwo\", \"responseCookieValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withBody(\"responseBody\")" + NEW_LINE +
                        "                        .withDelay(new Delay(TimeUnit.MINUTES, 1))" + NEW_LINE +
                        "                        .withConnectionOptions(" + NEW_LINE +
                        "                                connectionOptions()" + NEW_LINE +
                        "                                        .withSuppressContentLengthHeader(true)" + NEW_LINE +
                        "                                        .withContentLengthHeaderOverride(10)" + NEW_LINE +
                        "                                        .withSuppressConnectionHeader(true)" + NEW_LINE +
                        "                                        .withKeepAliveOverride(true)" + NEW_LINE +
                        "                                        .withCloseSocket(true)" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
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
                                        .withBody(new StringBody("somebody")),
                                Times.once(),
                                TimeToLive.unlimited()).thenRespond(
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
                                        .withDelay(new Delay(TimeUnit.MINUTES, 1))
                                        .withConnectionOptions(
                                                connectionOptions()
                                                        .withSuppressContentLengthHeader(true)
                                                        .withContentLengthHeaderOverride(10)
                                                        .withSuppressConnectionHeader(true)
                                                        .withKeepAliveOverride(true)
                                                        .withCloseSocket(true)
                                        )
                        )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithParameterBodyResponseAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withBody(" + NEW_LINE +
                        "                                new ParameterBody(" + NEW_LINE +
                        "                                        new Parameter(\"requestBodyParameterNameOne\", \"requestBodyParameterValueOneOne\", \"requestBodyParameterValueOneTwo\")," + NEW_LINE +
                        "                                        new Parameter(\"requestBodyParameterNameTwo\", \"requestBodyParameterValueTwo\")" + NEW_LINE +
                        "                                )" + NEW_LINE +
                        "                        )," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .respond(" + NEW_LINE +
                        "                response()" + NEW_LINE +
                        "                        .withBody(\"responseBody\")" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
                                new HttpRequest()
                                        .withBody(
                                                new ParameterBody(
                                                        new Parameter("requestBodyParameterNameOne", "requestBodyParameterValueOneOne", "requestBodyParameterValueOneTwo"),
                                                        new Parameter("requestBodyParameterNameTwo", "requestBodyParameterValueTwo")
                                                )
                                        ),
                                Times.once(),
                                TimeToLive.unlimited())
                                .thenRespond(
                                        new HttpResponse()
                                                .withBody("responseBody")
                                )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithBinaryBodyResponseAsJava() throws IOException {
        // when
        assertEquals(NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withBody(Base64Converter.base64StringToBytes(\"" + Base64Converter.bytesToBase64String("request body".getBytes()) + "\"))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .respond(" + NEW_LINE +
                        "                response()" + NEW_LINE +
                        "                        .withBody(\"responseBody\")" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
                                new HttpRequest()
                                        .withBody(
                                                new BinaryBody("request body".getBytes())
                                        ),
                                Times.once(),
                                TimeToLive.unlimited())
                                .thenRespond(
                                        new HttpResponse()
                                                .withBody("responseBody")
                                )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withMethod(\"GET\")" + NEW_LINE +
                        "                        .withPath(\"somePath\")" + NEW_LINE +
                        "                        .withHeaders(" + NEW_LINE +
                        "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                        "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withCookies(" + NEW_LINE +
                        "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                        "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withQueryStringParameters(" + NEW_LINE +
                        "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                        "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .forward(" + NEW_LINE +
                        "                forward()" + NEW_LINE +
                        "                        .withHost(\"some_host\")" + NEW_LINE +
                        "                        .withPort(9090)" + NEW_LINE +
                        "                        .withScheme(HttpForward.Scheme.HTTPS)" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
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
                                        .withBody(new StringBody("somebody")),
                                Times.once(),
                                TimeToLive.unlimited())
                                .thenForward(
                                        new HttpForward()
                                                .withHost("some_host")
                                                .withPort(9090)
                                                .withScheme(HttpForward.Scheme.HTTPS)
                                )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithErrorAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withMethod(\"GET\")" + NEW_LINE +
                        "                        .withPath(\"somePath\")" + NEW_LINE +
                        "                        .withHeaders(" + NEW_LINE +
                        "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                        "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withCookies(" + NEW_LINE +
                        "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                        "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withQueryStringParameters(" + NEW_LINE +
                        "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                        "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .error(" + NEW_LINE +
                        "                error()" + NEW_LINE +
                        "                        .withDelay(new Delay(TimeUnit.MINUTES, 1))" + NEW_LINE +
                        "                        .withDropConnection(true)" + NEW_LINE +
                        "                        .withResponseBytes(Base64Converter.base64StringToBytes(\"" + Base64Converter.bytesToBase64String("some_bytes".getBytes()) + "\"))" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
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
                                        .withBody(new StringBody("somebody")),
                                Times.once(),
                                TimeToLive.unlimited()
                        )
                                .thenError(
                                        error()
                                                .withDelay(new Delay(TimeUnit.MINUTES, 1))
                                                .withDropConnection(true)
                                                .withResponseBytes("some_bytes".getBytes())
                                )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithCallbackAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withMethod(\"GET\")" + NEW_LINE +
                        "                        .withPath(\"somePath\")" + NEW_LINE +
                        "                        .withHeaders(" + NEW_LINE +
                        "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + NEW_LINE +
                        "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withCookies(" + NEW_LINE +
                        "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," + NEW_LINE +
                        "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withQueryStringParameters(" + NEW_LINE +
                        "                                new Parameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + NEW_LINE +
                        "                                new Parameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + NEW_LINE +
                        "                        )" + NEW_LINE +
                        "                        .withBody(new StringBody(\"somebody\"))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .callback(" + NEW_LINE +
                        "                callback()" + NEW_LINE +
                        "                        .withCallbackClass(\"some_class\")" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
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
                                        .withBody(new StringBody("somebody")),
                                Times.once(),
                                TimeToLive.unlimited())
                                .thenCallback(
                                        new HttpClassCallback()
                                                .withCallbackClass("some_class")
                                )
                )
        );
    }

    @Test
    public void shouldEscapeJsonBodies() throws IOException {
        assertEquals("" + NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withPath(\"somePath\")" + NEW_LINE +
                        "                        .withBody(new JsonBody(\"[" + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }," + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }," + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }" + StringEscapeUtils.escapeJava(NEW_LINE) + "]\", JsonBodyMatchType.ONLY_MATCHING_FIELDS))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .respond(" + NEW_LINE +
                        "                response()" + NEW_LINE +
                        "                        .withStatusCode(304)" + NEW_LINE +
                        "                        .withBody(\"[" + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }," + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }," + StringEscapeUtils.escapeJava(NEW_LINE) + "    {" + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) + "        \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) + "    }" + StringEscapeUtils.escapeJava(NEW_LINE) + "]\")" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
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
                                                "]")),
                                Times.once(),
                                TimeToLive.unlimited()).thenRespond(
                                new HttpResponse()
                                        .withStatusCode(304)
                                        .withBody("[" + NEW_LINE +
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
                                                "]")
                        )
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
                "            \"description\": \"The unique identifier for a product\"," + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"description\": \"Name of the product\"," + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}";
        assertEquals("" + NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withPath(\"somePath\")" + NEW_LINE +
                        "                        .withBody(new JsonSchemaBody(\"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .respond(" + NEW_LINE +
                        "                response()" + NEW_LINE +
                        "                        .withStatusCode(304)" + NEW_LINE +
                        "                        .withBody(\"responseBody\")" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
                                new HttpRequest()
                                        .withPath("somePath")
                                        .withBody(new JsonSchemaBody(jsonSchema)),
                                Times.once(),
                                TimeToLive.unlimited()).thenRespond(
                                new HttpResponse()
                                        .withStatusCode(304)
                                        .withBody("responseBody")
                        )
                )
        );
    }

    @Test
    public void shouldEscapeXmlSchemaBodies() throws IOException {
        String xmlSchema = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>";
        assertEquals("" + NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withPath(\"somePath\")" + NEW_LINE +
                        "                        .withBody(new XmlSchemaBody(\"" + StringEscapeUtils.escapeJava(xmlSchema) + "\"))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .respond(" + NEW_LINE +
                        "                response()" + NEW_LINE +
                        "                        .withStatusCode(304)" + NEW_LINE +
                        "                        .withBody(\"responseBody\")" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
                                new HttpRequest()
                                        .withPath("somePath")
                                        .withBody(new XmlSchemaBody(xmlSchema)),
                                Times.once(),
                                TimeToLive.unlimited()
                        )
                                .thenRespond(
                                        new HttpResponse()
                                                .withStatusCode(304)
                                                .withBody("responseBody")
                                )
                )
        );
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        new MockServerClient(\"localhost\", 1080)" + NEW_LINE +
                        "        .when(" + NEW_LINE +
                        "                request()" + NEW_LINE +
                        "                        .withPath(\"somePath\")" + NEW_LINE +
                        "                        .withBody(new StringBody(\"responseBody\"))," + NEW_LINE +
                        "                Times.once()" + NEW_LINE +
                        "        )" + NEW_LINE +
                        "        .respond(" + NEW_LINE +
                        "                response()" + NEW_LINE +
                        "                        .withStatusCode(304)" + NEW_LINE +
                        "        );",
                new ExpectationToJavaSerializer().serializeAsJava(1,
                        new Expectation(
                                new HttpRequest()
                                        .withPath("somePath")
                                        .withBody(new StringBody("responseBody")),
                                Times.once(),
                                TimeToLive.unlimited()).thenRespond(
                                new HttpResponse()
                                        .withStatusCode(304)
                        )
                )
        );
    }
}
