package org.mockserver.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.mappers.HttpServerRequestMapper;
import org.mockserver.mappers.HttpServerResponseMapper;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.vertxtest.http.MockHttpServerRequest;
import org.vertxtest.http.MockHttpServerResponse;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerVerticalTest {

    @Mock
    private MockServer mockServer;
    @Mock
    private Expectation expectation;
    @Mock
    private HttpServerRequestMapper httpServerRequestMapper;
    @Mock
    private HttpServerResponseMapper httpServerResponseMapper;
    @Mock
    private ExpectationSerializer expectationSerializer;
    @InjectMocks
    private MockServerVertical mockServerVertical;

    @Before
    public void setupTestFixture() {
        mockServerVertical = new MockServerVertical();

        initMocks(this);
    }

    @Test
    public void shouldMapRespondWhenRequestMatches() throws IOException {
        // given
        // - an http request mapping
        HttpRequest httpRequest = new HttpRequest();
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("GET");
        when(httpServerRequestMapper.createHttpRequest(httpServerRequest, "".getBytes())).thenReturn(httpRequest);
        // - an expectation that does match
        HttpResponse httpResponse = new HttpResponse();
        when(mockServer.handle(httpRequest)).thenReturn(httpResponse);

        // when - receive http request
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then - response mapping should be called
        verify(httpServerResponseMapper).mapHttpServerResponse(same(httpResponse), same(httpServerRequest.response()));
    }

    @Test
    public void shouldNotMapRespondWhenRequestDoesNotMatch() throws IOException {
        // given
        // - an http request mapping
        HttpRequest httpRequest = new HttpRequest();
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("GET");
        when(httpServerRequestMapper.createHttpRequest(httpServerRequest, "".getBytes())).thenReturn(httpRequest);
        // - an expectation that does not match
        when(mockServer.handle(httpRequest)).thenReturn(null);

        // when - receive http request
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then - response mapping should be called
        verify(httpServerResponseMapper, never()).mapHttpServerResponse(any(HttpResponse.class), same(httpServerRequest.response()));
    }

    @Test
    public void setupExpectation() throws IOException {
        // given
        // - a request
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("PUT")
                        .withBody("requestBytes".getBytes());
        // - that deserializes to an expectation
        Times times = Times.unlimited();
        HttpRequest httpRequest = new HttpRequest();
        Expectation expectation = spy(new Expectation(httpRequest, times).respond(new HttpResponse()));
        when(expectationSerializer.deserialize(httpServerRequest.body())).thenReturn(expectation);
        // - an MockServer that returns the deserialized expectation
        when(mockServer.when(same(httpRequest), same(times))).thenReturn(expectation);

        // when - receive an expectation request
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then
        // - expectation is added to MockServer
        verify(mockServer).when(same(httpRequest), same(times));
        verify(expectation).respond(expectation.getHttpResponse());
        // - and response code is set
        assertEquals(HttpStatusCode.CREATED_201.code(), httpServerRequest.response().getStatusCode());
    }

    @Test
    public void shouldClearExpectations() throws IOException {
        // given
        MockHttpServerResponse httpServerResponse = new MockHttpServerResponse();
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("PUT")
                        .withPath("/clear")
                        .withResponse(httpServerResponse);
        HttpRequest httpRequest = new HttpRequest();
        Expectation expectation = new Expectation(httpRequest, Times.unlimited()).respond(new HttpResponse());

        byte[] requestBytes = "requestBytes".getBytes();
        httpServerRequest.withBody(requestBytes);
        when(expectationSerializer.deserialize(requestBytes)).thenReturn(expectation);

        // when
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then
        verify(mockServer).clear(httpRequest);
        verifyNoMoreInteractions(httpServerRequestMapper);
    }

    @Test
    public void setupExpectationFromJSONWithAllDefault() throws IOException {
        // given
        String jsonExpectation = "{" +
                "    \"httpRequest\": {" +
                "        \"method\": \"\", " +
                "        \"path\": \"\", " +
                "        \"body\": \"\", " +
                "        \"headers\": [ ], " +
                "        \"cookies\": [ ], " +
                "        \"parameters\": [ ]" +
                "    }, " +
                "    \"httpResponse\": {" +
                "        \"statusCode\": 200, " +
                "        \"body\": \"\", " +
                "        \"cookies\": [ ], " +
                "        \"headers\": [ ], " +
                "        \"delay\": {" +
                "            \"timeUnit\": \"MICROSECONDS\", " +
                "            \"value\": 0" +
                "        }" +
                "    }, " +
                "    \"times\": {" +
                "        \"remainingTimes\": 1, " +
                "        \"unlimited\": true" +
                "    }" +
                "}";
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("PUT")
                        .withResponse(new MockHttpServerResponse())
                        .withBody(jsonExpectation.getBytes());

        // when
        new MockServerVertical().getRequestHandler().handle(httpServerRequest);

        // then
        assertEquals(HttpStatusCode.CREATED_201.code(), httpServerRequest.response().getStatusCode());
    }

    @Test
    public void setupExpectationFromJSONWithAllEmpty() throws IOException {
        // given
        String jsonExpectation = "{" +
                "    \"httpRequest\": { }," +
                "    \"httpResponse\": { }," +
                "    \"times\": { }" +
                "}";
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("PUT")
                        .withResponse(new MockHttpServerResponse())
                        .withBody(jsonExpectation.getBytes());

        // when
        new MockServerVertical().getRequestHandler().handle(httpServerRequest);

        // then
        assertEquals(HttpStatusCode.CREATED_201.code(), httpServerRequest.response().getStatusCode());
    }

    @Test
    public void setupExpectationFromJSONWithPartiallyEmptyFields() throws IOException {
        // given
        String jsonExpectation = "{" +
                "    \"httpRequest\": {" +
                "        \"path\": \"\"" +
                "    }, " +
                "    \"httpResponse\": {" +
                "        \"body\": \"\"" +
                "    }, " +
                "    \"times\": {" +
                "        \"remainingTimes\": 1, " +
                "        \"unlimited\": true" +
                "    }" +
                "}";
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("PUT")
                        .withResponse(new MockHttpServerResponse())
                        .withBody(jsonExpectation.getBytes());

        // when
        new MockServerVertical().getRequestHandler().handle(httpServerRequest);

        // then
        assertEquals(HttpStatusCode.CREATED_201.code(), httpServerRequest.response().getStatusCode());
    }

}
