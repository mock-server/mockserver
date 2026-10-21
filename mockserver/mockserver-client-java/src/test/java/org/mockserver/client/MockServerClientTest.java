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
import org.mockserver.load.LoadProfile;
import org.mockserver.load.LoadScenario;
import org.mockserver.load.LoadStage;
import org.mockserver.load.LoadStep;
import org.mockserver.load.RampCurve;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ExpectationFilter;
import org.mockserver.mock.MockMode;
import org.mockserver.model.*;
import org.mockserver.serialization.*;
import org.mockserver.serialization.model.*;
import org.mockserver.slo.SloCriteria;
import org.mockserver.slo.SloObjective;
import org.mockserver.slo.SloVerdict;
import org.mockserver.slo.SloWindow;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;
import org.mockserver.version.Version;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_ACCEPTABLE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
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
import static org.junit.Assert.fail;

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
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    private HttpResponseSerializer mockHttpResponseSerializer;
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
        assertThat(authorizationHeader.contains(authorizationHeaderValue), is(true));
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
    public void shouldMockOpenIdProviderAndReturnExpectations() {
        // given
        Expectation discovery = new Expectation(request().withPath("/.well-known/openid-configuration"))
            .thenRespond(response().withBody("{}"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody("[ {} ]"));
        when(mockExpectationSerializer.deserializeArray("[ {} ]", true))
            .thenReturn(new Expectation[]{discovery});

        // when
        Expectation[] result = mockServerClient.mockOpenIdProvider(
            new org.mockserver.oidc.OidcProviderConfiguration().setIssuer("https://idp.test"));

        // then — round-trips and returns the deserialized expectations
        assertThat(result.length, is(1));
        assertThat(result[0], is(discovery));
        verify(mockHttpClient, atLeastOnce()).sendRequest(
            httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getAllValues().stream()
            .filter(r -> "/mockserver/oidc".equals(r.getPath().getValue()))
            .findFirst().orElseThrow(() -> new AssertionError("no PUT /mockserver/oidc was sent"));
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getBodyAsString(), containsString("https://idp.test"));
    }

    @Test
    public void shouldMockOpenIdProviderWithDefaultsSendsEmptyBody() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody(""));

        // when
        Expectation[] result = mockServerClient.mockOpenIdProvider();

        // then — empty body, empty result
        assertThat(result.length, is(0));
        verify(mockHttpClient, atLeastOnce()).sendRequest(
            httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getAllValues().stream()
            .filter(r -> "/mockserver/oidc".equals(r.getPath().getValue()))
            .findFirst().orElseThrow(() -> new AssertionError("no PUT /mockserver/oidc was sent"));
        assertThat(sent.getBodyAsString(), is(""));
    }

    @Test
    public void shouldImportExpectationsFromJson() {
        // given
        Expectation expectationOne = new Expectation(request().withPath("/some_path"), unlimited(), TimeToLive.unlimited(), 0)
            .thenRespond(response().withBody("some_body_one"));
        String expectationsJson = "[ { \"httpRequest\": { \"path\": \"/some_path\" } } ]";
        when(mockExpectationSerializer.deserializeArray(expectationsJson, false)).thenReturn(new Expectation[]{expectationOne});
        when(mockExpectationSerializer.serialize(expectationOne)).thenReturn("serialized_body");

        // when
        mockServerClient.importExpectations(expectationsJson);

        // then
        verify(mockExpectationSerializer).deserializeArray(expectationsJson, false);
        verify(mockHttpClient, atLeastOnce()).sendRequest(
            request()
                .withHeader(CONTENT_TYPE.toString(), APPLICATION_JSON_UTF_8.toString())
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withBody("serialized_body", UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldImportNoExpectationsForBlankJson() {
        // when
        Expectation[] imported = mockServerClient.importExpectations("   ");

        // then
        assertThat(imported.length, is(0));
        verifyNoInteractions(mockHttpClient);
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpResponse(), sameInstance(httpResponse));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpResponseTemplate(), sameInstance(template));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpResponseClassCallback(), sameInstance(httpClassCallback));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpForward(), sameInstance(httpForward));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpForwardTemplate(), sameInstance(template));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpForwardClassCallback(), sameInstance(httpClassCallback));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpForwardTemplate(), nullValue());
        assertThat(expectation.getHttpOverrideForwardedRequest(), is(new HttpOverrideForwardedRequest()
                                                                         .withRequestOverride(request().withBody("some_overridden_body"))
        ));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpError(), sameInstance(httpError));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
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
    public void shouldSendClearByMetadataRequest() {
        // when
        mockServerClient.clearByMetadata("ticket", 42);

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clear")
                .withQueryStringParameter("type", ClearType.EXPECTATIONS.name().toLowerCase())
                .withQueryStringParameter("metadataKey", "ticket")
                .withQueryStringParameter("metadataValue", "42")
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldRefuseAClearWithABlankOrNonSelectingFilterAndSendNothing() {
        for (String blank : java.util.Arrays.asList(null, "", " ")) {
            assertThrows(IllegalArgumentException.class, () -> mockServerClient.clearByMetadata(blank, "x"));
        }
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(null, (ExpectationFilter) null));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.clearByMetadata("owner", null));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.clearByMetadataKey(" "));
        for (String blank : java.util.Arrays.asList(null, "", " ")) {
            assertThrows(IllegalArgumentException.class, () -> mockServerClient.clearByNamespace(blank));
            assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(blank));
            assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(blank, ClearType.EXPECTATIONS));
            assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(org.mockserver.model.ExpectationId.expectationId(blank)));
        }
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear((org.mockserver.model.ExpectationId) null));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear((org.mockserver.model.ExpectationId) null, ClearType.ALL));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(null, ExpectationFilter.expectationFilter()));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(null, ExpectationFilter.expectationFilter().withUnmatched(false)));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(null, ExpectationFilter.expectationFilter().withUnmatched(true).withLimit(1)));

        verify(mockHttpClient, never()).sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean());
    }

    @Test
    public void shouldSendClearByMetadataKeyRequest() {
        // when
        mockServerClient.clearByMetadataKey("owner");

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clear")
                .withQueryStringParameter("type", ClearType.EXPECTATIONS.name().toLowerCase())
                .withQueryStringParameter("metadataKey", "owner")
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendClearUnmatchedExpectationsRequest() {
        // when
        mockServerClient.clearUnmatchedExpectations();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clear")
                .withQueryStringParameter("type", ClearType.EXPECTATIONS.name().toLowerCase())
                .withQueryStringParameter("unmatched", "true")
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendRetrieveActiveExpectationsWithFilterRequest() {
        // given
        Expectation[] expectations = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("body"));
        when(mockExpectationSerializer.deserializeArray("body", true)).thenReturn(expectations);

        // when
        assertThat(mockServerClient.retrieveActiveExpectations(null, ExpectationFilter.expectationFilter()
            .withMetadataJsonPath("$.tags[?(@ == 'smoke')]")
            .withUnmatched(true)
            .withOffset(10)
            .withLimit(5)), sameInstance(expectations));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withQueryStringParameter("metadataJsonPath", "$.tags[?(@ == 'smoke')]")
                .withQueryStringParameter("unmatched", "true")
                .withQueryStringParameter("limit", "5")
                .withQueryStringParameter("offset", "10")
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendRetrieveRecordedExpectationsPageRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody("[]"));

        // when
        assertThat(mockServerClient.retrieveRecordedExpectations(null, 20, 10).length, is(0));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withQueryStringParameter("limit", "10")
                .withQueryStringParameter("offset", "20")
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendClearByNamespaceRequest() {
        // when
        mockServerClient.clearByNamespace("team-a");

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clear")
                .withQueryStringParameter("type", ClearType.EXPECTATIONS.name().toLowerCase())
                .withQueryStringParameter("namespace", "team-a"),
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
        assertThat(mockServerClient.retrieveRecordedRequestsAndResponses(someRequestMatcher), sameInstance(httpRequests));

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
        assertThat(mockServerClient.retrieveRecordedRequestsAndResponses(null), sameInstance(httpRequests));

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
        assertThat(mockServerClient.retrieveActiveExpectations(someRequestMatcher), sameInstance(expectations));

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
    public void shouldRaiseAServerFailureFromEveryControlPlaneCallInsteadOfReturningItAsData() {
        // given - MockServer failed unexpectedly while handling the request
        String failure = "unexpected error processing request, see the MockServer log for correlation id: 1234";
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(500).withBody(failure));

        // then - callers that read the body as their result raise rather than return the failure text
        ClientException retrieveFailure = assertThrows(ClientException.class, () -> mockServerClient.retrieveActiveExpectations(request("/some_path"), Format.JSON));
        assertThat(retrieveFailure.getMessage(), containsString(failure));
        ClientException logsFailure = assertThrows(ClientException.class, () -> mockServerClient.retrieveLogsByCorrelationId("some-id"));
        assertThat(logsFailure.getMessage(), containsString(failure));
        ClientException configurationFailure = assertThrows(ClientException.class, () -> mockServerClient.retrieveConfiguration());
        assertThat(configurationFailure.getMessage(), containsString(failure));
    }

    @Test
    public void shouldRaiseAFailedRetrieveOfActiveExpectationsOrLogsInsteadOfReturningItAsData() {
        // given - a retrieve that could not reach every cluster peer
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(502).withBody("{\"error\":\"peer unreachable\"}"));

        // then
        ClientException retrieveFailure = assertThrows(ClientException.class, () -> mockServerClient.retrieveActiveExpectations(request("/some_path"), Format.JSON));
        assertThat(retrieveFailure.getMessage(), containsString("peer unreachable"));
        ClientException logsFailure = assertThrows(ClientException.class, () -> mockServerClient.retrieveLogsByCorrelationId("some-id"));
        assertThat(logsFailure.getMessage(), containsString("peer unreachable"));
    }

    @Test
    public void shouldStillRaiseAnInvalidRequestAsAnIllegalArgumentException() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(BAD_REQUEST.code()).withBody("incorrect request matcher json format"));

        // then
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class, () -> mockServerClient.retrieveLogsByCorrelationId("some-id"));
        assertThat(invalid.getMessage(), is("incorrect request matcher json format"));
    }

    @Test
    public void shouldRetrieveActiveExpectationsByNamespace() {
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
        assertThat(mockServerClient.retrieveActiveExpectations(someRequestMatcher, "team-a"), sameInstance(expectations));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withQueryStringParameter("namespace", "team-a")
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
        assertThat(mockServerClient.retrieveActiveExpectations(null), sameInstance(expectations));

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
        assertThat(mockServerClient.retrieveRecordedExpectations(someRequestMatcher), sameInstance(expectations));

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
        assertThat(mockServerClient.retrieveRecordedExpectations(null), sameInstance(expectations));

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
    public void shouldVerifyAllPassesWhenEveryVerificationPasses() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean())).thenReturn(response().withBody(""));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");

        // when
        mockServerClient.verifyAll(
            verification().withRequest(request().withPath("/one")).withTimes(once()),
            verification().withRequest(request().withPath("/two")).withTimes(once())
        );

        // then - one verify request sent per verification, no exception thrown
        verify(mockHttpClient, new AtLeast(2)).sendRequest(
            any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()
        );
    }

    @Test
    public void shouldVerifyAllCollectsEveryFailureInOneAssertionError() {
        // given - every verify request returns a distinct failure body
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withBody("FAILURE_ONE"))
            .thenReturn(response().withBody("FAILURE_TWO"));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");

        try {
            mockServerClient.verifyAll(
                verification().withRequest(request().withPath("/one")).withTimes(once()),
                verification().withRequest(request().withPath("/two")).withTimes(once())
            );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            // then - both failures appear in the single thrown message
            assertThat(ae.getMessage(), containsString("FAILURE_ONE"));
            assertThat(ae.getMessage(), containsString("FAILURE_TWO"));
        }
    }

    @Test
    public void shouldVerifyAllRejectNullArray() {
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verifyAll((Verification[]) null));
        assertThat(illegalArgumentException.getMessage(), containsString("verifyAll(Verification...) requires a non-null non-empty array of Verification objects"));
    }

    @Test
    public void shouldVerifyAllRejectEmptyArray() {
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verifyAll(new Verification[0]));
        assertThat(illegalArgumentException.getMessage(), containsString("verifyAll(Verification...) requires a non-null non-empty array of Verification objects"));
    }

    @Test
    public void shouldVerifyWithinTimeoutPassWhenRequestArrivesDuringWindow() {
        // given - first poll fails (request not yet recorded), second poll passes (empty body)
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withBody("Request not found"))
            .thenReturn(response().withBody(""));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");

        // when - eventual verify polls until it passes
        mockServerClient.verify(request().withPath("/some_path"), once(), java.time.Duration.ofSeconds(5));

        // then - at least two verify requests were sent (one failing, one passing)
        verify(mockHttpClient, new AtLeast(2)).sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean());
    }

    @Test
    public void shouldVerifyWithinTimeoutFailCleanlyWhenRequestNeverArrives() {
        // given - every poll fails (request never recorded)
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");

        try {
            mockServerClient.verify(request().withPath("/some_path"), once(), java.time.Duration.ofMillis(250));
            fail("expected AssertionError to be thrown after timeout");
        } catch (AssertionError ae) {
            // then - the last failure message is surfaced
            assertThat(ae.getMessage(), containsString("Request not found"));
        }
    }

    @Test
    public void shouldVerifyWithinTimeoutRejectNullDuration() {
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verify(request(), once(), (java.time.Duration) null));
        assertThat(illegalArgumentException.getMessage(), containsString("requires a non null non-negative Duration object"));
    }

    @Test
    public void shouldVerifyNeverPassWhenNoMatchingRequestArrivesInWindow() {
        // given - every poll fails to match (no matching request), so the window completes cleanly
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withBody("Request not found"));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");

        // when - negative-within-timeout returns normally because the verification never passed
        mockServerClient.verifyNever(request().withPath("/should_not_be_called"), java.time.Duration.ofMillis(250));

        // then - it polled at least once during the window
        verify(mockHttpClient, new AtLeast(1)).sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean());
    }

    @Test
    public void shouldVerifyNeverFailWhenMatchingRequestArrives() {
        // given - the verification passes (empty body) meaning a matching request was observed
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withBody(""));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");

        try {
            mockServerClient.verifyNever(request().withPath("/should_not_be_called"), java.time.Duration.ofSeconds(5));
            fail("expected AssertionError to be thrown when a matching request is observed");
        } catch (AssertionError ae) {
            // then - the window failed because a match was found
            assertThat(ae.getMessage(), containsString("window that was expected to find no match"));
        }
    }

    @Test
    public void shouldVerifyNeverFailWhenTheServerCannotBeReached() {
        // given - a failed attempt is an error, not "no match"
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenThrow(new RuntimeException("connection refused"));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");

        AssertionError assertionError = assertThrows(AssertionError.class, () -> mockServerClient.verifyNever(request().withPath("/should_not_be_called"), java.time.Duration.ofMillis(250)));

        assertThat(assertionError.getMessage(), containsString("connection refused"));
    }

    @Test
    public void shouldVerifyNeverRejectNullWindow() {
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verifyNever(request(), (java.time.Duration) null));
        assertThat(illegalArgumentException.getMessage(), containsString("requires a non null non-negative Duration object"));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpResponse(), sameInstance(httpResponse));
        assertThat(expectation.getTimes(), is(Times.unlimited()));

        ArgumentCaptor<HttpRequest> configRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).sendRequest(configRequestCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        assertThat(configRequestCaptor.getValue().isSecure(), is(false));
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
        assertThat(expectation.isActive(), is(true));
        assertThat(expectation.getHttpResponse(), sameInstance(httpResponse));
        assertThat(expectation.getTimes(), is(Times.unlimited()));

        ArgumentCaptor<HttpRequest> configRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).sendRequest(configRequestCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        assertThat(configRequestCaptor.getValue().isSecure(), is(true));
    }

    // -------------------------------------------------------------------
    // Clock Control
    // -------------------------------------------------------------------

    @Test
    public void shouldSendFreezeClockRequestWithInstant() {
        // when
        mockServerClient.freezeClock(java.time.Instant.parse("2025-01-15T09:30:00Z"));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clock")
                .withBody("{\"action\":\"freeze\",\"instant\":\"2025-01-15T09:30:00Z\"}", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendFreezeClockRequestWithoutInstant() {
        // when
        mockServerClient.freezeClock();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clock")
                .withBody("{\"action\":\"freeze\"}", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendAdvanceClockRequest() {
        // when
        mockServerClient.advanceClock(java.time.Duration.ofHours(1));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clock")
                .withBody("{\"action\":\"advance\",\"durationMillis\":3600000}", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendResetClockRequest() {
        // when
        mockServerClient.resetClock();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/clock")
                .withBody("{\"action\":\"reset\"}", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendClockStatusRequest() {
        // when
        mockServerClient.clockStatus();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("GET")
                .withPath("/mockserver/clock"),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    // -------------------------------------------------------------------
    // Service-scoped Chaos
    // -------------------------------------------------------------------

    @Test
    public void shouldSendSetServiceChaosRequest() {
        // when
        mockServerClient.setServiceChaos("payments.svc", HttpChaosProfile.httpChaosProfile().withErrorStatus(503).withErrorProbability(1.0));

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/serviceChaos"));
        assertThat(sent.getBodyAsString(), containsString("\"host\":\"payments.svc\""));
        assertThat(sent.getBodyAsString(), containsString("\"errorStatus\":503"));
        assertThat("no ttl when not requested", sent.getBodyAsString().contains("ttlMillis"), is(false));
    }

    @Test
    public void shouldSendSetServiceChaosRequestWithTtl() {
        // when
        mockServerClient.setServiceChaos("payments.svc", HttpChaosProfile.httpChaosProfile().withErrorStatus(503), 300000L);

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/serviceChaos"));
        assertThat(sent.getBodyAsString(), containsString("\"host\":\"payments.svc\""));
        assertThat(sent.getBodyAsString(), containsString("\"ttlMillis\":300000"));
    }

    @Test
    public void shouldSendRemoveServiceChaosRequest() {
        // when
        mockServerClient.removeServiceChaos("payments.svc");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/serviceChaos"));
        assertThat(sent.getBodyAsString(), containsString("\"host\":\"payments.svc\""));
        assertThat(sent.getBodyAsString(), containsString("\"remove\":true"));
    }

    @Test
    public void shouldSendClearServiceChaosRequest() {
        // when
        mockServerClient.clearServiceChaos();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath("/mockserver/serviceChaos")
                .withBody("{\"clear\":true}", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendServiceChaosStatusRequest() {
        // when
        mockServerClient.serviceChaosStatus();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("GET")
                .withPath("/mockserver/serviceChaos"),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    // -------------------------------------------------------------------
    // Load Scenario (load injection) Control-Plane
    // -------------------------------------------------------------------

    private static LoadScenario sampleLoadScenario() {
        return LoadScenario.loadScenario("smoke")
            .withProfile(LoadProfile.of(
                LoadStage.rampVus(0, 5, 5000L, RampCurve.LINEAR),
                LoadStage.constantVus(5, 10000L),
                LoadStage.pause(1000L),
                LoadStage.constantRate(2.0, 5000L)
            ))
            .withSteps(java.util.Collections.singletonList(
                LoadStep.loadStep(request().withMethod("GET").withPath("/api/health"))
            ));
    }

    @Test
    public void shouldSendRegisterLoadScenarioRequest() {
        // when
        mockServerClient.loadScenario(sampleLoadScenario());

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario"));
        assertThat(sent.getBodyAsString(), containsString("\"name\" : \"smoke\""));
        assertThat(sent.getBodyAsString(), containsString("/api/health"));
    }

    @Test
    public void shouldThrowErrorWhenRegisterLoadScenarioRejected() {
        // given (a 4xx other than 400/401, which sendRequest itself maps to dedicated exceptions)
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CONFLICT.code()).withBody("{\"error\":\"a load scenario named 'smoke' is already registered\"}"));

        // when
        ClientException clientException = assertThrows(ClientException.class, () -> mockServerClient.loadScenario(sampleLoadScenario()));

        // then
        assertThat(clientException.getMessage(), containsString("while registering load scenario"));
    }

    @Test
    public void shouldSendListLoadScenariosRequest() {
        // when
        mockServerClient.loadScenarios();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("GET")
                .withPath("/mockserver/loadScenario"),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendGetLoadScenarioByNameRequest() {
        // when
        mockServerClient.getLoadScenario("smoke");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("GET"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/smoke"));
    }

    @Test
    public void shouldSendDeleteLoadScenarioByNameRequest() {
        // when
        mockServerClient.deleteLoadScenario("smoke");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("DELETE"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/smoke"));
    }

    @Test
    public void shouldSendClearLoadScenariosRequest() {
        // when
        mockServerClient.clearLoadScenarios();

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("DELETE"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario"));
    }

    @Test
    public void shouldSendStartLoadScenariosRequest() {
        // when
        mockServerClient.startLoadScenarios("smoke", "soak");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/start"));
        assertThat(sent.getBodyAsString(), is("{\"names\":[\"smoke\",\"soak\"]}"));
    }

    @Test
    public void shouldThrowHelpfulErrorWhenLoadGenerationDisabledOnStart() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(FORBIDDEN.code()).withBody("{\"error\":\"load generation not enabled (set loadGenerationEnabled=true)\"}"));

        // when
        ClientException clientException = assertThrows(ClientException.class, () -> mockServerClient.startLoadScenarios("smoke"));

        // then
        assertThat(clientException.getMessage(), containsString("loadGenerationEnabled=true"));
    }

    @Test
    public void shouldThrowErrorWhenStartLoadScenarioUnknownName() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(NOT_FOUND.code()).withBody("{\"error\":\"no load scenario registered with name 'ghost'\"}"));

        // when
        ClientException clientException = assertThrows(ClientException.class, () -> mockServerClient.startLoadScenarios("ghost"));

        // then
        assertThat(clientException.getMessage(), containsString("while starting load scenarios"));
    }

    @Test
    public void shouldSendStopNamedLoadScenariosRequest() {
        // when
        mockServerClient.stopLoadScenarios("smoke");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/stop"));
        assertThat(sent.getBodyAsString(), is("{\"names\":[\"smoke\"]}"));
    }

    @Test
    public void shouldSendStopAllLoadScenariosRequest() {
        // when
        mockServerClient.stopLoadScenarios();

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/stop"));
        assertThat(sent.getBodyAsString(), is(""));
    }

    @Test
    public void shouldRunLoadScenarioRegisterThenStart() {
        // when
        mockServerClient.runLoadScenario(sampleLoadScenario());

        // then
        verify(mockHttpClient, times(2)).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        java.util.List<HttpRequest> sent = httpRequestArgumentCaptor.getAllValues();
        HttpRequest register = sent.get(0);
        assertThat(register.getMethod().getValue(), is("PUT"));
        assertThat(register.getPath().getValue(), is("/mockserver/loadScenario"));
        assertThat(register.getBodyAsString(), containsString("\"name\" : \"smoke\""));
        HttpRequest start = sent.get(1);
        assertThat(start.getMethod().getValue(), is("PUT"));
        assertThat(start.getPath().getValue(), is("/mockserver/loadScenario/start"));
        assertThat(start.getBodyAsString(), is("{\"names\":[\"smoke\"]}"));
    }

    @Test
    public void shouldSendGetLoadScenarioReportRequest() {
        // when
        mockServerClient.getLoadScenarioReport("smoke");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("GET"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/smoke/report"));
        assertThat(sent.getFirstQueryStringParameter("format"), is(""));
    }

    @Test
    public void shouldSendGetLoadScenarioReportRequestWithJunitFormat() {
        // when
        mockServerClient.getLoadScenarioReport("smoke", "junit");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("GET"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/smoke/report"));
        assertThat(sent.getFirstQueryStringParameter("format"), is("junit"));
    }

    @Test
    public void shouldSendGetLoadScenarioReportRequestWithoutFormatWhenBlank() {
        // when
        mockServerClient.getLoadScenarioReport("smoke", "");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("GET"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/smoke/report"));
        assertThat(sent.getFirstQueryStringParameter("format"), is(""));
    }

    @Test
    public void shouldSendGenerateLoadScenarioFromOpenAPIRequest() {
        // when
        mockServerClient.generateLoadScenarioFromOpenAPI("{\"name\":\"petstore-load\",\"specUrlOrPayload\":\"https://example.com/petstore.yaml\"}");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/generateFromOpenAPI"));
        assertThat(sent.getBodyAsString(), containsString("\"name\":\"petstore-load\""));
        assertThat(sent.getBodyAsString(), containsString("specUrlOrPayload"));
    }

    @Test
    public void shouldThrowErrorWhenGenerateLoadScenarioFromOpenAPIBadRequest() {
        // given (sendRequest itself maps 400 to IllegalArgumentException carrying the server body)
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(BAD_REQUEST.code()).withBody("{\"error\":\"spec has no operations\"}"));

        // when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> mockServerClient.generateLoadScenarioFromOpenAPI("{\"name\":\"x\"}"));

        // then
        assertThat(exception.getMessage(), containsString("spec has no operations"));
    }

    @Test
    public void shouldThrowErrorWhenGenerateLoadScenarioFromOpenAPIRejected() {
        // given (a 500, which sendRequest raises as a ClientException carrying the server body)
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(500).withBody("{\"error\":\"boom\"}"));

        // when
        ClientException clientException = assertThrows(ClientException.class, () -> mockServerClient.generateLoadScenarioFromOpenAPI("{\"name\":\"x\"}"));

        // then
        assertThat(clientException.getMessage(), containsString("boom"));
        assertThat(clientException.getMessage(), containsString("/mockserver/loadScenario/generateFromOpenAPI"));
    }

    @Test
    public void shouldSendGenerateLoadScenarioFromRecordingRequest() {
        // when
        mockServerClient.generateLoadScenarioFromRecording("{\"name\":\"replay-prod-traffic\",\"mode\":\"TEMPLATIZED\"}");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/loadScenario/generateFromRecording"));
        assertThat(sent.getBodyAsString(), containsString("\"name\":\"replay-prod-traffic\""));
        assertThat(sent.getBodyAsString(), containsString("TEMPLATIZED"));
    }

    @Test
    public void shouldThrowErrorWhenGenerateLoadScenarioFromRecordingBadRequest() {
        // given (sendRequest itself maps 400 to IllegalArgumentException carrying the server body).
        // 400 now means only that the request is malformed - an empty event log is a 409.
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(BAD_REQUEST.code()).withBody("{\"error\":\"invalid 'mode'\"}"));

        // when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> mockServerClient.generateLoadScenarioFromRecording("{\"name\":\"x\"}"));

        // then
        assertThat(exception.getMessage(), containsString("invalid 'mode'"));
    }

    @Test
    public void shouldThrowErrorWhenGenerateLoadScenarioFromRecordingHasNoRecordedTraffic() {
        // given - 409 when the event log is empty: the body was well-formed, only the state is missing
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CONFLICT.code()).withBody("{\"error\":\"no recorded requests to convert\"}"));

        // when
        ClientException exception = assertThrows(ClientException.class, () -> mockServerClient.generateLoadScenarioFromRecording("{\"name\":\"x\"}"));

        // then
        assertThat(exception.getMessage(), containsString("no recorded requests to convert"));
    }

    @Test
    public void shouldThrowErrorWhenGenerateLoadScenarioFromRecordingRejected() {
        // given (a 500, which sendRequest raises as a ClientException carrying the server body)
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(500).withBody("{\"error\":\"boom\"}"));

        // when
        ClientException clientException = assertThrows(ClientException.class, () -> mockServerClient.generateLoadScenarioFromRecording("{\"name\":\"x\"}"));

        // then
        assertThat(clientException.getMessage(), containsString("boom"));
        assertThat(clientException.getMessage(), containsString("/mockserver/loadScenario/generateFromRecording"));
    }

    // -------------------------------------------------------------------
    // AsyncAPI Control-Plane
    // -------------------------------------------------------------------

    @Test
    public void shouldSendLoadAsyncApiRequest() {
        // when
        mockServerClient.loadAsyncApi("{\"asyncapi\":\"2.6.0\"}");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/asyncapi"));
        assertThat(sent.getBodyAsString(), containsString("asyncapi"));
    }

    @Test
    public void shouldSendAsyncApiStatusRequest() {
        // when
        mockServerClient.asyncApiStatus();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1090)
                .withMethod("GET")
                .withPath("/mockserver/asyncapi"),
            20000,
            TimeUnit.MILLISECONDS,
            false
        );
    }

    @Test
    public void shouldSendVerifyAsyncMessageRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response()
                .withStatusCode(202)
                .withBody("")
            );

        // when
        mockServerClient.verifyAsyncMessage("{\"channel\":\"orders\"}");

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/asyncapi/verify"));
        assertThat(sent.getBodyAsString(), containsString("channel"));
    }

    @Test
    public void shouldThrowAssertionErrorOnVerifyAsyncMessageFailure() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response()
                .withStatusCode(406)
                .withBody("expected at least 1 message(s) matching channel 'orders' but found 0")
            );

        // when
        AssertionError error = assertThrows(AssertionError.class,
            () -> mockServerClient.verifyAsyncMessage("{\"channel\":\"orders\"}")
        );

        // then
        assertThat(error.getMessage(), containsString("expected at least 1"));
    }

    @Test
    public void shouldThrowIllegalArgumentForNullVerifyAsyncMessage() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.verifyAsyncMessage(null));
    }

    // -------------------------------------------------------------------
    // Replay
    // -------------------------------------------------------------------

    @Test
    public void shouldSendReplayRequest() {
        // given
        HttpRequest requestToReplay = request().withMethod("GET").withPath("/api/data");
        when(mockHttpRequestSerializer.serialize(requestToReplay)).thenReturn("{\"method\":\"GET\",\"path\":\"/api/data\"}");
        HttpResponse upstreamResponse = response().withStatusCode(200).withBody("ok");
        String serializedUpstreamResponse = "{\"statusCode\":200,\"body\":\"ok\"}";
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withBody(serializedUpstreamResponse));
        when(mockHttpResponseSerializer.deserialize(serializedUpstreamResponse)).thenReturn(upstreamResponse);

        // when
        HttpResponse result = mockServerClient.replay(requestToReplay);

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sentRequest = httpRequestArgumentCaptor.getValue();
        assertThat(sentRequest.getMethod().getValue(), is("PUT"));
        assertThat(sentRequest.getPath().getValue(), is("/mockserver/replay"));
        assertThat(sentRequest.getBodyAsString(), is("{\"method\":\"GET\",\"path\":\"/api/data\"}"));
        assertThat(result, is(upstreamResponse));
        verify(mockHttpResponseSerializer).deserialize(serializedUpstreamResponse);
    }

    @Test
    public void shouldThrowIllegalArgumentForNullReplayRequest() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.replay(null));
    }

    // -------------------------------------------------------------------
    // Contract testing & Pact helpers
    // -------------------------------------------------------------------

    @Test
    public void shouldSendContractTestRequestAndParseReport() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response()
                .withStatusCode(200)
                .withBody("{\"baseUrl\":\"http://localhost:8080\",\"totalOperations\":1,\"passed\":1,\"failed\":0,\"allPassed\":true," +
                    "\"results\":[{\"operationId\":\"listPets\",\"method\":\"GET\",\"path\":\"/pets\",\"statusCodeReceived\":200,\"passed\":true,\"validationErrors\":[]}]}"));

        // when
        MockServerClient.ContractReport report = mockServerClient.contractTest("{\"openapi\":\"3.0.0\"}", "http://localhost:8080", "listPets");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/contractTest"));
        assertThat(sent.getBodyAsString(), containsString("\"baseUrl\":\"http://localhost:8080\""));
        assertThat(sent.getBodyAsString(), containsString("\"operationId\":\"listPets\""));
        assertThat(report.total(), is(1));
        assertThat(report.passed(), is(1));
        assertThat(report.allPassed(), is(true));
        assertThat(report.results().size(), is(1));
        assertThat(report.results().get(0).operationId(), is("listPets"));
        assertThat(report.results().get(0).statusCode(), is(200));
        assertThat(report.results().get(0).passed(), is(true));
    }

    @Test
    public void shouldRejectContractTestWithBlankSpecOrBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.contractTest(" ", "http://localhost:8080"));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.contractTest("{}", " "));
    }

    @Test
    public void shouldSendTrafficValidateRequestAndParseReport() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response()
                .withStatusCode(200)
                .withBody("{\"totalRequests\":1,\"passed\":0,\"failed\":1,\"allPassed\":false," +
                    "\"results\":[{\"method\":\"GET\",\"path\":\"/pets\",\"matchedOperation\":\"GET /pets\",\"passed\":false," +
                    "\"requestErrors\":[],\"responseErrors\":[\"body does not match schema\"]}]}"));

        // when
        MockServerClient.ContractReport report = mockServerClient.trafficValidate("org/mockserver/openapi/openapi_petstore_example.json");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/trafficValidate"));
        assertThat(sent.getBodyAsString(), containsString("\"spec\""));
        assertThat(report.total(), is(1));
        assertThat(report.allPassed(), is(false));
        assertThat(report.failed(), is(1));
        assertThat(report.results().get(0).matchedOperation(), is("GET /pets"));
        assertThat(report.results().get(0).responseErrors(), hasItem("body does not match schema"));
    }

    @Test
    public void shouldRejectTrafficValidateWithBlankSpec() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.trafficValidate(" "));
    }

    @Test
    public void shouldSendPactImportRequestToImportEndpoint() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody("[]"));

        // when
        String result = mockServerClient.pactImport("{\"consumer\":{\"name\":\"c\"}}");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/pact/import"));
        assertThat(result, is("[]"));
    }

    @Test
    public void shouldSendPactExportRequestWithConsumerAndProviderQueryParams() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(200).withBody("{\"consumer\":{\"name\":\"My Consumer\"}}"));

        // when
        String result = mockServerClient.pactExport("My Consumer", "My Provider");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/pact"));
        assertThat(sent.getFirstQueryStringParameter("consumer"), is("My Consumer"));
        assertThat(sent.getFirstQueryStringParameter("provider"), is("My Provider"));
        assertThat(result, containsString("My Consumer"));
    }

    @Test
    public void shouldSendPactExportRequestWithNoQueryParamsWhenConsumerAndProviderBlank() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(200).withBody("{}"));

        // when
        mockServerClient.pactExport(null, null);

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getPath().getValue(), is("/mockserver/pact"));
        assertThat(sent.getQueryStringParameters() == null || sent.getQueryStringParameters().getEntries().isEmpty(), is(true));
    }

    @Test
    public void shouldSendPactVerifyRequestToVerifyEndpoint() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(202).withBody("{\"verified\":true}"));

        // when
        String result = mockServerClient.pactVerify("{\"consumer\":{\"name\":\"c\"}}");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/pact/verify"));
        assertThat(result, containsString("verified"));
    }

    @Test
    public void shouldReturnReportBodyWhenPactVerifyFailsWith406() {
        // given - a FAIL verdict: the server replies 406 NOT_ACCEPTABLE with the verification report
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(406).withBody("{\"verified\":false,\"failures\":[\"no matching expectation\"]}"));

        // when - the helper must NOT throw; it must return the report body
        String result = mockServerClient.pactVerify("{\"consumer\":{\"name\":\"c\"}}");

        // then
        assertThat(result, containsString("\"verified\":false"));
        assertThat(result, containsString("no matching expectation"));
    }

    @Test
    public void shouldRejectBlankPactImportAndVerify() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.pactImport(" "));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.pactVerify(" "));
    }

    // -------------------------------------------------------------------
    // Metrics (Prometheus exposition)
    // -------------------------------------------------------------------

    @Test
    public void shouldSendScrapeMetricsRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody("# HELP foo\nfoo 1\n"));

        // when
        String result = mockServerClient.scrapeMetrics();

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("GET"));
        assertThat(sent.getPath().getValue(), is("/mockserver/metrics"));
        assertThat(result, containsString("foo 1"));
    }

    // -------------------------------------------------------------------
    // Drift detection
    // -------------------------------------------------------------------

    @Test
    public void shouldSendRetrieveDriftRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody("{\"count\":1,\"drifts\":[{\"path\":\"/foo\"}]}"));

        // when
        String result = mockServerClient.retrieveDrift();

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("GET"));
        assertThat(sent.getPath().getValue(), is("/mockserver/drift"));
        assertThat(result, containsString("\"count\":1"));
        assertThat(result, containsString("\"drifts\""));
    }

    @Test
    public void shouldSendClearDriftRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody("{\"status\":\"cleared\"}"));

        // when
        mockServerClient.clearDrift();

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/drift/clear"));
    }

    // -------------------------------------------------------------------
    // File store
    // -------------------------------------------------------------------

    @Test
    public void shouldSendStoreFileTextRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody("{\"name\":\"a.txt\",\"size\":5}"));

        // when
        mockServerClient.storeFile("a.txt", "hello");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/files/store"));
        assertThat(sent.getBodyAsString(), containsString("\"name\":\"a.txt\""));
        assertThat(sent.getBodyAsString(), containsString("\"content\":\"hello\""));
        assertThat(sent.getBodyAsString(), not(containsString("base64")));
    }

    @Test
    public void shouldSendStoreFileBinaryRequestBase64Encoded() {
        // given
        byte[] content = new byte[]{1, 2, 3, 4};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody("{\"name\":\"a.bin\",\"size\":4}"));

        // when
        mockServerClient.storeFile("a.bin", content);

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/files/store"));
        assertThat(sent.getBodyAsString(), containsString("\"name\":\"a.bin\""));
        assertThat(sent.getBodyAsString(), containsString("\"base64\":true"));
        assertThat(sent.getBodyAsString(), containsString("\"content\":\"" + Base64.getEncoder().encodeToString(content) + "\""));
    }

    @Test
    public void shouldSendRetrieveFileRequestAndReturnRawBytes() {
        // given
        byte[] content = new byte[]{9, 8, 7};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody(new BinaryBody(content)));

        // when
        byte[] result = mockServerClient.retrieveFile("a.bin");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/files/retrieve"));
        assertThat(sent.getBodyAsString(), containsString("\"name\":\"a.bin\""));
        assertThat(result, is(content));
    }

    @Test
    public void shouldThrowWhenRetrieveFileNotFound() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(404).withBody("file not found: missing.bin"));

        // then
        assertThrows(ClientException.class, () -> mockServerClient.retrieveFile("missing.bin"));
    }

    @Test
    public void shouldSendListFilesRequestAndParseNames() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody("[ \"a.txt\", \"b.bin\" ]"));

        // when
        Set<String> result = mockServerClient.listFiles();

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/files/list"));
        assertThat(result, hasItems("a.txt", "b.bin"));
        assertThat(result, hasSize(2));
    }

    @Test
    public void shouldSendDeleteFileRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()));

        // when
        mockServerClient.deleteFile("a.txt");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/files/delete"));
        assertThat(sent.getBodyAsString(), containsString("\"name\":\"a.txt\""));
    }

    @Test
    public void shouldThrowWhenDeleteFileNotFound() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(404).withBody("file not found: missing.txt"));

        // then
        assertThrows(ClientException.class, () -> mockServerClient.deleteFile("missing.txt"));
    }

    @Test
    public void shouldRejectBlankFileNames() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.storeFile(" ", "x"));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.storeFile(" ", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.retrieveFile(" "));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.deleteFile(" "));
    }

    // -------------------------------------------------------------------
    // Import (HAR / Postman collection)
    // -------------------------------------------------------------------

    @Test
    public void shouldSendImportHarRequestWithFormatQueryParam() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody("[]"));

        // when
        Expectation[] result = mockServerClient.importHar("{\"log\":{\"entries\":[]}}");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/import"));
        assertThat(sent.getFirstQueryStringParameter("format"), is("har"));
        assertThat(result == null || result.length == 0, is(true));
    }

    @Test
    public void shouldSendImportPostmanCollectionRequestWithFormatQueryParam() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody("[]"));

        // when
        mockServerClient.importPostmanCollection("{\"info\":{},\"item\":[]}");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/import"));
        assertThat(sent.getFirstQueryStringParameter("format"), is("postman"));
    }

    @Test
    public void shouldSendImportDocumentRequestWithoutFormatWhenBlankForAutoDetect() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody("[]"));

        // when
        mockServerClient.importDocument("{\"log\":{\"entries\":[]}}", null);

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getPath().getValue(), is("/mockserver/import"));
        assertThat(sent.getQueryStringParameters() == null || sent.getQueryStringParameters().getEntries().isEmpty(), is(true));
    }

    @Test
    public void shouldRejectBlankImportDocument() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.importHar(" "));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.importPostmanCollection(" "));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.importDocument(" ", "har"));
    }

    // -------------------------------------------------------------------
    // Operating mode
    // -------------------------------------------------------------------

    @Test
    public void shouldSendSetModeRequestWithModeQueryParam() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody("{\"mode\":\"SPY\",\"proxyUnmatchedRequests\":true}"));

        // when
        mockServerClient.setMode(MockMode.SPY);

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/mode"));
        assertThat(sent.getFirstQueryStringParameter("mode"), is("SPY"));
    }

    @Test
    public void shouldSendRetrieveModeRequestAndParseMode() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody("{\"mode\":\"CAPTURE\",\"proxyUnmatchedRequests\":true}"));

        // when
        MockMode result = mockServerClient.retrieveMode();

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("GET"));
        assertThat(sent.getPath().getValue(), is("/mockserver/mode"));
        assertThat(result, is(MockMode.CAPTURE));
    }

    @Test
    public void shouldRejectNullMode() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.setMode(null));
    }

    // -------------------------------------------------------------------
    // WSDL
    // -------------------------------------------------------------------

    @Test
    public void shouldSendWsdlExpectationRequestWithXmlBody() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody("[]"));

        // when
        Expectation[] result = mockServerClient.wsdlExpectation("<definitions/>");

        // then
        verify(mockHttpClient, atLeastOnce()).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/wsdl"));
        assertThat(sent.getBodyAsString(), is("<definitions/>"));
        assertThat(result == null || result.length == 0, is(true));
    }

    @Test
    public void shouldRejectBlankWsdl() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.wsdlExpectation(" "));
    }

    // SRE control-plane: verifySLO

    private static SloCriteria sampleSloCriteria() {
        return SloCriteria.sloCriteria()
            .withName("checkout-latency")
            .withWindow(SloWindow.lookback(60000L))
            .withMinimumSampleCount(10)
            .withObjectives(
                SloObjective.sloObjective()
                    .withSli(SloObjective.Sli.LATENCY_P99)
                    .withComparator(SloObjective.Comparator.LESS_THAN)
                    .withThreshold(250.0)
            );
    }

    private static String verdictJson(String result) {
        return "{\"name\":\"checkout-latency\",\"result\":\"" + result + "\",\"sampleCount\":42,"
            + "\"windowFromEpochMillis\":1000,\"windowToEpochMillis\":61000,"
            + "\"objectiveResults\":[{\"sli\":\"LATENCY_P99\",\"comparator\":\"LESS_THAN\",\"threshold\":250.0,"
            + "\"observedValue\":120.0,\"result\":\"" + result + "\",\"detail\":\"ok\"}]}";
    }

    @Test
    public void shouldSendVerifySloRequestAndReturnPassVerdict() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody(verdictJson("PASS")));

        // when
        SloVerdict verdict = mockServerClient.verifySLO(sampleSloCriteria());

        // then - correct endpoint + serialized payload
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/verifySLO"));
        assertThat(sent.getBodyAsString(), containsString("\"name\" : \"checkout-latency\""));
        assertThat(sent.getBodyAsString(), containsString("LATENCY_P99"));
        // and - parsed verdict
        assertThat(verdict.getResult(), is(SloVerdict.Result.PASS));
        assertThat(verdict.getName(), is("checkout-latency"));
        assertThat(verdict.getSampleCount(), is(42L));
        assertThat(verdict.getObjectiveResults(), hasSize(1));
        assertThat(verdict.getObjectiveResults().get(0).getSli(), is(SloObjective.Sli.LATENCY_P99));
    }

    @Test
    public void shouldReturnInconclusiveVerdictAsValue() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody(verdictJson("INCONCLUSIVE")));

        // when
        SloVerdict verdict = mockServerClient.verifySLO(sampleSloCriteria());

        // then
        assertThat(verdict.getResult(), is(SloVerdict.Result.INCONCLUSIVE));
    }

    @Test
    public void shouldThrowAssertionErrorWhenSloVerdictFails() {
        // given - 406 NOT_ACCEPTABLE signals a FAIL verdict
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(NOT_ACCEPTABLE.code()).withBody(verdictJson("FAIL")));

        // when
        AssertionError assertionError = assertThrows(AssertionError.class, () -> mockServerClient.verifySLO(sampleSloCriteria()));

        // then - catchable as AssertionError (same as verify(...)), carrying the verdict body
        assertThat(assertionError.getMessage(), containsString("\"result\":\"FAIL\""));
        assertThat(assertionError, instanceOf(MockServerClient.SloVerdictAssertionError.class));
        SloVerdict verdict = ((MockServerClient.SloVerdictAssertionError) assertionError).getVerdict();
        assertThat(verdict, is(notNullValue()));
        assertThat(verdict.getResult(), is(SloVerdict.Result.FAIL));
    }

    @Test
    public void shouldSurface403AsIllegalStateWhenSloTrackingDisabled() {
        // given - the server returns 403 when sloTrackingEnabled=false. This used to be a 400, which
        // it shared with malformed criteria, so a caller could not tell a typo in its criteria from a
        // server with the feature switched off.
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(FORBIDDEN.code()).withBody("{\"error\":\"SLO tracking not enabled (set sloTrackingEnabled=true)\"}"));

        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> mockServerClient.verifySLO(sampleSloCriteria()));

        // then
        assertThat(exception.getMessage(), containsString("sloTrackingEnabled=true"));
    }

    @Test
    public void shouldSurface400AsIllegalArgumentForMalformedSloCriteria() {
        // given - 400 now means only that the criteria are malformed; sendRequest maps it to
        // IllegalArgumentException, the common client convention for every 400
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(BAD_REQUEST.code()).withBody("{\"error\":\"objectives must be an array\"}"));

        // when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> mockServerClient.verifySLO(sampleSloCriteria()));

        // then
        assertThat(exception.getMessage(), containsString("objectives must be an array"));
    }

    @Test
    public void shouldRejectNullSloCriteria() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.verifySLO(null));
    }

    // SRE control-plane: startChaosExperiment

    private static final String SAMPLE_EXPERIMENT_JSON =
        "{\"name\":\"flaky-upstream\",\"loop\":false,\"stages\":[{\"durationMillis\":30000,\"profiles\":{\"api.example.com\":{\"latency\":{\"fixedDelayMillis\":500}}}}]}";

    @Test
    public void shouldSendStartChaosExperimentRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(OK.code()).withBody("{\"status\":\"started\",\"name\":\"flaky-upstream\",\"stages\":1,\"loop\":false}"));

        // when
        String result = mockServerClient.startChaosExperiment(SAMPLE_EXPERIMENT_JSON);

        // then
        verify(mockHttpClient).sendRequest(httpRequestArgumentCaptor.capture(), anyLong(), any(TimeUnit.class), anyBoolean());
        HttpRequest sent = httpRequestArgumentCaptor.getValue();
        assertThat(sent.getMethod().getValue(), is("PUT"));
        assertThat(sent.getPath().getValue(), is("/mockserver/chaosExperiment"));
        assertThat(sent.getBodyAsString(), is(SAMPLE_EXPERIMENT_JSON));
        assertThat(result, containsString("\"status\":\"started\""));
    }

    @Test
    public void shouldThrowHelpfulErrorWhenForbiddenOnStartExperiment() {
        // given - 403 FORBIDDEN (control-plane authorization denied)
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(FORBIDDEN.code()).withBody("{\"error\":\"Forbidden for control plane\"}"));

        // when
        ClientException clientException = assertThrows(ClientException.class, () -> mockServerClient.startChaosExperiment(SAMPLE_EXPERIMENT_JSON));

        // then
        assertThat(clientException.getMessage(), containsString("forbidden while starting chaos experiment"));
        assertThat(clientException.getMessage(), containsString("Forbidden for control plane"));
    }

    @Test
    public void shouldSurface400AsIllegalArgumentWhenChaosExperimentInvalid() {
        // given - server returns 400 for a malformed experiment; sendRequest maps 400 -> IllegalArgumentException
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(BAD_REQUEST.code()).withBody("{\"error\":\"invalid experiment definition: stages is required\"}"));

        // when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> mockServerClient.startChaosExperiment(SAMPLE_EXPERIMENT_JSON));

        // then
        assertThat(exception.getMessage(), containsString("invalid experiment definition"));
    }

    @Test
    public void shouldRejectBlankChaosExperiment() {
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.startChaosExperiment(" "));
        assertThrows(IllegalArgumentException.class, () -> mockServerClient.startChaosExperiment(null));
    }

}
