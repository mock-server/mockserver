package org.mockserver.configuration;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class SystemProperties {

    static final long DEFAULT_MAX_TIMEOUT = 120;
    static final int DEFAULT_BUFFER_SIZE = 1024 * 1500;
    private static final Logger logger = LoggerFactory.getLogger(SystemProperties.class);

    // general config
    public static long maxTimeout() {
        return SystemProperties.readLongProperty("mockserver.maxTimeout", TimeUnit.SECONDS.toMillis(SystemProperties.DEFAULT_MAX_TIMEOUT));
    }

    public static void maxTimeout(long timeout) {
        System.setProperty("mockserver.maxTimeout", "" + timeout);
    }

    public static int bufferSize() {
        return SystemProperties.readIntegerProperty("mockserver.requestBufferSize", SystemProperties.DEFAULT_BUFFER_SIZE);
    }

    public static void bufferSize(int size) {
        System.setProperty("mockserver.requestBufferSize", "" + size);
    }

    // mockserver config
    public static int mockServerHttpPort() {
        return SystemProperties.readIntegerProperty("mockserver.mockServerHttpPort", -1);
    }

    public static void mockServerHttpPort(int port) {
        System.setProperty("mockserver.mockServerHttpPort", "" + port);
    }

    // proxy config
    public static int proxyHttpPort() {
        return SystemProperties.readIntegerProperty("mockserver.proxyHttpPort", -1);
    }

    public static void proxyHttpPort(int port) {
        System.setProperty("mockserver.proxyHttpPort", "" + port);
    }

    private static Integer readIntegerProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(System.getProperty(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            logger.error("NumberFormatException converting " + key + " with value [" + System.getProperty(key) + "]", nfe);
            return defaultValue;
        }
    }

    private static Long readLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(System.getProperty(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            logger.error("NumberFormatException converting " + key + " with value [" + System.getProperty(key) + "]", nfe);
            return defaultValue;
        }
    }
}
