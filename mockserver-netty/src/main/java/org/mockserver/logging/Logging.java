package org.mockserver.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * @author jamesdbloom
 */
public class Logging {
    private static final Logger logger = LoggerFactory.getLogger(Logging.class);

    /**
     * Override the debug WARN logging level
     *
     * @param level the log level, which can be ALL, DEBUG, INFO, WARN, ERROR, OFF
     */
    public static void overrideLogLevel(String level) {
        Logger rootLogger = LoggerFactory.getLogger("org.mockserver");

        try {
            // create level instance
            Class logbackLevelClass = Logging.class.getClassLoader().loadClass("ch.qos.logback.classic.Level");
            Method toLevelMethod = logbackLevelClass.getMethod("toLevel", String.class);
            Object levelInstance = toLevelMethod.invoke(logbackLevelClass, level);

            // update root level
            Method setLevelMethod = rootLogger.getClass().getMethod("setLevel", logbackLevelClass);
            setLevelMethod.invoke(rootLogger, levelInstance);
        } catch (Exception e) {
            logger.warn("Exception updating logging level using reflection, likely cause is Logback is not on the classpath");
        }
    }
}
