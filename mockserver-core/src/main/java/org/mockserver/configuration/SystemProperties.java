package org.mockserver.configuration;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static int serverStopPort(Integer port, Integer securePort) {
        return SystemProperties.readIntegerProperty("mockserver.serverStopPort", Math.max((port != null ? port : 0), (securePort != null ? securePort : 0)) + 1);
    }

    public static void serverStopPort(int port) {
        System.setProperty("mockserver.serverStopPort", "" + port);
    }

    // proxy config
    public static int proxyStopPort(Integer port, Integer securePort) {
        return SystemProperties.readIntegerProperty("mockserver.proxyStopPort", Math.max((port != null ? port : 0), (securePort != null ? securePort : 0)) + 1);
    }

    public static void proxyStopPort(int port) {
        System.setProperty("mockserver.proxyStopPort", "" + port);
    }

    // socks config
    public static int socksPort() {
        return SystemProperties.readIntegerProperty("mockserver.socksPort", -1);
    }

    public static void socksPort(int port) {
        System.setProperty("mockserver.socksPort", "" + port);
    }

    private static Integer readIntegerProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(System.getProperty(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            logger.error("NumberFormatException converting " + key + " with value [" + System.getProperty(key) + "]", nfe);
            throw new RuntimeException("NumberFormatException converting " + key + " with value [" + System.getProperty(key) + "]", nfe);
        }
    }

    private static Long readLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(System.getProperty(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            logger.error("NumberFormatException converting " + key + " with value [" + System.getProperty(key) + "]", nfe);
            throw new RuntimeException("NumberFormatException converting " + key + " with value [" + System.getProperty(key) + "]", nfe);
        }
    }
}
