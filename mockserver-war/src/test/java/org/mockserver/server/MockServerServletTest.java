package org.mockserver.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.*;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Charsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;

/**
 * @author jamesdbloom
 */
public class MockServerServletTest {

    @Mock
    private MockServerMatcher mockMockServerMatcher;
    @Mock
    private HttpServletRequestToMockServerRequestDecoder mockHttpServletRequestToMockServerRequestDecoder;
    @Mock
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder;
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationSequenceSerializer mockVerificationSequenceSerializer;
    @Mock
    private ActionHandler mockActionHandler;
    @Mock
    private RequestLogFilter mockRequestLogFilter;
    @InjectMocks
    private MockServerServlet mockServerServlet;

    @Before
    public void setupTestFixture() {
        mockServerServlet = new MockServerServlet();

        initMocks(this);
    }

    @Test
    public void shouldReturnMatchedExpectation() {
        // given
        HttpRequest request = new HttpRequest().withPath("somepath");
        HttpResponse response = new HttpResponse().withHeaders(new Header("name", "value")).withBody("somebody");
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);
        when(mockMockServerMatcher.retrieveAction(any(HttpRequest.class))).thenReturn(response);
        when(mockActionHandler.processAction(any(HttpResponse.class), any(HttpRequest.class))).thenReturn(response);

        // when
        mockServerServlet.service(httpServletRequest, httpServletResponse);

        // then
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest)).thenReturn(request);
        verify(mockMockServerMatcher).retrieveAction(request);
        when(mockActionHandler.processAction(response, request)).thenReturn(response);
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
        assertThat(httpServletResponse.getStatus(), is(200));
    }

    @Test
    public void shouldFailForExpectationWithConnectionOptions() throws UnsupportedEncodingException {
        // given
        HttpRequest request = new HttpRequest().withPath("somepath");
        HttpResponse response = new HttpResponse().withConnectionOptions(connectionOptions());
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);
        when(mockMockServerMatcher.retrieveAction(any(HttpRequest.class))).thenReturn(response);
        when(mockActionHandler.processAction(any(HttpResponse.class), any(HttpRequest.class))).thenReturn(response);

        // when
        mockServerServlet.service(httpServletRequest, httpServletResponse);

        // then
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest)).thenReturn(request);
        verify(mockMockServerMatcher).retrieveAction(request);
        when(mockActionHandler.processAction(response, request)).thenReturn(response);
        verifyNoMoreInteractions(mockServerResponseToHttpServletResponseEncoder);
        assertThat(httpServletResponse.getStatus(), is(406));
        assertThat(httpServletResponse.getContentAsString(), is("ConnectionOptions is not supported by MockServer deployable WAR due to limitations in the JEE specification; use mockserver-netty to enable these features"));
    }

    @Test
    public void shouldForwardMatchedExpectation() throws IOException {
        // given
        HttpRequest request = new HttpRequest().withPath("somepath");
        HttpForward forward = new HttpForward().withHost("some-host").withPort(1234);
        HttpResponse response = new HttpResponse();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);
        when(mockMockServerMatcher.retrieveAction(any(HttpRequest.class))).thenReturn(forward);
        when(mockActionHandler.processAction(any(HttpForward.class), any(HttpRequest.class))).thenReturn(response);

        // when
        mockServerServlet.service(httpServletRequest, httpServletResponse);

        // then
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest)).thenReturn(request);
        verify(mockMockServerMatcher).retrieveAction(request);
        when(mockActionHandler.processAction(forward, request)).thenReturn(response);
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
        assertThat(httpServletResponse.getStatus(), is(200));
    }

    @Test
    public void shouldFailForExpectationWithError() throws IOException {
        // given
        HttpRequest request = new HttpRequest().withPath("somepath");
        HttpError error = new HttpError().withDropConnection(true);
        HttpResponse response = new HttpResponse();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);
        when(mockMockServerMatcher.retrieveAction(any(HttpRequest.class))).thenReturn(error);
        when(mockActionHandler.processAction(any(HttpForward.class), any(HttpRequest.class))).thenReturn(response);

        // when
        mockServerServlet.service(httpServletRequest, httpServletResponse);

        // then
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest)).thenReturn(request);
        verify(mockMockServerMatcher).retrieveAction(request);
        when(mockActionHandler.processAction(error, request)).thenReturn(response);
        verifyNoMoreInteractions(mockServerResponseToHttpServletResponseEncoder);
        assertThat(httpServletResponse.getStatus(), is(406));
        assertThat(httpServletResponse.getContentAsString(), is("HttpError is not supported by MockServer deployable WAR due to limitations in the JEE specification; use mockserver-netty to enable these features"));
    }

    @Test
    public void shouldCallbackMatchedExpectation() throws IOException {
        // given
        HttpRequest request = new HttpRequest().withPath("somepath");
        HttpClassCallback callback = new HttpClassCallback().withCallbackClass("some-class");
        HttpResponse response = new HttpResponse();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);
        when(mockMockServerMatcher.retrieveAction(any(HttpRequest.class))).thenReturn(callback);
        when(mockActionHandler.processAction(any(HttpClassCallback.class), any(HttpRequest.class))).thenReturn(response);

        // when
        mockServerServlet.service(httpServletRequest, httpServletResponse);

        // then
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest)).thenReturn(request);
        verify(mockMockServerMatcher).retrieveAction(request);
        when(mockActionHandler.processAction(callback, request)).thenReturn(response);
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
        assertThat(httpServletResponse.getStatus(), is(200));
    }

    @Test
    public void setupExpectation() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest httpRequest = mock(HttpRequest.class);
        Times times = mock(Times.class);
        TimeToLive timeToLive = mock(TimeToLive.class);
        Expectation expectation = new Expectation(httpRequest, times, timeToLive).thenRespond(new HttpResponse());

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/expectation")
                                .withBody("requestBytes")
                );
        when(mockExpectationSerializer.deserializeArray("requestBytes")).thenReturn(new Expectation[]{expectation});
        when(mockMockServerMatcher.when(same(httpRequest), same(times), same(timeToLive))).thenReturn(expectation);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockMockServerMatcher).when(same(httpRequest), same(times), same(timeToLive));
        assertEquals(HttpServletResponse.SC_CREATED, httpServletResponse.getStatus());
    }

    @Test
    public void setupExpectationFromJSONWithAllDefault() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/expectation");
        String jsonExpectation = "{" +
                "    \"httpRequest\": {" +
                "        \"method\": \"\", " +
                "        \"path\": \"\", " +
                "        \"body\": \"\", " +
                "        \"headers\": [ ], " +
                "        \"cookies\": [ ] " +
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
        httpServletRequest.setContent(jsonExpectation.getBytes(UTF_8));

        // when
        new MockServerServlet().service(httpServletRequest, httpServletResponse);

        // then
        assertEquals(httpServletResponse.getStatus(), HttpServletResponse.SC_CREATED);
    }

    @Test
    public void setupExpectationFromJSONWithAllEmpty() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/expectation");
        String jsonExpectation = "{" +
                "    \"httpRequest\": { }," +
                "    \"httpResponse\": { }," +
                "    \"times\": { }" +
                "}";
        httpServletRequest.setContent(jsonExpectation.getBytes(UTF_8));

        // when
        new MockServerServlet().service(httpServletRequest, httpServletResponse);

        // then
        assertEquals(httpServletResponse.getStatus(), HttpServletResponse.SC_CREATED);
    }

    @Test
    public void setupExpectationFromJSONWithPartiallyEmptyFields() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/expectation");
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
        httpServletRequest.setContent(jsonExpectation.getBytes(UTF_8));

        // when
        new MockServerServlet().service(httpServletRequest, httpServletResponse);

        // then
        assertEquals(httpServletResponse.getStatus(), HttpServletResponse.SC_CREATED);
    }

    @Test
    public void shouldClearExpectationsAndLogs() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest httpRequest = new HttpRequest();

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/clear")
                                .withBody("requestBytes")
                );
        when(mockHttpRequestSerializer.deserialize("requestBytes")).thenReturn(httpRequest);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockMockServerMatcher).clear(httpRequest);
        verify(mockRequestLogFilter).clear(httpRequest);
    }

    @Test
    public void shouldClearExpectationsOnly() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest httpRequest = new HttpRequest();

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/clear")
                                .withQueryStringParameter("type", "expectation")
                                .withBody("requestBytes")
                );
        when(mockHttpRequestSerializer.deserialize("requestBytes")).thenReturn(httpRequest);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockMockServerMatcher).clear(httpRequest);
        verifyNoMoreInteractions(mockRequestLogFilter);
    }

    @Test
    public void shouldClearLogsOnly() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest httpRequest = new HttpRequest();

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/clear")
                                .withQueryStringParameter("type", "log")
                                .withBody("requestBytes")
                );
        when(mockHttpRequestSerializer.deserialize("requestBytes")).thenReturn(httpRequest);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verifyNoMoreInteractions(mockMockServerMatcher);
        verify(mockRequestLogFilter).clear(httpRequest);
    }

    @Test
    public void shouldResetMockServer() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        Expectation expectation = new Expectation(new HttpRequest(), Times.unlimited(), TimeToLive.unlimited()).thenRespond(new HttpResponse());

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/reset")
                                .withBody("requestBytes")
                );
        when(mockExpectationSerializer.deserialize("requestBytes")).thenReturn(expectation);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockMockServerMatcher).reset();
    }

    @Test
    public void shouldDumpAllExpectationsToLogInJson() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest httpRequest = new HttpRequest();

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/dumpToLog")
                                .withBody("requestBytes")
                );
        when(mockHttpRequestSerializer.deserialize("requestBytes")).thenReturn(httpRequest);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockHttpRequestSerializer).deserialize("requestBytes");
        verify(mockMockServerMatcher).dumpToLog(httpRequest, false);
    }

    @Test
    public void shouldDumpAllExpectationsToLogInJava() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest httpRequest = new HttpRequest();

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/dumpToLog")
                                .withQueryStringParameter("type", "java")
                                .withBody("requestBytes")
                );
        when(mockHttpRequestSerializer.deserialize("requestBytes")).thenReturn(httpRequest);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockHttpRequestSerializer).deserialize("requestBytes");
        verify(mockMockServerMatcher).dumpToLog(httpRequest, true);
    }

    @Test
    public void shouldReturnRecordedRequests() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/retrieve")
                                .withBody("requestBytes")
                );

        // and - a request matcher
        HttpRequest request = new HttpRequest();
        when(mockHttpRequestSerializer.deserialize(anyString())).thenReturn(request);

        // and - a set of requests retrieved from the log
        HttpRequest[] httpRequests = {request, request};
        when(mockRequestLogFilter.retrieve(any(HttpRequest.class))).thenReturn(httpRequests);
        when(mockHttpRequestSerializer.serialize(httpRequests)).thenReturn("request_response");

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockRequestLogFilter).retrieve(request);
        assertThat(httpServletResponse.getContentAsByteArray(), is("request_response".getBytes(UTF_8)));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.OK_200.code()));
    }

    @Test
    public void shouldReturnSetupExpectationsRequests() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/retrieve")
                                .withQueryStringParameter("type", "expectation")
                                .withBody("requestBytes")
                );

        // and - a request matcher
        HttpRequest request = new HttpRequest();
        when(mockHttpRequestSerializer.deserialize(anyString())).thenReturn(request);

        // and - a set of expectations retrieved from the matcher
        Expectation expectation = new Expectation(new HttpRequest(), Times.unlimited(), TimeToLive.unlimited()).thenRespond(new HttpResponse());
        List<Expectation> expectations = Arrays.asList(expectation, expectation);
        when(mockMockServerMatcher.retrieveExpectations(any(HttpRequest.class))).thenReturn(expectations);
        when(mockExpectationSerializer.serialize(expectations)).thenReturn("expectations_response");

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockMockServerMatcher).retrieveExpectations(request);
        assertThat(httpServletResponse.getContentAsByteArray(), is("expectations_response".getBytes(UTF_8)));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.OK_200.code()));
    }

    @Test
    public void shouldVerifyRequestNotMatching() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        Verification verification = verification().withRequest(new HttpRequest()).withTimes(VerificationTimes.once());

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/verify")
                                .withBody("requestBytes")
                );
        when(mockVerificationSerializer.deserialize("requestBytes")).thenReturn(verification);
        when(mockRequestLogFilter.verify(verification)).thenReturn("verification_error");

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockRequestLogFilter).verify(verification);
        assertThat(httpServletResponse.getContentAsString(), is("verification_error"));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.NOT_ACCEPTABLE_406.code()));
    }

    @Test
    public void shouldVerifyRequestMatching() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        Verification verification = verification().withRequest(new HttpRequest()).withTimes(VerificationTimes.once());

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/verify")
                                .withBody("requestBytes")
                );
        when(mockVerificationSerializer.deserialize("requestBytes")).thenReturn(verification);
        when(mockRequestLogFilter.verify(verification)).thenReturn("");

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockRequestLogFilter).verify(verification);
        assertThat(httpServletResponse.getContentAsString(), is(""));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.ACCEPTED_202.code()));
    }

    @Test
    public void shouldVerifySequenceRequestNotMatching() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        VerificationSequence verification = new VerificationSequence().withRequests(request("one"), request("two"));

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/verifySequence")
                                .withBody("requestBytes")
                );
        when(mockVerificationSequenceSerializer.deserialize("requestBytes")).thenReturn(verification);
        when(mockRequestLogFilter.verify(verification)).thenReturn("verification_error");

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockRequestLogFilter).verify(verification);
        assertThat(httpServletResponse.getContentAsString(), is("verification_error"));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.NOT_ACCEPTABLE_406.code()));
    }

    @Test
    public void shouldVerifySequenceRequestMatching() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        VerificationSequence verification = new VerificationSequence().withRequests(request("one"), request("two"));

        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/verifySequence")
                                .withBody("requestBytes")
                );
        when(mockVerificationSequenceSerializer.deserialize("requestBytes")).thenReturn(verification);
        when(mockRequestLogFilter.verify(verification)).thenReturn("");

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        verify(mockRequestLogFilter).verify(verification);
        assertThat(httpServletResponse.getContentAsString(), is(""));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.ACCEPTED_202.code()));
    }

    @Test
    public void shouldGetStatus() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/status");
        httpServletRequest.setLocalPort(1080);
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/status")
                );

        // when
        mockServerServlet.service(httpServletRequest, httpServletResponse);

        // then
        assertThat(httpServletResponse.getContentAsString(), is("" +
                "{" + NEW_LINE +
                "  \"ports\" : [ 1080 ]" + NEW_LINE +
                "}"));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.OK_200.code()));
    }

    @Test
    public void shouldPreventBindingToAdditionalPort() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class)))
                .thenReturn(
                        request()
                                .withMethod("PUT")
                                .withPath("/bind")
                );

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.NOT_IMPLEMENTED_501.code()));
    }
}
