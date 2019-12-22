package org.mockserver.test;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PrintOutCurrentTestRunListener extends RunListener {

    Map<String, Long> startTimes = new ConcurrentHashMap<>();

    public void testStarted(Description description) {
        startTimes.put(description.getMethodName(), System.currentTimeMillis());
        System.out.print("STARTED: " + description.getMethodName() + "\n");
    }

    public void testFinished(Description description) {
        Long startTime = startTimes.get(description.getMethodName());
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