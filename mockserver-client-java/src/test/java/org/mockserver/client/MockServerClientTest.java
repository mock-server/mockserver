package org.mockserver.client;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.AtLeast;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.httpclient.SocketConnectionException;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.*;
import org.mockserver.serialization.model.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;
import org.mockserver.version.Version;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.matchers.Times.unlimited;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON_UTF_8;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * @author jamesdbloom
 */
public class MockServerClientTest {

    @Mock
    private NettyHttpClient mockHttpClient;
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private RequestDefinitionSerializer mockRequestDefinitionSerializer;
    @Mock
    private LogEventRequestAndResponseSerializer httpRequestResponseSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationSequenceSerializer mockVerificationSequenceSerializer;
    @InjectMocks
    private MockServerClient mockServerClient;

    @Captor
    ArgumentCaptor<HttpRequest> httpRequestArgumentCaptor;

    @Before
    public void setupTestFixture() {
        mockServerClient = new MockServerClient("localhost", 1090);

        openMocks(this);
    }

    @Test
    public void shouldHandleNullHostnameExceptions() {
        // when
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> new MockServerClient(null, 1090));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString("Host can not be null or empty"));
    }

    @Test
    public void shouldHandleNullHttpRequestEnhancerException() {
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.withRequestOverride(null));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString("Request with default properties can not be null"));
    }

    @Test
    public void shouldEnhanceRequestWithAuthorizationHeader() {
        // given
        String authorizationKey = "Authorization";
        String authorizationHeaderValue = "Basic dGVzdFVzZXI6dGVzdA==";
        HttpRequest defaultRequestProperties = new HttpRequest();
        defaultRequestProperties.withHeader(authorizationKey, authorizationHeaderValue);

        // when
        mockServerClient
            .withRequestOverride(defaultRequestProperties)
            .reset();

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        List<String> authorizationHeader = httpRequestArgumentCaptor.getValue().getHeader(authorizationKey);
        assertTrue(authorizationHeader.contains(authorizationHeaderValue));
    }

    @Test
    public void shouldHandleNullContextPathExceptions() {
        // when
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> new MockServerClient("localhost", 1090, null));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString("ContextPath can not be null"));

    }

    @Test
    public void shouldHandleInvalidExpectationException() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response()
                            .withStatusCode(BAD_REQUEST.code())
                            .withBody("error_body")
            );

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(request());
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> forwardChainExpectation.respond(response()));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString("error_body"));
    }

    @Test
    public void shouldHandleNonMatchingServerVersion() {
        try {
            System.setProperty("MOCKSERVER_VERSION", "1.2.3");

            // given
            when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
                .thenReturn(response()
                                .withHeader("version", "1.3.2")
                                .withStatusCode(CREATED.code())
                );

            // when
            ForwardChainExpectation forwardChainExpectation = mockServerClient.when(request());
            ClientException clientException = assertThrows(ClientException.class, () -> forwardChainExpectation.respond(response()));

            // then
            assertThat(clientException.getMessage(), containsString("Client version \"" + Version.getVersion() + "\" major and minor versions do not match server version \"1.3.2\""));
        } finally {
            System.clearProperty("MOCKSERVER_VERSION");
        }
    }

    @Test
    public void shouldHandleMatchingServerVersion() {
        try {
            // given
            System.setProperty("MOCKSERVER_VERSION", "same_version");
            when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
                .thenReturn(response()
                                .withHeader("version", Version.getVersion())
                                .withStatusCode(CREATED.code())
                );

            // when
            ForwardChainExpectation forwardChainExpectation = mockServerClient.when(request());
            forwardChainExpectation.respond(response());
        } catch (Throwable t) {
            // then - no exception should be thrown
            fail();
        } finally {
            System.clearProperty("MOCKSERVER_VERSION");
        }
    }

    @Test
    public void shouldHandleMatchingMajorAndMinorServerVersion() {
        try {
            // given
            System.setProperty("MOCKSERVER_VERSION", "1.2.3");
            when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
                .thenReturn(response()
                                .withHeader("version", StringUtils.substringBeforeLast(Version.getVersion(), ".") + ".100")
                                .withStatusCode(CREATED.code())
                );

            // when
            ForwardChainExpectation forwardChainExpectation = mockServerClient.when(request());
            forwardChainExpectation.respond(response());
        } catch (Throwable t) {
            // then - no exception should be thrown
            fail();
        } finally {
            System.clearProperty("MOCKSERVER_VERSION");
        }
    }

    @Test
    public void shouldUpsertExpectations() {
        // given
        Expectation expectationOne = new Expectation(request().withPath("/some_path"), unlimited(), TimeToLive.unlimited(), 0)
            .thenRespond(
                response()
                    .withBody("some_body_one")
            );
        Expectation expectationTwo = new Expectation(request().withPath("/some_path"), unlimited(), TimeToLive.unlimited(), 10)
            .thenRespond(
                response()
                    .withBody("some_body_two")
            );
        when(mockExpectationSerializer.serialize(expectationOne, expectationTwo)).thenReturn("some_body");

        // when
        mockServerClient.upsert(expectationOne, expectationTwo);

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(
            request()
                .withHeader(CONTENT_TYPE.toString(), APPLICATION_JSON_UTF_8.toString())
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withBody("some_body", UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSetupExpectationWithResponse() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpResponse httpResponse =
            new HttpResponse()
                .withBody("some_response_body")
                .withHeaders(new Header("responseName", "responseValue"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpResponse);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpResponse, expectation.getHttpResponse());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithResponseTemplate() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpTemplate template =
            new HttpTemplate(HttpTemplate.TemplateType.VELOCITY)
                .withTemplate("some_template");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(template);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(template, expectation.getHttpResponseTemplate());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithResponseClassCallback() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpClassCallback httpClassCallback =
            new HttpClassCallback()
                .withCallbackClass("some_class");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpClassCallback);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpClassCallback, expectation.getHttpResponseClassCallback());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithForward() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpForward httpForward =
            new HttpForward()
                .withHost("some_host")
                .withPort(9090)
                .withScheme(HttpForward.Scheme.HTTPS);

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.forward(httpForward);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpForward, expectation.getHttpForward());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithForwardTemplate() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpTemplate template =
            new HttpTemplate(HttpTemplate.TemplateType.VELOCITY)
                .withTemplate("some_template");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.forward(template);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(template, expectation.getHttpForwardTemplate());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithForwardClassCallback() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpClassCallback httpClassCallback =
            new HttpClassCallback()
                .withCallbackClass("some_class");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.forward(httpClassCallback);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpClassCallback, expectation.getHttpForwardClassCallback());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithOverrideForwardedRequest() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.forward(forwardOverriddenRequest(request().withBody("some_overridden_body")));

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertThat(expectation.getHttpForwardTemplate(), nullValue());
        assertThat(expectation.getHttpOverrideForwardedRequest(), is(new HttpOverrideForwardedRequest()
                                                                         .withRequestOverride(request().withBody("some_overridden_body"))
        ));
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithError() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpError httpError =
            new HttpError()
                .withDropConnection(true)
                .withResponseBytes("silly_bytes".getBytes(UTF_8));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.error(httpError);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpError, expectation.getHttpError());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSendExpectationWithRequest() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .respond(
                new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpResponse(new HttpResponseDTO(new HttpResponse()
                                                         .withBody("some_response_body")
                                                         .withHeaders(new Header("responseName", "responseValue"))))
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithRequestTemplate() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .respond(
                new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpResponse(new HttpResponseDTO(new HttpResponse()
                                                         .withBody("some_response_body")
                                                         .withHeaders(new Header("responseName", "responseValue"))))
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithRequestClassCallback() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .respond(
                new HttpClassCallback()
                    .withCallbackClass("some_class")
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpResponseClassCallback(
                    new HttpClassCallbackDTO(
                        new HttpClassCallback()
                            .withCallbackClass("some_class")
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithForward() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .forward(
                new HttpForward()
                    .withHost("some_host")
                    .withPort(9090)
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpForward(
                    new HttpForwardDTO(
                        new HttpForward()
                            .withHost("some_host")
                            .withPort(9090)
                            .withScheme(HttpForward.Scheme.HTTPS)
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithForwardTemplate() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .forward(
                new HttpForward()
                    .withHost("some_host")
                    .withPort(9090)
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpForward(
                    new HttpForwardDTO(
                        new HttpForward()
                            .withHost("some_host")
                            .withPort(9090)
                            .withScheme(HttpForward.Scheme.HTTPS)
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithForwardClassCallback() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .forward(
                new HttpClassCallback()
                    .withCallbackClass("some_class")
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpForwardClassCallback(
                    new HttpClassCallbackDTO(
                        new HttpClassCallback()
                            .withCallbackClass("some_class")
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithOverrideForwardedRequest() {
        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            );
        forwardChainExpectation.forward(forwardOverriddenRequest(request().withBody("some_replaced_body")));

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpOverrideForwardedRequest(
                    new HttpOverrideForwardedRequestDTO(
                        new HttpOverrideForwardedRequest()
                            .withRequestOverride(
                                request()
                                    .withBody("some_replaced_body")
                            )
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithError() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .error(
                new HttpError()
                    .withDelay(TimeUnit.MILLISECONDS, 100)
                    .withResponseBytes("random_bytes".getBytes(UTF_8))
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpError(
                    new HttpErrorDTO(
                        new HttpError()
                            .withDelay(TimeUnit.MILLISECONDS, 100)
                            .withResponseBytes("random_bytes".getBytes(UTF_8))
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationRequestWithDefaultTimes() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .respond(
                new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                                       .withPath("/some_path")
                                                       .withBody(new StringBody("some_request_body"))))
                .setHttpResponse(new HttpResponseDTO(new HttpResponse()
                                                         .withBody("some_response_body")
                                                         .withHeaders(new Header("responseName", "responseValue"))))
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.unlimited()))
                .buildObject()
        );
    }

    @Test
    public void shouldSendStopRequest() {
        // when
        mockServerClient.stop();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withPath("/mockserver/stop"),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldBeCloseable() {
        // when
        mockServerClient.close();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withPath("/mockserver/stop"),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldQueryRunningStatus() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withStatusCode(HttpStatusCode.OK_200.code()));

        // when
        boolean hasStarted = mockServerClient.hasStarted();
        boolean hasStopped = mockServerClient.hasStopped();

        // then
        assertThat(hasStopped, is(false));
        assertThat(hasStarted, is(true));
        verify(mockHttpClient, new AtLeast(1))
            .sendRequest(
                request()
                    .withHeader(HOST.toString(), "localhost:" + 1090)
                    .withMethod("PUT")
                    .withPath("/mockserver/status"),
                20000,
                TimeUnit.MILLISECONDS,
                false
            );
    }

    @Test
    public void shouldQueryRunningStatusWhenSocketConnectionException() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenThrow(SocketConnectionException.class);

        // when
        boolean hasStopped = mockServerClient.hasStopped();
        boolean hasStarted = mockServerClient.hasStarted();

        // then
        assertThat(hasStopped, is(true));
        assertThat(hasStarted, is(false));
        verify(mockHttpClient, atLeastOnce()).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withPath("/mockserver/status"),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendResetRequest() {
        // when
        mockServerClient.reset();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withPath("/mockserver/reset"),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendClearRequest() {
        // given
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockRequestDefinitionSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // when
        mockServerClient.clear(someRequestMatcher);

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clear")
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendClearRequestWithType() {
        // given
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockRequestDefinitionSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // when
        mockServerClient.clear(someRequestMatcher, ClearType.LOG);

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clear")
                .withQueryStringParameter("type", "log")
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendClearRequestForNullRequest() {
        // when
        mockServerClient
            .clear((RequestDefinition) null);

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clear")
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldRetrieveRequests() {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockRequestDefinitionSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));

        // and - a response
        HttpRequest[] httpRequests = {};
        when(mockRequestDefinitionSerializer.deserializeArray("body")).thenReturn(httpRequests);

        // when
        HttpRequest[] recordedRequests = mockServerClient.retrieveRecordedRequests(someRequestMatcher);
        assertThat(recordedRequests, instanceOf(HttpRequest[].class));
        assertThat(recordedRequests, arrayWithSize(0));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
        verify(mockRequestDefinitionSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveRequestsWithNullRequest() {
        // given
        HttpRequest[] httpRequests = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));
        when(mockRequestDefinitionSerializer.deserializeArray("body")).thenReturn(httpRequests);

        // when
        HttpRequest[] recordedRequests = mockServerClient.retrieveRecordedRequests(null);
        assertThat(recordedRequests, instanceOf(HttpRequest[].class));
        assertThat(recordedRequests, arrayWithSize(0));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
        verify(mockRequestDefinitionSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveRequestResponses() {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockRequestDefinitionSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));

        // and - a response
        LogEventRequestAndResponse[] httpRequests = {};
        when(httpRequestResponseSerializer.deserializeArray("body")).thenReturn(httpRequests);

        // when
        assertSame(httpRequests, mockServerClient.retrieveRecordedRequestsAndResponses(someRequestMatcher));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
        verify(httpRequestResponseSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveRequestResponsesWithNullRequest() {
        // given
        LogEventRequestAndResponse[] httpRequests = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));
        when(httpRequestResponseSerializer.deserializeArray("body")).thenReturn(httpRequests);

        // when
        assertSame(httpRequests, mockServerClient.retrieveRecordedRequestsAndResponses(null));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
        verify(httpRequestResponseSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveActiveExpectations() {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockRequestDefinitionSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));

        // and - an expectation
        Expectation[] expectations = {};
        when(mockExpectationSerializer.deserializeArray("body", true)).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveActiveExpectations(someRequestMatcher));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
        verify(mockExpectationSerializer).deserializeArray("body", true);
    }

    @Test
    public void shouldRetrieveActiveExpectationsWithNullRequest() {
        // given
        Expectation[] expectations = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));
        when(mockExpectationSerializer.deserializeArray("body", true)).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveActiveExpectations(null));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
        verify(mockExpectationSerializer).deserializeArray("body", true);
    }

    @Test
    public void shouldRetrieveRecordedExpectations() {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockRequestDefinitionSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));

        // and - an expectation
        Expectation[] expectations = {};
        when(mockExpectationSerializer.deserializeArray("body", true)).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveRecordedExpectations(someRequestMatcher));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
        verify(mockExpectationSerializer).deserializeArray("body", true);
    }

    @Test
    public void shouldRetrieveExpectationsWithNullRequest() {
        // given
        Expectation[] expectations = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));
        when(mockExpectationSerializer.deserializeArray("body", true)).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveRecordedExpectations(null));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
        verify(mockExpectationSerializer).deserializeArray("body", true);
    }

    @Test
    public void shouldVerifyDoesNotMatchSingleRequestNoVerificationTimes() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
        when(mockVerificationSequenceSerializer.serialize(any(VerificationSequence.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest);

            // then
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest));
            verify(mockHttpClient).sendRequest(
                request()
                    .withHeader(HOST.toString(), "localhost:" + 1090)
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath("/mockserver/verifySequence")
                    .withBody("verification_json", StandardCharsets.UTF_8),
                20000,
                TimeUnit.MILLISECONDS,
                false
            );
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldVerifyDoesNotMatchMultipleRequestsNoVerificationTimes() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
        when(mockVerificationSequenceSerializer.serialize(any(VerificationSequence.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest, httpRequest);

            // then
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest, httpRequest));
            verify(mockHttpClient).sendRequest(
                request()
                    .withHeader(HOST.toString(), "localhost:" + 1090)
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath("/mockserver/verifySequence")
                    .withBody("verification_json", StandardCharsets.UTF_8),
                20000,
                TimeUnit.MILLISECONDS,
                false
            );
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldVerifyDoesMatchSingleRequestNoVerificationTimes() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody(""));
        when(mockVerificationSequenceSerializer.serialize(any(VerificationSequence.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest);

            // then
        } catch (AssertionError ae) {
            fail();
        }

        // then
        verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest));
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/verifySequence")
                .withBody("verification_json", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldVerifyDoesMatchSingleRequestOnce() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody(""));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest, once());

            // then
        } catch (AssertionError ae) {
            fail();
        }

        // then
        verify(mockVerificationSerializer).serialize(verification().withRequest(httpRequest).withTimes(once()));
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/verify")
                .withBody("verification_json", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldVerifyDoesNotMatchSingleRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest, atLeast(1));

            // then
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            verify(mockVerificationSerializer).serialize(verification().withRequest(httpRequest).withTimes(atLeast(1)));
            verify(mockHttpClient).sendRequest(
                request()
                    .withHeader(HOST.toString(), "localhost:" + 1090)
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath("/mockserver/verify")
                    .withBody("verification_json", StandardCharsets.UTF_8),
                20000,
                TimeUnit.MILLISECONDS,
                false
            );
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldHandleNullHttpRequest() {
        // when
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verify((RequestDefinition) null, VerificationTimes.exactly(2)));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString("verify(RequestDefinition, VerificationTimes) requires a non null RequestDefinition object"));
    }

    @Test
    public void shouldHandleNullVerificationTimes() {
        // when
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verify(request(), null));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString("verify(RequestDefinition, VerificationTimes) requires a non null VerificationTimes object"));
    }

    @Test
    public void shouldHandleNullHttpRequestSequence() {
        // when
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verify((HttpRequest) null));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString("verify(RequestDefinition...) requires a non-null non-empty array of RequestDefinition objects"));
    }

    @Test
    public void shouldHandleEmptyHttpRequestSequence() {
        // when
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verify(new RequestDefinition[0]));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString("verify(RequestDefinition...) requires a non-null non-empty array of RequestDefinition objects"));
    }

    @Test
    public void shouldHandleExplicitUnsecuredConnectionsToMockServer() {
        // given
        mockServerClient.withSecure(false);

        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));

        HttpResponse httpResponse =
            new HttpResponse()
                .withBody("some_response_body")
                .withHeaders(new Header("responseName", "responseValue"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpResponse);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpResponse, expectation.getHttpResponse());
        assertEquals(Times.unlimited(), expectation.getTimes());

        ArgumentCaptor<HttpRequest> configRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).sendRequest(configRequestCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        assertFalse(configRequestCaptor.getValue().isSecure());
    }

    @Test
    public void shouldHandleExplicitSecuredConnectionsToMockServer() {
        // given
        mockServerClient.withSecure(true);

        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));

        HttpResponse httpResponse =
            new HttpResponse()
                .withBody("some_response_body")
                .withHeaders(new Header("responseName", "responseValue"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpResponse);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpResponse, expectation.getHttpResponse());
        assertEquals(Times.unlimited(), expectation.getTimes());

        ArgumentCaptor<HttpRequest> configRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).sendRequest(configRequestCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        assertTrue(configRequestCaptor.getValue().isSecure());
    }

}
