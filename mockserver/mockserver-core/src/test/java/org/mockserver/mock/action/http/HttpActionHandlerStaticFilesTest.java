package org.mockserver.mock.action.http;

import io.netty.channel.ChannelHandlerContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.crud.CrudDispatcher;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * Which thread reads a static file, and what a request gets when the static file read queue is full.
 */
public class HttpActionHandlerStaticFilesTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Scheduler scheduler;
    private HttpActionHandler actionHandler;
    private ResponseWriter responseWriter;
    private final CompletableFuture<String> writingThread = new CompletableFuture<>();
    private final CompletableFuture<HttpResponse> written = new CompletableFuture<>();

    @Before
    public void createHandler() throws Exception {
        File served = temporaryFolder.newFolder("__files");
        Files.write(new File(served, "hello.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        Configuration configuration = configuration().staticFilesDirectory(served.getPath());
        MockServerLogger mockServerLogger = new MockServerLogger(HttpActionHandlerStaticFilesTest.class);
        scheduler = new Scheduler(configuration, mockServerLogger);
        HttpState httpState = mock(HttpState.class);
        when(httpState.getScheduler()).thenReturn(scheduler);
        when(httpState.getMockServerLogger()).thenReturn(mockServerLogger);
        when(httpState.getUniqueLoopPreventionHeaderName()).thenReturn("x-forwarded-by");
        when(httpState.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_static_files_test");
        when(httpState.getCrudDispatcher()).thenReturn(new CrudDispatcher());
        actionHandler = new HttpActionHandler(configuration, null, httpState, null, null);
        responseWriter = mock(ResponseWriter.class);
        doAnswer(invocation -> {
            writingThread.complete(Thread.currentThread().getName());
            written.complete(invocation.getArgument(1));
            return null;
        }).when(responseWriter).writeResponse(any(HttpRequest.class), any(HttpResponse.class), anyBoolean());
    }

    @After
    public void stopScheduler() {
        scheduler.shutdown();
    }

    @Test
    public void shouldReadOnFileReadPoolWhenCalledSynchronouslyOnAChannelAsHttp3Does() throws Exception {
        actionHandler.processAction(request("/hello.json").withMethod("GET"), responseWriter, mock(ChannelHandlerContext.class), Collections.emptySet(), false, true);

        assertThat(writingThread.get(10, TimeUnit.SECONDS), startsWith("MockServer-FileRead"));
        assertThat(written.get(10, TimeUnit.SECONDS).getStatusCode(), is(200));
    }

    @Test
    public void shouldReadInlineWhenCalledSynchronouslyWithoutAChannelAsServletsDo() throws Exception {
        actionHandler.processAction(request("/hello.json").withMethod("GET"), responseWriter, null, Collections.emptySet(), false, true);

        assertThat(writingThread.isDone(), is(true));
        assertThat(writingThread.get(), is(Thread.currentThread().getName()));
        assertThat(written.get().getStatusCode(), is(200));
    }

    @Test
    public void shouldAnswerServiceUnavailableWhenStaticFileReadQueueIsFull() throws Exception {
        scheduler.maxQueuedFileReads(0);

        actionHandler.processAction(request("/hello.json").withMethod("GET"), responseWriter, mock(ChannelHandlerContext.class), Collections.emptySet(), false, false);

        HttpResponse response = written.get(10, TimeUnit.SECONDS);
        assertThat(response.getStatusCode(), is(503));
        assertThat(response.getFirstHeader("Retry-After"), is("1"));
        assertThat(response.getBodyAsString(), is("MockServer is overloaded, too many static file reads are already waiting"));
        assertThat(scheduler.getOverloadRejectionCount(Scheduler.OverloadReason.STATIC_FILES), is(1L));
    }

    @Test
    public void shouldRefuseStaticFileReadAfterShutdown() {
        scheduler.shutdown();
        AtomicBoolean ran = new AtomicBoolean();
        AtomicBoolean refused = new AtomicBoolean();

        scheduler.submitStaticFileRead(() -> ran.set(true), () -> refused.set(true), false);

        assertThat(ran.get(), is(false));
        assertThat(refused.get(), is(true));
    }
}
