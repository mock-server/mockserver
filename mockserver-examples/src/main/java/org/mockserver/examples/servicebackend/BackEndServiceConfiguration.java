package org.mockserver.examples.servicebackend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;

/**
 * This configuration contains top level beans and any configuration required by filters (as WebMvcConfiguration only loaded within Dispatcher Servlet)
 *
 * @author jamesdbloom
 */
@Configuration
@Profile("backend")
@PropertySource({"classpath:application.properties"})
public class BackEndServiceConfiguration {

    @Resource
    private Environment environment;

    @Bean
    public BookServer bookServer() {
        return new BookServer(environment.getProperty("bookService.port", Integer.class), false);
    }

}
