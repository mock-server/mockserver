package org.mockserver.mockserver;

import com.google.common.base.Charsets;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerHandlerGeneralOperationsTest extends MockServerHandlerTest {

    @Test
    public void shouldResetExpectations() {
        // given
        HttpRequest request = request("/reset").withMethod("PUT");

        // when
        embeddedChannel.writeInbound(request);

        // then - filter and matcher is reset
        verify(mockRequestLogFilter).reset();
        verify(mockMockServerMatcher).reset();

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldClearExpectationsOnly() {
        // given
        HttpRequest request = request("/clear").withQueryStringParameter("type", "expectation").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - only matcher is cleared
        verifyNoMoreInteractions(mockRequestLogFilter);
        verify(mockMockServerMatcher).clear(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldClearLogsOnly() {
        // given
        HttpRequest request = request("/clear").withQueryStringParameter("type", "log").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - only filter is cleared
        verify(mockRequestLogFilter).clear(mockHttpRequest);
        verifyNoMoreInteractions(mockMockServerMatcher);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldClearExpectationsAndLogs() {
        // given
        HttpRequest request = request("/clear").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - filter and matcher is cleared
        verify(mockRequestLogFilter).clear(mockHttpRequest);
        verify(mockMockServerMatcher).clear(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldDumpExpectationsToLog() {
        // given
        HttpRequest request = request("/dumpToLog").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - expectations dumped to log
        verify(mockMockServerMatcher).dumpToLog(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldSetupExpectation() {
        // given
        HttpRequest request = request("/expectation").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockExpectationSerializer).deserialize("some_content");

        // and - expectation correctly setup
        verify(mockMockServerMatcher).when(any(HttpRequest.class), any(Times.class), any(TimeToLive.class));
        verify(mockExpectation).thenRespond(any(HttpResponse.class));
        verify(mockExpectation).thenForward(any(HttpForward.class));
        verify(mockExpectation).thenError(any(HttpError.class));
        verify(mockExpectation).thenCallback(any(HttpCallback.class));

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.CREATED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldAddSubjectAlternativeName() throws UnknownHostException {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();
        HttpRequest request = request("/expectation").withMethod("PUT").withBody("some_content");
        when(mockHttpRequest.getFirstHeader(HttpHeaders.Names.HOST)).thenReturn("somehostname");
        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByName("somehostname");
        } catch (UnknownHostException uhe) {
            // do nothing
        }

        // when
        embeddedChannel.writeInbound(request);

        // then
        if (inetAddress != null) {
            assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("localhost", inetAddress.getHostName(), inetAddress.getCanonicalHostName()));
        } else {
            assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("localhost", "somehostname"));
        }

        // cleanup
        embeddedChannel.readOutbound();
    }

    @Test
    public void shouldReturnRecordedRequests() {
        // given
        HttpRequest[] requests = {};
        when(mockRequestLogFilter.retrieve(mockHttpRequest)).thenReturn(requests);
        when(mockHttpRequestSerializer.serialize(requests)).thenReturn("requests");
        HttpRequest request = request("/retrieve").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - matching requests should be retrieved
        verify(mockRequestLogFilter).retrieve(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.OK.code()));
        assertThat(httpResponse.getBodyAsString(), is("requests"));
    }

    @Test
    public void shouldReturnSetupExpectationsRequests() {
        // given
        Expectation[] expectations = {};
        when(mockMockServerMatcher.retrieve(mockHttpRequest)).thenReturn(expectations);
        when(mockExpectationSerializer.serialize(expectations)).thenReturn("expectations");
        HttpRequest request = request("/retrieve").withQueryStringParameter("type", "expectation").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockHttpRequestSerializer).deserialize("some_content");

        // then - matching expectations should be retrieved
        verify(mockMockServerMatcher).retrieve(mockHttpRequest);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.OK.code()));
        assertThat(httpResponse.getBodyAsString(), is("expectations"));
    }

    @Test
    public void shouldReturnBadRequestAfterException() {
        // given
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");
        when(mockMockServerMatcher.handle(request)).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        embeddedChannel.writeInbound(request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.BAD_REQUEST.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldReturnNotFoundAfterNoMatch() {
        // given
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");
        when(mockMockServerMatcher.handle(request)).thenReturn(null);

        // when
        embeddedChannel.writeInbound(request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.NOT_FOUND.code()));
        assertThat(httpResponse.getBodyAsString(), nullValue());
    }

    @Test
    public void shouldActionResult() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.handle(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request))
                .thenReturn(
                        response()
                                .withStatusCode(HttpResponseStatus.PAYMENT_REQUIRED.code())
                                .withBody("some_content")

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(embeddedChannel.isOpen(), is(false));
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.PAYMENT_REQUIRED.code()));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("close"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldVerifyPassingRequest() {
        // given
        when(mockRequestLogFilter.verify(any(Verification.class))).thenReturn("");

        // and - a request
        HttpRequest request = request("/verify").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockVerificationSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockRequestLogFilter).verify(mockVerification);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldVerifyFailingRequest() {
        // given
        when(mockRequestLogFilter.verify(any(Verification.class))).thenReturn("failure response");

        // and - a request
        HttpRequest request = request("/verify").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockVerificationSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockRequestLogFilter).verify(mockVerification);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.NOT_ACCEPTABLE.code()));
        assertThat(httpResponse.getBodyAsString(), is("failure response"));
    }

    @Test
    public void shouldVerifySequencePassingRequest() {
        // given
        when(mockRequestLogFilter.verify(any(VerificationSequence.class))).thenReturn("");

        // and - a request
        HttpRequest request = request("/verifySequence").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockVerificationSequenceSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockRequestLogFilter).verify(mockVerificationSequence);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldVerifySequenceFailingRequest() {
        // given
        when(mockRequestLogFilter.verify(any(VerificationSequence.class))).thenReturn("failure response");

        // and - a request
        HttpRequest request = request("/verifySequence").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockVerificationSequenceSerializer).deserialize("some_content");

        // and - log filter called
        verify(mockRequestLogFilter).verify(mockVerificationSequence);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.NOT_ACCEPTABLE.code()));
        assertThat(httpResponse.getBodyAsString(), is("failure response"));
    }

    @Test
    public void shouldStopMockServer() {
        // given
        HttpRequest request = request("/stop").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - mock server is stopped
        verify(mockMockServer).stop();

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldGetStatus() {
        // given
        HttpRequest request = request("/status").withMethod("PUT");
        when(mockMockServer.getPorts()).thenReturn(Arrays.asList(1, 2, 3, 4, 5));

        // when
        embeddedChannel.writeInbound(request);

        // then - mock server is stopped
        verify(mockMockServer).getPorts();

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.OK.code()));
        assertThat(httpResponse.getBodyAsString(), is("" +
                "{" + System.getProperty("line.separator") +
                "  \"ports\" : [ 1, 2, 3, 4, 5 ]" + System.getProperty("line.separator") +
                "}"));
    }

    @Test
    public void shouldBindAdditionalPort() {
        // given
        HttpRequest request = request("/bind").withMethod("PUT").withBody("" +
                "{" + System.getProperty("line.separator") +
                "  \"ports\" : [ 1, 2, 3, 4, 5 ]" + System.getProperty("line.separator") +
                "}");
        when(mockMockServer.bindToPorts(anyList())).thenReturn(Arrays.asList(1, 2, 3, 4, 5));

        // when
        embeddedChannel.writeInbound(request);

        // then - mock server is stopped
        verify(mockMockServer).bindToPorts(Arrays.asList(1, 2, 3, 4, 5));

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.ACCEPTED.code()));
        assertThat(httpResponse.getBodyAsString(), is("" +
                "{" + System.getProperty("line.separator") +
                "  \"ports\" : [ 1, 2, 3, 4, 5 ]" + System.getProperty("line.separator") +
                "}"));
    }
}
