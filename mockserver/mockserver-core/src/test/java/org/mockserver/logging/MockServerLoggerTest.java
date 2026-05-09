package org.mockserver.logging;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.ArrayMatching.arrayContaining;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

public class MockServerLoggerTest {

    private final Configuration configuration = configuration().logLevel(Level.INFO).disableSystemOut(false);

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

}
