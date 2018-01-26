package org.mockserver.test;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class PrintCurrentTestListener extends RunListener {

    @Override
    public void testRunStarted(Description description) {
        if (description != null) {
            System.out.println("testRunStarted " + String.valueOf(description.getClassName()) + " " + String.valueOf(description.getDisplayName()) + " " + String.valueOf(description.toString()));
        }
    }

    @Override
    public void testStarted(Description description) {
        System.out.println("testStarted " + String.valueOf(description));
    }

    @Override
    public void testFinished(Description description) {
        System.out.println("testFinished " + String.valueOf(description));
    }

    public void testFailure(Failure failure) {
        System.out.println("testFailure " + String.valueOf(failure));
    }

    public void testAssumptionFailure(Failure failure) {
        System.out.println("testAssumptionFailure " + String.valueOf(failure));
    }

    public void testIgnored(Description description) {
        System.out.println("testIgnored " + String.valueOf(description));
    }

    @Override
    public void testRunFinished(Result result) {
        if (result != null) {
            System.out.println("testRunFinished " + String.valueOf(result)
                + " time:" + String.valueOf(result.getRunTime())
                + " R" + String.valueOf(result.getRunCount())
                + " F" + String.valueOf(result.getFailureCount())
                + " I" + String.valueOf(result.getIgnoreCount())
            );
        }
    }

}