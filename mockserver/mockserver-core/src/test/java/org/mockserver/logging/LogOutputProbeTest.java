package org.mockserver.logging;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.helpers.NOPLogger;
import org.slf4j.helpers.SubstituteLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * Each logger here has a name of its own, with parent handlers turned off, so no other test's output, and no
 * global logging configuration, reaches it.
 */
public class LogOutputProbeTest {

    // the java.util.logging manager holds loggers weakly; these keep each test's loggers configured
    private final List<java.util.logging.Logger> julLoggers = new ArrayList<>();

    private java.util.logging.Logger julLogger(String name) {
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(name);
        julLogger.setLevel(java.util.logging.Level.ALL);
        julLoggers.add(julLogger);
        return julLogger;
    }

    private static String uniqueName() {
        return "org.mockserver.test.probe" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    public void shouldUseTheJavaUtilLoggingProvider() {
        assertThat(LoggerFactory.getLogger(uniqueName()).getClass().getName(), endsWith(".jul.JDK14LoggerAdapter"));
    }

    @Test
    public void shouldDiscardWhenNoHandlerIsReachable() {
        String name = uniqueName();
        julLogger(name).setUseParentHandlers(false);

        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(name), Level.INFO), is(true));
        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(name), Level.ERROR), is(true));
    }

    @Test
    public void shouldNotDiscardWhenTheLoggerHasAHandler() {
        String name = uniqueName();
        java.util.logging.Logger julLogger = julLogger(name);
        julLogger.setUseParentHandlers(false);
        julLogger.addHandler(new CapturingHandler());

        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(name), Level.INFO), is(false));
    }

    @Test
    public void shouldNotDiscardWhenAParentItPassesRecordsToHasAHandler() {
        String parent = uniqueName();
        java.util.logging.Logger parentLogger = julLogger(parent);
        parentLogger.setUseParentHandlers(false);
        parentLogger.addHandler(new CapturingHandler());
        julLogger(parent + ".child");

        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(parent + ".child"), Level.INFO), is(false));
    }

    @Test
    public void shouldDiscardWhenTheParentWithAHandlerIsNotPassedRecords() {
        String parent = uniqueName();
        julLogger(parent).addHandler(new CapturingHandler());
        julLogger(parent + ".child").setUseParentHandlers(false);

        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(parent + ".child"), Level.INFO), is(true));
    }

    @Test
    public void shouldDiscardALevelBelowTheLoggersLevel() {
        String name = uniqueName();
        java.util.logging.Logger julLogger = julLogger(name);
        julLogger.setUseParentHandlers(false);
        julLogger.addHandler(new CapturingHandler());
        julLogger.setLevel(java.util.logging.Level.WARNING);

        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(name), Level.INFO), is(true));
        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(name), Level.WARN), is(false));
    }

    @Test
    public void shouldNotDiscardWhenTheLoggerHasAFilter() {
        String name = uniqueName();
        java.util.logging.Logger julLogger = julLogger(name);
        julLogger.setUseParentHandlers(false);
        julLogger.setFilter(record -> true);

        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(name), Level.INFO), is(false));
    }

    @Test
    public void shouldNotDiscardForAnotherProviderOrNoLevel() {
        String name = uniqueName();
        julLogger(name).setUseParentHandlers(false);

        assertThat(LogOutputProbe.discards(mock(Logger.class), Level.INFO), is(false));
        assertThat(LogOutputProbe.discards(null, Level.INFO), is(false));
        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(name), null), is(false));
    }

    @Test
    public void shouldNotDiscardForALoggerThatPrintsWithoutHandlersAsLog4jJulDoes() {
        String name = uniqueName();
        RoutingLogger routing = routingLogger(name);
        Logger logger = LoggerFactory.getLogger(name);
        Configuration configuration = configuration().logLevel(Level.INFO).compactLogFormat(false);
        LogEntry entry = new LogEntry().setLogLevel(Level.ERROR).setMessageFormat("routed:{}").setArguments(request("/path"));

        assertThat(LogOutputProbe.discards(logger, Level.ERROR), is(false));
        assertThat(LogOutputProbe.discards(logger, Level.INFO), is(false));
        MockServerLogger.writeToSystemOut(logger, entry, configuration);

        assertThat(routing.messages, contains(entry.getMessage(configuration)));
    }

    @Test
    public void shouldNotDiscardWhenAParentIsALoggerThatPrintsWithoutHandlers() {
        String parent = uniqueName();
        routingLogger(parent);
        julLogger(parent + ".child");

        assertThat(LogOutputProbe.discards(LoggerFactory.getLogger(parent + ".child"), Level.INFO), is(false));
    }

    // a java.util.logging logger that, like log4j-jul's ApiLogger, prints records itself rather than through handlers
    private RoutingLogger routingLogger(String name) {
        RoutingLogger routing = new RoutingLogger(name);
        assertThat(java.util.logging.LogManager.getLogManager().addLogger(routing), is(true));
        routing.setLevel(java.util.logging.Level.ALL);
        routing.setUseParentHandlers(false);
        julLoggers.add(routing);
        return routing;
    }

    private static class RoutingLogger extends java.util.logging.Logger {
        private final List<String> messages = new ArrayList<>();

        RoutingLogger(String name) {
            super(name, null);
        }

        @Override
        public synchronized void log(LogRecord record) {
            messages.add(record.getMessage());
        }
    }

    @Test
    public void shouldDiscardForTheNoOperationLoggerAndAStandInForIt() {
        String name = uniqueName();
        java.util.logging.Logger julLogger = julLogger(name);
        julLogger.setUseParentHandlers(false);
        julLogger.addHandler(new CapturingHandler());
        SubstituteLogger noOperation = new SubstituteLogger(name, new LinkedBlockingQueue<>(), true);
        SubstituteLogger printing = new SubstituteLogger(name, new LinkedBlockingQueue<>(), true);
        printing.setDelegate(LoggerFactory.getLogger(name));
        SubstituteLogger recording = new SubstituteLogger(name, new LinkedBlockingQueue<>(), false);

        assertThat(LogOutputProbe.discards(NOPLogger.NOP_LOGGER, Level.INFO), is(true));
        assertThat(LogOutputProbe.discards(noOperation, Level.INFO), is(true));
        assertThat(LogOutputProbe.discards(printing, Level.INFO), is(false));
        assertThat(LogOutputProbe.discards(recording, Level.INFO), is(false));
    }

    @Test
    public void shouldNotRenderAMessageNothingPrintsAndRenderItForAReaderLater() {
        String name = uniqueName();
        java.util.logging.Logger julLogger = julLogger(name);
        julLogger.setUseParentHandlers(false);
        Logger logger = LoggerFactory.getLogger(name);
        Configuration configuration = configuration().logLevel(Level.INFO).compactLogFormat(false);
        CountingArgument argument = new CountingArgument();
        LogEntry entry = new LogEntry().setLogLevel(Level.INFO).setMessageFormat("value:{}").setArguments(argument, request("/path"));

        MockServerLogger.writeToSystemOut(logger, entry, configuration);
        MockServerLogger.writeToSystemOut(logger, entry);

        assertThat(argument.renders.get(), is(0));
        assertThat(entry.getMessage(configuration), is("value:" + NEW_LINE + NEW_LINE + "  counted" + NEW_LINE));
        assertThat(argument.renders.get(), is(1));
    }

    @Test
    public void shouldWriteTheRenderedMessageWhenAHandlerIsAttached() {
        String name = uniqueName();
        java.util.logging.Logger julLogger = julLogger(name);
        julLogger.setUseParentHandlers(false);
        CapturingHandler handler = new CapturingHandler();
        julLogger.addHandler(handler);
        Logger logger = LoggerFactory.getLogger(name);
        Configuration configuration = configuration().logLevel(Level.INFO).compactLogFormat(false);
        LogEntry entry = new LogEntry().setLogLevel(Level.INFO).setPort(1080).setMessageFormat("received request:{}").setArguments(request("/path").withBody("a\nb"));

        MockServerLogger.writeToSystemOut(logger, entry, configuration);

        assertThat(handler.messages, contains("1080 " + entry.getMessage(configuration)));
    }

    private static class CountingArgument {
        private final AtomicInteger renders = new AtomicInteger();

        @Override
        public String toString() {
            renders.incrementAndGet();
            return "counted";
        }
    }

    private static class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
