package org.mockserver.mock.action.http;

import io.netty.channel.ConnectTimeoutException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.httpclient.UpstreamProxyUnreachableException;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.lang.reflect.Constructor;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;

/**
 * A forward that failed because its upstream proxy could not be reached is answered on the matched forward and
 * unmatched proxy routes with a 502 that names the proxy's property and what happened to the connection, where the same
 * failure without a proxy names the upstream; it is logged at the same levels as that failure, and the wrapper's class
 * name appears nowhere.
 */
public class HttpActionHandlerUpstreamProxyFailureTest {

    private static final HttpForward ACTION = forward().withHost("upstream.example").withPort(8080).withScheme(HttpForward.Scheme.HTTP);
    private static final InetSocketAddress UPSTREAM = InetSocketAddress.createUnresolved("upstream.example", 8080);
    private static final InetSocketAddress PROXY = InetSocketAddress.createUnresolved("proxy.example", 3128);

    private interface Route {
        void fail(HttpActionHandler handler, HttpRequest request, ResponseWriter responseWriter, Throwable failure);
    }

    private static final Route MATCHED_FORWARD = (handler, request, writer, failure) -> handler.handleExceptionDuringForwardingRequest(ACTION, request, writer, failure);
    private static final Route UNMATCHED_PROXY = (handler, request, writer, failure) -> handler.handleUnmatchedForwardFailure(failure, request, writer, UPSTREAM, false);
    private static final Route EXPLORATORY_PROXY = (handler, request, writer, failure) -> handler.handleUnmatchedForwardFailure(failure, request, writer, UPSTREAM, true);

    @Test
    public void shouldNameAnUnreachableProxyRatherThanTheUpstreamOnEveryRoute() throws Exception {
        Map<Throwable, String> causes = new LinkedHashMap<>();
        causes.put(new ConnectException("Connection refused"), "it refused the connection");
        causes.put(new ConnectTimeoutException("connection timed out after 5000 ms: proxy.example/192.0.2.1:3128"), "the connection timed out");
        causes.put(new UnknownHostException("proxy.example: nodename nor servname provided, or not known"), "its host name could not be resolved");
        causes.put(new UnresolvedAddressException(), "its host name could not be resolved");
        causes.put(new NoRouteToHostException("No route to host"), "there is no route to it");
        List<Function<Throwable, Throwable>> deliveries = List.of(failure -> failure, ExecutionException::new, CompletionException::new);
        for (Route route : new Route[]{MATCHED_FORWARD, UNMATCHED_PROXY, EXPLORATORY_PROXY}) {
            for (Map.Entry<Throwable, String> cause : causes.entrySet()) {
                for (Function<Throwable, Throwable> delivered : deliveries) {
                    Outcome withoutProxy = outcome(route, delivered.apply(cause.getKey()));
                    Outcome throughProxy = outcome(route, delivered.apply(unreachable(cause.getKey())));

                    assertThat(throughProxy.toString(), throughProxy.answer.getStatusCode(), is(502));
                    assertThat(throughProxy.answer.getBodyAsString(), is("upstream proxy set by forwardHttpProxy could not be reached (" + cause.getValue() + "): check the proxy is running and its host and port are correct"));
                    assertThat(withoutProxy.toString(), withoutProxy.answer.getBodyAsString(), not(startsWith("upstream proxy")));
                    assertThat(throughProxy.levels, is(withoutProxy.levels));
                    assertThat(throughProxy.toString(), not(containsString(UpstreamProxyUnreachableException.class.getSimpleName())));
                }
            }
        }
    }

    private static final class Outcome {
        private final HttpResponse answer;
        private final List<String> levels;
        private final String logged;

        private Outcome(HttpResponse answer, List<LogEntry> logged) {
            this.answer = answer;
            this.levels = logged.stream().map(entry -> entry.getLogLevel() + " " + entry.getType()).collect(Collectors.toList());
            this.logged = logged.stream().map(entry -> entry.getLogLevel() + " " + entry.getType() + " " + entry.getMessage() + " " + entry.getThrowable()).collect(Collectors.joining("\n"));
        }

        @Override
        public String toString() {
            return "answer: " + answer + "\nlogged: " + logged;
        }
    }

    private static Outcome outcome(Route route, Throwable failure) {
        List<LogEntry> logged = new CopyOnWriteArrayList<>();
        MockServerLogger mockServerLogger = new MockServerLogger(HttpActionHandlerUpstreamProxyFailureTest.class) {
            @Override
            public boolean isEnabledForInstance(Level level) {
                return true;
            }

            @Override
            public void logEvent(LogEntry logEntry) {
                logged.add(logEntry);
            }
        };
        HttpState httpState = mock(HttpState.class);
        when(httpState.getMockServerLogger()).thenReturn(mockServerLogger);
        when(httpState.getUniqueLoopPreventionHeaderName()).thenReturn("x-forwarded-by");
        when(httpState.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_" + UUIDService.getUUID());
        HttpActionHandler handler = new HttpActionHandler(configuration(), null, httpState, null, null);
        ResponseWriter responseWriter = mock(ResponseWriter.class);
        HttpRequest request = request("/some_path");
        request.withLogCorrelationId("correlation");

        route.fail(handler, request, responseWriter, failure);

        ArgumentCaptor<HttpResponse> response = ArgumentCaptor.forClass(HttpResponse.class);
        verify(responseWriter).writeResponse(eq(request), response.capture(), eq(false));
        return new Outcome(response.getValue(), logged);
    }

    private static Throwable unreachable(Throwable cause) throws Exception {
        Constructor<UpstreamProxyUnreachableException> constructor = UpstreamProxyUnreachableException.class.getDeclaredConstructor(InetSocketAddress.class, String.class, Throwable.class);
        constructor.setAccessible(true);
        return constructor.newInstance(PROXY, "forwardHttpProxy", cause);
    }
}
