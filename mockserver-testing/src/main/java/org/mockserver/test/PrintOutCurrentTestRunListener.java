package org.mockserver.test;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PrintOutCurrentTestRunListener extends RunListener {

    private static final Map<String, Long> START_TIMES = new ConcurrentHashMap<>();

    public void testStarted(Description description) {
        START_TIMES.put(description.getMethodName(), System.currentTimeMillis());
        System.out.print("STARTED: " + description.getMethodName() + "\n");
    }

    public void testFinished(Description description) {
        Long startTime = START_TIMES.get(description.getMethodName());
        Long duration = startTime != null ? System.currentTimeMillis() - startTime : null;
        System.out.print("FINISHED: " + description.getMethodName() + (duration != null ? " duration: " + duration : "") + "\n");
    }

    public void testFailure(Failure failure) {
        System.out.print("FAILED: " + failure.getDescription().getMethodName() + "\n");
    }

    public void testIgnored(Description description) {
        System.out.print("IGNORED: " + description.getMethodName() + "\n");
    }

}