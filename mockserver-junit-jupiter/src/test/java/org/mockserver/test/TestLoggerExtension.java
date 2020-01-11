package org.mockserver.test;

import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestLoggerExtension implements Extension, BeforeEachCallback, AfterEachCallback {

    private static final Map<String, Long> START_TIMES = new ConcurrentHashMap<>();

    @Override
    public void beforeEach(ExtensionContext context) {
        String methodName = context.getTestMethod().map(Method::getName).orElse("unknown_method");
        START_TIMES.put(methodName, System.currentTimeMillis());
        System.out.print("STARTED: " + methodName + "\n");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        String methodName = context.getTestMethod().map(Method::getName).orElse("unknown_method");
        Long startTime = START_TIMES.get(methodName);
        Long duration = startTime != null ? System.currentTimeMillis() - startTime : null;
        System.out.print("FINISHED: " + methodName + (duration != null ? " duration: " + duration : "") + "\n");
    }

}
