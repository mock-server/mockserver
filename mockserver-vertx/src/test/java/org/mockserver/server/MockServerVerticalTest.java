package org.mockserver.server;

import com.google.common.base.Charsets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.VertXToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToVertXResponseMapper;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
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
    private MockServerMatcher mockServerMatcher;
    @Mock
    private Expectation expectation;
    @Mock
    private VertXToMockServerRequestMapper vertXToMockServerRequestMapper;
    @Mock
    private MockServerToVertXResponseMapper mockServerToVertXResponseMapper;
    @Mock
    private ExpectationSerializer expectationSerializer;
    @Mock
    private HttpRequestSerializer httpRequestSerializer;
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
        // - an http http mapping
        HttpRequest httpRequest = new HttpRequest();
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("GET");
        when(vertXToMockServerRequestMapper.mapVertXRequestToMockServerRequest(httpServerRequest, "".getBytes())).thenReturn(httpRequest);
        // - an expectation that does match
        HttpResponse httpResponse = new HttpResponse();
        when(mockServerMatcher.handle(httpRequest)).thenReturn(httpResponse);

        // when - receive http http
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then - response mapping should be called
        verify(mockServerToVertXResponseMapper).mapMockServerResponseToVertXResponse(same(httpResponse), same(httpServerRequest.response()));
    }

    @Test
    public void shouldNotMapRespondWhenRequestDoesNotMatch() throws IOException {
        // given
        // - an http http mapping
        HttpRequest httpRequest = new HttpRequest();
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("GET");
        when(vertXToMockServerRequestMapper.mapVertXRequestToMockServerRequest(httpServerRequest, "".getBytes())).thenReturn(httpRequest);
        // - an expectation that does not match
        when(mockServerMatcher.handle(httpRequest)).thenReturn(null);

        // when - receive http http
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then - response mapping should be called
        verify(mockServerToVertXResponseMapper, never()).mapMockServerResponseToVertXResponse(any(HttpResponse.class), same(httpServerRequest.response()));
    }

    @Test
    public void setupExpectation() throws IOException {
        // given
        // - a http
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("PUT")
                        .withPath("/expectation")
                        .withBody("requestBytes".getBytes());
        // - that deserializes to an expectation
        Times times = Times.unlimited();
        HttpRequest httpRequest = new HttpRequest();
        Expectation expectation = spy(new Expectation(httpRequest, times).thenRespond(new HttpResponse()));
        when(expectationSerializer.deserialize(new String(httpServerRequest.body(), Charsets.UTF_8))).thenReturn(expectation);
        // - an MockServer that returns the deserialized expectation
        when(mockServerMatcher.when(same(httpRequest), same(times))).thenReturn(expectation);

        // when - receive an expectation http
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then
        // - expectation is added to MockServer
        verify(mockServerMatcher).when(same(httpRequest), same(times));
        verify(expectation).thenRespond(expectation.getHttpResponse());
        // - and response code is set
        assertEquals(HttpStatusCode.CREATED_201.code(), httpServerRequest.response().getStatusCode());
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
                "        \"cookies\": [ ]" +
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
                        .withPath("/expectation")
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
                        .withPath("/expectation")
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
                        .withPath("/expectation")
                        .withResponse(new MockHttpServerResponse())
                        .withBody(jsonExpectation.getBytes());

        // when
        new MockServerVertical().getRequestHandler().handle(httpServerRequest);

        // then
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

        String requestBytes = "requestBytes";
        httpServerRequest.withBody(requestBytes.getBytes());
        when(httpRequestSerializer.deserialize(requestBytes)).thenReturn(httpRequest);

        // when
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then
        verify(mockServerMatcher).clear(httpRequest);
        verifyNoMoreInteractions(vertXToMockServerRequestMapper);
    }

    @Test
    public void shouldResetMockServer() throws IOException {
        // given
        MockHttpServerResponse httpServerResponse = new MockHttpServerResponse();
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("PUT")
                        .withPath("/reset")
                        .withResponse(httpServerResponse);
        Expectation expectation = new Expectation(new HttpRequest(), Times.unlimited()).thenRespond(new HttpResponse());

        String requestBytes = "requestBytes";
        httpServerRequest.withBody(requestBytes.getBytes());
        when(expectationSerializer.deserialize(requestBytes)).thenReturn(expectation);

        // when
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then
        verify(mockServerMatcher).reset();
        verifyNoMoreInteractions(vertXToMockServerRequestMapper);
    }

    @Test
    public void shouldDumpAllExpectationsToLog() throws IOException {
        // given
        MockHttpServerResponse httpServerResponse = new MockHttpServerResponse();
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withMethod("PUT")
                        .withPath("/dumpToLog")
                        .withResponse(httpServerResponse);
        HttpRequest httpRequest = new HttpRequest();

        String requestBytes = "requestBytes";
        httpServerRequest.withBody(requestBytes.getBytes());
        when(httpRequestSerializer.deserialize(requestBytes)).thenReturn(httpRequest);

        // when
        mockServerVertical.getRequestHandler().handle(httpServerRequest);

        // then
        verify(httpRequestSerializer).deserialize(requestBytes);
        verify(mockServerMatcher).dumpToLog(httpRequest);
        verifyNoMoreInteractions(vertXToMockServerRequestMapper);
    }
}
