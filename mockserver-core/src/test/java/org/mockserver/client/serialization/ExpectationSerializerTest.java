package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializerTest {

    private final Expectation fullExpectation = new Expectation(
            new HttpRequest()
                    .withMethod("GET")
                    .withURL("http://www.example.com")
                    .withPath("somePath")
                    .withQueryStringParameters(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))
                    .withBody(new StringBody("somebody", Body.Type.STRING))
                    .withHeaders(new Header("headerName", "headerValue"))
                    .withCookies(new Cookie("cookieName", "cookieValue")),
            Times.once()
    ).thenRespond(new HttpResponse()
            .withStatusCode(304)
            .withBody("responseBody")
            .withHeaders(new Header("headerName", "headerValue"))
            .withCookies(new Cookie("cookieName", "cookieValue"))
            .withDelay(new Delay(TimeUnit.MICROSECONDS, 1)));
    private final ExpectationDTO fullExpectationDTO = new ExpectationDTO()
            .setHttpRequest(
                    new HttpRequestDTO()
                            .setMethod("GET")
                            .setURL("http://www.example.com")
                            .setPath("somePath")
                            .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
                            .setBody(BodyDTO.createDTO(new StringBody("somebody", Body.Type.STRING)))
                            .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("headerName", Arrays.asList("headerValue")))))
                            .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("cookieName", Arrays.asList("cookieValue")))))
            )
            .setHttpResponse(
                    new HttpResponseDTO()
                            .setStatusCode(304)
                            .setBody(new StringBodyDTO(new StringBody("responseBody", Body.Type.STRING)))
                            .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("headerName", Arrays.asList("headerValue")))))
                            .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("cookieName", Arrays.asList("cookieValue")))))
                            .setDelay(
                                    new DelayDTO()
                                            .setTimeUnit(TimeUnit.MICROSECONDS)
                                            .setValue(1)
                            )
            )
            .setTimes(new TimesDTO(Times.once()));
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private ExpectationSerializer expectationSerializer;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setupTestFixture() {
        expectationSerializer = spy(new ExpectationSerializer());

        initMocks(this);
    }

    @Test
    public void shouldSerializeObject() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        // when
        expectationSerializer.serialize(fullExpectation);

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(fullExpectationDTO);
    }

    @Test
    public void shouldSerializeFullObjectWithResponseAsJava() throws IOException {
        // when
        assertEquals(System.getProperty("line.separator") +
                        "new MockServerClient()" + System.getProperty("line.separator") +
                        "        .when(" + System.getProperty("line.separator") +
                        "                request()" + System.getProperty("line.separator") +
                        "                        .withMethod(\"GET\")" + System.getProperty("line.separator") +
                        "                        .withURL(\"http://www.example.com\")" + System.getProperty("line.separator") +
                        "                        .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "                        .withHeaders(" + System.getProperty("line.separator") +
                        "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withCookies(" + System.getProperty("line.separator") +
                        "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOneOne\", \"requestCookieValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withQueryStringParameters(" + System.getProperty("line.separator") +
                        "                                new QueryStringParameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new QueryStringParameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withBody(new StringBody(\"somebody\", Body.Type.STRING))," + System.getProperty("line.separator") +
                        "                Times.once()" + System.getProperty("line.separator") +
                        "        )" + System.getProperty("line.separator") +
                        "        .thenRespond(" + System.getProperty("line.separator") +
                        "                response()" + System.getProperty("line.separator") +
                        "                        .withStatusCode(304)" + System.getProperty("line.separator") +
                        "                        .withHeaders(" + System.getProperty("line.separator") +
                        "                                new Header(\"responseHeaderNameOne\", \"responseHeaderValueOneOne\", \"responseHeaderValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new Header(\"responseHeaderNameTwo\", \"responseHeaderValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withCookies(" + System.getProperty("line.separator") +
                        "                                new Cookie(\"responseCookieNameOne\", \"responseCookieValueOneOne\", \"responseCookieValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new Cookie(\"responseCookieNameTwo\", \"responseCookieValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withBody(\"responseBody\")" + System.getProperty("line.separator") +
                        "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withMethod("GET")
                                        .withURL("http://www.example.com")
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
                                                new Cookie("requestCookieNameOne", "requestCookieValueOneOne", "requestCookieValueOneTwo"),
                                                new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                        )
                                        .withBody(new StringBody("somebody", Body.Type.STRING)),
                                Times.once()
                        ).thenRespond(
                                new HttpResponse()
                                        .withStatusCode(304)
                                        .withHeaders(
                                                new Header("responseHeaderNameOne", "responseHeaderValueOneOne", "responseHeaderValueOneTwo"),
                                                new Header("responseHeaderNameTwo", "responseHeaderValueTwo")
                                        )
                                        .withCookies(
                                                new Cookie("responseCookieNameOne", "responseCookieValueOneOne", "responseCookieValueOneTwo"),
                                                new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                                        )
                                        .withBody("responseBody")
                        )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithParameterBodyResponseAsJava() throws IOException {
        // when
        assertEquals(System.getProperty("line.separator") +
                        "new MockServerClient()" + System.getProperty("line.separator") +
                        "        .when(" + System.getProperty("line.separator") +
                        "                request()" + System.getProperty("line.separator") +
                        "                        .withBody(" + System.getProperty("line.separator") +
                        "                                new ParameterBody(" + System.getProperty("line.separator") +
                        "                                        new Parameter(\"requestBodyParameterNameOne\", \"requestBodyParameterValueOneOne\", \"requestBodyParameterValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                        new Parameter(\"requestBodyParameterNameTwo\", \"requestBodyParameterValueTwo\")" + System.getProperty("line.separator") +
                        "                                )" + System.getProperty("line.separator") +
                        "                        )," + System.getProperty("line.separator") +
                        "                Times.once()" + System.getProperty("line.separator") +
                        "        )" + System.getProperty("line.separator") +
                        "        .thenRespond(" + System.getProperty("line.separator") +
                        "                response()" + System.getProperty("line.separator") +
                        "                        .withStatusCode(200)" + System.getProperty("line.separator") +
                        "                        .withBody(\"responseBody\")" + System.getProperty("line.separator") +
                        "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withBody(
                                                new ParameterBody(
                                                        new Parameter("requestBodyParameterNameOne", "requestBodyParameterValueOneOne", "requestBodyParameterValueOneTwo"),
                                                        new Parameter("requestBodyParameterNameTwo", "requestBodyParameterValueTwo")
                                                )
                                        ),
                                Times.once()
                        ).thenRespond(
                                new HttpResponse()
                                        .withBody("responseBody")
                        )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithBinaryBodyResponseAsJava() throws IOException {
        // when
        assertEquals(System.getProperty("line.separator") +
                        "new MockServerClient()" + System.getProperty("line.separator") +
                        "        .when(" + System.getProperty("line.separator") +
                        "                request()" + System.getProperty("line.separator") +
                        "                        .withBody(new byte[0]) /* note: not possible to generate code for binary data */," + System.getProperty("line.separator") +
                        "                Times.once()" + System.getProperty("line.separator") +
                        "        )" + System.getProperty("line.separator") +
                        "        .thenRespond(" + System.getProperty("line.separator") +
                        "                response()" + System.getProperty("line.separator") +
                        "                        .withStatusCode(200)" + System.getProperty("line.separator") +
                        "                        .withBody(\"responseBody\")" + System.getProperty("line.separator") +
                        "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withBody(
                                                new BinaryBody(new byte[0])
                                        ),
                                Times.once()
                        ).thenRespond(
                                new HttpResponse()
                                        .withBody("responseBody")
                        )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() throws IOException {
        // when
        assertEquals(System.getProperty("line.separator") +
                        "new MockServerClient()" + System.getProperty("line.separator") +
                        "        .when(" + System.getProperty("line.separator") +
                        "                request()" + System.getProperty("line.separator") +
                        "                        .withMethod(\"GET\")" + System.getProperty("line.separator") +
                        "                        .withURL(\"http://www.example.com\")" + System.getProperty("line.separator") +
                        "                        .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "                        .withHeaders(" + System.getProperty("line.separator") +
                        "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withCookies(" + System.getProperty("line.separator") +
                        "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOneOne\", \"requestCookieValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withQueryStringParameters(" + System.getProperty("line.separator") +
                        "                                new QueryStringParameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new QueryStringParameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withBody(new StringBody(\"somebody\", Body.Type.STRING))," + System.getProperty("line.separator") +
                        "                Times.once()" + System.getProperty("line.separator") +
                        "        )" + System.getProperty("line.separator") +
                        "        .thenForward(" + System.getProperty("line.separator") +
                        "                forward()" + System.getProperty("line.separator") +
                        "                        .withHost(\"some_host\")" + System.getProperty("line.separator") +
                        "                        .withPort(9090)" + System.getProperty("line.separator") +
                        "                        .withScheme(HttpForward.Scheme.HTTPS)" + System.getProperty("line.separator") +
                        "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withMethod("GET")
                                        .withURL("http://www.example.com")
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
                                                new Cookie("requestCookieNameOne", "requestCookieValueOneOne", "requestCookieValueOneTwo"),
                                                new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                        )
                                        .withBody(new StringBody("somebody", Body.Type.STRING)),
                                Times.once()
                        ).thenForward(
                                new HttpForward()
                                        .withHost("some_host")
                                        .withPort(9090)
                                        .withScheme(HttpForward.Scheme.HTTPS)
                        )
                )
        );
    }

    @Test
    public void shouldSerializeFullObjectWithCallbackAsJava() throws IOException {
        // when
        assertEquals(System.getProperty("line.separator") +
                        "new MockServerClient()" + System.getProperty("line.separator") +
                        "        .when(" + System.getProperty("line.separator") +
                        "                request()" + System.getProperty("line.separator") +
                        "                        .withMethod(\"GET\")" + System.getProperty("line.separator") +
                        "                        .withURL(\"http://www.example.com\")" + System.getProperty("line.separator") +
                        "                        .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "                        .withHeaders(" + System.getProperty("line.separator") +
                        "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withCookies(" + System.getProperty("line.separator") +
                        "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOneOne\", \"requestCookieValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withQueryStringParameters(" + System.getProperty("line.separator") +
                        "                                new QueryStringParameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\")," + System.getProperty("line.separator") +
                        "                                new QueryStringParameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")" + System.getProperty("line.separator") +
                        "                        )" + System.getProperty("line.separator") +
                        "                        .withBody(new StringBody(\"somebody\", Body.Type.STRING))," + System.getProperty("line.separator") +
                        "                Times.once()" + System.getProperty("line.separator") +
                        "        )" + System.getProperty("line.separator") +
                        "        .thenCallback(" + System.getProperty("line.separator") +
                        "                callback()" + System.getProperty("line.separator") +
                        "                        .withCallbackClass(\"some_class\")" + System.getProperty("line.separator") +
                        "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withMethod("GET")
                                        .withURL("http://www.example.com")
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
                                                new Cookie("requestCookieNameOne", "requestCookieValueOneOne", "requestCookieValueOneTwo"),
                                                new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                        )
                                        .withBody(new StringBody("somebody", Body.Type.STRING)),
                                Times.once()
                        ).thenCallback(
                                new HttpCallback()
                                        .withCallbackClass("some_class")
                        )
                )
        );
    }

    @Test
    public void shouldEscapeJSONBodies() throws IOException {
        // when
        assertEquals("" + System.getProperty("line.separator") +
                        "new MockServerClient()" + System.getProperty("line.separator") +
                        "        .when(" + System.getProperty("line.separator") +
                        "                request()" + System.getProperty("line.separator") +
                        "                        .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "                        .withBody(new StringBody(\"[" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "]\", Body.Type.STRING))," + System.getProperty("line.separator") +
                        "                Times.once()" + System.getProperty("line.separator") +
                        "        )" + System.getProperty("line.separator") +
                        "        .thenRespond(" + System.getProperty("line.separator") +
                        "                response()" + System.getProperty("line.separator") +
                        "                        .withStatusCode(304)" + System.getProperty("line.separator") +
                        "                        .withBody(\"[" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"1\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"James Tatum\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"0691067570\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"1989\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"2\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"Katharine A. Harmon\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"1568984308\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"2004\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"id\\\": \\\"3\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"author\\\": \\\"Deborah Tannen\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"isbn\\\": \\\"0345372050\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "        \\\"publicationDate\\\": \\\"1990\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "    }" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) + "]\")" + System.getProperty("line.separator") +
                        "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withPath("somePath")
                                        .withBody(new StringBody("[" + System.getProperty("line.separator") +
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
                                                "]", Body.Type.STRING)),
                                Times.once()
                        ).thenRespond(
                                new HttpResponse()
                                        .withStatusCode(304)
                                        .withBody("[" + System.getProperty("line.separator") +
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
                                                "]")
                        )
                )
        );
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() throws IOException {
        // when
        assertEquals(System.getProperty("line.separator") +
                        "new MockServerClient()" + System.getProperty("line.separator") +
                        "        .when(" + System.getProperty("line.separator") +
                        "                request()" + System.getProperty("line.separator") +
                        "                        .withPath(\"somePath\")" + System.getProperty("line.separator") +
                        "                        .withBody(new StringBody(\"responseBody\", Body.Type.STRING))," + System.getProperty("line.separator") +
                        "                Times.once()" + System.getProperty("line.separator") +
                        "        )" + System.getProperty("line.separator") +
                        "        .thenRespond(" + System.getProperty("line.separator") +
                        "                response()" + System.getProperty("line.separator") +
                        "                        .withStatusCode(304)" + System.getProperty("line.separator") +
                        "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withPath("somePath")
                                        .withBody(new StringBody("responseBody", Body.Type.STRING)),
                                Times.once()
                        ).thenRespond(
                                new HttpResponse()
                                        .withStatusCode(304)
                        )
                )
        );
    }

    @Test
    public void shouldSerializeArray() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);


        // when
        expectationSerializer.serialize(new Expectation[]{fullExpectation, fullExpectation});

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(new ExpectationDTO[]{fullExpectationDTO, fullExpectationDTO});
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing expectation to JSON with value Expectation[httpRequest=<null>,times=<null>,httpRequestMatcher=null,httpResponse=<null>,httpForward=<null>,httpCallback=<null>]");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(ExpectationDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        expectationSerializer.serialize(new Expectation(null, null));
    }

    @Test
    public void shouldHandleExceptionWhileSerializingArray() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing expectation to JSON with value [Expectation[httpRequest=<null>,times=<null>,httpRequestMatcher=null,httpResponse=<null>,httpForward=<null>,httpCallback=<null>]]");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(ExpectationDTO[].class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        expectationSerializer.serialize(new Expectation[]{new Expectation(null, null)});
    }

    @Test
    public void shouldHandleNullAndEmptyWhileSerializingArray() throws IOException {
        // when
        assertEquals("", expectationSerializer.serialize(new Expectation[]{}));
        assertEquals("", expectationSerializer.serialize((Expectation[]) null));
    }

    @Test
    public void shouldDeserializeObject() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO.class))).thenReturn(fullExpectationDTO);

        // when
        Expectation expectation = expectationSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullExpectation, expectation);
    }

    @Test
    public void shouldDeserializeArray() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO[].class))).thenReturn(new ExpectationDTO[]{fullExpectationDTO, fullExpectationDTO});

        // when
        Expectation[] expectations = expectationSerializer.deserializeArray("requestBytes");

        // then
        assertArrayEquals(new Expectation[]{fullExpectation, fullExpectation}, expectations);
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while parsing response [requestBytes] for http response expectation");
        // and
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO.class))).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        expectationSerializer.deserialize("requestBytes");
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingArray() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while parsing response [requestBytes] for http response expectation array");
        // and
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO[].class))).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        expectationSerializer.deserializeArray("requestBytes");
    }

    @Test
    public void shouldValidateInputForObject() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Expected an JSON expectation object but http body is empty");
        // when
        expectationSerializer.deserialize("");
    }

    @Test
    public void shouldValidateInputForArray() throws IOException {
        // when
        assertArrayEquals(new Expectation[]{}, expectationSerializer.deserializeArray(""));
    }
}
