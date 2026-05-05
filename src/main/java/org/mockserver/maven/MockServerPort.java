package org.mockserver.maven;

import com.google.common.base.Splitter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MockServerPort {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger();

    public static List<Integer> mockServerPort() {
        final String mockServerPort = System.getProperty("mockserver.mockServerPort");
        try {
            return toList(mockServerPort);
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                            .setType(LogEntry.LogMessageType.EXCEPTION)
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("NumberFormatException converting " + "mockserver.mockServerPort" + " with value [" + mockServerPort + "]")
                            .setThrowable(nfe)
            );
            return Collections.emptyList();
        }
    }

    private static List<Integer> toList(String integers) {
        List<Integer> integerList = new ArrayList<>();
        for (String integer : Splitter.on(",").split(integers)) {
            try {
                integerList.add(Integer.parseInt(integer.trim()));
            } catch (NumberFormatException throwable) {
                MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                                .setType(LogEntry.LogMessageType.EXCEPTION)
                                .setLogLevel(Level.ERROR)
                                .setMessageFormat("NumberFormatException converting " + integer + " to integer")
                                .setThrowable(throwable)
                );
            }
        }
        return integerList;
    }
}
