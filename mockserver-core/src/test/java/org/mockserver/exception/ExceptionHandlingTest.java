package org.mockserver.exception;

import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockserver.exception.ExceptionHandling.swallowThrowable;

public class ExceptionHandlingTest {

    @Test
    public void shouldSwallowException() {
        // given
        ExceptionHandling.mockServerLogger = mock(MockServerLogger.class);

        // when
        swallowThrowable(() -> {
            throw new RuntimeException();
        });

        // then
        verify(ExceptionHandling.mockServerLogger).logEvent(any(LogEntry.class));
    }

    @Test
    public void shouldOnlyLogExceptions() {
        // given
        ExceptionHandling.mockServerLogger = mock(MockServerLogger.class);

        // when
        swallowThrowable(() -> {
            System.out.println("ignore me");
        });

        // then
        verify(ExceptionHandling.mockServerLogger, never()).logEvent(any(LogEntry.class));
    }

}