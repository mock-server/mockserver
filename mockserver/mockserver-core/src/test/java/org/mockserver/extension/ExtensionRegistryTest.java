package org.mockserver.extension;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.server.initialize.ExpectationInitializer;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ExtensionRegistryTest {

    // each test's events, in order; every test clears it first (methods of one class run one at a time)
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    public abstract static class Recording implements Extension {
        @Override
        public void start(ExtensionContext context) throws Exception {
            EVENTS.add("start:" + name());
        }

        @Override
        public void onStarted(List<Integer> ports) {
            EVENTS.add("onStarted:" + name() + ":" + ports);
        }

        @Override
        public void stop() {
            EVENTS.add("stop:" + name());
        }
    }

    public static class Alpha extends Recording {
        @Override
        public String name() {
            return "registry-alpha";
        }
    }

    public static class Beta extends Recording {
        @Override
        public String name() {
            return "registry-beta";
        }
    }

    public static class Early extends Recording {
        @Override
        public String name() {
            return "registry-zzz-early";
        }

        @Override
        public int priority() {
            return -10;
        }
    }

    public static class Late extends Recording {
        @Override
        public String name() {
            return "registry-late";
        }

        @Override
        public int priority() {
            return 10;
        }
    }

    public static class FailsToStart extends Recording {
        @Override
        public String name() {
            return "registry-fails";
        }

        @Override
        public int priority() {
            return 5;
        }

        @Override
        public void start(ExtensionContext context) {
            EVENTS.add("start:" + name());
            throw new IllegalStateException("cannot start");
        }
    }

    public static class NeverStarts extends Recording {
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Override
        public String name() {
            return "registry-never-starts";
        }

        @Override
        public int priority() {
            return 5;
        }

        @Override
        public void start(ExtensionContext context) throws InterruptedException {
            EVENTS.add("start:" + name());
            RELEASE.await(30, SECONDS);
        }
    }

    public static class FailsToStop extends Recording {
        @Override
        public String name() {
            return "registry-fails-to-stop";
        }

        @Override
        public void stop() {
            EVENTS.add("stop:" + name());
            throw new IllegalStateException("cannot stop");
        }
    }

    public static class FailsOnStarted extends Recording {
        @Override
        public String name() {
            return "registry-fails-on-started";
        }

        @Override
        public void onStarted(List<Integer> ports) {
            throw new IllegalStateException("cannot finish starting");
        }
    }

    public static class Initializer extends Recording implements ExpectationInitializer {
        @Override
        public String name() {
            return "registry-initializer";
        }

        @Override
        public Expectation[] initializeExpectations() {
            return new Expectation[]{new Expectation(request("/from-extension")).thenRespond(response("hello"))};
        }
    }

    public static class ContextCapturing extends Recording {
        static volatile ExtensionContext captured;

        @Override
        public String name() {
            return "registry-context";
        }

        @Override
        public void start(ExtensionContext context) {
            captured = context;
        }
    }

    public static class Scanned extends Recording {
        @Override
        public String name() {
            return "registry-scanned";
        }
    }

    public static class SameNameAsAlpha extends Recording {
        @Override
        public String name() {
            return "registry-alpha";
        }
    }

    public static class BadName extends Recording {
        @Override
        public String name() {
            return "Not Valid";
        }
    }

    public static class NoNoArgConstructor extends Recording {
        public NoNoArgConstructor(String ignored) {
        }

        @Override
        public String name() {
            return "registry-no-constructor";
        }
    }

    public static class NotAnExtension {
    }

    private CapturingLogger logger;

    @Before
    public void clearEvents() {
        EVENTS.clear();
        logger = new CapturingLogger(configuration().logLevel(Level.INFO));
    }

    @Test
    public void shouldLoadNamedClassesStartThemInPriorityThenNameOrderAndStopThemInReverse() {
        Configuration configuration = configuration().extensions(" " + Beta.class.getName() + " , " + Alpha.class.getName() + "," + Early.class.getName());

        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        registry.notifyStarted(Arrays.asList(1080, 1081));
        registry.stop();
        registry.stop();

        assertThat(EVENTS, contains(
            "start:registry-zzz-early", "start:registry-alpha", "start:registry-beta",
            "onStarted:registry-zzz-early:[1080, 1081]", "onStarted:registry-alpha:[1080, 1081]", "onStarted:registry-beta:[1080, 1081]",
            "stop:registry-beta", "stop:registry-alpha", "stop:registry-zzz-early"
        ));
        assertThat(registry.isStartedNotified(), is(true));
        assertThat(registry.extensions().stream().map(LoadedExtension::name).collect(Collectors.toList()),
            contains("registry-zzz-early", "registry-alpha", "registry-beta"));
        assertThat(logger.messages(Level.INFO), hasItem(containsString("registry-alpha")));
    }

    @Test
    public void shouldGiveEachExtensionTheServersConfigurationAndLogger() {
        Configuration configuration = configuration().extensions(ContextCapturing.class);

        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        try {
            assertThat(ContextCapturing.captured.configuration(), sameInstance(configuration));
            assertThat(ContextCapturing.captured.logger(), sameInstance(logger));
        } finally {
            registry.stop();
        }
    }

    @Test
    public void shouldStopTheStartedExtensionsInReverseOrderWhenALaterOneFailsToStart() {
        Configuration configuration = configuration().extensions(Late.class, Alpha.class, Early.class, FailsToStart.class, Beta.class);

        ExtensionException failure = assertThrows(ExtensionException.class, () -> ExtensionRegistry.start(configuration, logger));

        assertThat(failure.getMessage(), containsString("registry-fails failed to start"));
        assertThat(failure.getCause().getMessage(), is("cannot start"));
        // the failing one was never started, so it is not stopped, and the one after it never starts
        assertThat(EVENTS, contains(
            "start:registry-zzz-early", "start:registry-alpha", "start:registry-beta", "start:registry-fails",
            "stop:registry-beta", "stop:registry-alpha", "stop:registry-zzz-early"
        ));
        assertNoExtensionThreadsRemain();
    }

    @Test
    public void shouldTreatAStartThatTimesOutAsNotStarted() {
        Configuration configuration = configuration()
            .extensions(Alpha.class, NeverStarts.class)
            .extensionStartTimeoutMillis(200L);
        try {
            ExtensionException failure = assertThrows(ExtensionException.class, () -> ExtensionRegistry.start(configuration, logger));

            assertThat(failure.getMessage(), containsString("registry-never-starts did not return from start within 200ms"));
            assertThat(EVENTS, contains("start:registry-alpha", "start:registry-never-starts", "stop:registry-alpha"));
            assertThat(logger.messages(Level.WARN), hasItem(containsString("treated as not started")));
        } finally {
            NeverStarts.RELEASE.countDown();
        }
    }

    @Test
    public void shouldKeepStoppingTheOthersWhenOneFailsToStop() {
        Configuration configuration = configuration().extensions(Alpha.class, FailsToStop.class);

        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        registry.stop();

        assertThat(EVENTS, contains("start:registry-alpha", "start:registry-fails-to-stop", "stop:registry-fails-to-stop", "stop:registry-alpha"));
        assertThat(logger.messages(Level.WARN), hasItem(containsString("failed to stop")));
    }

    @Test
    public void shouldFailWhenOnStartedThrows() {
        ExtensionRegistry registry = ExtensionRegistry.start(configuration().extensions(FailsOnStarted.class), logger);
        try {
            ExtensionException failure = assertThrows(ExtensionException.class, () -> registry.notifyStarted(Arrays.asList(1080)));

            assertThat(failure.getMessage(), containsString("registry-fails-on-started failed in onStarted"));
            assertThat(registry.isStartedNotified(), is(false));
        } finally {
            registry.stop();
        }
    }

    @Test
    public void shouldRefuseAClassThatCannotBeFound() {
        ExtensionException failure = assertThrows(ExtensionException.class,
            () -> ExtensionRegistry.start(configuration().extensions("com.example.DoesNotExist"), logger));

        assertThat(failure.getMessage(), containsString("extension class com.example.DoesNotExist could not be loaded"));
    }

    @Test
    public void shouldRefuseAClassThatIsNotAnExtension() {
        ExtensionException failure = assertThrows(ExtensionException.class,
            () -> ExtensionRegistry.start(configuration().extensions(NotAnExtension.class.getName()), logger));

        assertThat(failure.getMessage(), containsString("does not implement org.mockserver.extension.Extension"));
    }

    @Test
    public void shouldRefuseAClassWithoutANoArgumentConstructor() {
        ExtensionException failure = assertThrows(ExtensionException.class,
            () -> ExtensionRegistry.start(configuration().extensions(NoNoArgConstructor.class.getName()), logger));

        assertThat(failure.getMessage(), containsString("has no public no-argument constructor"));
    }

    @Test
    public void shouldRefuseTwoExtensionsWithTheSameName() {
        ExtensionException failure = assertThrows(ExtensionException.class,
            () -> ExtensionRegistry.start(configuration().extensions(Alpha.class, SameNameAsAlpha.class), logger));

        assertThat(failure.getMessage(), containsString("both have the name \"registry-alpha\""));
        assertThat("nothing starts when loading fails", EVENTS, is(empty()));
    }

    @Test
    public void shouldRefuseAnInvalidName() {
        ExtensionException failure = assertThrows(ExtensionException.class,
            () -> ExtensionRegistry.start(configuration().extensions(BadName.class), logger));

        assertThat(failure.getMessage(), containsString("has the name \"Not Valid\""));
    }

    @Test
    public void shouldRefuseASupplierThatReturnsNull() {
        ExtensionException failure = assertThrows(ExtensionException.class,
            () -> ExtensionRegistry.start(configuration().extensions(() -> null), logger));

        assertThat(failure.getMessage(), containsString("supplier returned null"));
    }

    @Test
    public void shouldRefuseASupplierThatThrows() {
        ExtensionException failure = assertThrows(ExtensionException.class,
            () -> ExtensionRegistry.start(configuration().extensions(() -> {
                throw new IllegalStateException("no extension for you");
            }), logger));

        assertThat(failure.getMessage(), containsString("an extension supplier threw"));
        assertThat(failure.getCause().getMessage(), is("no extension for you"));
    }

    @Test
    public void shouldNotifyStartedOnlyOnceAndNotAfterStopping() {
        ExtensionRegistry registry = ExtensionRegistry.start(configuration().extensions(Alpha.class), logger);
        registry.notifyStarted(Arrays.asList(1));
        registry.notifyStarted(Arrays.asList(2));
        registry.stop();

        ExtensionRegistry stoppedFirst = ExtensionRegistry.start(configuration().extensions(Beta.class), logger);
        stoppedFirst.stop();
        stoppedFirst.notifyStarted(Arrays.asList(3));

        assertThat(EVENTS, contains("start:registry-alpha", "onStarted:registry-alpha:[1]", "stop:registry-alpha",
            "start:registry-beta", "stop:registry-beta"));
        assertThat(stoppedFirst.isStartedNotified(), is(false));
        assertThat("one started extension at the default timeout", registry.maximumStopMillis(), is(30_000L));
    }

    @Test
    public void shouldLoadAClassOnceHoweverManySourcesNameIt() {
        Configuration configuration = configuration()
            .extensions(Alpha.class.getName() + "," + Alpha.class.getName())
            .extensions(Alpha.class);

        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        registry.stop();

        assertThat(registry.extensions(), hasSize(1));
        assertThat(EVENTS, contains("start:registry-alpha", "stop:registry-alpha"));
    }

    @Test
    public void shouldGiveEachRegistryBuiltFromOneConfigurationItsOwnInstances() {
        Configuration configuration = configuration()
            .extensions(Alpha.class.getName())
            .extensions(Beta::new);

        ExtensionRegistry first = ExtensionRegistry.start(configuration, logger);
        ExtensionRegistry second = ExtensionRegistry.start(configuration, logger);
        first.stop();
        second.stop();

        for (int i = 0; i < 2; i++) {
            assertThat(first.extensions().get(i).name(), is(second.extensions().get(i).name()));
            assertThat(first.extensions().get(i).extension(), not(sameInstance(second.extensions().get(i).extension())));
        }
    }

    @Test
    public void shouldNotLookForServiceEntriesUnlessScanningIsOn() throws IOException {
        Path directory = servicesDirectory(Scanned.class.getName());
        try (RecordingClassLoader classLoader = new RecordingClassLoader(directory, getClass().getClassLoader())) {
            ExtensionRegistry withoutScanning = withContextClassLoader(classLoader,
                () -> ExtensionRegistry.start(configuration().extensions(Alpha.class), logger));
            withoutScanning.stop();
            assertThat(classLoader.requestedResources, not(hasItem("META-INF/services/org.mockserver.extension.Extension")));
            assertThat(withoutScanning.extensions().stream().map(LoadedExtension::name).collect(Collectors.toList()), contains("registry-alpha"));

            ExtensionRegistry withScanning = withContextClassLoader(classLoader,
                () -> ExtensionRegistry.start(configuration().extensions(Alpha.class).extensionScanning(true), logger));
            withScanning.stop();
            assertThat(withScanning.extensions().stream().map(LoadedExtension::name).collect(Collectors.toList()), contains("registry-alpha", "registry-scanned"));
            assertThat(withScanning.extensions().get(1).source(), notNullValue());
        }
    }

    @Test
    public void shouldExpressAHookThatIsLoadedButDisabledWithItsReason() {
        ExtensionRegistry registry = ExtensionRegistry.start(configuration().extensions(Initializer.class, Alpha.class), logger);
        try {
            LoadedExtension initializer = registry.extensions().get(1);
            assertThat(initializer.name(), is("registry-initializer"));
            assertThat(initializer.hooks(), contains(ExtensionHook.EXPECTATION_INITIALIZER));
            assertThat(initializer.isHookEnabled(ExtensionHook.EXPECTATION_INITIALIZER), is(true));

            int disabled = registry.disableHook(ExtensionHook.EXPECTATION_INITIALIZER, "it is switched off for this test");

            assertThat(disabled, is(1));
            assertThat(initializer.isHookEnabled(ExtensionHook.EXPECTATION_INITIALIZER), is(false));
            assertThat(initializer.disabledReason(ExtensionHook.EXPECTATION_INITIALIZER), is("it is switched off for this test"));
            assertThat(registry.extensions().get(0).disabledReason(ExtensionHook.EXPECTATION_INITIALIZER), nullValue());
            assertThat(logger.messages(Level.WARN), hasItem(containsString("it is switched off for this test")));
            assertThat("disabling twice reports nothing new", registry.disableHook(ExtensionHook.EXPECTATION_INITIALIZER, "again"), is(0));
        } finally {
            registry.stop();
        }
    }

    @Test
    public void shouldLoadTheExpectationsOfAnExtensionThatIsAnInitializer() {
        Configuration configuration = configuration().extensions(Initializer.class);
        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        HttpState httpState = new HttpState(configuration, logger, mock(Scheduler.class), registry);
        try {
            List<Expectation> active = httpState.getRequestMatchers().retrieveActiveExpectations(null);
            assertThat(active, hasSize(1));
            assertThat(String.valueOf(active.get(0).getHttpRequest()), containsString("/from-extension"));
            assertThat(httpState.getExtensionRegistry(), sameInstance(registry));
            assertThat("not ready until onStarted has run", httpState.isInitializationComplete(), is(false));
            registry.notifyStarted(new ArrayList<>());
            assertThat(httpState.isInitializationComplete(), is(true));
        } finally {
            httpState.stop();
            registry.stop();
        }
    }

    @Test
    public void shouldNotLoadTheExpectationsOfADisabledInitializer() {
        Configuration configuration = configuration().extensions(Initializer.class);
        ExtensionRegistry registry = ExtensionRegistry.start(configuration, logger);
        registry.disableHook(ExtensionHook.EXPECTATION_INITIALIZER, "test");
        HttpState httpState = new HttpState(configuration, logger, mock(Scheduler.class), registry);
        try {
            assertThat(httpState.getRequestMatchers().retrieveActiveExpectations(null), is(empty()));
        } finally {
            httpState.stop();
            registry.stop();
        }
    }

    @Test
    public void shouldReportNoExtensionsConfiguredByDefault() {
        assertThat(configuration().extensionsConfigured(), is(false));
        assertThat(configuration().extensions("").extensionScanning(false).extensionsConfigured(), is(false));
        assertThat(configuration().extensions(Alpha.class.getName()).extensionsConfigured(), is(true));
        assertThat(configuration().extensionScanning(true).extensionsConfigured(), is(true));
        assertThat(configuration().extensions(Alpha.class).extensionsConfigured(), is(true));
        assertThat(configuration().extensions(Alpha::new).extensionsConfigured(), is(true));
    }

    private static void assertNoExtensionThreadsRemain() {
        long deadline = System.currentTimeMillis() + 5_000;
        List<String> remaining;
        do {
            remaining = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith("MockServer-ext-") && name.contains("registry-"))
                .collect(Collectors.toList());
        } while (!remaining.isEmpty() && System.currentTimeMillis() < deadline && pause());
        assertThat(remaining, is(empty()));
    }

    private static boolean pause() {
        try {
            Thread.sleep(20);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private interface Action<T> {
        T run();
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, Action<T> action) {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return action.run();
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static Path servicesDirectory(String className) throws IOException {
        Path directory = Files.createTempDirectory("extension-services");
        Path services = directory.resolve("META-INF/services/org.mockserver.extension.Extension");
        Files.createDirectories(services.getParent());
        Files.write(services, (className + "\n").getBytes(StandardCharsets.UTF_8));
        services.toFile().deleteOnExit();
        services.getParent().toFile().deleteOnExit();
        services.getParent().getParent().toFile().deleteOnExit();
        directory.toFile().deleteOnExit();
        return directory;
    }

    private static final class RecordingClassLoader extends URLClassLoader {
        private final List<String> requestedResources = new CopyOnWriteArrayList<>();

        private RecordingClassLoader(Path directory, ClassLoader parent) throws IOException {
            super(new URL[]{directory.toUri().toURL()}, parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResources.add(name);
            return super.getResources(name);
        }
    }

    private static final class CapturingLogger extends MockServerLogger {
        private final List<String> entries = new CopyOnWriteArrayList<>();

        private CapturingLogger(Configuration configuration) {
            super(configuration, ExtensionRegistryTest.class);
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            entries.add(logEntry.getLogLevel() + " " + logEntry.getMessage());
        }

        List<String> messages(Level level) {
            return entries.stream().filter(entry -> entry.startsWith(level + " ")).collect(Collectors.toList());
        }
    }
}
