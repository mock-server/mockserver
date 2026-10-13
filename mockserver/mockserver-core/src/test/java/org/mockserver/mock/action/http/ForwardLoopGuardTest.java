package org.mockserver.mock.action.http;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.filters.TransportHeaderFilter;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.mock.action.http.ForwardLoopGuard.HEADER_NAME;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpTemplate.template;

public class ForwardLoopGuardTest {

    private static final String THIS_INSTANCE = "MockServer_this";
    private static final String OTHER_INSTANCE = "MockServer_other";

    private static ForwardLoopGuard guard(int maxHops) {
        return new ForwardLoopGuard(new Configuration().forwardLoopMaxHops(maxHops), THIS_INSTANCE);
    }

    @Test
    public void shouldCountOnlyThisInstancesEntries() {
        HttpRequest received = request().withHeader(HEADER_NAME, THIS_INSTANCE, OTHER_INSTANCE, THIS_INSTANCE, OTHER_INSTANCE);

        assertThat(guard(10).hopsThroughThisInstance(received), is(2));
        assertThat(guard(10).hopsThroughThisInstance(request()), is(0));
        assertThat(guard(10).hopsThroughThisInstance(null), is(0));
    }

    @Test
    public void shouldCountEntriesAnIntermediaryFoldedIntoOneLine() {
        HttpRequest received = request().withHeader(HEADER_NAME, THIS_INSTANCE + ", " + OTHER_INSTANCE + "," + THIS_INSTANCE);

        assertThat(guard(10).hopsThroughThisInstance(received), is(2));
    }

    @Test
    public void shouldNotCountAnEntryThatOnlyContainsThisInstancesId() {
        HttpRequest received = request().withHeader(HEADER_NAME, THIS_INSTANCE + "_suffix", "prefix_" + THIS_INSTANCE);

        assertThat(guard(10).hopsThroughThisInstance(received), is(0));
    }

    @Test
    public void shouldLetARequestBeForwardedUntilItHasPassedTheLimit() {
        assertThat(guard(2).loopDetectedResponse(request().withHeader(HEADER_NAME, THIS_INSTANCE)), nullValue());

        HttpResponse refusal = guard(2).loopDetectedResponse(request().withHeader(HEADER_NAME, THIS_INSTANCE, OTHER_INSTANCE, THIS_INSTANCE));

        assertThat(refusal.getStatusCode(), is(508));
        assertThat(refusal.getReasonPhrase(), is("Loop Detected"));
        assertThat(refusal.getBodyAsString(), is("MockServer did not forward this request because it has already forwarded it 2 times, so the forward's target leads back to this MockServer, where the request matched a forward again: check the forward's host and port, or raise forwardLoopMaxHops if the repeated forwarding is intended"));
    }

    @Test
    public void shouldNotCountOtherInstancesTowardsTheLimit() {
        assertThat(guard(2).loopDetectedResponse(request().withHeader(HEADER_NAME, OTHER_INSTANCE, OTHER_INSTANCE, OTHER_INSTANCE, THIS_INSTANCE)), nullValue());
    }

    @Test
    public void shouldNeitherRefuseNorMarkWhenLoopDetectionIsOff() {
        HttpRequest received = request().withHeader(HEADER_NAME, THIS_INSTANCE, THIS_INSTANCE, THIS_INSTANCE);
        HttpRequest toSend = request("/upstream");

        assertThat(guard(0).loopDetectedResponse(received), nullValue());
        assertThat(guard(0).markForwarded(received, toSend).getHeader(HEADER_NAME), is(empty()));
        assertThat(guard(-1).loopDetectedResponse(received), nullValue());
    }

    @Test
    public void shouldNeitherRefuseNorMarkWithoutAnInstanceId() {
        ForwardLoopGuard noInstance = new ForwardLoopGuard(new Configuration().forwardLoopMaxHops(1), null);
        HttpRequest received = request().withHeader(HEADER_NAME, THIS_INSTANCE);

        assertThat(noInstance.loopDetectedResponse(received), nullValue());
        assertThat(noInstance.markForwarded(received, request()).getHeader(HEADER_NAME), is(empty()));
    }

    @Test
    public void shouldCarryTheReceivedEntriesAndAppendThisInstance() {
        HttpRequest received = request().withHeader(HEADER_NAME, OTHER_INSTANCE, THIS_INSTANCE);

        HttpRequest marked = guard(10).markForwarded(received, request("/upstream"));

        assertThat(marked.getHeader(HEADER_NAME), contains(OTHER_INSTANCE, THIS_INSTANCE, THIS_INSTANCE));
    }

    @Test
    public void shouldReplaceEntriesTheForwardedRequestWasBuiltWith() {
        HttpRequest received = request().withHeader(HEADER_NAME, THIS_INSTANCE);

        HttpRequest marked = guard(10).markForwarded(received, request("/upstream").withHeader(HEADER_NAME.toUpperCase(), "reset-by-a-template"));

        assertThat(marked.getHeader(HEADER_NAME), contains(THIS_INSTANCE, THIS_INSTANCE));
    }

    @Test
    public void shouldKeepTheHeaderOutOfRecordedExpectations() {
        assertThat(TransportHeaderFilter.isTransportHeader(HEADER_NAME), is(true));
        HttpRequest recorded = request("/recorded").withHeader(HEADER_NAME, THIS_INSTANCE).withHeader("x-kept", "value");

        assertThat(TransportHeaderFilter.withoutTransportHeaders(recorded).getHeader(HEADER_NAME), is(empty()));
        assertThat(TransportHeaderFilter.withoutTransportHeaders(recorded).getHeader("x-kept"), contains("value"));
    }

    @Test
    public void shouldSendAForwardWithThisInstanceAppendedWithoutChangingTheRecordedRequest() {
        NettyHttpClient httpClient = mock(NettyHttpClient.class);
        CompletableFuture<HttpResponse> upstream = new CompletableFuture<>();
        when(httpClient.sendRequest(any(HttpRequest.class), nullable(InetSocketAddress.class))).thenReturn(upstream);
        HttpForwardActionHandler handler = new HttpForwardActionHandler(mock(MockServerLogger.class), new Configuration(), httpClient);
        handler.setForwardLoopGuard(guard(10));
        HttpRequest received = request("/forwarded").withHeader(HEADER_NAME, OTHER_INSTANCE);

        HttpForwardActionResult result = handler.handle(forward().withHost("127.0.0.1").withPort(1080), received);

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendRequest(sent.capture(), nullable(InetSocketAddress.class));
        assertThat(sent.getValue().getHeader(HEADER_NAME), contains(OTHER_INSTANCE, THIS_INSTANCE));
        assertThat(result.getHttpResponse(), sameInstance(upstream));
        assertThat(result.getHttpRequest().getHeader(HEADER_NAME), contains(OTHER_INSTANCE));
    }

    @Test
    public void shouldCarryTheCountOnARequestATemplateBuiltAfresh() {
        NettyHttpClient httpClient = mock(NettyHttpClient.class);
        when(httpClient.sendRequest(any(HttpRequest.class), nullable(InetSocketAddress.class))).thenReturn(new CompletableFuture<>());
        HttpForwardTemplateActionHandler handler = new HttpForwardTemplateActionHandler(mock(MockServerLogger.class), new Configuration(), httpClient);
        handler.setForwardLoopGuard(guard(10));
        HttpTemplate freshRequest = template(HttpTemplate.TemplateType.VELOCITY, "{ 'path': '/templated', 'headers': { 'Host': [ '127.0.0.1:1080' ] } }");

        handler.handle(freshRequest, request("/received").withHeader(HEADER_NAME, THIS_INSTANCE));

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendRequest(sent.capture(), nullable(InetSocketAddress.class));
        assertThat(sent.getValue().getPath().getValue(), is("/templated"));
        assertThat(sent.getValue().getHeader(HEADER_NAME), contains(THIS_INSTANCE, THIS_INSTANCE));
    }

    @Test
    public void shouldAnswerWith508WithoutConnectingOnceTheLimitIsReached() throws Exception {
        NettyHttpClient httpClient = mock(NettyHttpClient.class);
        HttpForwardActionHandler handler = new HttpForwardActionHandler(mock(MockServerLogger.class), new Configuration(), httpClient);
        handler.setForwardLoopGuard(guard(2));

        HttpForwardActionResult result = handler.handle(forward().withHost("127.0.0.1").withPort(1080).withScheme(HttpForward.Scheme.HTTP), request("/loop").withHeader(HEADER_NAME, THIS_INSTANCE, THIS_INSTANCE));

        assertThat(result.getHttpResponse().get().getStatusCode(), is(508));
        verify(httpClient, never()).sendRequest(any(HttpRequest.class), nullable(InetSocketAddress.class));
        verify(httpClient, never()).sendRequest(any(HttpRequest.class), nullable(InetSocketAddress.class), nullable(Long.class), anyBoolean());
    }
}
