package org.mockserver.logging;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.logLevel;
import static org.mockserver.model.HttpRequest.request;

public class MockServerLoggerTest {

    @Test
    public void shouldFormatInfoLogMessagesForRequest() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("INFO");
            Logger mockLogger = mock(Logger.class);
            HttpStateHandler mockHttpStateHandler = mock(HttpStateHandler.class);
            MockServerLogger logFormatter = new MockServerLogger(mockLogger, mockHttpStateHandler);
            when(mockLogger.isInfoEnabled()).thenReturn(true);
            HttpRequest request = request("some_path");

            // when
            logFormatter.info(
                request,
                "some random message with {} and {}",
                "some" + NEW_LINE + "multi-line" + NEW_LINE + "object",
                "another" + NEW_LINE + "multi-line" + NEW_LINE + "object"
            );

            // then
            String message = "some random message with " + NEW_LINE +
                "" + NEW_LINE +
                "\tsome" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE +
                " and " + NEW_LINE +
                "" + NEW_LINE +
                "\tanother" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE;
            verify(mockLogger).info(message);
            ArgumentCaptor<MessageLogEntry> captor = ArgumentCaptor.forClass(MessageLogEntry.class);
            verify(mockHttpStateHandler, times(1)).log(captor.capture());

            MessageLogEntry messageLogEntry = captor.getValue();
            assertThat(messageLogEntry.getHttpRequest(), is(request));
            assertThat(messageLogEntry.getMessage(), containsString(message));
        } finally {
            logLevel(originalLevel.toString());
        }
    }

    @Test
    public void shouldFormatInfoLogMessagesForRequestList() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("INFO");
            Logger mockLogger = mock(Logger.class);
            HttpStateHandler mockHttpStateHandler = mock(HttpStateHandler.class);
            MockServerLogger logFormatter = new MockServerLogger(mockLogger, mockHttpStateHandler);
            when(mockLogger.isInfoEnabled()).thenReturn(true);
            HttpRequest request = request("some_path");

            // when
            logFormatter.info(
                Arrays.asList(request, request),
                "some random message with {} and {}",
                "some" + NEW_LINE + "multi-line" + NEW_LINE + "object",
                "another" + NEW_LINE + "multi-line" + NEW_LINE + "object"
            );

            // then
            String message = "some random message with " + NEW_LINE +
                "" + NEW_LINE +
                "\tsome" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE +
                " and " + NEW_LINE +
                "" + NEW_LINE +
                "\tanother" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE;
            verify(mockLogger).info(message);
            ArgumentCaptor<MessageLogEntry> captor = ArgumentCaptor.forClass(MessageLogEntry.class);
            verify(mockHttpStateHandler, times(2)).log(captor.capture());

            for (MessageLogEntry messageLogEntry : captor.getAllValues()) {
                assertThat(messageLogEntry.getHttpRequest(), is(request));
                assertThat(messageLogEntry.getMessage(), containsString(message));
            }
        } finally {
            logLevel(originalLevel.toString());
        }
    }

    @Test
    public void shouldFormatErrorLogMessagesForRequest() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("INFO");
            Logger mockLogger = mock(Logger.class);
            HttpStateHandler mockHttpStateHandler = mock(HttpStateHandler.class);
            MockServerLogger logFormatter = new MockServerLogger(mockLogger, mockHttpStateHandler);
            when(mockLogger.isErrorEnabled()).thenReturn(true);
            HttpRequest request = request("some_path");

            // when
            logFormatter.error(
                request,
                "some random message with {} and {}",
                "some" + NEW_LINE + "multi-line" + NEW_LINE + "object",
                "another" + NEW_LINE + "multi-line" + NEW_LINE + "object"
            );

            // then
            String message = "some random message with " + NEW_LINE +
                "" + NEW_LINE +
                "\tsome" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE +
                " and " + NEW_LINE +
                "" + NEW_LINE +
                "\tanother" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE;
            verify(mockLogger).error(message, (Throwable) null);

            ArgumentCaptor<MessageLogEntry> captor = ArgumentCaptor.forClass(MessageLogEntry.class);
            verify(mockHttpStateHandler, times(1)).log(captor.capture());

            MessageLogEntry messageLogEntry = captor.getValue();
            assertThat(messageLogEntry.getHttpRequest(), is(request));
            assertThat(messageLogEntry.getMessage(), containsString(message));
        } finally {
            logLevel(originalLevel.toString());
        }
    }

    @Test
    public void shouldFormatExceptionErrorLogMessagesForRequest() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("INFO");
            Logger mockLogger = mock(Logger.class);
            HttpStateHandler mockHttpStateHandler = mock(HttpStateHandler.class);
            MockServerLogger logFormatter = new MockServerLogger(mockLogger, mockHttpStateHandler);
            when(mockLogger.isErrorEnabled()).thenReturn(true);
            HttpRequest request = request("some_path");
            RuntimeException exception = new RuntimeException("TEST EXCEPTION");

            // when
            logFormatter.error(
                request,
                exception,
                "some random message with {} and {}",
                "some" + NEW_LINE + "multi-line" + NEW_LINE + "object",
                "another" + NEW_LINE + "multi-line" + NEW_LINE + "object"
            );

            // then
            String message = "some random message with " + NEW_LINE +
                "" + NEW_LINE +
                "\tsome" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE +
                " and " + NEW_LINE +
                "" + NEW_LINE +
                "\tanother" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE;
            verify(mockLogger).error(message, exception);

            ArgumentCaptor<MessageLogEntry> captor = ArgumentCaptor.forClass(MessageLogEntry.class);
            verify(mockHttpStateHandler, times(1)).log(captor.capture());

            MessageLogEntry messageLogEntry = captor.getValue();
            assertThat(messageLogEntry.getHttpRequest(), is(request));
            assertThat(messageLogEntry.getMessage(), containsString(message));
        } finally {
            logLevel(originalLevel.toString());
        }
    }

    @Test
    public void shouldFormatExceptionErrorLogMessagesForRequestList() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("INFO");
            Logger mockLogger = mock(Logger.class);
            HttpStateHandler mockHttpStateHandler = mock(HttpStateHandler.class);
            MockServerLogger logFormatter = new MockServerLogger(mockLogger, mockHttpStateHandler);
            when(mockLogger.isErrorEnabled()).thenReturn(true);
            HttpRequest request = request("some_path");
            RuntimeException exception = new RuntimeException("TEST EXCEPTION");

            // when
            logFormatter.error(
                Arrays.asList(request, request),
                exception,
                "some random message with {} and {}",
                "some" + NEW_LINE + "multi-line" + NEW_LINE + "object",
                "another" + NEW_LINE + "multi-line" + NEW_LINE + "object"
            );

            // then
            String message = "some random message with " + NEW_LINE +
                "" + NEW_LINE +
                "\tsome" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE +
                " and " + NEW_LINE +
                "" + NEW_LINE +
                "\tanother" + NEW_LINE +
                "\tmulti-line" + NEW_LINE +
                "\tobject" + NEW_LINE;
            verify(mockLogger).error(message, exception);

            ArgumentCaptor<MessageLogEntry> captor = ArgumentCaptor.forClass(MessageLogEntry.class);
            verify(mockHttpStateHandler, times(2)).log(captor.capture());

            for (MessageLogEntry messageLogEntry : captor.getAllValues()) {
                assertThat(messageLogEntry.getHttpRequest(), is(request));
                assertThat(messageLogEntry.getMessage(), containsString(message));
            }
        } finally {
            logLevel(originalLevel.toString());
        }
    }

}
