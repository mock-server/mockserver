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

    public static List<Integer> extraHTTPPorts() {
        return SystemProperties.readIntegerListProperty("mockserver.extraHTTPPorts", -1);
    }

    public static void extraHTTPPorts(List<Integer> port) {
        System.setProperty("mockserver.extraHTTPPorts", "" + Joiner.on(",").join(port));
    }

    public static List<Integer> extraHTTPSPorts() {
        return SystemProperties.readIntegerListProperty("mockserver.extraHTTPSPorts", -1);
    }

    public static void extraHTTPSPorts(List<Integer> port) {
        System.setProperty("mockserver.extraHTTPSPorts", "" + Joiner.on(",").join(port));
    }

    private static Integer readIntegerProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(System.getProperty(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            logger.error("NumberFormatException converting " + key + " with value [" + System.getProperty(key) + "]", nfe);
            throw new RuntimeException("NumberFormatException converting " + key + " with value [" + System.getProperty(key) + "]", nfe);
        }
    }

    private static List<Integer> readIntegerListProperty(String key, int defaultValue) {
        try {
            List<Integer> integers = new ArrayList<>();
            for (String value : Splitter.on(",").split(System.getProperty(key, "" + defaultValue))) {
                integers.add(Integer.parseInt(value));
            }
            return integers;
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
