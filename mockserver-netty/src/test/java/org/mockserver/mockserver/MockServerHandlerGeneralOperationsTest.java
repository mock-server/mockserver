package org.mockserver.mockserver;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerHandlerGeneralOperationsTest extends MockServerHandlerTest {

    @Test
    public void shouldReset() {
        // given
        HttpRequest request = request("/reset").withMethod("PUT");

        // when
        embeddedChannel.writeInbound(request);

        // then - http state handler is called
        verify(httpStateHandler).reset();

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(OK.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldClear() {
        // given
        HttpRequest request = request("/clear").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - http state handler is called
        verify(httpStateHandler).clear(request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(OK.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldDumpExpectationsToLog() {
        // given
        HttpRequest request = request()
                .withPath("/dumpToLog")
                .withMethod("PUT")
                .withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - http state handler is called
        verify(httpStateHandler).dumpExpectationsToLog(request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(OK.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldReturnRecordedRequestsOrExpectations() {
        // given
        HttpRequest request = request("/retrieve").withMethod("PUT").withBody("some_content");
        when(httpStateHandler.retrieve(request)).thenReturn("requests");

        // when
        embeddedChannel.writeInbound(request);

        // then - http state handler is called
        verify(httpStateHandler).retrieve(request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(OK.code()));
        assertThat(httpResponse.getBodyAsString(), is("requests"));
    }

    @Test
    public void shouldReturnNotFoundAfterNoMatch() {
        // given
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(null);

        // when
        embeddedChannel.writeInbound(request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(NOT_FOUND.code()));
        assertThat(httpResponse.getBodyAsString(), nullValue());
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
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(ACCEPTED.code()));
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
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(NOT_ACCEPTABLE.code()));
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
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(ACCEPTED.code()));
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
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(NOT_ACCEPTABLE.code()));
        assertThat(httpResponse.getBodyAsString(), is("failure response"));
    }

    @Test
    public void shouldStopMockServer() throws InterruptedException {
        // given
        HttpRequest request = request("/stop").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // ensure that stop thread has run
        TimeUnit.SECONDS.sleep(3);

        // then - mock server is stopped
        verify(mockMockServer).stop();

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(OK.code()));
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
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(OK.code()));
        assertThat(httpResponse.getBodyAsString(), is("" +
                "{" + NEW_LINE +
                "  \"ports\" : [ 1, 2, 3, 4, 5 ]" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldBindAdditionalPort() {
        // given
        HttpRequest request = request("/bind").withMethod("PUT").withBody("" +
                "{" + NEW_LINE +
                "  \"ports\" : [ 1, 2, 3, 4, 5 ]" + NEW_LINE +
                "}");
        when(mockMockServer.bindToPorts(anyListOf(Integer.class))).thenReturn(Arrays.asList(1, 2, 3, 4, 5));

        // when
        embeddedChannel.writeInbound(request);

        // then - mock server is stopped
        verify(mockMockServer).bindToPorts(Arrays.asList(1, 2, 3, 4, 5));

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(OK.code()));
        assertThat(httpResponse.getBodyAsString(), is("" +
                "{" + NEW_LINE +
                "  \"ports\" : [ 1, 2, 3, 4, 5 ]" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldActionResult() {
        // given - a request
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");

        // and - a matcher
        when(mockMockServerMatcher.retrieveAction(request)).thenReturn(response().withBody("some_response"));

        // and - a action handler
        when(mockActionHandler.processAction(response().withBody("some_response"), request))
                .thenReturn(
                        response()
                                .withStatusCode(PAYMENT_REQUIRED.code())
                                .withBody("some_content")

                );

        // when
        embeddedChannel.writeInbound(request);

        // then
        verify(mockActionHandler).processAction(response().withBody("some_response"), request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(embeddedChannel.isOpen(), is(false));
        assertThat(httpResponse.getStatusCode(), is(PAYMENT_REQUIRED.code()));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
        assertThat(httpResponse.getHeader("Connection"), containsInAnyOrder("close"));
        assertThat(httpResponse.getBodyAsString(), is("some_content"));
    }

    @Test
    public void shouldSetupExpectation() {
        // given
        HttpRequest request = request("/expectation").withMethod("PUT").withBody("some_content");

        // when
        embeddedChannel.writeInbound(request);

        // then - request deserialized
        verify(mockExpectationSerializer).deserializeArray("some_content");

        // and - expectation correctly setup
        verify(mockMockServerMatcher).when(any(HttpRequest.class), any(Times.class), any(TimeToLive.class));
        verify(mockExpectation).thenRespond(any(HttpResponse.class));
        verify(mockExpectation).thenRespond(any(HttpTemplate.class));
        verify(mockExpectation).thenForward(any(HttpForward.class));
        verify(mockExpectation).thenError(any(HttpError.class));
        verify(mockExpectation).thenCallback(any(HttpClassCallback.class));

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.CREATED.code()));
        assertThat(httpResponse.getBodyAsString(), is(""));
    }

    @Test
    public void shouldAddSubjectAlternativeName() throws UnknownHostException {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();
        HttpRequest request = request("/expectation").withMethod("PUT").withBody("some_content");
        when(mockHttpRequest.getFirstHeader(HOST.toString())).thenReturn("somehostname");
        Set<String> expectedDomainNames = new TreeSet<>();
        try {
            for (InetAddress addr : InetAddress.getAllByName("somehostname")) {
                expectedDomainNames.add(addr.getHostAddress());
                expectedDomainNames.add(addr.getHostName());
                expectedDomainNames.add(addr.getCanonicalHostName());
            }
        } catch (UnknownHostException uhe) {
            expectedDomainNames.add("somehostname");
        }

        // when
        embeddedChannel.writeInbound(request);

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder(expectedDomainNames.toArray()));

        // cleanup
        embeddedChannel.readOutbound();
    }

    @Test
    public void shouldReturnBadRequestAfterException() {
        // given
        HttpRequest request = request("/randomPath").withMethod("GET").withBody("some_content");
        when(mockMockServerMatcher.retrieveAction(request)).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        embeddedChannel.writeInbound(request);

        // and - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(BAD_REQUEST.code()));
        assertThat(httpResponse.getBodyAsString(), is("TEST EXCEPTION"));
    }
}
