package org.mockserver.responsewriter;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Pins the {@code Connection} header {@code addConnectionHeader} synthesises for every branch. The
 * value is now sourced from two shared immutable {@link org.mockserver.model.Header} constants
 * rather than freshly constructed per response; these assertions bind to that value, so degrading a
 * constant turns them red.
 */
public class ResponseWriterConnectionHeaderTest {

    private static final class TestResponseWriter extends ResponseWriter {
        private TestResponseWriter() {
            super(Configuration.configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
        }
    }

    private static HttpResponse addConnectionHeader(HttpRequest request, HttpResponse response) {
        return new TestResponseWriter().addConnectionHeader(request, response);
    }

    @Test
    public void keepAliveRequestGetsKeepAliveConnectionHeader() {
        HttpResponse out = addConnectionHeader(request().withKeepAlive(true), response("body"));
        assertThat(out.getFirstHeader("connection"), is("keep-alive"));
    }

    @Test
    public void nonKeepAliveRequestGetsCloseConnectionHeader() {
        HttpResponse out = addConnectionHeader(request().withKeepAlive(false), response("body"));
        assertThat(out.getFirstHeader("connection"), is("close"));
    }

    @Test
    public void keepAliveOverrideTrueGetsKeepAliveConnectionHeader() {
        HttpResponse out = addConnectionHeader(
            request().withKeepAlive(false),
            response("body").withConnectionOptions(connectionOptions().withKeepAliveOverride(true)));
        assertThat(out.getFirstHeader("connection"), is("keep-alive"));
    }

    @Test
    public void keepAliveOverrideFalseGetsCloseConnectionHeader() {
        HttpResponse out = addConnectionHeader(
            request().withKeepAlive(true),
            response("body").withConnectionOptions(connectionOptions().withKeepAliveOverride(false)));
        assertThat(out.getFirstHeader("connection"), is("close"));
    }

    @Test
    public void closeSocketOnKeepAliveRequestGetsCloseConnectionHeader() {
        HttpResponse out = addConnectionHeader(
            request().withKeepAlive(true),
            response("body").withConnectionOptions(connectionOptions().withCloseSocket(true)));
        assertThat(out.getFirstHeader("connection"), is("close"));
    }

    @Test
    public void closeSocketFalseOnKeepAliveRequestGetsKeepAliveConnectionHeader() {
        HttpResponse out = addConnectionHeader(
            request().withKeepAlive(true),
            response("body").withConnectionOptions(connectionOptions().withCloseSocket(false)));
        assertThat(out.getFirstHeader("connection"), is("keep-alive"));
    }

    @Test
    public void closeSocketWithKeepAliveOverrideTrueKeepsKeepAliveConnectionHeader() {
        HttpResponse out = addConnectionHeader(
            request().withKeepAlive(true),
            response("body").withConnectionOptions(connectionOptions().withCloseSocket(true).withKeepAliveOverride(true)));
        assertThat(out.getFirstHeader("connection"), is("keep-alive"));
    }

    @Test
    public void suppressConnectionHeaderAddsNoConnectionHeader() {
        HttpResponse out = addConnectionHeader(
            request().withKeepAlive(true),
            response("body").withConnectionOptions(connectionOptions().withSuppressConnectionHeader(true)));
        assertThat(out.getFirstHeader("connection"), is(""));
    }
}
