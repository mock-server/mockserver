package org.mockserver.servicebackend;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;

/**
 * This configuration contains top level beans and any configuration required by filters (as WebMvcConfiguration only loaded within Dispatcher Servlet)
 *
 * @author jamesdbloom
 */
@Configuration
@Profile("dev")
@PropertySource({"classpath:application.properties"})
public class BackEndServiceConfiguration {

    @Resource
    private Environment environment;

    @Bean
    public BookServer bookServer() {
        return new BookServer(environment.getProperty("bookService.port", Integer.class));
    }

}
