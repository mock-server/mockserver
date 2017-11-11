package org.mockserver.maven;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 *
 * Used to guarantee that the MockServer is stopped even when tests fail.
 * As maven does not run any phases are a the test or integration-test phase
 * when a test fails this class can be used to guarantee that the MockServer
 * is stopped, for example:
 *
 * &lt;plugin&gt;
 *  &lt;groupId&gt;org.apache.maven.plugins&lt;/groupId&gt;
 *  &lt;artifactId&gt;maven-surefire-plugin&lt;/artifactId&gt;
 *  &lt;version&gt;2.19.1&lt;/version&gt;
 *  &lt;configuration&gt;
 *      &lt;properties&gt;
 *          &lt;property&gt;
 *              &lt;name&gt;listener&lt;/name&gt;
 *              &lt;value&gt;org.mockserver.maven.StopMockServerTestListener&lt;/value&gt;
 *          &lt;/property&gt;
 *      &lt;/properties&gt;
 *  &lt;/configuration&gt;
 * &lt;/plugin&gt;
 *
 * or:
 *
 * &lt;plugin&gt;
 *  &lt;groupId&gt;org.apache.maven.plugins&lt;/groupId&gt;
 *  &lt;artifactId&gt;maven-failsafe-plugin&lt;/artifactId&gt;
 *  &lt;version&gt;2.17&lt;/version&gt;
 *  &lt;configuration&gt;
 *      &lt;properties&gt;
 *          &lt;property&gt;
 *              &lt;name&gt;listener&lt;/name&gt;
 *              &lt;value&gt;org.mockserver.maven.StopMockServerTestListener&lt;/value&gt;
 *          &lt;/property&gt;
 *      &lt;/properties&gt;
 *  &lt;/configuration&gt;
 * &lt;/plugin&gt;
 *
 * This will only work if the mockserver-maven-plugin dependency is also added:
 *
 * &lt;dependencies&gt;
 *  ...
 *  &lt;dependency&gt;
 *      &lt;groupId&gt;org.mock-server&lt;/groupId&gt;
 *      &lt;artifactId&gt;mockserver-maven-plugin&lt;/artifactId&gt;
 *      &lt;version&gt;${mockserver.version}&lt;/version&gt;
 *      &lt;scope&gt;test&lt;/scope&gt;
 *  &lt;/dependency&gt;
 *  ...
 * &lt;/dependencies&gt;
 *
 */
public class StopMockServerTestListener extends RunListener {

    private static final Logger logger = LoggerFactory.getLogger(StopMockServerTestListener.class);

    @Override
    public void testRunStarted(Description description) throws Exception {
        super.testRunStarted(description);
    }

    @Override
    public void testStarted(Description description) throws Exception {
        super.testStarted(description);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        super.testFinished(description);
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        super.testFailure(failure);
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        super.testAssumptionFailure(failure);
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        super.testIgnored(description);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        if (!ConfigurationProperties.mockServerPort().isEmpty()) {
            logger.info("Stopping the MockServer");
            new MockServerClient("127.0.0.1", ConfigurationProperties.mockServerPort().get(0)).stop();
        } else {
            logger.info("Failed to stop MockServer as HTTP port is unknown");
        }
    }

}