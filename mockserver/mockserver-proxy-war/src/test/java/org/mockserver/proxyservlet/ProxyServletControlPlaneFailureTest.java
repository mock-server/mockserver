package org.mockserver.proxyservlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.HttpServletRequestToMockServerHttpRequestDecoder;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.responsewriter.ControlPlaneFailureResponse.UNEXPECTED_FAILURE_MESSAGE;

/**
 * How the ProxyServlet answers a control-plane request whose handling throws: a client error is a {@code 400}
 * carrying its message, anything else a {@code 500} with a generic message and one {@code ERROR} log entry. A
 * data-plane request whose handling throws is answered {@code 500} with the generic message as a mock response, so
 * without CORS headers unless {@code enableCORSForAllResponses} is on.
 */
public class ProxyServletControlPlaneFailureTest {

    private static final String EXPECTATION_JSON = "{\"httpRequest\":{\"path\":\"/some_path\"},\"httpResponse\":{\"statusCode\":200}}";

    private RuntimeException fault;
    private HttpState httpStateHandler;
    private HttpState realHttpState;
    private MockServerLogger mockServerLogger;
    private HttpServletRequestToMockServerHttpRequestDecoder httpServletRequestToMockServerRequestDecoder;
    private HttpActionHandler actionHandler;
    private Configuration configuration;

    @InjectMocks
    private ProxyServlet servlet;

    @Before
    public void setupFixture() {
        realHttpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class)) {
            @Override
            public List<Expectation> add(Expectation... expectations) {
                if (fault != null) {
                    throw fault;
                }
                return super.add(expectations);
            }
        };
        httpStateHandler = spy(realHttpState);
        mockServerLogger = mock(MockServerLogger.class);
        actionHandler = mock(HttpActionHandler.class);
        configuration = spy(configuration().livenessHttpGetPath("/liveness/probe"));
        httpServletRequestToMockServerRequestDecoder = spy(new HttpServletRequestToMockServerHttpRequestDecoder(configuration(), new MockServerLogger()));
        servlet = new ProxyServlet();
        openMocks(this);
    }

    @After
    public void shutdown() {
        realHttpState.stop();
        servlet.destroy();
    }

    @Test
    public void shouldAnswerAnUnexpectedFailureWithAServerErrorAndAGenericMessage() {
        // given
        fault = new NullPointerException("internal detail of the fault");

        // when
        MockHttpServletResponse response = putExpectation(EXPECTATION_JSON);

        // then
        String body = new String(response.getContentAsByteArray(), UTF_8);
        assertThat(response.getStatus(), is(500));
        assertThat(body, startsWith(UNEXPECTED_FAILURE_MESSAGE));
        assertThat(body, not(containsString("internal detail of the fault")));

        // and - logged once at ERROR, with the stack trace, under the correlation id the caller was given
        ArgumentCaptor<LogEntry> logged = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockServerLogger, atLeastOnce()).logEvent(logged.capture());
        List<LogEntry> errors = logged.getAllValues().stream()
            .filter(entry -> entry.getLogLevel() == Level.ERROR && entry.getThrowable() == fault)
            .collect(Collectors.toList());
        assertThat(errors, hasSize(1));
        assertThat(body, is(UNEXPECTED_FAILURE_MESSAGE + errors.get(0).getCorrelationId()));
    }

    @Test
    public void shouldAnswerAnUnsupportedOperationAsABadRequestWithItsMessage() {
        // given
        fault = new UnsupportedOperationException("expectation type not supported here");

        // when
        MockHttpServletResponse response = putExpectation(EXPECTATION_JSON);

        // then
        assertThat(response.getStatus(), is(400));
        assertThat(new String(response.getContentAsByteArray(), UTF_8), is("expectation type not supported here"));
    }

    @Test
    public void shouldAnswerAnInvalidExpectationAsABadRequestWithTheValidationMessage() {
        // when
        MockHttpServletResponse response = putExpectation("{\"httpRequest\":{\"path\":\"/some_path\"},\"httpResponse\":{\"statusCode\":\"not a number\"}}");

        // then
        assertThat(response.getStatus(), is(400));
        assertThat(new String(response.getContentAsByteArray(), UTF_8), containsString("incorrect expectation json format"));
    }

    @Test
    public void shouldAnswerARequestWhoseBodyCannotBeReadWithAServerErrorAndAGenericMessage() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/mockserver/expectation") {
            @Override
            public ServletInputStream getInputStream() {
                return new FailingServletInputStream();
            }
        };
        request.addHeader("Origin", "http://localhost:3000");

        // when
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);

        // then
        String body = new String(response.getContentAsByteArray(), UTF_8);
        assertThat(response.getStatus(), is(500));
        assertThat(body, startsWith(UNEXPECTED_FAILURE_MESSAGE));
        assertThat(body, not(containsString("connection reset reading body")));
        assertThat(response.getHeader("Access-Control-Allow-Origin"), is("http://localhost:3000"));

        // and - logged once at ERROR, against the request as far as it could be read
        ArgumentCaptor<LogEntry> logged = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockServerLogger, atLeastOnce()).logEvent(logged.capture());
        List<LogEntry> errors = logged.getAllValues().stream()
            .filter(entry -> entry.getLogLevel() == Level.ERROR && entry.getThrowable() != null)
            .collect(Collectors.toList());
        assertThat(errors, hasSize(1));
        assertThat(body, is(UNEXPECTED_FAILURE_MESSAGE + errors.get(0).getCorrelationId()));
        assertThat(((HttpRequest) errors.get(0).getHttpRequest()).getPath().getValue(), is("/mockserver/expectation"));
    }

    @Test
    public void shouldAnswerARequestTheDecoderRejectsAsABadRequestWithItsMessage() {
        // given
        doThrow(new IllegalArgumentException("request line not understood"))
            .when(httpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(any());

        // when
        MockHttpServletResponse response = putExpectation(EXPECTATION_JSON);

        // then
        assertThat(response.getStatus(), is(400));
        assertThat(new String(response.getContentAsByteArray(), UTF_8), is("request line not understood"));
    }

    @Test
    public void shouldAnswerADataPlaneFailureWithAServerErrorAndAGenericMessageWithoutCorsHeaders() {
        // given
        RuntimeException dataPlaneFault = new IllegalArgumentException("internal detail of the data plane fault");
        doThrow(dataPlaneFault).when(actionHandler).processAction(any(), any(), any(), any(), anyBoolean(), anyBoolean());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/some_mocked_path");
        request.addHeader("Origin", "https://app.example.com");

        // when
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);

        // then
        String body = new String(response.getContentAsByteArray(), UTF_8);
        assertThat(response.getStatus(), is(500));
        assertThat(body, startsWith(UNEXPECTED_FAILURE_MESSAGE));
        assertThat(body, not(containsString("internal detail of the data plane fault")));
        assertThat(response.getHeader("Access-Control-Allow-Origin"), nullValue());
        assertThat(response.getHeaderNames(), not(hasItem(startsWith("Access-Control-"))));

        // and - logged once at ERROR, with the stack trace, under the correlation id the caller was given
        ArgumentCaptor<LogEntry> logged = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockServerLogger, atLeastOnce()).logEvent(logged.capture());
        List<LogEntry> errors = logged.getAllValues().stream()
            .filter(entry -> entry.getLogLevel() == Level.ERROR && entry.getThrowable() == dataPlaneFault)
            .collect(Collectors.toList());
        assertThat(errors, hasSize(1));
        assertThat(body, is(UNEXPECTED_FAILURE_MESSAGE + errors.get(0).getCorrelationId()));
    }

    @Test
    public void shouldAnswerADataPlaneRequestWhoseBodyCannotBeReadWithAServerErrorWithoutCorsHeaders() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/some_mocked_path") {
            @Override
            public ServletInputStream getInputStream() {
                return new FailingServletInputStream();
            }
        };
        request.addHeader("Origin", "https://app.example.com");

        // when
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);

        // then
        String body = new String(response.getContentAsByteArray(), UTF_8);
        assertThat(response.getStatus(), is(500));
        assertThat(body, startsWith(UNEXPECTED_FAILURE_MESSAGE));
        assertThat(response.getHeader("Access-Control-Allow-Origin"), nullValue());
        assertThat(response.getHeaderNames(), not(hasItem(startsWith("Access-Control-"))));
    }

    @Test
    public void shouldAnswerADataPlaneRequestTheDecoderRejectsWithAServerErrorAndAGenericMessage() {
        // given
        doThrow(new IllegalArgumentException("request line not understood"))
            .when(httpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(any());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/some_mocked_path");
        request.addHeader("Origin", "https://app.example.com");

        // when
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);

        // then
        assertThat(response.getStatus(), is(500));
        assertThat(new String(response.getContentAsByteArray(), UTF_8), startsWith(UNEXPECTED_FAILURE_MESSAGE));
        assertThat(response.getHeader("Access-Control-Allow-Origin"), nullValue());
    }

    @Test
    public void shouldAnswerALivenessRequestTheDecoderRejectsAsABadRequestWithItsMessage() {
        // given
        doThrow(new IllegalArgumentException("request line not understood"))
            .when(httpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(any());

        // when
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(new MockHttpServletRequest("GET", "/liveness/probe"), response);

        // then - answered as the control plane, not as a mock
        assertThat(response.getStatus(), is(400));
        assertThat(new String(response.getContentAsByteArray(), UTF_8), is("request line not understood"));
    }

    private static class FailingServletInputStream extends ServletInputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("connection reset reading body");
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
        }
    }

    private MockHttpServletResponse putExpectation(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/mockserver/expectation");
        request.addHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8");
        request.setContent(body.getBytes(UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);
        return response;
    }
}
