package org.mockserver.maven;

import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.mockserver.client.server.MockServerClient;
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
 * <plugin>
 *  <groupId>org.apache.maven.plugins</groupId>
 *  <artifactId>maven-surefire-plugin</artifactId>
 *  <version>2.17</version>
 *  <configuration>
 *      <properties>
 *          <property>
 *              <name>listener</name>
 *              <value>org.mockserver.maven.StopMockServerTestListener</value>
 *          </property>
 *      </properties>
 *  </configuration>
 * </plugin>
 *
 * or:
 *
 * <plugin>
 *  <groupId>org.apache.maven.plugins</groupId>
 *  <artifactId>maven-failsafe-plugin</artifactId>
 *  <version>2.17</version>
 *  <configuration>
 *      <properties>
 *          <property>
 *              <name>listener</name>
 *              <value>org.mockserver.maven.StopMockServerTestListener</value>
 *          </property>
 *      </properties>
 *  </configuration>
 * </plugin>
 *
 * This will only work if the mockserver-maven-plugin dependency is also added:
 *
 * &ltdependencies&gt
 *  ...
 *  &ltdependency&gt
 *      &ltgroupId&gtorg.mock-server&lt/groupId&gt
 *      &ltartifactId&gtmockserver-maven-plugin&lt/artifactId&gt
 *      &ltversion&gt${mockserver.version}&lt/version&gt
 *      &ltscope&gttest&lt/scope&gt
 *  &lt/dependency&gt
 *  ...
 * &lt/dependencies&gt
 *
 */
public class StopMockServerTestListener extends RunListener {

    private static final Logger logger = LoggerFactory.getLogger(StopMockServerTestListener.class);

    @Override
    public void testRunFinished(Result result) throws Exception {
        if (SystemProperties.mockServerHttpPort() != -1) {
            logger.info("Stopping the MockServer");
            new MockServerClient("127.0.0.1", SystemProperties.mockServerHttpPort()).stop();
        } else {
            logger.info("Failed to stop MockServer as HTTP port is unknown");
        }
    }

}