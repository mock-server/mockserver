package org.mockserver.netty.http3;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.Delay;
import org.slf4j.event.Level;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Covers the reporting of {@link ConnectionOptions} that HTTP/3 does not act on.
 *
 * <h3>Context</h3>
 * <p>{@code ConnectionOptions} is honoured throughout the HTTP/1.1 response writer and nowhere in
 * the HTTP/3 package, so setting {@code closeSocket} or {@code chunkSize} on an expectation served
 * over HTTP/3 silently does nothing while the expectation still reports as created. Until the
 * applicable subset is implemented, the writer at least says so.</p>
 *
 * <h3>Why two groups of tests</h3>
 * <p>The classification (which fields count as unimplemented versus inapplicable) and the wiring
 * (that the warning is actually emitted on the response path) are asserted separately. A correct
 * classification that is never called would be the "defined but not wired" defect, and a wiring
 * test alone would not notice a field silently dropping out of the classification.</p>
 */
public class Http3ConnectionOptionsIgnoredTest {

    private static final Configuration CONFIGURATION = configuration();

    // --- classification ---

    @Test
    public void shouldReportNothingWhenNoConnectionOptionsAreSet() {
        ConnectionOptions empty = ConnectionOptions.connectionOptions();

        assertThat(Http3ResponseWriter.unimplementedOnHttp3(empty), is(empty()));
        assertThat(Http3ResponseWriter.notApplicableOnHttp3(empty), is(empty()));
    }

    @Test
    public void shouldReportNothingForNullConnectionOptions() {
        assertThat(Http3ResponseWriter.unimplementedOnHttp3(null), is(empty()));
        assertThat(Http3ResponseWriter.notApplicableOnHttp3(null), is(empty()));
    }

    @Test
    public void shouldReportEverySetOptionThatHasAnHttp3EquivalentAsUnimplemented() {
        ConnectionOptions options = ConnectionOptions.connectionOptions()
            .withCloseSocket(true)
            .withCloseSocketDelay(new Delay(TimeUnit.MILLISECONDS, 10))
            .withChunkSize(128)
            .withChunkDelay(new Delay(TimeUnit.MILLISECONDS, 5))
            .withSuppressContentLengthHeader(true)
            .withContentLengthHeaderOverride(42);

        assertThat(Http3ResponseWriter.unimplementedOnHttp3(options), containsInAnyOrder(
            "closeSocket", "closeSocketDelay", "chunkSize", "chunkDelay",
            "suppressContentLengthHeader", "contentLengthHeaderOverride"));
    }

    @Test
    public void shouldReportConnectionHeaderOptionsAsNotApplicableRatherThanUnimplemented() {
        // RFC 9114 section 4.2 forbids connection-specific header fields on HTTP/3, so these two
        // have nothing to implement — reporting them as "not yet implemented" would imply future
        // work that will never happen.
        ConnectionOptions options = ConnectionOptions.connectionOptions()
            .withSuppressConnectionHeader(true)
            .withKeepAliveOverride(true);

        assertThat(Http3ResponseWriter.notApplicableOnHttp3(options),
            containsInAnyOrder("suppressConnectionHeader", "keepAliveOverride"));
        assertThat(Http3ResponseWriter.unimplementedOnHttp3(options), is(empty()));
    }

    @Test
    public void shouldReportOnlyTheOptionsActuallySet() {
        ConnectionOptions options = ConnectionOptions.connectionOptions().withCloseSocket(true);

        assertThat(Http3ResponseWriter.unimplementedOnHttp3(options), containsInAnyOrder("closeSocket"));
        assertThat(Http3ResponseWriter.notApplicableOnHttp3(options), is(empty()));
    }

    @Test
    public void shouldReportAnExplicitlyFalseOptionAsSet() {
        // withCloseSocket(false) is a deliberate instruction to keep the connection open, which is
        // equally not honoured; only an unset (null) field means "nothing was asked for".
        ConnectionOptions options = ConnectionOptions.connectionOptions().withCloseSocket(false);

        assertThat(Http3ResponseWriter.unimplementedOnHttp3(options), containsInAnyOrder("closeSocket"));
    }

    // --- wiring: the warning reaches the log on the response path ---

    @Test
    public void shouldWarnWhenSendingAResponseCarryingIgnoredConnectionOptions() {
        MockServerLogger logger = mock(MockServerLogger.class);
        Http3ResponseWriter writer = new Http3ResponseWriter(CONFIGURATION, logger, mockActiveCtx());

        writer.sendResponse(request().withPath("/test"), response()
            .withStatusCode(200)
            .withBody("hello")
            .withConnectionOptions(ConnectionOptions.connectionOptions().withCloseSocket(true)));

        LogEntry warning = capturedWarning(logger);
        assertThat("expected a WARN naming the ignored connectionOptions", warning, is(notNullValue()));
        assertThat(warning.getMessageFormat(), containsString("not applied on HTTP/3"));
        assertThat(argumentsToString(warning), containsString("closeSocket"));
    }

    @Test
    public void shouldNotWarnWhenTheResponseCarriesNoConnectionOptions() {
        // The common case must stay silent, or the warning becomes noise and gets ignored.
        MockServerLogger logger = mock(MockServerLogger.class);
        Http3ResponseWriter writer = new Http3ResponseWriter(CONFIGURATION, logger, mockActiveCtx());

        writer.sendResponse(request().withPath("/test"), response().withStatusCode(200).withBody("hello"));

        assertThat(capturedWarning(logger), is(nullValue()));
    }

    @Test
    public void shouldNotWarnWhenConnectionOptionsArePresentButEmpty() {
        MockServerLogger logger = mock(MockServerLogger.class);
        Http3ResponseWriter writer = new Http3ResponseWriter(CONFIGURATION, logger, mockActiveCtx());

        writer.sendResponse(request().withPath("/test"), response()
            .withStatusCode(200)
            .withBody("hello")
            .withConnectionOptions(ConnectionOptions.connectionOptions()));

        assertThat(capturedWarning(logger), is(nullValue()));
    }

    // --- helpers ---

    private LogEntry capturedWarning(MockServerLogger logger) {
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeast(0)).logEvent(captor.capture());
        for (LogEntry entry : captor.getAllValues()) {
            if (entry.getLogLevel() == Level.WARN
                && entry.getMessageFormat() != null
                && entry.getMessageFormat().contains("connectionOptions")) {
                return entry;
            }
        }
        return null;
    }

    private String argumentsToString(LogEntry entry) {
        StringBuilder builder = new StringBuilder();
        for (Object argument : entry.getArguments()) {
            builder.append(argument);
        }
        return builder.toString();
    }

    private ChannelHandlerContext mockActiveCtx() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.executor()).thenReturn(io.netty.util.concurrent.ImmediateEventExecutor.INSTANCE);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.isActive()).thenReturn(true);
        ChannelFuture future = mock(ChannelFuture.class);
        when(future.addListener(any())).thenReturn(future);
        when(ctx.writeAndFlush(any())).thenReturn(future);
        when(ctx.write(any())).thenReturn(future);
        return ctx;
    }
}
