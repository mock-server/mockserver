package org.mockserver.configuration;

import org.mockserver.service.apacheclient.ApacheHttpClientConfiguration;
import org.mockserver.service.grizzlyclient.GrizzlyHttpClientConfiguration;
import org.mockserver.service.jerseyclient.JerseyClientConfiguration;
import org.mockserver.service.jettyclient.JettyHttpClientConfiguration;
import org.mockserver.service.springclient.SpringRestTemplateConfiguration;
import org.mockserver.servicebackend.BackEndServiceConfiguration;
import org.mockserver.socket.PortFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

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
        GrizzlyHttpClientConfiguration.class,
        SpringRestTemplateConfiguration.class
})
public class RootConfiguration {

    @PostConstruct
    public void updateServerPort() {
        System.setProperty("bookService.port", "" + PortFactory.findFreePort());
    }

}
