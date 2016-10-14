package org.mockserver.examples.configuration;

import org.mockserver.examples.service.apacheclient.ApacheHttpClientConfiguration;
import org.mockserver.examples.service.googleclient.GoogleHttpClientConfiguration;
import org.mockserver.examples.service.javaclient.JavaHttpClientConfiguration;
import org.mockserver.examples.service.jerseyclient.JerseyClientConfiguration;
import org.mockserver.examples.service.jettyclient.JettyHttpClientConfiguration;
import org.mockserver.examples.service.springclient.SpringRestTemplateConfiguration;
import org.mockserver.examples.servicebackend.BackEndServiceConfiguration;
import org.mockserver.socket.PortFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

import javax.annotation.PostConstruct;

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

    @PostConstruct
    public void updateServerPort() {
        System.setProperty("bookService.port", "" + PortFactory.findFreePort());
    }

}
