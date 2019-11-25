package org.mockserver.examples.proxy.web.controller.googleclient;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.mockserver.examples.proxy.configuration.RootConfiguration;
import org.mockserver.examples.proxy.web.configuration.WebMvcConfiguration;
import org.mockserver.examples.proxy.web.controller.BooksPageEndToEndIntegrationTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assume.assumeThat;

/**
 * @author jamesdbloom
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({
    @ContextConfiguration(
        classes = {
            RootConfiguration.class
        }
    ),
    @ContextConfiguration(
        classes = {
            WebMvcConfiguration.class
        }
    )
})
@ActiveProfiles(profiles = {"backend", "googleClient"})
public class BooksPageGoogleClientSOCKSProxyEndToEndIntegrationTest extends BooksPageEndToEndIntegrationTest {

    @BeforeClass
    public static void setProxyType() {
        System.setProperty("http.proxyType", "SOCKS");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void testProxyTypeEnabled() {
        assumeThat("SOCKS5 is broken in JRE <9", System.getProperty("java.version"), not(anyOf(
            startsWith("1.7."), equalTo("1.7"),
            startsWith("7."), equalTo("7"),
            startsWith("1.8."), equalTo("1.8"),
            startsWith("8."), equalTo("8"))
        ));
    }

}
