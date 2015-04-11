package org.mockserver.logging;

import org.junit.Test;
import org.slf4j.Logger;

import static org.mockito.Mockito.*;

public class LogFormatterTest {

    @Test
    public void shouldFormatLogMessages() {
        // given
        Logger mockLogger = mock(Logger.class);
        LogFormatter logFormatter = new LogFormatter(mockLogger);
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        // when
        logFormatter.infoLog(
                "some random message with {} and {}",
                "some" + System.getProperty("line.separator") + "multi-line" + System.getProperty("line.separator") + "object",
                "another" + System.getProperty("line.separator") + "multi-line" + System.getProperty("line.separator") + "object"
        );

        // then
        verify(mockLogger).isInfoEnabled();
        verify(mockLogger).info(
                "some random message with {} and {}" + System.getProperty("line.separator"),
                new String[]{
                        System.getProperty("line.separator") +
                                System.getProperty("line.separator") +
                                "\tsome" + System.getProperty("line.separator") +
                                "\tmulti-line" + System.getProperty("line.separator") +
                                "\tobject" + System.getProperty("line.separator"),
                        System.getProperty("line.separator") +
                                System.getProperty("line.separator") +
                                "\tanother" + System.getProperty("line.separator") +
                                "\tmulti-line" + System.getProperty("line.separator") +
                                "\tobject" + System.getProperty("line.separator")
                }
        );
    }

}