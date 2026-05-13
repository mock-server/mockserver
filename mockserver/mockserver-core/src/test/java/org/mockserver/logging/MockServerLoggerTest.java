package org.mockserver.logging;

import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.ArrayMatching.arrayContaining;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockServerLoggerTest {

    private final Configuration configuration = configuration().logLevel(Level.INFO).disableSystemOut(false);

    @After
    public void tearDown() {
        MockServerLogger.setGlobalLogEventListener(null);
    }

    @Test
    public void shouldSendEventToStateHandler() {
        // given
        HttpState mockHttpStateHandler = mock(HttpState.class);
        MockServerLogger logFormatter = new MockServerLogger(configuration, mockHttpStateHandler);
        HttpRequest request = request("some_path");

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("some random message with{}and{}")
                .setArguments("some" + NEW_LINE + "multi-line" + NEW_LINE + "object", "another" + NEW_LINE + "multi-line" + NEW_LINE + "object")
        );

        // then
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockHttpStateHandler, times(1)).log(captor.capture());

        LogEntry messageLogEntry = captor.getValue();
        assertThat(messageLogEntry.getHttpRequests(), is(new HttpRequest[]{request}));
        assertThat(messageLogEntry.getMessage(), containsString("some random message with" + NEW_LINE +
            NEW_LINE +
            "  some" + NEW_LINE +
            "  multi-line" + NEW_LINE +
            "  object" + NEW_LINE +
            NEW_LINE +
            " and" + NEW_LINE +
            NEW_LINE +
            "  another" + NEW_LINE +
            "  multi-line" + NEW_LINE +
            "  object" + NEW_LINE));
        assertThat(messageLogEntry.getMessageFormat(), containsString("some random message with{}and{}"));
        assertThat(messageLogEntry.getArguments(), arrayContaining(new Object[]{
            "some" + NEW_LINE + "multi-line" + NEW_LINE + "object",
            "another" + NEW_LINE + "multi-line" + NEW_LINE + "object"
        }));
    }

    @Test
    public void shouldFormatErrorLogMessagesForRequest() {
        // given
        Logger mockLogger = mock(Logger.class);
        MockServerLogger logFormatter = new MockServerLogger(configuration, mockLogger);
        HttpRequest request = request("some_path");

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setHttpRequest(request)
                .setMessageFormat("some random message with{}and{}")
                .setArguments("some" + NEW_LINE + "multi-line" + NEW_LINE + "object", "another" + NEW_LINE + "multi-line" + NEW_LINE + "object")
        );

        // then
        String message = "some random message with" + NEW_LINE +
            NEW_LINE +
            "  some" + NEW_LINE +
            "  multi-line" + NEW_LINE +
            "  object" + NEW_LINE +
            NEW_LINE +
            " and" + NEW_LINE +
            NEW_LINE +
            "  another" + NEW_LINE +
            "  multi-line" + NEW_LINE +
            "  object" + NEW_LINE;
        verify(mockLogger).error(message, (Throwable) null);
    }

    @Test
    public void shouldFormatLogMessagesWithException() {
        // given
        Logger mockLogger = mock(Logger.class);
        MockServerLogger logFormatter = new MockServerLogger(configuration, mockLogger);
        HttpRequest request = request("some_path");
        RuntimeException exception = new RuntimeException("TEST EXCEPTION");

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setHttpRequest(request)
                .setMessageFormat("some random message with{}and{}")
                .setArguments(
                    "some" + NEW_LINE +
                        "multi-line" + NEW_LINE +
                        "object",
                    "another" + NEW_LINE +
                        "multi-line" + NEW_LINE +
                        "object"
                )
                .setThrowable(exception)
        );

        // then
        String message = "some random message with" + NEW_LINE +
            NEW_LINE +
            "  some" + NEW_LINE +
            "  multi-line" + NEW_LINE +
            "  object" + NEW_LINE +
            NEW_LINE +
            " and" + NEW_LINE +
            NEW_LINE +
            "  another" + NEW_LINE +
            "  multi-line" + NEW_LINE +
            "  object" + NEW_LINE;
        verify(mockLogger).error(message, exception);
    }

    @Test
    public void shouldFormatLogMessages() {
        // given
        Logger mockLogger = mock(Logger.class);
        MockServerLogger logFormatter = new MockServerLogger(configuration, mockLogger);
        HttpRequest request = request("some_path");

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setHttpRequest(request)
                .setMessageFormat("some random message with{}and{}")
                .setArguments(
                    "some" + NEW_LINE +
                        "multi-line" + NEW_LINE +
                        "object",
                    "another" + NEW_LINE +
                        "multi-line" + NEW_LINE +
                        "object"
                )
        );

        // then
        String message = "some random message with" + NEW_LINE +
            NEW_LINE +
            "  some" + NEW_LINE +
            "  multi-line" + NEW_LINE +
            "  object" + NEW_LINE +
            NEW_LINE +
            " and" + NEW_LINE +
            NEW_LINE +
            "  another" + NEW_LINE +
            "  multi-line" + NEW_LINE +
            "  object" + NEW_LINE;
        verify(mockLogger).error(message, (Throwable) null);
    }

    @Test
    public void shouldFormatCompactLogMessages() {
        // given
        Configuration compactConfig = configuration().logLevel(Level.INFO).disableSystemOut(false).compactLogFormat(true);
        Logger mockLogger = mock(Logger.class);
        MockServerLogger logFormatter = new MockServerLogger(compactConfig, mockLogger);

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.INFO)
                .setType(LogEntry.LogMessageType.EXPECTATION_RESPONSE)
                .setMessageFormat("returning response:{}for request:{}from expectation:{}")
                .setArguments(
                    response().withStatusCode(200),
                    request("/test").withMethod("GET"),
                    "test-expectation-id"
                )
        );

        // then
        verify(mockLogger).info("returning response: 200 for request: GET /test from expectation: test-expectation-id", (Throwable) null);
    }

    @Test
    public void shouldFormatVerboseLogMessagesWhenCompactDisabled() {
        // given
        Configuration verboseConfig = configuration().logLevel(Level.INFO).disableSystemOut(false).compactLogFormat(false);
        Logger mockLogger = mock(Logger.class);
        MockServerLogger logFormatter = new MockServerLogger(verboseConfig, mockLogger);

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.INFO)
                .setType(LogEntry.LogMessageType.EXPECTATION_RESPONSE)
                .setMessageFormat("some message with{}and{}")
                .setArguments("value1", "value2")
        );

        // then
        String expectedMessage = "some message with" + NEW_LINE +
            NEW_LINE +
            "  value1" + NEW_LINE +
            NEW_LINE +
            " and" + NEW_LINE +
            NEW_LINE +
            "  value2" + NEW_LINE;
        verify(mockLogger).info(expectedMessage, (Throwable) null);
    }

    @Test
    public void shouldCallGlobalLogEventListener() {
        // given
        List<LogEntry> capturedEntries = new ArrayList<>();
        MockServerLogger.setGlobalLogEventListener(capturedEntries::add);
        HttpState mockHttpStateHandler = mock(HttpState.class);
        MockServerLogger logFormatter = new MockServerLogger(configuration, mockHttpStateHandler);
        HttpRequest request = request("some_path");

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.INFO)
                .setHttpRequest(request)
                .setMessageFormat("listener test message")
        );

        // then
        List<LogEntry> matchingEntries = new ArrayList<>();
        for (LogEntry entry : capturedEntries) {
            if ("listener test message".equals(entry.getMessageFormat())) {
                matchingEntries.add(entry);
            }
        }
        assertThat(matchingEntries.size(), is(1));
        assertThat(matchingEntries.get(0).getMessageFormat(), is("listener test message"));
        verify(mockHttpStateHandler, times(1)).log(any(LogEntry.class));
    }

    @Test
    public void shouldNotFailWhenNoGlobalLogEventListenerSet() {
        // given
        MockServerLogger.setGlobalLogEventListener(null);
        Logger mockLogger = mock(Logger.class);
        MockServerLogger logFormatter = new MockServerLogger(configuration, mockLogger);

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setMessageFormat("test message without listener")
        );

        // then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger).error(messageCaptor.capture(), eq((Throwable) null));
        assertThat(messageCaptor.getValue(), containsString("test message without listener"));
    }

    @Test
    public void shouldCallGlobalLogEventListenerBeforeDispatching() {
        // given
        List<String> callOrder = new ArrayList<>();
        MockServerLogger.setGlobalLogEventListener(entry -> {
            if ("order test".equals(entry.getMessageFormat())) {
                callOrder.add("listener");
            }
        });
        HttpState mockHttpStateHandler = mock(HttpState.class);
        doAnswer(invocation -> {
            LogEntry entry = invocation.getArgument(0);
            if ("order test".equals(entry.getMessageFormat())) {
                callOrder.add("httpStateHandler");
            }
            return null;
        }).when(mockHttpStateHandler).log(any(LogEntry.class));
        MockServerLogger logFormatter = new MockServerLogger(configuration, mockHttpStateHandler);

        // when
        logFormatter.logEvent(
            new LogEntry()
                .setLogLevel(Level.INFO)
                .setMessageFormat("order test")
        );

        // then
        assertThat(callOrder.size(), is(2));
        assertThat(callOrder.get(0), is("listener"));
        assertThat(callOrder.get(1), is("httpStateHandler"));
    }

}
