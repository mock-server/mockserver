package org.mockserver.templates.engine;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * The {@code systemValue} helper's behaviour, run against each template engine by a subclass. It reads only
 * variables every JVM and CI machine has ({@code PATH}, {@code java.version}, {@code java.vendor}), so it changes no
 * global state. Each allow list carries a unique entry, because parsed allow lists, and the names already warned
 * about, are shared by every engine with the same list.
 */
public abstract class SystemValueTemplateBehaviour<E> {

    protected abstract E engine(Configuration configuration, MockServerLogger mockServerLogger);

    protected abstract String env(E engine, String name);

    protected abstract String property(E engine, String name);

    private static String uniqueAllowList(String names) {
        return names + ",UNUSED_" + UUID.randomUUID().toString().replace('-', '_');
    }

    private E engine(String allowList, MockServerLogger mockServerLogger) {
        return engine(configuration().templateSystemValueAllowList(allowList), mockServerLogger);
    }

    private static MockServerLogger warnEnabledLogger() {
        MockServerLogger mockServerLogger = mock(MockServerLogger.class);
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        return mockServerLogger;
    }

    private static List<LogEntry> refusals(MockServerLogger mockServerLogger) {
        ArgumentCaptor<LogEntry> logEntries = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockServerLogger, atLeast(0)).logEvent(logEntries.capture());
        return logEntries.getAllValues().stream()
            .filter(logEntry -> logEntry.getMessageFormat().contains("refused to read"))
            .collect(Collectors.toList());
    }

    @Test
    public void shouldReadNothingWhenTheAllowListIsEmpty() {
        assumeTrue("PATH is set", System.getenv("PATH") != null);
        E engine = engine("", warnEnabledLogger());

        assertThat(env(engine, "PATH"), is(""));
        assertThat(property(engine, "java.version"), is(""));
    }

    @Test
    public void shouldReadNothingByDefault() {
        assumeTrue("PATH is set", System.getenv("PATH") != null);
        assumeTrue("no allow list is configured for this JVM", ConfigurationProperties.templateSystemValueAllowList().isEmpty());
        E engine = engine(configuration(), warnEnabledLogger());

        assertThat(env(engine, "PATH"), is(""));
        assertThat(property(engine, "java.version"), is(""));
    }

    @Test
    public void shouldReadExactNamesOnTheAllowListOnly() {
        assumeTrue("PATH is set", System.getenv("PATH") != null);
        E engine = engine(uniqueAllowList(" PATH , java.version "), warnEnabledLogger());

        assertThat(env(engine, "PATH"), is(System.getenv("PATH")));
        assertThat(property(engine, "java.version"), is(System.getProperty("java.version")));
        assertThat(property(engine, "java.vendor"), is(""));
        assertThat(property(engine, "java.versio"), is(""));
        assertThat(env(engine, "java.version"), is(""));
    }

    @Test
    public void shouldReadNamesStartingWithAnAllowedPrefix() {
        assumeTrue("PATH is set", System.getenv("PATH") != null);
        E engine = engine(uniqueAllowList("PA*,java.ver*"), warnEnabledLogger());

        assertThat(env(engine, "PATH"), is(System.getenv("PATH")));
        assertThat(property(engine, "java.version"), is(System.getProperty("java.version")));
        assertThat(property(engine, "java.vendor"), is(""));
        assertThat(env(engine, "HOME"), is(""));
    }

    @Test
    public void shouldNotLetAWildcardAllowEveryName() {
        assumeTrue("PATH is set", System.getenv("PATH") != null);
        MockServerLogger mockServerLogger = warnEnabledLogger();
        E engine = engine(uniqueAllowList("*, P*TH ,java.*.version"), mockServerLogger);

        assertThat(env(engine, "PATH"), is(""));
        assertThat(property(engine, "java.version"), is(""));
        assertThat(property(engine, "*"), is(""));
    }

    @Test
    public void shouldReturnEmptyForAnAllowedNameThatIsNotSet() {
        String unset = "UNSET_" + UUID.randomUUID().toString().replace('-', '_');
        E engine = engine(uniqueAllowList(unset), warnEnabledLogger());

        assertThat(env(engine, unset), is(""));
        assertThat(property(engine, unset), is(""));
    }

    @Test
    public void shouldWarnOnceForEachRefusedName() {
        MockServerLogger mockServerLogger = warnEnabledLogger();
        E engine = engine(uniqueAllowList("java.version"), mockServerLogger);

        for (int i = 0; i < 5; i++) {
            assertThat(property(engine, "java.vendor"), is(""));
            assertThat(env(engine, "HOME"), is(""));
        }
        assertThat(property(engine, "java.version"), is(not("")));

        List<LogEntry> refusals = refusals(mockServerLogger);
        assertThat(refusals, hasSize(2));
        assertThat(refusals.get(0).getMessage(), containsString("java.vendor"));
        assertThat(refusals.get(1).getMessage(), containsString("HOME"));
        for (LogEntry refusal : refusals) {
            assertThat(refusal.getLogLevel(), is(Level.WARN));
            assertThat(refusal.getMessage(), containsString("templateSystemValueAllowList"));
        }
    }

    @Test
    public void shouldWarnOnceForEachRefusedNameAcrossEnginesSharingALogger() {
        String allowList = uniqueAllowList("java.version");
        MockServerLogger mockServerLogger = warnEnabledLogger();
        E first = engine(allowList, mockServerLogger);
        E second = engine(allowList, mockServerLogger);

        property(first, "java.vendor");
        property(second, "java.vendor");

        assertThat(refusals(mockServerLogger), hasSize(1));
    }

    @Test
    public void shouldNeverReadMockServerSettings() {
        E engine = engine(uniqueAllowList("mockserver.*,MOCKSERVER_*,java.version"), warnEnabledLogger());

        assertThat(property(engine, "mockserver.logLevel"), is(""));
        assertThat(env(engine, "MOCKSERVER_LOG_LEVEL"), is(""));
        assertThat(property(engine, "java.version"), is(System.getProperty("java.version")));
    }
}
