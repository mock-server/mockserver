package org.mockserver.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.util.SocketUtils;

/* must be registered in spring.factories
    org.springframework.boot.env.EnvironmentPostProcessor=org.mockserver.spring.MockServerPortPostProcessor
 */
public class MockServerPortPostProcessor implements EnvironmentPostProcessor {

    static int mockServerPort = SocketUtils.findAvailableTcpPort();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(
               new MockPropertySource().withProperty("mockServerPort", mockServerPort)
        );
    }
}
