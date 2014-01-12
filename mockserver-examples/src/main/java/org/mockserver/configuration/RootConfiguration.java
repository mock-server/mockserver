package org.mockserver.configuration;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * This configuration contains top level beans and any configuration required by filters (as WebMvcConfiguration only loaded within Dispatcher Servlet)
 *
 * @author jamesdbloom
 */
@Configuration
@PropertySource({"classpath:application.properties"})
@ComponentScan(basePackages = {"org.mockserver.service"})
public class RootConfiguration {

}
