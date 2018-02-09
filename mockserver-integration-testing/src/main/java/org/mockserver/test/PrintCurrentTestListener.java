package org.mockserver.test;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * <pre>
 *  &lt;plugin&gt;
 *  &lt;groupId&gt;org.apache.maven.plugins&lt;/groupId&gt;
 *  &lt;artifactId&gt;maven-failsafe-plugin&lt;/artifactId&gt;
 *  &lt;version&gt;2.20.1&lt;/version&gt;
 *  &lt;configuration&gt;
 *     &lt;properties&gt;;
 *     &lt;property&gt;
 *     &lt;name&gt;listener&lt;/name&gt;
 *     &lt;value&gt;org.mockserver.test.PrintCurrentTestListener&lt;/value&gt;
 *     &lt;/property&gt;
 *     &lt;/properties&gt;
 *     &lt;/configuration&gt;
 *     &lt;executions&gt;
 *     &lt;execution&gt;
 *     &lt;goals&gt;
 *     &lt;goal&gt;integration-test&lt;/goal&gt;
 *     &lt;goal&gt;verify&lt;/goal&gt;
 *     &lt;/goals&gt;
 *     &lt;/execution&gt;
 *     &lt;/executions&gt;
 *  &lt;/plugin&gt;
 * </pre>
 */
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