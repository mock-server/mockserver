package org.mockserver.examples.proxy.configuration;

import org.mockserver.examples.proxy.service.apacheclient.ApacheHttpClientConfiguration;
import org.mockserver.examples.proxy.service.googleclient.GoogleHttpClientConfiguration;
import org.mockserver.examples.proxy.service.javaclient.JavaHttpClientConfiguration;
import org.mockserver.examples.proxy.service.jerseyclient.JerseyClientConfiguration;
import org.mockserver.examples.proxy.service.jettyclient.JettyHttpClientConfiguration;
import org.mockserver.examples.proxy.service.springclient.SpringRestTemplateConfiguration;
import org.mockserver.examples.proxy.servicebackend.BackEndServiceConfiguration;
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
    BackEndServiceConfiguration.class,
    ApacheHttpClientConfiguration.class,
    JettyHttpClientConfiguration.class,
    JerseyClientConfiguration.class,
    GoogleHttpClientConfiguration.class,
    JavaHttpClientConfiguration.class,
    SpringRestTemplateConfiguration.class
})
public class RootConfiguration {

}
