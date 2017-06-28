package org.mockserver.logging;

import org.junit.Test;
import org.slf4j.Logger;

import static org.mockito.Mockito.*;
import static org.mockserver.character.Character.NEW_LINE;

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
                "some" + NEW_LINE + "multi-line" + NEW_LINE + "object",
                "another" + NEW_LINE + "multi-line" + NEW_LINE + "object"
        );

        // then
        verify(mockLogger).isInfoEnabled();
        verify(mockLogger).info(
                "some random message with {} and {}" + NEW_LINE,
                new Object[]{
                        NEW_LINE +
                                NEW_LINE +
                                "\tsome" + NEW_LINE +
                                "\tmulti-line" + NEW_LINE +
                                "\tobject" + NEW_LINE,
                        NEW_LINE +
                                NEW_LINE +
                                "\tanother" + NEW_LINE +
                                "\tmulti-line" + NEW_LINE +
                                "\tobject" + NEW_LINE
                }
        );
    }

}