package org.mockserver.maven;

import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.configuration.SystemProperties;
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
 *  &lt;version&gt;2.17&lt;/version&gt;
 *  &lt;configuration&gt;
 *      &lt;properties&gt;
 *          &lt;property&gt;
 *              &lt;name&gt;listener&lt;/name&gt;
 *              &lt;value&gt;org.mockserver.maven.StopProxyTestListener&lt;/value&gt;
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
 *              &lt;value&gt;org.mockserver.maven.StopProxyTestListener&lt;/value&gt;
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
public class StopProxyTestListener extends RunListener {

    private static final Logger logger = LoggerFactory.getLogger(StopProxyTestListener.class);

    @Override
    public void testRunFinished(Result result) throws Exception {
        if (SystemProperties.proxyHttpPort() != -1) {
            logger.info("Stopping the MockServer proxy");
            new ProxyClient("127.0.0.1", SystemProperties.proxyHttpPort()).stop();
        } else {
            logger.info("Failed to stop MockServer proxy as HTTP port is unknown");
        }
    }

}