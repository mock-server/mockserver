package org.mockserver.configuration;

import org.mockserver.service.apacheclient.ApacheHttpClientConfiguration;
import org.mockserver.service.jettyclient.JettyHttpClientConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

/**
 * This configuration contains top level beans and any configuration required by filters (as WebMvcConfiguration only loaded within Dispatcher Servlet)
 *
 * @author jamesdbloom
 */
@Configuration
@PropertySource({"classpath:application.properties"})
@Import({
        ApacheHttpClientConfiguration.class,
        JettyHttpClientConfiguration.class
})
public class RootConfiguration {

}
