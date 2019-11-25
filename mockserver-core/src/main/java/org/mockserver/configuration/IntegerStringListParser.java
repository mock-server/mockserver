package org.mockserver.configuration;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class IntegerStringListParser {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(IntegerStringListParser.class);

    public Integer[] toArray(String integers) {
        List<Integer> integerList = toList(integers);
        return integerList.toArray(new Integer[0]);
    }

    List<Integer> toList(String integers) {
        List<Integer> integerList = new ArrayList<Integer>();
        for (String integer : Splitter.on(",").split(integers)) {
            try {
                integerList.add(Integer.parseInt(integer.trim()));
            } catch (NumberFormatException nfe) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("NumberFormatException converting " + integer + " to integer")
                        .setThrowable(nfe)
                );
            }
        }
        return integerList;
    }

    public String toString(Integer[] integers) {
        return toString(Arrays.asList(integers));
    }

    public String toString(List<Integer> integers) {
        return Joiner.on(",").join(integers);
    }
}
