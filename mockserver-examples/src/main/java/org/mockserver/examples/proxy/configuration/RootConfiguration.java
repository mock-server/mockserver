package org.mockserver.examples.proxy.configuration;

import org.mockserver.examples.proxy.service.apacheclient.ApacheHttpClientConfiguration;
import org.mockserver.examples.proxy.service.googleclient.http.GoogleHttpClientConfigurationHttpProxy;
import org.mockserver.examples.proxy.service.googleclient.socks.GoogleHttpClientConfigurationSocksProxy;
import org.mockserver.examples.proxy.service.javaclient.http.JavaHttpClientConfigurationHttpProxy;
import org.mockserver.examples.proxy.service.javaclient.https.JavaHttpClientConfigurationHttpsProxy;
import org.mockserver.examples.proxy.service.javaclient.socks.JavaHttpClientConfigurationSocksProxy;
import org.mockserver.examples.proxy.service.jerseyclient.JerseyClientConfiguration;
import org.mockserver.examples.proxy.service.jettyclient.JettyHttpClientConfiguration;
import org.mockserver.examples.proxy.service.springresttemplate.SpringRestTemplateConfiguration;
import org.mockserver.examples.proxy.service.springwebclient.https.SpringWebClientConfigurationHttpsProxy;
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
    GoogleHttpClientConfigurationHttpProxy.class,
    GoogleHttpClientConfigurationSocksProxy.class,
    JavaHttpClientConfigurationHttpProxy.class,
    JavaHttpClientConfigurationHttpsProxy.class,
    JavaHttpClientConfigurationSocksProxy.class,
    SpringRestTemplateConfiguration.class,
    SpringWebClientConfigurationHttpsProxy.class
})
public class RootConfiguration {

}
