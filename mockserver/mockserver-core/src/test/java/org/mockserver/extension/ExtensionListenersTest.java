package org.mockserver.extension;

import org.junit.After;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.model.ConfigurationDTO;
import org.mockserver.state.InMemoryStateBackend;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;

/**
 * The serve-event (H3), expectation-lifecycle (H7) and settings (H8) listeners: what each is told and why, and the
 * bounded, never-waiting queues between the server and the listener threads.
 */
public class ExtensionListenersTest {

    private final List<Runnable> cleanUp = new ArrayList<>();
    private final List<HttpState> httpStates = new ArrayList<>();
    private final List<MockServerEventLog> eventLogs = new ArrayList<>();

    @After
    public void cleanUp() {
        httpStates.forEach(HttpState::stop);
        eventLogs.forEach(MockServerEventLog::stop);
        for (int i = cleanUp.size() - 1; i >= 0; i--) {
            cleanUp.get(i).run();
        }
    }

    public static class Recorder implements ServeEventListener, ExpectationLifecycleListener, SettingsListener {
        private final String name;
        final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        final List<String> threads = new ArrayList<>();
        volatile CountDownLatch gate = new CountDownLatch(0);
        volatile CountDownLatch entered = new CountDownLatch(1);
        volatile boolean throwOnEvent;

        Recorder(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void onServed(ServeEvent event) {
            receive(event);
        }

        @Override
        public void onChange(ExpectationChange change) {
            receive(change);
        }

        @Override
        public void onConfigurationUpdated(Configuration configuration) {
            receive(configuration);
        }

        private void receive(Object event) {
            synchronized (threads) {
                threads.add(Thread.currentThread().getName());
            }
            entered.countDown();
            try {
                gate.await(30, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (throwOnEvent) {
                throwOnEvent = false;
                throw new IllegalStateException("listener failure");
            }
            events.add(event);
        }

        ExpectationChange nextChange() throws InterruptedException {
            Object event = events.poll(10, SECONDS);
            assertThat("expected an expectation change", event, notNullValue());
            return (ExpectationChange) event;
        }

        ServeEvent nextServeEvent() throws InterruptedException {
            Object event = events.poll(10, SECONDS);
            assertThat("expected a serve event", event, notNullValue());
            return (ServeEvent) event;
        }

        void assertNothingMore() throws InterruptedException {
            assertThat(events.poll(200, MILLISECONDS), nullValue());
        }
    }

    public static class LifecycleOnly implements ExpectationLifecycleListener {
        final BlockingQueue<ExpectationChange> changes = new LinkedBlockingQueue<>();

        @Override
        public String name() {
            return "listeners-lifecycle-only";
        }

        @Override
        public void onChange(ExpectationChange change) {
            changes.add(change);
        }
    }

    public static class Initializer implements org.mockserver.server.initialize.ExpectationInitializer, Extension {
        @Override
        public String name() {
            return "listeners-initializer";
        }

        @Override
        public Expectation[] initializeExpectations() {
            return new Expectation[]{new Expectation(request("/from-extension")).thenRespond(response("hello"))};
        }
    }

    public static class NotAListener implements Extension {
        @Override
        public String name() {
            return "listeners-not-a-listener";
        }
    }

    private final MockServerLogger logger = new MockServerLogger(ExtensionListenersTest.class);

    private ExtensionRegistry registry(Configuration configuration) {
        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        cleanUp.add(registry::stop);
        return registry;
    }

    private RequestMatchers store(Configuration configuration, ExtensionRegistry registry) {
        RequestMatchers requestMatchers = new RequestMatchers(configuration, logger, new Scheduler(configuration, logger, true), mock(WebSocketClientRegistry.class), registry);
        requestMatchers.setStateBackend(new InMemoryStateBackend(configuration.maxExpectations()));
        return requestMatchers;
    }

    private static Expectation expectation(String id, Times times, TimeToLive timeToLive) {
        return new Expectation(request("/" + id), times, timeToLive, 0).withId(id).thenRespond(response("body-" + id));
    }

    private static void assertChange(ExpectationChange change, ExpectationChange.Type type, ChangeCause cause, String id) {
        assertThat(change.toString(), change.type(), is(type));
        assertThat(change.toString(), change.cause(), is(cause));
        assertThat(change.toString(), change.expectation().getId(), is(id));
        assertThat(change.toString(), change.isFleetWide(), is(false));
    }

    // H7 causes

    @Test
    public void shouldReportAddUpdateAndRemoveThroughTheApi() throws Exception {
        Recorder recorder = new Recorder("listeners-api");
        Configuration configuration = configuration().extensions(() -> recorder);
        RequestMatchers requestMatchers = store(configuration, registry(configuration));

        requestMatchers.add(expectation("a", Times.unlimited(), TimeToLive.unlimited()), Cause.API);
        assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.API, "a");

        Expectation changed = new Expectation(request("/a"), Times.unlimited(), TimeToLive.unlimited(), 0).withId("a").thenRespond(response("changed"));
        requestMatchers.add(changed, Cause.API);
        ExpectationChange updated = recorder.nextChange();
        assertChange(updated, ExpectationChange.Type.UPDATED, ChangeCause.API, "a");
        assertThat("the live expectation is passed, not a copy", updated.expectation(), sameInstance(changed));

        // an identical re-add changes nothing, so is not reported
        requestMatchers.add(changed, Cause.API);
        recorder.assertNothingMore();

        requestMatchers.clear(new ExpectationId().withId("a"), "correlation");
        assertChange(recorder.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.API, "a");
        recorder.assertNothingMore();
    }

    @Test
    public void shouldReportAClearByRequestAndOneRemovalPerExpectationOnReset() throws Exception {
        Recorder recorder = new Recorder("listeners-reset");
        Configuration configuration = configuration().extensions(() -> recorder);
        RequestMatchers requestMatchers = store(configuration, registry(configuration));
        for (String id : new String[]{"a", "b", "c"}) {
            requestMatchers.add(expectation(id, Times.unlimited(), TimeToLive.unlimited()), Cause.API);
            assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.API, id);
        }

        requestMatchers.clear(request("/a"));
        assertChange(recorder.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.API, "a");

        requestMatchers.reset();
        List<String> removed = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ExpectationChange change = recorder.nextChange();
            assertThat(change.type(), is(ExpectationChange.Type.REMOVED));
            assertThat(change.cause(), is(ChangeCause.API));
            removed.add(change.expectation().getId());
        }
        assertThat(removed.stream().sorted().collect(Collectors.toList()), contains("b", "c"));
        recorder.assertNothingMore();
    }

    @Test
    public void shouldReportExhaustedWhenTheResponseThatUsedTheLastMatchCompletes() throws Exception {
        Recorder recorder = new Recorder("listeners-exhausted");
        Configuration configuration = configuration().extensions(() -> recorder);
        RequestMatchers requestMatchers = store(configuration, registry(configuration));
        requestMatchers.add(expectation("twice", Times.exactly(2), TimeToLive.unlimited()), Cause.API);
        assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.API, "twice");

        // a served decrement that leaves a match is not a change
        Expectation first = requestMatchers.firstMatchingExpectation(request("/twice"));
        requestMatchers.postProcess(first);
        recorder.assertNothingMore();

        // the last match is reported as soon as its response completes, with no further request
        Expectation last = requestMatchers.firstMatchingExpectation(request("/twice"));
        requestMatchers.postProcess(last);
        assertChange(recorder.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.EXHAUSTED, "twice");
        recorder.assertNothingMore();
    }

    @Test
    public void shouldReportExpiredAtTheNextRetrievalWhenTheTimeToLiveLapsesWithNoTraffic() throws Exception {
        Recorder recorder = new Recorder("listeners-expired-idle");
        Configuration configuration = configuration().extensions(() -> recorder);
        RequestMatchers requestMatchers = store(configuration, registry(configuration));
        requestMatchers.add(expectation("short-lived", Times.unlimited(), TimeToLive.exactly(MILLISECONDS, 50L)), Cause.API);
        assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.API, "short-lived");

        Thread.sleep(150);
        recorder.assertNothingMore();
        assertThat(requestMatchers.retrieveActiveExpectations(null), is(empty()));
        assertChange(recorder.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.EXPIRED, "short-lived");
    }

    @Test
    public void shouldReportExpiredFromTheResponseDuringWhichTheTimeToLiveLapsed() throws Exception {
        Recorder recorder = new Recorder("listeners-expired-in-flight");
        Configuration configuration = configuration().extensions(() -> recorder);
        RequestMatchers requestMatchers = store(configuration, registry(configuration));
        requestMatchers.add(expectation("lapsing", Times.unlimited(), TimeToLive.exactly(MILLISECONDS, 100L)), Cause.API);
        assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.API, "lapsing");

        Expectation matched = requestMatchers.firstMatchingExpectation(request("/lapsing"));
        assertThat(matched, notNullValue());
        Thread.sleep(200);
        requestMatchers.postProcess(matched);
        assertChange(recorder.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.EXPIRED, "lapsing");
    }

    @Test
    public void shouldReportExhaustedWhenAnExpectationIsBothUsedUpAndExpired() throws Exception {
        Recorder recorder = new Recorder("listeners-exhausted-and-expired");
        Configuration configuration = configuration().extensions(() -> recorder);
        RequestMatchers requestMatchers = store(configuration, registry(configuration));
        requestMatchers.add(expectation("both", Times.once(), TimeToLive.exactly(MILLISECONDS, 100L)), Cause.API);
        assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.API, "both");

        Expectation matched = requestMatchers.firstMatchingExpectation(request("/both"));
        Thread.sleep(200);
        requestMatchers.postProcess(matched);
        assertChange(recorder.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.EXHAUSTED, "both");
    }

    @Test
    public void shouldReportAFleetExhaustedExpectationAsExhaustedAndRemoteChangesAsCluster() throws Exception {
        Recorder local = new Recorder("listeners-fleet-local");
        Recorder peer = new Recorder("listeners-fleet-peer");
        InMemoryStateBackend sharedBackend = new InMemoryStateBackend(10) {
            @Override
            public boolean isClustered() {
                return true;
            }
        };
        Configuration localConfiguration = configuration().maxExpectations(10).extensions(() -> local);
        Configuration peerConfiguration = configuration().maxExpectations(10).extensions(() -> peer);
        RequestMatchers localStore = new RequestMatchers(localConfiguration, logger, new Scheduler(localConfiguration, logger, true), mock(WebSocketClientRegistry.class), registry(localConfiguration));
        RequestMatchers peerStore = new RequestMatchers(peerConfiguration, logger, new Scheduler(peerConfiguration, logger, true), mock(WebSocketClientRegistry.class), registry(peerConfiguration));
        localStore.setStateBackend(sharedBackend);
        peerStore.setStateBackend(sharedBackend);

        localStore.add(expectation("fleet", Times.once(), TimeToLive.unlimited()), Cause.API);
        assertChange(local.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.API, "fleet");
        peerStore.reconcileFromBackend();
        assertChange(peer.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.CLUSTER, "fleet");

        // the shared counter is used up, but the node-local Times never is, so the serving node removes nothing yet
        Expectation served = localStore.firstMatchingExpectation(request("/fleet"));
        assertThat(served, notNullValue());
        localStore.postProcess(served);
        local.assertNothingMore();

        // the next attempt to match finds the fleet-wide counter exhausted
        assertThat(localStore.firstMatchingExpectation(request("/fleet")), nullValue());
        ExpectationChange exhausted = local.nextChange();
        assertThat(exhausted.type(), is(ExpectationChange.Type.REMOVED));
        assertThat(exhausted.cause(), is(ChangeCause.EXHAUSTED));
        assertThat(exhausted.isFleetWide(), is(true));
        assertThat(exhausted.toString(), containsString("fleet-wide"));

        peerStore.reconcileFromBackend();
        assertChange(peer.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.CLUSTER, "fleet");
        local.assertNothingMore();
        peer.assertNothingMore();
    }

    @Test
    public void shouldReportInitialiserFileWatcherAndEvictionCauses() throws Exception {
        Recorder recorder = new Recorder("listeners-causes");
        Configuration configuration = configuration().maxExpectations(2).extensions(() -> recorder);
        RequestMatchers requestMatchers = store(configuration, registry(configuration));

        requestMatchers.update(new Expectation[]{expectation("file", Times.unlimited(), TimeToLive.unlimited())}, new Cause("expectations.json", Cause.Type.FILE_INITIALISER));
        assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.FILE_INITIALISER, "file");

        // the watched file now holds a different expectation: the new one is added, then the old one removed
        requestMatchers.update(new Expectation[]{expectation("watched", Times.unlimited(), TimeToLive.unlimited())}, new Cause("expectations.json", Cause.Type.FILE_INITIALISER), true);
        assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.FILE_WATCHER, "watched");
        assertChange(recorder.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.FILE_WATCHER, "file");

        requestMatchers.update(new Expectation[]{expectation("class", Times.unlimited(), TimeToLive.unlimited())}, new Cause("com.example.Initializer", Cause.Type.CLASS_INITIALISER));
        assertChange(recorder.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.CLASS_INITIALISER, "class");

        // maxExpectations is 2, so the oldest is evicted
        requestMatchers.add(expectation("third", Times.unlimited(), TimeToLive.unlimited()), Cause.API);
        List<ExpectationChange> changes = new ArrayList<>();
        changes.add(recorder.nextChange());
        changes.add(recorder.nextChange());
        ExpectationChange evicted = changes.stream().filter(change -> change.type() == ExpectationChange.Type.REMOVED).findFirst().orElse(null);
        assertThat(String.valueOf(changes), evicted, notNullValue());
        assertChange(evicted, ExpectationChange.Type.REMOVED, ChangeCause.EVICTED, "watched");
        recorder.assertNothingMore();
    }

    @Test
    public void shouldReportAnExtensionsOwnInitialisedExpectationsWhenTheServerStarts() throws Exception {
        LifecycleOnly listener = new LifecycleOnly();
        Configuration configuration = configuration().extensions(() -> listener, Initializer::new);
        ExtensionRegistry registry = registry(configuration);
        HttpState httpState = new HttpState(configuration, logger, mock(Scheduler.class), registry);
        httpStates.add(httpState);

        ExpectationChange change = listener.changes.poll(10, SECONDS);
        assertThat(change, notNullValue());
        assertThat(change.type(), is(ExpectationChange.Type.ADDED));
        assertThat(change.cause(), is(ChangeCause.CLASS_INITIALISER));
        assertThat(String.valueOf(change.expectation().getHttpRequest()), containsString("/from-extension"));
    }

    @Test
    public void shouldLeaveTheLegacyListenerFiringSetUnchanged() {
        assertThat(legacyNotifications(null), is(legacyNotifications(new Recorder("listeners-legacy"))));
    }

    private List<String> legacyNotifications(Recorder recorder) {
        Configuration configuration = configuration().maxExpectations(10);
        ExtensionRegistry registry = null;
        if (recorder != null) {
            configuration.extensions(() -> recorder);
            registry = registry(configuration);
        }
        RequestMatchers requestMatchers = store(configuration, registry);
        List<String> notifications = new ArrayList<>();
        requestMatchers.registerListener((matchers, cause) -> notifications.add(cause.getType() + ":" + cause.getSource()));
        requestMatchers.add(expectation("a", Times.exactly(2), TimeToLive.unlimited()), Cause.API);
        requestMatchers.add(expectation("b", Times.unlimited(), TimeToLive.unlimited()), Cause.API);
        requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(request("/a")));
        requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(request("/a")));
        requestMatchers.update(new Expectation[]{expectation("f", Times.unlimited(), TimeToLive.unlimited())}, new Cause("file", Cause.Type.FILE_INITIALISER), true);
        requestMatchers.update(new Expectation[0], new Cause("file", Cause.Type.FILE_INITIALISER), true);
        requestMatchers.clear(request("/b"));
        requestMatchers.reset();
        return notifications;
    }

    @Test
    public void shouldHoldNoDispatchWhenNoStartedExtensionIsAListener() {
        Configuration configuration = configuration().extensions(NotAListener.class);
        ExtensionRegistry registry = registry(configuration);
        assertThat(registry.listenerDispatch(), nullValue());
        assertThat(registry.lifecycleDispatch(), nullValue());
        assertThat(registry.serveEventDispatch(), nullValue());
        // a lifecycle-only listener has no serve-event dispatch
        ExtensionRegistry lifecycleOnly = registry(configuration().extensions(LifecycleOnly::new));
        assertThat(lifecycleOnly.lifecycleDispatch(), notNullValue());
        assertThat(lifecycleOnly.serveEventDispatch(), nullValue());
    }

    // H8

    @Test
    public void shouldReportAConfigurationUpdateFromTheControlPlaneOnly() throws Exception {
        Recorder recorder = new Recorder("listeners-settings");
        Configuration configuration = configuration().extensions(() -> recorder);
        HttpState httpState = new HttpState(configuration, logger, mock(Scheduler.class), registry(configuration));
        httpStates.add(httpState);

        httpState.applyConfigurationUpdate();
        recorder.assertNothingMore();

        httpState.applyConfigurationUpdate(new ConfigurationDTO().setMaxExpectations(123));
        Object event = recorder.events.poll(10, SECONDS);
        assertThat(event, sameInstance(configuration));
        assertThat(recorder.threads.get(0), is("MockServer-ext-listener-listeners-settings"));
    }

    // H3

    private MockServerEventLog eventLog(Configuration configuration, ExtensionRegistry registry) {
        MockServerEventLog eventLog = new MockServerEventLog(configuration, logger, mock(Scheduler.class), true, registry);
        eventLogs.add(eventLog);
        return eventLog;
    }

    private static LogEntry served(LogEntry.LogMessageType type, String path, String body) {
        HttpRequest request = request(path).withBody(body);
        return new LogEntry()
            .setType(type)
            .setLogLevel(Level.INFO)
            .setCorrelationId("correlation-" + path)
            .setHttpRequest(request)
            .setHttpResponse(response(body))
            .setExpectationId(type == EXPECTATION_RESPONSE ? "expectation-" + path : null)
            .setMessageFormat("served:{}")
            .setArguments(request);
    }

    @Test
    public void shouldReportEachAnsweredRequestAfterBodyTruncation() throws Exception {
        Recorder recorder = new Recorder("listeners-served");
        Configuration configuration = configuration().maxLoggedBodyBytes(8).extensions(() -> recorder);
        MockServerEventLog eventLog = eventLog(configuration, registry(configuration));

        eventLog.add(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setHttpRequest(request("/ignored")));
        eventLog.add(served(EXPECTATION_RESPONSE, "/mocked", "a body longer than eight bytes"));
        eventLog.add(served(NO_MATCH_RESPONSE, "/unmatched", "short"));
        eventLog.add(served(FORWARDED_REQUEST, "/forwarded", "short"));
        eventLog.add(served(FORWARDED_REQUEST, "/reimported", "short").setSkipRecordedRequestPersistence(true));
        eventLog.add(new LogEntry().setType(EXPECTATION_RESPONSE).setLogLevel(Level.INFO).setHttpRequest(request("/request-only")));

        ServeEvent mocked = recorder.nextServeEvent();
        assertThat(mocked.disposition(), is(ServeEvent.Disposition.MOCKED));
        assertThat(mocked.expectationId(), is("expectation-/mocked"));
        assertThat(mocked.correlationId(), is("correlation-/mocked"));
        assertThat(((HttpRequest) mocked.request()).getPath().getValue(), is("/mocked"));
        assertThat("bodies honour maxLoggedBodyBytes", mocked.response().getFirstHeader(MockServerEventLog.TRUNCATED_BODY_HEADER), is("30"));
        assertThat(mocked.response().getBodyAsString().length() < 30, is(true));
        assertThat(mocked.epochTimeMillis() > 0, is(true));

        ServeEvent unmatched = recorder.nextServeEvent();
        assertThat(unmatched.disposition(), is(ServeEvent.Disposition.NOT_MATCHED));
        assertThat(unmatched.expectationId(), nullValue());
        assertThat(recorder.nextServeEvent().disposition(), is(ServeEvent.Disposition.FORWARDED));
        recorder.assertNothingMore();
    }

    @Test
    public void shouldRetainTheSameEventLogBytesWithAServeEventListenerLoaded() throws Exception {
        Configuration without = configuration();
        MockServerEventLog plain = eventLog(without, null);
        Recorder recorder = new Recorder("listeners-weight");
        Configuration with = configuration().extensions(() -> recorder);
        MockServerEventLog listened = eventLog(with, registry(with));
        for (int i = 0; i < 20; i++) {
            plain.add(served(EXPECTATION_RESPONSE, "/weight-" + i, "{\"value\": " + i + ", \"padding\": \"" + "x".repeat(200) + "\"}"));
            listened.add(served(EXPECTATION_RESPONSE, "/weight-" + i, "{\"value\": " + i + ", \"padding\": \"" + "x".repeat(200) + "\"}"));
        }
        for (int i = 0; i < 20; i++) {
            recorder.nextServeEvent();
        }
        awaitRetained(plain, 20);
        awaitRetained(listened, 20);
        assertThat(listened.getRetainedBytes(), is(plain.getRetainedBytes()));
    }

    private static void awaitRetained(MockServerEventLog eventLog, int entries) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (eventLog.size() < entries && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(eventLog.size(), is(entries));
    }

    @Test
    public void shouldKeepABlockedListenerWithinItsByteShareWhileAnotherStillReceivesEvents() throws Exception {
        // distinct classes: the loader instantiates each extension class once
        Recorder blocked = new Recorder("listeners-blocked") {
        };
        blocked.gate = new CountDownLatch(1);
        Recorder healthy = new Recorder("listeners-healthy") {
        };
        long maxBytes = 200_000;
        Configuration configuration = configuration().extensionListenerMaxBytes(maxBytes).extensions(() -> blocked, () -> healthy);
        ExtensionRegistry registry = registry(configuration);
        MockServerEventLog eventLog = eventLog(configuration, registry);
        cleanUp.add(() -> blocked.gate.countDown());
        ListenerQueue blockedQueue = registry.listenerDispatch().queue("listeners-blocked");
        assertThat("each serve-event listener gets an equal share", blockedQueue.maxBytes(), is(maxBytes / 2));

        String largeBody = "x".repeat(20_000);
        for (int i = 0; i < 40; i++) {
            eventLog.add(served(EXPECTATION_RESPONSE, "/large-" + i, largeBody));
            // one at a time, so the healthy listener is never behind and only the blocked one fills its share
            healthy.nextServeEvent();
        }
        assertThat(blocked.entered.await(10, SECONDS), is(true));
        assertThat(blockedQueue.pendingBytes(), lessThanOrEqualTo(maxBytes / 2));
        assertThat(blockedQueue.dropped(ListenerQueue.DropReason.BYTES) > 0, is(true));
        assertThat(registry.listenerDispatch().queue("listeners-healthy").dropped(ListenerQueue.DropReason.BYTES), is(0L));
    }

    @Test
    public void shouldKeepTheExpectationsABlockedLifecycleListenerHoldsWithinItsByteShare() throws Exception {
        LifecycleOnly blocked = new LifecycleOnly() {
            final CountDownLatch gate = new CountDownLatch(1);

            @Override
            public void onChange(ExpectationChange change) {
                try {
                    gate.await(30, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.onChange(change);
            }
        };
        long maxBytes = 2_000_000;
        Configuration configuration = configuration().extensionListenerMaxBytes(maxBytes).extensions(() -> blocked);
        ExtensionRegistry registry = registry(configuration);
        RequestMatchers requestMatchers = store(configuration, registry);
        ListenerQueue queue = registry.listenerDispatch().queue("listeners-lifecycle-only");
        assertThat(queue.maxBytes(), is(maxBytes));

        // each re-add of a large expectation supersedes the last, and every superseded version is held by a queued event
        String largeBody = "x".repeat(100_000);
        for (int i = 0; i < 100; i++) {
            requestMatchers.add(new Expectation(request("/large"), Times.unlimited(), TimeToLive.unlimited(), 0).withId("large").thenRespond(response(largeBody + i)), Cause.API);
        }
        assertThat(queue.pendingBytes(), lessThanOrEqualTo(maxBytes));
        assertThat(queue.dropped(ListenerQueue.DropReason.BYTES) > 0, is(true));
        assertThat(queue.dropped(ListenerQueue.DropReason.COUNT), is(0L));
    }

    @Test
    public void shouldOnlyOfferFromAnEventLoopThatRemovesAnExpectation() throws Exception {
        Recorder blocked = new Recorder("listeners-event-loop");
        blocked.gate = new CountDownLatch(1);
        Configuration configuration = configuration().extensionListenerQueueSize(10).extensions(() -> blocked);
        ExtensionRegistry registry = registry(configuration);
        RequestMatchers requestMatchers = store(configuration, registry);
        io.netty.channel.DefaultEventLoopGroup eventLoop = new io.netty.channel.DefaultEventLoopGroup(1);
        cleanUp.add(eventLoop::shutdownGracefully);
        cleanUp.add(() -> blocked.gate.countDown());
        List<Expectation> served = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            requestMatchers.add(expectation("loop-" + i, Times.once(), TimeToLive.unlimited()), Cause.API);
            served.add(requestMatchers.firstMatchingExpectation(request("/loop-" + i)));
        }

        // the response-completion thread of a binary or gRPC-bidi exchange is an event loop: with the listener stuck
        // and its queue full, each removal there is still only an offer, refused at once
        eventLoop.submit(() -> served.forEach(requestMatchers::postProcess)).get(10, SECONDS);
        assertThat(requestMatchers.size(), is(0));
        assertThat(registry.listenerDispatch().queue("listeners-event-loop").dropped(ListenerQueue.DropReason.COUNT) >= 50, is(true));

        blocked.gate.countDown();
        long deadline = System.currentTimeMillis() + 10_000;
        while (blocked.events.size() < 10 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        blocked.events.clear();
        requestMatchers.add(expectation("loop-after", Times.once(), TimeToLive.unlimited()), Cause.API);
        assertChange(blocked.nextChange(), ExpectationChange.Type.ADDED, ChangeCause.API, "loop-after");
        Expectation last = requestMatchers.firstMatchingExpectation(request("/loop-after"));
        eventLoop.submit(() -> requestMatchers.postProcess(last)).get(10, SECONDS);
        assertChange(blocked.nextChange(), ExpectationChange.Type.REMOVED, ChangeCause.EXHAUSTED, "loop-after");
    }

    @Test
    public void shouldNotTaintVerificationWhenAListenerDropsEvents() throws Exception {
        Recorder blocked = new Recorder("listeners-drops-verify");
        blocked.gate = new CountDownLatch(1);
        Configuration configuration = configuration().extensionListenerQueueSize(1).extensions(() -> blocked);
        ExtensionRegistry registry = registry(configuration);
        MockServerEventLog eventLog = eventLog(configuration, registry);
        cleanUp.add(() -> blocked.gate.countDown());
        for (int i = 0; i < 10; i++) {
            eventLog.add(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setHttpRequest(request("/verified")));
            eventLog.add(served(EXPECTATION_RESPONSE, "/verified", "body"));
        }
        CompletableFuture<String> result = new CompletableFuture<>();
        eventLog.verify(verification().withRequest(request("/verified")).withTimes(VerificationTimes.exactly(10)), result::complete);
        assertThat(result.get(10, SECONDS), is(""));
        assertThat(registry.listenerDispatch().queue("listeners-drops-verify").dropped(ListenerQueue.DropReason.COUNT) > 0, is(true));
        assertThat(eventLog.getDroppedLogEventCount(), is(0L));
    }

    // bounded queues

    @Test
    public void shouldDropBeyondTheCountBoundWithoutEverWaiting() throws Exception {
        Recorder blocked = new Recorder("listeners-count-bound");
        blocked.gate = new CountDownLatch(1);
        ListenerQueue queue = new ListenerQueue(loaded(blocked), logger, 10, 0);
        cleanUp.add(queue::shutdown);
        cleanUp.add(() -> blocked.gate.countDown());

        int accepted = 0;
        long started = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            if (queue.offer(new ExpectationChange(ExpectationChange.Type.ADDED, ChangeCause.API, null, false), 0L)) {
                accepted++;
            }
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertThat(accepted, is(10));
        assertThat(queue.dropped(ListenerQueue.DropReason.COUNT), is(100_000L - 10));
        assertThat("offers to a blocked listener must not wait", elapsedMillis, lessThan(5_000L));
    }

    @Test
    public void shouldAdmitOneOversizeEventIntoAnEmptyQueueOnly() {
        Recorder blocked = new Recorder("listeners-oversize");
        blocked.gate = new CountDownLatch(1);
        ListenerQueue queue = new ListenerQueue(loaded(blocked), logger, 100, 1_000);
        cleanUp.add(queue::shutdown);
        cleanUp.add(() -> blocked.gate.countDown());

        assertThat(queue.offer(serveEvent(5_000), 5_000), is(true));
        assertThat(queue.offer(serveEvent(1), 1), is(false));
        assertThat(queue.dropped(ListenerQueue.DropReason.BYTES), is(1L));
        assertThat(queue.pendingBytes(), is(5_000L));
    }

    @Test
    public void shouldKeepDeliveringAfterAListenerThrows() throws Exception {
        Recorder recorder = new Recorder("listeners-throws");
        recorder.throwOnEvent = true;
        ListenerQueue queue = new ListenerQueue(loaded(recorder), logger, 10, 0);
        cleanUp.add(queue::shutdown);
        ExpectationChange first = new ExpectationChange(ExpectationChange.Type.ADDED, ChangeCause.API, null, false);
        ExpectationChange second = new ExpectationChange(ExpectationChange.Type.REMOVED, ChangeCause.API, null, false);
        queue.offer(first, 0);
        queue.offer(second, 0);
        assertThat(recorder.nextChange(), sameInstance(second));
        assertThat(queue.errors(), is(1L));
        assertThat(queue.delivered(), is(1L));
    }

    @Test
    public void shouldDeliverWhatIsQueuedOnStopThenEndTheListenerThread() throws Exception {
        Recorder slow = new Recorder("listeners-flush");
        Configuration configuration = configuration().stopDrainMillis(10_000L).extensions(() -> slow);
        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        ListenerDispatch dispatch = registry.lifecycleDispatch();
        AtomicInteger offered = new AtomicInteger();
        for (int i = 0; i < 50; i++) {
            dispatch.added(null, ChangeCause.API);
            offered.incrementAndGet();
        }
        registry.stop();
        assertThat(slow.events.size(), is(offered.get()));
        ListenerQueue queue = registry.listenerDispatch().queue("listeners-flush");
        long deadline = System.currentTimeMillis() + 5_000;
        while (queue.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat("the listener thread ends once stopped", queue.isAlive(), is(false));
        assertThat(registry.maximumStopMillis(), is(10_000L + configuration.extensionStartTimeoutMillis()));
    }

    @Test
    public void shouldDropWhatIsLeftWhenTheFlushBudgetRunsOut() throws Exception {
        Recorder blocked = new Recorder("listeners-flush-budget");
        blocked.gate = new CountDownLatch(1);
        Configuration configuration = configuration().stopDrainMillis(100L).extensions(() -> blocked);
        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        ListenerDispatch dispatch = registry.lifecycleDispatch();
        for (int i = 0; i < 5; i++) {
            dispatch.added(null, ChangeCause.API);
        }
        assertThat(blocked.entered.await(10, SECONDS), is(true));
        long started = System.currentTimeMillis();
        registry.stop();
        assertThat(System.currentTimeMillis() - started, lessThan(5_000L));
        ListenerQueue queue = registry.listenerDispatch().queue("listeners-flush-budget");
        assertThat(queue.dropped(ListenerQueue.DropReason.SHUTDOWN), is(4L));
        blocked.gate.countDown();
        // offers after stop are refused
        dispatch.added(null, ChangeCause.API);
        assertThat(queue.dropped(ListenerQueue.DropReason.SHUTDOWN), is(5L));
    }

    private static ServeEvent serveEvent(long weight) {
        return new ServeEvent(ServeEvent.Disposition.MOCKED, request("/x"), response("x"), null, null, 1L, weight);
    }

    private static LoadedExtension loaded(Extension extension) {
        return new LoadedExtension(extension, extension.name(), 0, "test");
    }
}
