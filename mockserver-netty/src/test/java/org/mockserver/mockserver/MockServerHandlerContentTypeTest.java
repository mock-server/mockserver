package org.mockserver.mockserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;

/**
 * @author jamesdbloom
 */
public class MockServerHandlerContentTypeTest {

    // model objects
    @Mock
    private Expectation mockExpectation;
    @Mock
    private HttpRequest mockHttpRequest;
    @Mock
    private HttpResponse mockHttpResponse;
    @Mock
    private HttpForward mockHttpForward;
    @Mock
    private HttpError mockHttpError;
    @Mock
    private HttpCallback mockHttpCallback;
    @Mock
    private Verification mockVerification;
    @Mock
    private VerificationSequence mockVerificationSequence;
    // mockserver
    private RequestLogFilter mockRequestLogFilter;
    private MockServerMatcher mockMockServerMatcher;
    private MockServer mockMockServer;
    @Mock
    private ActionHandler mockActionHandler;
    // serializers
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationSequenceSerializer mockVerificationSequenceSerializer;
    // netty
    @Mock
    private ChannelHandlerContext mockChannelHandlerContext;

    @InjectMocks
    private MockServerHandler mockServerHandler;
    private EmbeddedChannel embeddedChannel;

    @Before
    public void setupFixture() {
        // given - a mock server handler
        mockRequestLogFilter = mock(RequestLogFilter.class);
        mockMockServerMatcher = mock(MockServerMatcher.class);
        mockMockServer = mock(MockServer.class);
        mockServerHandler = new MockServerHandler(mockMockServer, mockMockServerMatcher, mockRequestLogFilter);
        embeddedChannel = new EmbeddedChannel(mockServerHandler);

        initMocks(this);

        // given - serializers
        when(mockExpectationSerializer.deserialize(anyString())).thenReturn(mockExpectation);
        when(mockHttpRequestSerializer.deserialize(anyString())).thenReturn(mockHttpRequest);
        when(mockVerificationSerializer.deserialize(anyString())).thenReturn(mockVerification);
        when(mockVerificationSequenceSerializer.deserialize(anyString())).thenReturn(mockVerificationSequence);

        // given - an expectation that can be setup
        when(mockMockServerMatcher.when(any(HttpRequest.class), any(Times.class), any(TimeToLive.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenRespond(any(HttpResponse.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenForward(any(HttpForward.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenError(any(HttpError.class))).thenReturn(mockExpectation);
        when(mockExpectation.thenCallback(any(HttpCallback.class))).thenReturn(mockExpectation);

        // given - an expectation that has been setup
        when(mockExpectation.getHttpRequest()).thenReturn(mockHttpRequest);
        when(mockExpectation.getTimes()).thenReturn(Times.once());
        when(mockExpectation.getHttpResponse(anyBoolean())).thenReturn(mockHttpResponse);
        when(mockExpectation.getHttpForward()).thenReturn(mockHttpForward);
        when(mockExpectation.getHttpError()).thenReturn(mockHttpError);
        when(mockExpectation.getHttpCallback()).thenReturn(mockHttpCallback);
    }

    @After
    public void closeEmbeddedChanel() {
        assertThat(embeddedChannel.finish(), is(false));
    }

    @Test
    public void shouldReturnNoDefaultContentTypeWhenNoBodySpecified() {
        // given - a request & response
        HttpRequest request = request("/randomPath");
        HttpResponse response = response();

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response);

        // and - a action handler
        when(mockActionHandler.processAction(response, request)).thenReturn(response);

        // when
        embeddedChannel.writeInbound(request);

        // then
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Content-Type"), empty());
    }

    @Test
    public void shouldReturnContentTypeForStringBody() {
        // given - a request & response
        HttpRequest request = request("/randomPath");
        HttpResponse response = response().withBody("somebody");

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response);

        // and - a action handler
        when(mockActionHandler.processAction(response, request)).thenReturn(response);

        // when
        embeddedChannel.writeInbound(request);

        // then
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Content-Type"), containsInAnyOrder("text/plain"));
    }

    @Test
    public void shouldReturnContentTypeForJsonBody() {
        // given - a request & response
        HttpRequest request = request("/randomPath");
        HttpResponse response = response().withBody(json("somebody"));

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response);

        // and - a action handler
        when(mockActionHandler.processAction(response, request)).thenReturn(response);

        // when
        embeddedChannel.writeInbound(request);

        // then
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Content-Type"), containsInAnyOrder("application/json"));
    }

    @Test
    public void shouldReturnContentTypeForJsonSchemaBody() {
        // given - a request & response
        HttpRequest request = request("/randomPath");
        HttpResponse response = response().withBody(jsonSchema("somebody"));

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response);

        // and - a action handler
        when(mockActionHandler.processAction(response, request)).thenReturn(response);

        // when
        embeddedChannel.writeInbound(request);

        // then
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Content-Type"), containsInAnyOrder("application/json"));
    }

    @Test
    public void shouldReturnContentTypeForParameterBody() {
        // given - a request & response
        HttpRequest request = request("/randomPath");
        HttpResponse response = response().withBody(params(param("key", "value")));

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response);

        // and - a action handler
        when(mockActionHandler.processAction(response, request)).thenReturn(response);

        // when
        embeddedChannel.writeInbound(request);

        // then
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Content-Type"), containsInAnyOrder("application/x-www-form-urlencoded"));
    }

    @Test
    public void shouldReturnNoContentTypeForBodyWithNoAssociatedContentType() {
        // given - a request & response
        HttpRequest request = request("/randomPath");
        HttpResponse response = response().withBody(regex("some_value"));

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response);

        // and - a action handler
        when(mockActionHandler.processAction(response, request)).thenReturn(response);

        // when
        embeddedChannel.writeInbound(request);

        // then
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Content-Type"), empty());
    }

    @Test
    public void shouldNotSetDefaultContentTypeWhenContentTypeExplicitlySpecified() {
        // given - a request & response
        HttpRequest request = request("/randomPath");
        HttpResponse response = response()
                .withBody(json("somebody"))
                .withHeaders(new Header("Content-Type", "some/value"));

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response);

        // and - a action handler
        when(mockActionHandler.processAction(response, request)).thenReturn(response);

        // when
        embeddedChannel.writeInbound(request);

        // then
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Content-Type"), containsInAnyOrder("some/value"));
    }

}
