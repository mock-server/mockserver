package org.mockserver.client.serialization;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import javax.xml.bind.DatatypeConverter;
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
                    .withBody(new StringBody("somebody", Body.Type.EXACT))
                    .withHeaders(new Header("headerName", "headerValue"))
                    .withCookies(new Cookie("cookieName", "cookieValue")),
            Times.once()
    ).thenRespond(new HttpResponse()
            .withStatusCode(304)
            .withBody("someBody")
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
                            .setBody(BodyDTO.createDTO(new StringBody("somebody", Body.Type.EXACT)))
                            .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("headerName", Arrays.asList("headerValue")))))
                            .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("cookieName", Arrays.asList("cookieValue")))))
            )
            .setHttpResponse(
                    new HttpResponseDTO()
                            .setStatusCode(304)
                            .setBody(DatatypeConverter.printBase64Binary("someBody".getBytes()))
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
    public void shouldSerializeFullObjectAsJava() throws IOException {
        // when
        assertEquals("\n" +
                "new MockServerClient()\n" +
                "        .when(\n" +
                "                request()\n" +
                "                        .withMethod(\"GET\")\n" +
                "                        .withURL(\"http://www.example.com\")\n" +
                "                        .withPath(\"somePath\")\n" +
                "                        .withHeaders(\n" +
                "                                new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\"),\n" +
                "                                new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")\n" +
                "                        )\n" +
                "                        .withCookies(\n" +
                "                                new Cookie(\"requestCookieNameOne\", \"requestCookieValueOneOne\", \"requestCookieValueOneTwo\"),\n" +
                "                                new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")\n" +
                "                        )\n" +
                "                        .withQueryStringParameters(\n" +
                "                                new QueryStringParameter(\"requestQueryStringParameterNameOne\", \"requestQueryStringParameterValueOneOne\", \"requestQueryStringParameterValueOneTwo\"),\n" +
                "                                new QueryStringParameter(\"requestQueryStringParameterNameTwo\", \"requestQueryStringParameterValueTwo\")\n" +
                "                        )\n" +
                "                        .withBody(new StringBody(\"somebody\", Body.Type.EXACT)),\n" +
                "                Times.once()\n" +
                "        )\n" +
                "        .thenRespond(\n" +
                "                response()\n" +
                "                        .withStatusCode(304)\n" +
                "                        .withHeaders(\n" +
                "                                new Header(\"responseHeaderNameOne\", \"responseHeaderValueOneOne\", \"responseHeaderValueOneTwo\"),\n" +
                "                                new Header(\"responseHeaderNameTwo\", \"responseHeaderValueTwo\")\n" +
                "                        )\n" +
                "                        .withCookies(\n" +
                "                                new Cookie(\"responseCookieNameOne\", \"responseCookieValueOneOne\", \"responseCookieValueOneTwo\"),\n" +
                "                                new Cookie(\"responseCookieNameTwo\", \"responseCookieValueTwo\")\n" +
                "                        )\n" +
                "                        .withBody(\"someBody\")\n" +
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
                                        .withBody(new StringBody("somebody", Body.Type.EXACT)),
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
                                        .withBody("someBody")
                        )
                )
        );
    }

    @Test
    public void shouldEscapeJSONBodies() throws IOException {
        // when
        assertEquals("\n" +
                "new MockServerClient()\n" +
                "        .when(\n" +
                "                request()\n" +
                "                        .withPath(\"somePath\")\n" +
                "                        .withBody(new StringBody(\"[\\n    {\\n        \\\"id\\\": \\\"1\\\",\\n        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\",\\n        \\\"author\\\": \\\"James Tatum\\\",\\n        \\\"isbn\\\": \\\"0691067570\\\",\\n        \\\"publicationDate\\\": \\\"1989\\\"\\n    },\\n    {\\n        \\\"id\\\": \\\"2\\\",\\n        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\",\\n        \\\"author\\\": \\\"Katharine A. Harmon\\\",\\n        \\\"isbn\\\": \\\"1568984308\\\",\\n        \\\"publicationDate\\\": \\\"2004\\\"\\n    },\\n    {\\n        \\\"id\\\": \\\"3\\\",\\n        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\",\\n        \\\"author\\\": \\\"Deborah Tannen\\\",\\n        \\\"isbn\\\": \\\"0345372050\\\",\\n        \\\"publicationDate\\\": \\\"1990\\\"\\n    }\\n]\", Body.Type.EXACT)),\n" +
                "                Times.once()\n" +
                "        )\n" +
                "        .thenRespond(\n" +
                "                response()\n" +
                "                        .withStatusCode(304)\n" +
                "                        .withBody(\"[\\n    {\\n        \\\"id\\\": \\\"1\\\",\\n        \\\"title\\\": \\\"Xenophon's imperial fiction : on the education of Cyrus\\\",\\n        \\\"author\\\": \\\"James Tatum\\\",\\n        \\\"isbn\\\": \\\"0691067570\\\",\\n        \\\"publicationDate\\\": \\\"1989\\\"\\n    },\\n    {\\n        \\\"id\\\": \\\"2\\\",\\n        \\\"title\\\": \\\"You are here : personal geographies and other maps of the imagination\\\",\\n        \\\"author\\\": \\\"Katharine A. Harmon\\\",\\n        \\\"isbn\\\": \\\"1568984308\\\",\\n        \\\"publicationDate\\\": \\\"2004\\\"\\n    },\\n    {\\n        \\\"id\\\": \\\"3\\\",\\n        \\\"title\\\": \\\"You just don't understand : women and men in conversation\\\",\\n        \\\"author\\\": \\\"Deborah Tannen\\\",\\n        \\\"isbn\\\": \\\"0345372050\\\",\\n        \\\"publicationDate\\\": \\\"1990\\\"\\n    }\\n]\")\n" +
                "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withPath("somePath")
                                        .withBody(new StringBody("[\n" +
                                                "    {\n" +
                                                "        \"id\": \"1\",\n" +
                                                "        \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\",\n" +
                                                "        \"author\": \"James Tatum\",\n" +
                                                "        \"isbn\": \"0691067570\",\n" +
                                                "        \"publicationDate\": \"1989\"\n" +
                                                "    },\n" +
                                                "    {\n" +
                                                "        \"id\": \"2\",\n" +
                                                "        \"title\": \"You are here : personal geographies and other maps of the imagination\",\n" +
                                                "        \"author\": \"Katharine A. Harmon\",\n" +
                                                "        \"isbn\": \"1568984308\",\n" +
                                                "        \"publicationDate\": \"2004\"\n" +
                                                "    },\n" +
                                                "    {\n" +
                                                "        \"id\": \"3\",\n" +
                                                "        \"title\": \"You just don't understand : women and men in conversation\",\n" +
                                                "        \"author\": \"Deborah Tannen\",\n" +
                                                "        \"isbn\": \"0345372050\",\n" +
                                                "        \"publicationDate\": \"1990\"\n" +
                                                "    }\n" +
                                                "]", Body.Type.EXACT)),
                                Times.once()
                        ).thenRespond(
                                new HttpResponse()
                                        .withStatusCode(304)
                                        .withBody("[\n" +
                                                "    {\n" +
                                                "        \"id\": \"1\",\n" +
                                                "        \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\",\n" +
                                                "        \"author\": \"James Tatum\",\n" +
                                                "        \"isbn\": \"0691067570\",\n" +
                                                "        \"publicationDate\": \"1989\"\n" +
                                                "    },\n" +
                                                "    {\n" +
                                                "        \"id\": \"2\",\n" +
                                                "        \"title\": \"You are here : personal geographies and other maps of the imagination\",\n" +
                                                "        \"author\": \"Katharine A. Harmon\",\n" +
                                                "        \"isbn\": \"1568984308\",\n" +
                                                "        \"publicationDate\": \"2004\"\n" +
                                                "    },\n" +
                                                "    {\n" +
                                                "        \"id\": \"3\",\n" +
                                                "        \"title\": \"You just don't understand : women and men in conversation\",\n" +
                                                "        \"author\": \"Deborah Tannen\",\n" +
                                                "        \"isbn\": \"0345372050\",\n" +
                                                "        \"publicationDate\": \"1990\"\n" +
                                                "    }\n" +
                                                "]")
                        )
                )
        );
    }

    @Test
    public void shouldSerializeMinimalObjectAsJava() throws IOException {
        // when
        assertEquals("\n" +
                "new MockServerClient()\n" +
                "        .when(\n" +
                "                request()\n" +
                "                        .withPath(\"somePath\")\n" +
                "                        .withBody(new StringBody(\"someBody\", Body.Type.EXACT)),\n" +
                "                Times.once()\n" +
                "        )\n" +
                "        .thenRespond(\n" +
                "                response()\n" +
                "                        .withStatusCode(304)\n" +
                "        );",
                expectationSerializer.serializeAsJava(
                        new Expectation(
                                new HttpRequest()
                                        .withPath("somePath")
                                        .withBody(new StringBody("someBody", Body.Type.EXACT)),
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

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(ExpectationDTO.class))).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        expectationSerializer.serialize(mock(Expectation.class));
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhileSerializingArray() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(ExpectationDTO[].class))).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        expectationSerializer.serialize(new Expectation[]{mock(Expectation.class), mock(Expectation.class)});
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

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhileDeserializingObject() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO.class))).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        expectationSerializer.deserialize("requestBytes");
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhileDeserializingArray() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO[].class))).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        expectationSerializer.deserializeArray("requestBytes");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldValidateInputForObject() throws IOException {
        // when
        expectationSerializer.deserialize("");
    }

    @Test
    public void shouldValidateInputForArray() throws IOException {
        // when
        assertArrayEquals(new Expectation[]{}, expectationSerializer.deserializeArray(""));
    }
}
