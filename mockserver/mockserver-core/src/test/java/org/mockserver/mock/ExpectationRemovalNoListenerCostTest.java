package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.Extension;
import org.mockserver.extension.ExtensionRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.scheduler.Scheduler;

import java.lang.reflect.Field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.testing.AllocationMeasurement.allocatedBytes;
import static org.mockserver.testing.AllocationMeasurement.assumeHttpRequestIsNotInstrumented;

/**
 * With no lifecycle listener loaded, telling listeners about an expectation removal must be one null check that
 * allocates nothing, whichever origin the removal passes, and the per-request {@code postProcess} of an expectation
 * that stays active must allocate nothing either.
 */
public class ExpectationRemovalNoListenerCostTest {

    private static final int OPERATIONS = 200_000;
    // the least of several measurements: background work can only add bytes, never remove the code's own
    private static final int ATTEMPTS = 3;
    private static volatile Object sink;
    private final MockServerLogger mockServerLogger = new MockServerLogger(ExpectationRemovalNoListenerCostTest.class);

    public static class NotAListener implements Extension {
        @Override
        public String name() {
            return "removal-cost-not-a-listener";
        }
    }

    @Test
    public void shouldNotAllocateToReportARemovalWhenNoListenerIsLoaded() throws Exception {
        Configuration configuration = configuration().extensions(NotAListener.class);
        ExtensionRegistry registry = ExtensionRegistry.start(configuration, mockServerLogger);
        try {
            RequestMatchers withoutExtensions = store(configuration, null);
            RequestMatchers withANonListenerExtension = store(configuration, registry);
            assertThat(lifecycleDispatch(withoutExtensions), nullValue());
            assertThat(lifecycleDispatch(withANonListenerExtension), nullValue());

            Expectation exhausted = new Expectation(request("/exhausted"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("x"));
            assertThat(exhausted.consumeMatch(), is(true));
            for (RequestMatchers requestMatchers : new RequestMatchers[]{withoutExtensions, withANonListenerExtension}) {
                for (RequestMatchers.Origin origin : RequestMatchers.Origin.values()) {
                    report(requestMatchers, exhausted, origin);
                    long least = Long.MAX_VALUE;
                    for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                        long before = allocatedBytes();
                        report(requestMatchers, exhausted, origin);
                        least = Math.min(least, (allocatedBytes() - before) / OPERATIONS);
                    }
                    assumeHttpRequestIsNotInstrumented();
                    assertThat("bytes allocated per removal report with origin " + origin, least, is(0L));
                }
            }
        } finally {
            registry.stop();
        }
    }

    @Test
    public void shouldNotAllocateToPostProcessAnExpectationThatStaysActive() throws Exception {
        RequestMatchers requestMatchers = store(configuration(), null);
        requestMatchers.add(new Expectation(request("/active"), Times.unlimited(), TimeToLive.unlimited(), 0).withId("active").thenRespond(response("x")), Cause.API);
        Expectation matched = requestMatchers.firstMatchingExpectation(request("/active"));
        assertThat(matched, notNullValue());
        postProcess(requestMatchers, matched);
        long least = Long.MAX_VALUE;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long before = allocatedBytes();
            postProcess(requestMatchers, matched);
            least = Math.min(least, (allocatedBytes() - before) / OPERATIONS);
        }
        assumeHttpRequestIsNotInstrumented();
        assertThat("bytes allocated per postProcess of an active expectation", least, is(0L));
        assertThat(requestMatchers.size(), is(1));
    }

    @Test
    public void shouldKeepEveryListenerCheckOutOfThePerMatchCommitBranches() throws Exception {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/org/mockserver/mock/RequestMatchers.java")), java.nio.charset.StandardCharsets.UTF_8);
        int commitPoints = 0;
        for (int start = source.indexOf("// COMMIT POINT"); start >= 0; start = source.indexOf("// COMMIT POINT", start + 1)) {
            int matched = source.indexOf("matchedExpectation = expectation;", start);
            int returned = source.indexOf("return expectation;", start);
            int end = matched < 0 ? returned : returned < 0 ? matched : Math.min(matched, returned);
            assertThat("a commit point must end where the match is committed", end > start, is(true));
            String branch = source.substring(start, end);
            assertThat("a served match that leaves the expectation active is not a lifecycle change", branch.contains("lifecycleDispatch") || branch.contains("expectationRemoved"), is(false));
            commitPoints++;
        }
        assertThat("both the normal and the early (respondBeforeBody) commit points", commitPoints, is(2));
    }

    private RequestMatchers store(Configuration configuration, ExtensionRegistry registry) {
        return new RequestMatchers(configuration, mockServerLogger, new Scheduler(configuration, mockServerLogger, true), mock(WebSocketClientRegistry.class), registry);
    }

    private static void report(RequestMatchers requestMatchers, Expectation expectation, RequestMatchers.Origin origin) {
        for (int i = 0; i < OPERATIONS; i++) {
            requestMatchers.expectationRemoved(expectation, Cause.API, origin);
        }
    }

    private static void postProcess(RequestMatchers requestMatchers, Expectation expectation) {
        Expectation last = null;
        for (int i = 0; i < OPERATIONS; i++) {
            last = requestMatchers.postProcess(expectation);
        }
        sink = last;
    }

    private static Object lifecycleDispatch(RequestMatchers requestMatchers) throws Exception {
        Field field = RequestMatchers.class.getDeclaredField("lifecycleDispatch");
        field.setAccessible(true);
        return field.get(requestMatchers);
    }
}
